package representation.passes.name_resolving

import representation.asts.resolved.ResolvedPattern
import representation.asts.resolved.ResolvedTypeDef
import representation.asts.parsed.ParsedPattern
import util.ConsMap

// Returns the resolved pattern, as well as a set of types which have been reached by the pattern
fun resolvePattern(pattern: ParsedPattern, currentMappings: ConsMap<String, ResolvedTypeDef>): ResolvedPattern = when (pattern) {
    is ParsedPattern.EmptyPattern -> {
        val resolvedType = pattern.typeAnnotation?.let { resolveType(it, currentMappings) }
        ResolvedPattern.EmptyPattern(pattern.loc, resolvedType)
    }
    is ParsedPattern.BindingPattern -> {
        val resolvedType = pattern.typeAnnotation?.let { resolveType(it, currentMappings) }
        ResolvedPattern.BindingPattern(pattern.loc, pattern.name, pattern.isMut, resolvedType)
    }
    is ParsedPattern.TuplePattern -> {
        val resolvedElements = pattern.elements.map { resolvePattern(it, currentMappings) }
        ResolvedPattern.TuplePattern(pattern.loc, resolvedElements)
    }
}