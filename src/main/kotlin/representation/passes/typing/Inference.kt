package representation.passes.typing

import representation.asts.parsed.ParsedType
import representation.asts.resolved.ResolvedType
import representation.asts.typed.FromTypeHead
import representation.asts.typed.TypeDef
import java.util.Objects

data class TypeMatchingResult(
    val typeGenerics: List<Pair<Int, TypeDef>>,
    val methodGenerics: List<Pair<Int, TypeDef>>
)

// Results in a list of pairs. In each pair, the first element represents the index of the generic,
// and the second element represents the inferred type for said generic. There can be multiple entries
// with the same first element but different second elements, which could mean that there's a conflict.
// (for example, generic 0 is simultaneously i32 and String, if both (0 to i32) and (0 to String) are in
// the output list)
// If this returns null, then the match was inconsistent.
fun matchTypes(resolvedTypePattern: ResolvedType, concreteTypeScrutinee: TypeDef): TypeMatchingResult? {
    val concreteTypeScrutinee = concreteTypeScrutinee.unwrap()

    if (resolvedTypePattern is ResolvedType.TypeGeneric)
        return TypeMatchingResult(listOf(resolvedTypePattern.index to concreteTypeScrutinee), listOf())
    if (resolvedTypePattern is ResolvedType.MethodGeneric)
        return TypeMatchingResult(listOf(), listOf(resolvedTypePattern.index to concreteTypeScrutinee))

    val nestedPatterns = when (resolvedTypePattern) {
        is ResolvedType.Basic -> resolvedTypePattern.generics
        is ResolvedType.Tuple -> resolvedTypePattern.elements
        is ResolvedType.Func -> resolvedTypePattern.paramTypes + resolvedTypePattern.returnType
        else -> throw IllegalStateException()
    }

    // If type heads don't match, can return early
    if (resolvedTypePattern is ResolvedType.Basic && concreteTypeScrutinee is FromTypeHead) {
        if (resolvedTypePattern.base != concreteTypeScrutinee.typeHead)
            return null
    }

    // Type heads match, compare generics
    if (
        resolvedTypePattern is ResolvedType.Basic && concreteTypeScrutinee is FromTypeHead ||
        resolvedTypePattern is ResolvedType.Tuple && concreteTypeScrutinee is TypeDef.Tuple ||
        resolvedTypePattern is ResolvedType.Func && concreteTypeScrutinee is TypeDef.Func
    ) {
        return nestedPatterns.zip(concreteTypeScrutinee.generics).asSequence()
            .map { matchTypes(it.first, it.second) } // Recurse on generics
            .also { if (it.any(Objects::isNull)) return null } // If any was null, return null immediately
            .map { it!!.typeGenerics to it.methodGenerics } //Non-null
            .unzip()
            .let { TypeMatchingResult(
                it.first.flatten(),
                it.second.flatten()
            ) }
    }

    return null
}
