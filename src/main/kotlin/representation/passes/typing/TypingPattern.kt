package representation.passes.typing

import builtins.IntLiteralType
import errors.CompilationException
import errors.TypeCheckingException
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
    is ResolvedPattern.EmptyPattern -> pattern.typeAnnotation != null
    is ResolvedPattern.BindingPattern -> pattern.typeAnnotation != null
    is ResolvedPattern.TuplePattern -> pattern.elements.all { isExplicitlyTyped(it) }
}

/**
 * Create a typed pattern by checking this pattern against the inferred
 * scrutinee type, and ensuring it matches.
 */
fun checkPattern(pattern: ResolvedPattern, scrutineeType: TypeDef, topIndex: Int, typeCache: TypingCache, currentTypeGenerics: List<TypeDef>, currentMethodGenerics: List<TypeDef>): TypedPattern {
    // If scrutinee is an unstorable type, like IntLiteral, error here
    if (scrutineeType.builtin == IntLiteralType)
        throw CompilationException("Cannot infer type of literal - try adding more annotations", pattern.loc)
    // Switch on the type of pattern
    return when (pattern) {
        // Largely the same between EmptyPattern and BindingPattern
        is ResolvedPattern.EmptyPattern -> {
            if (pattern.typeAnnotation != null) {
                val explicitAnnotation = getTypeDef(pattern.typeAnnotation, typeCache, currentTypeGenerics, currentMethodGenerics)
                if (!scrutineeType.isSubtype(explicitAnnotation))
                    throw TypeCheckingException("RHS of type ${scrutineeType.name} does not match pattern of type ${explicitAnnotation.name}", pattern.loc)
                TypedPattern.EmptyPattern(pattern.loc, explicitAnnotation)
            } else {
                TypedPattern.EmptyPattern(pattern.loc, scrutineeType)
            }
        }
        is ResolvedPattern.BindingPattern -> {
            if (pattern.typeAnnotation != null) {
                // Check that the scrutinee is a subtype of the type annotation, if this has one
                val explicitAnnotation = getTypeDef(pattern.typeAnnotation, typeCache, currentTypeGenerics, currentMethodGenerics)
                if (!scrutineeType.isSubtype(explicitAnnotation))
                    throw TypeCheckingException("Scrutinee of type ${scrutineeType.name} does not match pattern of type ${explicitAnnotation.name}", pattern.loc)
                TypedPattern.BindingPattern(pattern.loc, explicitAnnotation, pattern.name, pattern.isMut, topIndex)
            } else {
                // Otherwise, just assume it matches
                TypedPattern.BindingPattern(pattern.loc, scrutineeType, pattern.name, pattern.isMut, topIndex)
            }
        }
        is ResolvedPattern.TuplePattern -> {
            val scrutineeType = scrutineeType.unwrap()
            // Need to recursively check the inner patterns here. First, ensure scrutinee is valid:
            if (scrutineeType !is TypeDef.Tuple)
                throw TypeCheckingException("Scrutinee of type ${scrutineeType.name} is not a tuple", pattern.loc)
            if (scrutineeType.innerTypes.size != pattern.elements.size)
                throw TypeCheckingException("Scrutinee tuple has ${scrutineeType.innerTypes.size} elements, but pattern has ${pattern.elements.size} elements", pattern.loc)
            // Now, recursively check the inner elements with the inner types of the tuple:
            var topIndex = topIndex // enable reassignment
            val checkedElements = pattern.elements.zip(scrutineeType.innerTypes).map {
                checkPattern(it.first, it.second, topIndex, typeCache, currentTypeGenerics, currentMethodGenerics)
                    .also { topIndex += it.type.stackSlots }
            }
            // And return.
            val patternType = getTuple(checkedElements.map { it.type }, typeCache)
            TypedPattern.TuplePattern(pattern.loc, patternType, checkedElements)
        }
    }
}

/**
 * Attempt to create a typed pattern out of only the import-resolved
 * pattern. Only possible when the pattern is explicitly typed.
 */
fun inferPattern(pattern: ResolvedPattern, topIndex: Int, typeCache: TypingCache, currentTypeGenerics: List<TypeDef>, currentMethodGenerics: List<TypeDef>): TypedPattern = when (pattern) {
    is ResolvedPattern.EmptyPattern -> {
        val annotatedType = pattern.typeAnnotation ?: throw IllegalStateException("Bug in compiler - attempt to infer pattern that isn't explicitly typed? Please report!")
        val type = getTypeDef(annotatedType, typeCache, currentTypeGenerics, currentMethodGenerics)
        TypedPattern.EmptyPattern(pattern.loc, type)
    }
    is ResolvedPattern.BindingPattern -> {
        val annotatedType = pattern.typeAnnotation ?: throw IllegalStateException("Bug in compiler - attempt to infer pattern that isn't explicitly typed? Please report!")
        val type = getTypeDef(annotatedType, typeCache, currentTypeGenerics, currentMethodGenerics)
        TypedPattern.BindingPattern(pattern.loc, type, pattern.name, pattern.isMut, topIndex)
    }
    is ResolvedPattern.TuplePattern -> {
        var topIndex = topIndex // enable reassignment
        // Resolve inner ones, combine
        val inferredElements = pattern.elements.map {
            inferPattern(it, topIndex, typeCache, currentTypeGenerics, currentMethodGenerics)
                .also { topIndex += it.type.stackSlots }
        }
        val type = getTuple(inferredElements.map { it.type }, typeCache)
        TypedPattern.TuplePattern(pattern.loc, type, inferredElements)
    }
}

/**
 * Check if the pattern is fallible.
 */
fun isFallible(pattern: TypedPattern): Boolean = when (pattern) {
    is TypedPattern.EmptyPattern -> false
    is TypedPattern.BindingPattern -> false
    is TypedPattern.TuplePattern -> false
}

/**
 * Get the bindings that will be added if the pattern successfully matches.
 * Returns the bindings.
 */
fun bindings(pattern: TypedPattern): ConsMap<String, VariableBinding> {
    // Top index is the index of the thing on top of the stack, plus its # of stack slots.
    return when (pattern) {
        is TypedPattern.EmptyPattern -> ConsMap.of()
        is TypedPattern.BindingPattern -> ConsMap.of(pattern.name to VariableBinding(pattern.type, pattern.isMut, pattern.variableIndex))
        is TypedPattern.TuplePattern -> {
            ConsMap.join(pattern.elements.map(::bindings))
        }
    }
}

// Add the given number to the variable indices of the pattern.
fun shiftPatternIndex(pattern: TypedPattern, indexShift: Int): TypedPattern = when (pattern) {
    is TypedPattern.EmptyPattern -> pattern
    is TypedPattern.BindingPattern -> TypedPattern.BindingPattern(pattern.loc, pattern.type, pattern.name, pattern.isMut, pattern.variableIndex + indexShift)
    is TypedPattern.TuplePattern -> TypedPattern.TuplePattern(pattern.loc, pattern.type, pattern.elements.map { shiftPatternIndex(it, indexShift) })
}

fun getTopIndex(scope: ConsMap<String, VariableBinding>): Int =
    scope.firstOrNull()?.second?.let { it.index + it.type.stackSlots } ?: 0