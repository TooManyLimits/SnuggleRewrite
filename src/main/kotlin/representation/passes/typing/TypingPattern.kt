package representation.passes.typing

import builtins.primitive.FloatLiteralType
import builtins.primitive.IntLiteralType
import errors.CompilationException
import errors.TypeCheckingException
import representation.asts.resolved.ResolvedInfalliblePattern
import representation.asts.typed.TypeDef
import representation.asts.typed.TypedInfalliblePattern
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
fun isExplicitlyTyped(pattern: ResolvedInfalliblePattern): Boolean = when (pattern) {
    // Binding: explicitly typed if it has a type annotation
    is ResolvedInfalliblePattern.Empty -> pattern.typeAnnotation != null
    is ResolvedInfalliblePattern.Binding -> pattern.typeAnnotation != null
    is ResolvedInfalliblePattern.Tuple -> pattern.elements.all { isExplicitlyTyped(it) }
}

/**
 * Create a typed pattern by checking this pattern against the inferred
 * scrutinee type, and ensuring it matches.
 */
fun checkPattern(pattern: ResolvedInfalliblePattern, scrutineeType: TypeDef, topIndex: Int, typeCache: TypingCache, currentTypeGenerics: List<TypeDef>, currentMethodGenerics: List<TypeDef>): TypedInfalliblePattern {
    // If scrutinee is an unstorable type, like IntLiteral, error here
    if (scrutineeType.builtin == IntLiteralType || scrutineeType.builtin == FloatLiteralType)
        throw CompilationException("Cannot infer type of literal - try adding more annotations", pattern.loc)
    // Switch on the type of pattern
    return when (pattern) {
        // Largely the same between EmptyPattern and BindingPattern
        is ResolvedInfalliblePattern.Empty -> {
            if (pattern.typeAnnotation != null) {
                val explicitAnnotation = getTypeDef(pattern.typeAnnotation, typeCache, currentTypeGenerics, currentMethodGenerics)
                if (!scrutineeType.isSubtype(explicitAnnotation))
                    throw TypeCheckingException("RHS of type ${scrutineeType.name} does not match pattern of type ${explicitAnnotation.name}", pattern.loc)
                TypedInfalliblePattern.Empty(pattern.loc, explicitAnnotation)
            } else {
                TypedInfalliblePattern.Empty(pattern.loc, scrutineeType)
            }
        }
        is ResolvedInfalliblePattern.Binding -> {
            if (pattern.typeAnnotation != null) {
                // Check that the scrutinee is a subtype of the type annotation, if this has one
                val explicitAnnotation = getTypeDef(pattern.typeAnnotation, typeCache, currentTypeGenerics, currentMethodGenerics)
                if (!scrutineeType.isSubtype(explicitAnnotation))
                    throw TypeCheckingException("Scrutinee of type ${scrutineeType.name} does not match pattern of type ${explicitAnnotation.name}", pattern.loc)
                TypedInfalliblePattern.Binding(pattern.loc, explicitAnnotation, pattern.name, pattern.isMut, topIndex)
            } else {
                // Otherwise, just assume it matches
                TypedInfalliblePattern.Binding(pattern.loc, scrutineeType, pattern.name, pattern.isMut, topIndex)
            }
        }
        is ResolvedInfalliblePattern.Tuple -> {
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
            TypedInfalliblePattern.Tuple(pattern.loc, patternType, checkedElements)
        }
    }
}

/**
 * Attempt to create a typed pattern out of only the import-resolved
 * pattern. Only possible when the pattern is explicitly typed.
 */
fun inferPattern(pattern: ResolvedInfalliblePattern, topIndex: Int, typeCache: TypingCache, currentTypeGenerics: List<TypeDef>, currentMethodGenerics: List<TypeDef>): TypedInfalliblePattern = when (pattern) {
    is ResolvedInfalliblePattern.Empty -> {
        val annotatedType = pattern.typeAnnotation ?: throw IllegalStateException("Bug in compiler - attempt to infer pattern that isn't explicitly typed? Please report!")
        val type = getTypeDef(annotatedType, typeCache, currentTypeGenerics, currentMethodGenerics)
        TypedInfalliblePattern.Empty(pattern.loc, type)
    }
    is ResolvedInfalliblePattern.Binding -> {
        val annotatedType = pattern.typeAnnotation ?: throw IllegalStateException("Bug in compiler - attempt to infer pattern that isn't explicitly typed? Please report!")
        val type = getTypeDef(annotatedType, typeCache, currentTypeGenerics, currentMethodGenerics)
        TypedInfalliblePattern.Binding(pattern.loc, type, pattern.name, pattern.isMut, topIndex)
    }
    is ResolvedInfalliblePattern.Tuple -> {
        var topIndex = topIndex // enable reassignment
        // Resolve inner ones, combine
        val inferredElements = pattern.elements.map {
            inferPattern(it, topIndex, typeCache, currentTypeGenerics, currentMethodGenerics)
                .also { topIndex += it.type.stackSlots }
        }
        val type = getTuple(inferredElements.map { it.type }, typeCache)
        TypedInfalliblePattern.Tuple(pattern.loc, type, inferredElements)
    }
}

/**
 * Get the bindings that will be added if the pattern successfully matches.
 * Returns the bindings.
 */
fun bindings(pattern: TypedInfalliblePattern): ConsMap<String, VariableBinding> {
    // Top index is the index of the thing on top of the stack, plus its # of stack slots.
    return when (pattern) {
        is TypedInfalliblePattern.Empty -> ConsMap.of()
        is TypedInfalliblePattern.Binding -> ConsMap.of(pattern.name to VariableBinding(pattern.type, pattern.isMut, pattern.variableIndex))
        is TypedInfalliblePattern.Tuple -> {
            ConsMap.join(pattern.elements.map(::bindings))
        }
    }
}

// Add the given number to the variable indices of the pattern.
fun shiftPatternIndex(pattern: TypedInfalliblePattern, indexShift: Int): TypedInfalliblePattern = when (pattern) {
    is TypedInfalliblePattern.Empty -> pattern
    is TypedInfalliblePattern.Binding -> TypedInfalliblePattern.Binding(pattern.loc, pattern.type, pattern.name, pattern.isMut, pattern.variableIndex + indexShift)
    is TypedInfalliblePattern.Tuple -> TypedInfalliblePattern.Tuple(pattern.loc, pattern.type, pattern.elements.map { shiftPatternIndex(it, indexShift) })
}

fun getTopIndex(scope: ConsMap<String, VariableBinding>): Int =
    scope.firstOrNull()?.second?.let { it.index + it.type.stackSlots } ?: 0