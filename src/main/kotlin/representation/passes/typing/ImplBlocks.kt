package representation.passes.typing

import representation.asts.resolved.ResolvedImplBlock
import representation.asts.typed.TypeDef
import java.util.Objects
import java.util.concurrent.atomic.AtomicInteger

/**
 * File handles impl blocks, the TypingCache, and instantiating them.
 * It's structured very similarly to Types.kt, so if the design seems
 * odd, that's probably why.
 */

fun getImplBlock(resolved: ResolvedImplBlock, generics: List<TypeDef>, cache: TypingCache): TypeDef {
    return instantiateImplBlock(resolved, generics, cache)
}

/**
 * Check whether the given impl block would accept the given typeDef to be "implemented" by it.
 * If it does match, provides the generics for the match. Otherwise, provides null.
 */
fun implBlockMatches(block: ResolvedImplBlock, typeDef: TypeDef): List<TypeDef>? {
    // Attempt to match the types. Only get the type generics, since that's all we care about.
    val typeMatchResult = matchTypes(block.unwrap().implType, typeDef)?.typeGenerics ?: return null
    // Make sure all the outputs are consistent with one another
    val result: Array<TypeDef?> = arrayOfNulls(block.unwrap().numGenerics)
    for ((index, type) in typeMatchResult) {
        if (result[index] == null)
            result[index] = type
        else if (result[index] != type)
            return null
    }
    if (result.any(Objects::isNull))
        return null

    return listOf(*result.requireNoNulls())
}


/**
 * Handle adding an indirection to the instantiation process.
 */
private fun indirect(base: ResolvedImplBlock, generics: List<TypeDef>,
                     typeCache: TypingCache, instantiator: (TypeDef.Indirection) -> TypeDef
): TypeDef {
    // Create the indirection and add it to the cache
    val indirection = TypeDef.Indirection()
    typeCache.putImplBlock(base, generics, indirection)
    // Instantiate, fill in the indirection with the result, and return.
    val instantiated = instantiator(indirection)
    indirection.promise.fulfill(instantiated)
    return indirection
}

// I wish there was a cleaner answer, and there probably is,
// but this is the best we're feeling up to right now
private val nextImplBlockId = AtomicInteger()

private fun instantiateImplBlock(base: ResolvedImplBlock, generics: List<TypeDef>, typeCache: TypingCache
): TypeDef = typeCache.getImplBlock(base, generics) { base, generics ->
    val base = base.unwrap()
    indirect(base, generics, typeCache) { indirection ->
        val implType = getTypeDef(base.implType, typeCache, generics, listOf())
        TypeDef.ImplBlockBackingDef(
            base.loc, "#ImplBlock#${nextImplBlockId.getAndIncrement()}", generics,
            base.methods.map { typeMethod(indirection, base.methods, it, typeCache, generics, implType) }
        )
    }
}