package representation.passes.name_resolving

import representation.asts.resolved.ResolvedPattern
import representation.asts.resolved.ResolvedTypeDef
import representation.asts.parsed.ParsedPattern
import util.ConsMap

// Returns the resolved pattern, as well as a set of types which have been reached by the pattern
fun resolvePattern(pattern: ParsedPattern, currentMappings: ConsMap<String, ResolvedTypeDef>): ResolvedPattern = when (pattern) {
    is ParsedPattern.BindingPattern -> {
        val resolved = pattern.typeAnnotation?.let { resolveType(it, currentMappings) }
        ResolvedPattern.BindingPattern(pattern.loc, pattern.name, pattern.isMut, resolved)
    }
}