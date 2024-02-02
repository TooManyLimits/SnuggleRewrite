package snuggle.toomanylimits.representation.passes.typing

import snuggle.toomanylimits.builtins.BuiltinType
import snuggle.toomanylimits.errors.CompilationException
import snuggle.toomanylimits.errors.TypeCheckingException
import snuggle.toomanylimits.representation.asts.resolved.ResolvedType
import snuggle.toomanylimits.representation.asts.resolved.ResolvedTypeDef
import snuggle.toomanylimits.representation.asts.typed.FieldDef
import snuggle.toomanylimits.representation.asts.typed.TypeDef
import snuggle.toomanylimits.representation.passes.lexing.Loc

/**
 * File handles dealing with TypeDefs, instantiating them, and the TypingCache
 */

/**
 * Get a TypeDef from a ResolvedType, using the cache for lookup.
 *
 * Small amount of mutability, from the cache.
 */
fun getTypeDef(type: ResolvedType, typeCache: TypingCache, currentTypeGenerics: List<TypeDef>, currentMethodGenerics: List<TypeDef>): TypeDef = when (type) {
    is ResolvedType.Basic -> {
        // Recursively get type defs for the generics, then pass that
        // to type def instantiation.
        val mappedGenerics = type.generics.map { getTypeDef(it, typeCache, currentTypeGenerics, currentMethodGenerics) }
        instantiateTypeDef(type.base, mappedGenerics, typeCache)
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
private fun indirect(base: ResolvedTypeDef, generics: List<TypeDef>, typeCache: TypingCache,
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
private fun instantiateTypeDef(base: ResolvedTypeDef, generics: List<TypeDef>, typeCache: TypingCache): TypeDef = typeCache.getBasic(base, generics) { _, _ ->
    when (base) {
        // Indirections just recurse:
        is ResolvedTypeDef.Indirection -> instantiateTypeDef(
            base.promise.expect(),
            generics,
            typeCache,
        )
        // Builtins are easy, just remember to indirect:
        is ResolvedTypeDef.Builtin -> indirect(base, generics, typeCache) {
            TypeDef.InstantiatedBuiltin(base.builtin, generics, base, typeCache)
        }
        // Other types need more work replacing generics.
        is ResolvedTypeDef.Class -> indirect(base, generics, typeCache) { indirection ->
            val supertype = getTypeDef(base.superType, typeCache, generics, listOf())
            // Ensure that the supertype is extensible:
            if (!supertype.extensible) {
                throw InvalidSuperclassException(supertype, base.superType.loc)
            }
            TypeDef.ClassDef(
                base.loc, base.name, supertype, generics,
                // Note: This is where the currentTypeGenerics start out! The generics
                // passed into this method are used as currentTypeGenerics when instantiating/typing methods and fields.
                // Map the fields
                base.fields.map {
                    FieldDef.SnuggleFieldDef(
                        it.loc,
                        it.pub,
                        it.static,
                        it.mutable,
                        it.name,
                        null,
                        getTypeDef(it.annotatedType, typeCache, generics, listOf())
                    )
                },
                // Map the methods, using the indirection as the "this"/owning type.
                base.methods.map { typeMethod(indirection, base.methods, it, typeCache, generics, null) },
                base
            )
        }
        is ResolvedTypeDef.Struct -> indirect(base, generics, typeCache) { indirection ->
            var currentPluralOffset = 0
            TypeDef.StructDef(
                base.loc, base.name, generics,
                base.fields.map {
                    val fieldType = getTypeDef(it.annotatedType, typeCache, generics, listOf())
                    val res = FieldDef.SnuggleFieldDef(
                        it.loc,
                        it.pub,
                        it.static,
                        false,
                        it.name,
                        currentPluralOffset,
                        fieldType
                    )
                    currentPluralOffset += fieldType.stackSlots
                    res
                },
                base.methods.map { typeMethod(indirection, base.methods, it, typeCache, generics, null) },
                base
            )
        }
    }
}

// Helpers for quickly getting certain TypeDefs where they're needed.
fun getGenericBuiltin(type: BuiltinType, generics: List<TypeDef>, typeCache: TypingCache): TypeDef =
    instantiateTypeDef(typeCache.builtins[type]!!, generics, typeCache)

fun getBasicBuiltin(type: BuiltinType, cache: TypingCache): TypeDef =
    getGenericBuiltin(type, listOf(), cache)

fun getTuple(elements: List<TypeDef>, cache: TypingCache): TypeDef.Tuple =
    cache.getTuple(elements) { TypeDef.Tuple(it) }

fun getUnit(cache: TypingCache): TypeDef.Tuple = getTuple(listOf(), cache)
fun getFunc(paramTypes: List<TypeDef>, returnType: TypeDef, cache: TypingCache): TypeDef.Func =
    cache.getFunc(paramTypes, returnType) { p, r -> TypeDef.Func(p, r, cache) }

fun getReflectedBuiltin(jClass: Class<*>, cache: TypingCache): TypeDef =
    getBasicBuiltin(
        cache.reflectedBuiltins[jClass]
            ?: throw IllegalStateException("Attempt to get builtin for java class \"${jClass.name}\", but none was registered?"),
        cache
    )

class InvalidSuperclassException(attemptedSupertype: TypeDef, loc: Loc)
    : CompilationException("Type \"${attemptedSupertype.name}\" cannot be used as a superclass!", loc)