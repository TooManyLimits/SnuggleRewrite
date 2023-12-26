package representation.passes.typing

import representation.asts.resolved.ResolvedPattern
import util.ConsMap

data class VariableBinding(val type: TypeDef, val mutable: Boolean)

/**
 * Code to deal with Patterns in the Typing phase.
 */

/**
 * Check if the given pattern is explicitly typed, like
 * "(a: bool, b: i32)", or implicitly typed, like
 * "x" or "(a: i64, b)"
 * In the case of declarations, this informs whether
 * the right side of the declaration should be check()ed
 * or infer()red, and how we deal with the pattern later on.
 */
fun isExplicitlyTyped(pattern: ResolvedPattern): Boolean = when (pattern) {
    // Binding: explicitly typed if it has a type annotation
    is ResolvedPattern.BindingPattern -> pattern.typeAnnotation != null
}

/**
 * Create a typed pattern by checking this pattern against the known
 * scrutinee type, and ensuring it matches.
 */
fun checkPattern(pattern: ResolvedPattern, scrutineeType: TypeDef, typeCache: TypeDefCache): TypedPattern = when (pattern) {
    is ResolvedPattern.BindingPattern -> {
        // TODO: Check that the scrutinee is a subtype of the type annotation, if this has one
        val type = getTypeDef(pattern.typeAnnotation!!, typeCache)
        TypedPattern.BindingPattern(pattern.loc, type, pattern.name, pattern.isMut)
    }
}

/**
 * Attempt to create a typed pattern out of only the import-resolved
 * pattern. Only possible when the pattern is explicitly typed.
 */
fun inferPattern(pattern: ResolvedPattern, typeCache: TypeDefCache): TypedPattern = when (pattern) {
    is ResolvedPattern.BindingPattern -> {
        val annotatedType = pattern.typeAnnotation ?: throw IllegalStateException("Bug in compiler - attempt to infer pattern that isn't explicitly typed? Please report!")
        val type = getTypeDef(annotatedType, typeCache)
        TypedPattern.BindingPattern(pattern.loc, type, pattern.name, pattern.isMut)
    }
}

/**
 * Check if the pattern is fallible.
 */
fun isFallible(pattern: TypedPattern): Boolean = when (pattern) {
    is TypedPattern.BindingPattern -> false
}

/**
 * Get the bindings that will be added if the pattern successfully matches.
 */
fun bindings(pattern: TypedPattern): ConsMap<String, VariableBinding> = when (pattern) {
    is TypedPattern.BindingPattern -> ConsMap.of(pattern.name to VariableBinding(pattern.type, pattern.isMut))
}