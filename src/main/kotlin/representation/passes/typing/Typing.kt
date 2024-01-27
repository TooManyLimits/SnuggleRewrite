package representation.passes.typing

import builtins.BuiltinType
import reflection.ReflectedBuiltinType
import representation.asts.resolved.ResolvedAST
import representation.asts.resolved.ResolvedImplBlock
import representation.asts.resolved.ResolvedTypeDef
import representation.asts.typed.TypeDef
import representation.asts.typed.TypedAST
import representation.asts.typed.TypedFile
import util.ConsList.Companion.nil
import util.ConsMap
import util.caching.EqualityCache
import java.util.*

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
    val typeCache = TypingCache(builtins = ast.builtinMap, reflectedBuiltins = reflectedBuiltins)

    return TypedAST(ast.allFiles.mapValues {
        // Top-level code does not have any currentTypeGenerics.
        TypedFile(it.value.name, inferExpr(it.value.code, ConsMap(nil()), typeCache, null, null, listOf(), listOf()).expr)
    })
}

// The typing cache, with related useful extension methods.
// It also stores the mappings of builtin objects to their corresponding resolved type defs.
data class TypingCache(
    val basicCache: EqualityCache<ResolvedTypeDef, EqualityCache<List<TypeDef>, TypeDef>> = EqualityCache(),
    val tupleCache: EqualityCache<List<TypeDef>, TypeDef.Tuple> = EqualityCache(),
    val funcCache: EqualityCache<List<TypeDef>, EqualityCache<TypeDef, TypeDef.Func>> = EqualityCache(),

    val implBlockCache: EqualityCache<ResolvedImplBlock, EqualityCache<List<TypeDef>, TypeDef>> = EqualityCache(),

    val builtins: IdentityHashMap<BuiltinType, ResolvedTypeDef.Builtin>,
    val reflectedBuiltins: IdentityHashMap<Class<*>, ReflectedBuiltinType>
) {

    fun getBasic(base: ResolvedTypeDef, generics: List<TypeDef>, func: (ResolvedTypeDef, List<TypeDef>) -> TypeDef) =
        this.basicCache.get(base) { EqualityCache() }.get(generics) {func(base, it)}
    fun putBasic(base: ResolvedTypeDef, generics: List<TypeDef>, value: TypeDef) =
        this.getBasic(base, generics) { _, _ -> value}
    fun getImplBlock(base: ResolvedImplBlock, generics: List<TypeDef>, func: (ResolvedImplBlock, List<TypeDef>) -> TypeDef) =
        this.implBlockCache.get(base) { EqualityCache() }.get(generics) {func(base, it)}
    fun putImplBlock(base: ResolvedImplBlock, generics: List<TypeDef>, value: TypeDef) =
        this.getImplBlock(base, generics) { _, _ -> value}
    fun getTuple(elements: List<TypeDef>, func: (List<TypeDef>) -> TypeDef.Tuple) =
        this.tupleCache.get(elements) {func(elements)}
    fun putTuple(elements: List<TypeDef>, value: TypeDef.Tuple) =
        this.getTuple(elements) { value }
    fun getFunc(paramTypes: List<TypeDef>, returnType: TypeDef, func: (List<TypeDef>, TypeDef) -> TypeDef.Func) =
        this.funcCache.get(paramTypes) { EqualityCache() }.get(returnType) {func(paramTypes, returnType)}
    fun putFunc(paramTypes: List<TypeDef>, returnType: TypeDef, value: TypeDef.Func) =
        this.getFunc(paramTypes, returnType) { _, _ -> value }

}

