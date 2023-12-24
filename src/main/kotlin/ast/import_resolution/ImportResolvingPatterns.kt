package ast.import_resolution

import ast.parsing.ParsedPattern
import util.ConsMap

// Returns the resolved pattern, as well as a set of types which have been reached by the pattern
fun resolvePattern(pattern: ParsedPattern, currentMappings: ConsMap<String, ImportResolvedTypeDef>): ImportResolvedPattern = when (pattern) {
    is ParsedPattern.BindingPattern -> {
        val resolved = pattern.typeAnnotation?.let { resolveType(it, currentMappings) }
        ImportResolvedPattern.BindingPattern(pattern.loc, pattern.name, pattern.isMut, resolved)
    }
}