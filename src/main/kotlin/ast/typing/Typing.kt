package ast.typing

import ast.import_resolution.ImportResolvedAST
import ast.import_resolution.ImportResolvedType
import ast.import_resolution.ImportResolvedTypeDef
import ast.lexing.Loc
import builtins.BuiltinType
import util.ConsList
import util.ConsList.Companion.nil
import util.ConsMap
import util.EqualityCache
import util.IdentityCache

/**
 * Convert an ImportResolvedAST into a TypedAST.
 * While doing so, it's important to create a cache for TypeDef, so that multiple instances
 * of the same ImportResolvedType will map to the same TypeDef in the end.
 * This will be a 2-level cache.
 * ImportResolvedTypeDef, compare by identity -> List<TypeDef>, compare by equality -> TypeDef
 */
fun typeAST(ast: ImportResolvedAST): TypedAST {

    val cache: TypeDefCache = TypeDefCache()

    TypedAST(ast.allFiles.mapValues {
        TypedFile(it.value.name, infer(it.value.code, ConsMap(nil()), cache).expr)
    })

    throw RuntimeException()
}

/**
 * Get a TypeDef from an ImportResolvedType, using the cache for lookup.
 *
 * Small amount of mutability, from the cache.
 */
fun getTypeDef(type: ImportResolvedType, cache: TypeDefCache): TypeDef = when (type) {

    is ImportResolvedType.Basic -> instantiateBasicType(type.base, type.generics.map { getTypeDef(it, cache) }, cache)

}

// Instantiate a basic type in the given cache.
private fun instantiateBasicType(base: ImportResolvedTypeDef, generics: List<TypeDef>, cache: TypeDefCache): TypeDef = cache.get(base, generics) {
    _, _ ->
    // We have the base, and the generics which have been recursively instantiated.
    // Before instantiating the main type, create an indirection, and store it in the cache:
    val indirection = TypeDef.Indirection()
    cache.put(base, generics, indirection)
    // Now instantiate the type:
    val instantiated = instantiateType(base, generics, cache)
    // Fill in the indirection and return it.
    indirection.also { it.promise.fill(instantiated) }
}

// Helpers for quickly instantiating BuiltinType into TypeDef where it's needed.
fun getGenericBuiltin(type: BuiltinType, generics: List<TypeDef>, cache: TypeDefCache): TypeDef = instantiateBasicType(ImportResolvedTypeDef.Builtin(type), generics, cache)
fun getBasicBuiltin(type: BuiltinType, cache: TypeDefCache): TypeDef = getGenericBuiltin(type, listOf(), cache)


fun instantiateType(resolvedTypeDef: ImportResolvedTypeDef, generics: List<TypeDef>, cache: TypeDefCache): TypeDef = when (resolvedTypeDef) {

    is ImportResolvedTypeDef.Builtin -> TypeDef.InstantiatedBuiltin(resolvedTypeDef.builtin, generics)

}


// Alias for the type def cache, with related useful extension methods get() and put()
typealias TypeDefCache = IdentityCache<ImportResolvedTypeDef, EqualityCache<List<TypeDef>, TypeDef>>
private fun TypeDefCache.get(base: ImportResolvedTypeDef, generics: List<TypeDef>, func: (ImportResolvedTypeDef, List<TypeDef>) -> TypeDef) =
    this.get(base) {EqualityCache()}.get(generics) {func(base, it)}
private fun TypeDefCache.put(base: ImportResolvedTypeDef, generics: List<TypeDef>, value: TypeDef) =
    this.get(base, generics) {_, _ -> value}


