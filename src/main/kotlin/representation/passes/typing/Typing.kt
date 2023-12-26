package representation.passes.typing

import representation.asts.resolved.ResolvedAST
import representation.asts.resolved.ResolvedType
import representation.asts.resolved.ResolvedTypeDef
import builtins.BuiltinType
import representation.asts.typed.TypeDef
import representation.asts.typed.TypedAST
import representation.asts.typed.TypedFile
import util.ConsList.Companion.nil
import util.ConsMap
import util.EqualityCache
import util.IdentityCache

/**
 * Convert a ResolvedAST into a TypedAST.
 * While doing so, it's important to create a cache for TypeDef, so that multiple instances
 * of the same ResolvedType will map to the same TypeDef in the end.
 * This will be a 2-level cache.
 * ResolvedTypeDef, compare by identity -> List<TypeDef>, compare by equality -> TypeDef
 */
fun typeAST(ast: ResolvedAST): TypedAST {

    val typeCache = TypeDefCache(IdentityCache(), ast.builtinMap)

    return TypedAST(ast.allFiles.mapValues {
        TypedFile(it.value.name, inferExpr(it.value.code, ConsMap(nil()), typeCache).expr)
    })
}

/**
 * Get a TypeDef from a ResolvedType, using the cache for lookup.
 *
 * Small amount of mutability, from the cache.
 */
fun getTypeDef(type: ResolvedType, typeCache: TypeDefCache): TypeDef = when (type) {

    is ResolvedType.Basic -> {
        // Recursively get type defs for the generics, then pass that
        // to type def instantiation.
        val mappedGenerics = type.generics.map { getTypeDef(it, typeCache) }
        instantiateTypeDef(type.base, mappedGenerics, typeCache)
    }

}

/**
 * Handle adding an indirection to the instantiation process.
 */
private inline fun indirect(base: ResolvedTypeDef, generics: List<TypeDef>, typeCache: TypeDefCache,
                            instantiator: (TypeDef.Indirection) -> TypeDef
): TypeDef {
    // Create the indirection and add it to the cache
    val indirection = TypeDef.Indirection()
    typeCache.put(base, generics, indirection)
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
private fun instantiateTypeDef(base: ResolvedTypeDef, generics: List<TypeDef>, typeCache: TypeDefCache): TypeDef = typeCache.get(base, generics) { _, _ ->
    when (base) {
        // Indirections just recurse:
        is ResolvedTypeDef.Indirection -> instantiateTypeDef(base.promise.expect(), generics, typeCache)
        // Builtins are easy, just remember to indirect:
        is ResolvedTypeDef.Builtin -> indirect(base, generics, typeCache) {
            TypeDef.InstantiatedBuiltin(base.builtin, generics, typeCache)
        }
        // Other types need more work replacing generics.
        is ResolvedTypeDef.Class -> indirect(base, generics, typeCache) { indirection ->
            val superType = getTypeDef(base.superType, typeCache)
            TypeDef.ClassDef(
                base.loc, base.name, superType, generics,
                // Map the fields
                base.fields.map { throw RuntimeException("Fields not implemented yet") },
                // Map the methods, using the indirection as the "this"/owning type.
                base.methods.map { typeMethod(indirection, it, typeCache) }
            )
        }
    }
}

// Helpers for quickly instantiating BuiltinType into TypeDef where it's needed.
fun getGenericBuiltin(type: BuiltinType, generics: List<TypeDef>, typeCache: TypeDefCache): TypeDef =
    instantiateTypeDef(typeCache.builtins[type]!!, generics, typeCache)
fun getBasicBuiltin(type: BuiltinType, cache: TypeDefCache): TypeDef =
    getGenericBuiltin(type, listOf(), cache)


// The type def cache, with related useful extension methods get() and put().
// It also stores the mapping of builtin objects to their corresponding resolved type defs.
data class TypeDefCache(
    val typeDefs: IdentityCache<ResolvedTypeDef, EqualityCache<List<TypeDef>, TypeDef>>,
    val builtins: Map<BuiltinType, ResolvedTypeDef>
)
private fun TypeDefCache.get(base: ResolvedTypeDef, generics: List<TypeDef>, func: (ResolvedTypeDef, List<TypeDef>) -> TypeDef) =
    this.typeDefs.get(base) {EqualityCache()}.get(generics) {func(base, it)}
private fun TypeDefCache.put(base: ResolvedTypeDef, generics: List<TypeDef>, value: TypeDef) =
    this.get(base, generics) {_, _ -> value}


