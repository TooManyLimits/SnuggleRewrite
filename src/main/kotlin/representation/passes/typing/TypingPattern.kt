package representation.passes.typing

import builtins.IntLiteralType
import errors.CompilationException
import errors.InferenceException
import representation.asts.resolved.ResolvedPattern
import representation.asts.typed.TypeDef
import representation.asts.typed.TypedPattern
import util.ConsMap

data class VariableBinding(val type: TypeDef, val mutable: Boolean, val index: Int)

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
fun checkPattern(pattern: ResolvedPattern, scrutineeType: TypeDef, typeCache: TypeDefCache, currentTypeGenerics: List<TypeDef>): TypedPattern = when (pattern) {
    is ResolvedPattern.BindingPattern -> {
        // If scrutinee is an unstorable type, like IntLiteral, error here
        if (scrutineeType.builtin == IntLiteralType)
            throw InferenceException("Cannot infer type of literal - try adding more annotations", pattern.loc)
        // TODO: Check that the scrutinee is a subtype of the type annotation, if this has one
        TypedPattern.BindingPattern(pattern.loc, scrutineeType, pattern.name, pattern.isMut)
    }
}

/**
 * Attempt to create a typed pattern out of only the import-resolved
 * pattern. Only possible when the pattern is explicitly typed.
 */
fun inferPattern(pattern: ResolvedPattern, typeCache: TypeDefCache, currentTypeGenerics: List<TypeDef>): TypedPattern = when (pattern) {
    is ResolvedPattern.BindingPattern -> {
        val annotatedType = pattern.typeAnnotation ?: throw IllegalStateException("Bug in compiler - attempt to infer pattern that isn't explicitly typed? Please report!")
        val type = getTypeDef(annotatedType, typeCache, currentTypeGenerics)
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
 * Returns the bindings as well as the local variable index where it's stored.
 */
fun bindings(pattern: TypedPattern, curScope: ConsMap<String, VariableBinding>): Pair<ConsMap<String, VariableBinding>, Int> {
    // Top index is the index of the thing on top of the stack, plus its # of stack slots.
    val topIndex = curScope.firstOrNull()?.second?.let { it.index + it.type.stackSlots } ?: 0
    return when (pattern) {
        is TypedPattern.BindingPattern -> ConsMap.of(pattern.name to VariableBinding(pattern.type, pattern.isMut, topIndex))
    } to topIndex
}