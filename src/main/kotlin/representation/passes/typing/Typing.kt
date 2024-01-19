package representation.passes.typing

import representation.asts.resolved.ResolvedAST
import representation.asts.resolved.ResolvedType
import representation.asts.resolved.ResolvedTypeDef
import builtins.BuiltinType
import reflection.ReflectedBuiltinType
import representation.asts.typed.FieldDef
import representation.asts.typed.TypeDef
import representation.asts.typed.TypedAST
import representation.asts.typed.TypedFile
import util.ConsList.Companion.nil
import util.ConsMap
import util.caching.EqualityCache
import util.caching.IdentityCache
import java.util.IdentityHashMap

/**
 * Convert a ResolvedAST into a TypedAST.
 * While doing so, it's important to create a cache for TypeDef, so that multiple instances
 * of the same ResolvedType will map to the same TypeDef in the end.
 * This will be a 2-level cache.
 * ResolvedTypeDef, compare by identity -> List<TypeDef>, compare by equality -> TypeDef.
 * Other special types like Tuple and Function will also have their own caches.
 */
fun typeAST(ast: ResolvedAST): TypedAST {

    val reflectedBuiltins = ast.builtinMap.keys.filterIsInstance<ReflectedBuiltinType>().associateByTo(IdentityHashMap()) { it.reflectedClass }
    val typeCache = TypeDefCache(IdentityCache(), EqualityCache(), EqualityCache(), ast.builtinMap, reflectedBuiltins)

    return TypedAST(ast.allFiles.mapValues {
        // Top-level code does not have any currentTypeGenerics.
        TypedFile(it.value.name, inferExpr(it.value.code, ConsMap(nil()), typeCache, null, null, listOf(), listOf()).expr)
    })
}

/**
 * Get a TypeDef from a ResolvedType, using the cache for lookup.
 *
 * Small amount of mutability, from the cache.
 */
fun getTypeDef(type: ResolvedType, typeCache: TypeDefCache, currentTypeGenerics: List<TypeDef>, currentMethodGenerics: List<TypeDef>): TypeDef = when (type) {
    is ResolvedType.Basic -> {
        // Recursively get type defs for the generics, then pass that
        // to type def instantiation.
        val mappedGenerics = type.generics.map { getTypeDef(it, typeCache, currentTypeGenerics, currentMethodGenerics) }
        instantiateTypeDef(type.base, mappedGenerics, typeCache, currentTypeGenerics, currentMethodGenerics)
    }
    is ResolvedType.TypeGeneric -> {
        // Get the correct type generic from the list and output it.
        currentTypeGenerics[type.index]
    }
    is ResolvedType.MethodGeneric -> currentMethodGenerics[type.index] // Same here
    is ResolvedType.Tuple -> {
        // Map the elements and query the tuple cache
        val mappedElements = type.elements.map { getTypeDef(it, typeCache, currentTypeGenerics, currentMethodGenerics) }
        typeCache.getTuple(mappedElements) { TypeDef.Tuple(it) }
    }
    is ResolvedType.Func -> {
        val mappedParams = type.paramTypes.map { getTypeDef(it, typeCache, currentTypeGenerics, currentMethodGenerics) }
        val mappedReturn = type.returnType.let { getTypeDef(it, typeCache, currentTypeGenerics, currentMethodGenerics) }
        typeCache.getFunc(mappedParams, mappedReturn) { p, r -> TypeDef.Func(p, r, typeCache) }
    }
}

/**
 * Handle adding an indirection to the instantiation process.
 */
private fun indirect(base: ResolvedTypeDef, generics: List<TypeDef>, typeCache: TypeDefCache,
                            instantiator: (TypeDef.Indirection) -> TypeDef
): TypeDef {
    // Create the indirection and add it to the cache
    val indirection = TypeDef.Indirection()
    typeCache.putBasic(base, generics, indirection)
    // Instantiate, fill in the indirection with the result, and return.
    val instantiated = instantiator(indirection)
    indirection.promise.fulfill(instantiated)
    return indirection
}

/**
 * If the instantiation was not already cached, then
 * instantiate a given type with the given generics
 * and cache the result.
 */
private fun instantiateTypeDef(base: ResolvedTypeDef, generics: List<TypeDef>, typeCache: TypeDefCache, currentTypeGenerics: List<TypeDef>, currentMethodGenerics: List<TypeDef>): TypeDef = typeCache.getBasic(base, generics) { _, _ ->
    when (base) {
        // Indirections just recurse:
        is ResolvedTypeDef.Indirection -> instantiateTypeDef(base.promise.expect(), generics, typeCache, currentTypeGenerics, currentMethodGenerics)
        // Builtins are easy, just remember to indirect:
        is ResolvedTypeDef.Builtin -> indirect(base, generics, typeCache) {
            TypeDef.InstantiatedBuiltin(base.builtin, generics, typeCache)
        }
        // Other types need more work replacing generics.
        is ResolvedTypeDef.Class -> indirect(base, generics, typeCache) { indirection ->
            val supertype = getTypeDef(base.superType, typeCache, generics, listOf())
            TypeDef.ClassDef(
                base.loc, base.name, supertype, generics,
                // Note: This is where the currentTypeGenerics start out! The generics
                // passed into this method are used as currentTypeGenerics when instantiating/typing methods and fields.
                // Map the fields
                base.fields.map { FieldDef.SnuggleFieldDef(it.loc, it.pub, it.static, it.mutable, it.name, null, getTypeDef(it.annotatedType, typeCache, generics, listOf())) },
                // Map the methods, using the indirection as the "this"/owning type.
                base.methods.map { typeMethod(indirection, base.methods, it, typeCache, generics) }
            )
        }
        is ResolvedTypeDef.Struct -> indirect(base, generics, typeCache) { indirection ->
            var currentPluralOffset = 0
            TypeDef.StructDef(
                base.loc, base.name, generics,
                base.fields.map {
                    val fieldType = getTypeDef(it.annotatedType, typeCache, generics, listOf())
                    val res = FieldDef.SnuggleFieldDef(it.loc, it.pub, it.static, false, it.name, currentPluralOffset, fieldType)
                    currentPluralOffset += fieldType.stackSlots
                    res
                },
                base.methods.map { typeMethod(indirection, base.methods, it, typeCache, generics) }
            )
        }
    }
}

// Helpers for quickly getting certain TypeDefs where they're needed.
fun getGenericBuiltin(type: BuiltinType, generics: List<TypeDef>, typeCache: TypeDefCache): TypeDef =
    instantiateTypeDef(typeCache.builtins[type]!!, generics, typeCache, listOf(), listOf())
fun getBasicBuiltin(type: BuiltinType, cache: TypeDefCache): TypeDef =
    getGenericBuiltin(type, listOf(), cache)
fun getTuple(elements: List<TypeDef>, cache: TypeDefCache): TypeDef.Tuple =
    cache.getTuple(elements) { TypeDef.Tuple(it) }
fun getUnit(cache: TypeDefCache): TypeDef.Tuple = getTuple(listOf(), cache)
fun getFunc(paramTypes: List<TypeDef>, returnType: TypeDef, cache: TypeDefCache): TypeDef.Func =
    cache.getFunc(paramTypes, returnType) { p, r -> TypeDef.Func(p, r, cache) }
fun getReflectedBuiltin(jClass: Class<*>, cache: TypeDefCache): TypeDef =
    getBasicBuiltin(cache.reflectedBuiltins[jClass] ?: throw IllegalStateException("Attempt to get builtin for java class \"${jClass.name}\", but none was registered?"), cache)

// The type def cache, with related useful extension methods get() and put().
// It also stores the mapping of builtin objects to their corresponding resolved type defs.
data class TypeDefCache(
    val basicCache: IdentityCache<ResolvedTypeDef, EqualityCache<List<TypeDef>, TypeDef>>,
    val tupleCache: EqualityCache<List<TypeDef>, TypeDef.Tuple>,
    val funcCache: EqualityCache<List<TypeDef>, IdentityCache<TypeDef, TypeDef.Func>>,
    val builtins: IdentityHashMap<BuiltinType, ResolvedTypeDef.Builtin>,
    val reflectedBuiltins: IdentityHashMap<Class<*>, ReflectedBuiltinType>
)
private fun TypeDefCache.getBasic(base: ResolvedTypeDef, generics: List<TypeDef>, func: (ResolvedTypeDef, List<TypeDef>) -> TypeDef) =
    this.basicCache.get(base) { EqualityCache() }.get(generics) {func(base, it)}
private fun TypeDefCache.putBasic(base: ResolvedTypeDef, generics: List<TypeDef>, value: TypeDef) =
    this.getBasic(base, generics) { _, _ -> value}
private fun TypeDefCache.getTuple(elements: List<TypeDef>, func: (List<TypeDef>) -> TypeDef.Tuple) =
    this.tupleCache.get(elements) {func(elements)}
private fun TypeDefCache.putTuple(elements: List<TypeDef>, value: TypeDef.Tuple) =
    this.getTuple(elements) { value }
private fun TypeDefCache.getFunc(paramTypes: List<TypeDef>, returnType: TypeDef, func: (List<TypeDef>, TypeDef) -> TypeDef.Func) =
    this.funcCache.get(paramTypes) {IdentityCache()}.get(returnType) {func(paramTypes, returnType)}
private fun TypeDefCache.putFunc(paramTypes: List<TypeDef>, returnType: TypeDef, value: TypeDef.Func) =
    this.getFunc(paramTypes, returnType) { _, _ -> value }
