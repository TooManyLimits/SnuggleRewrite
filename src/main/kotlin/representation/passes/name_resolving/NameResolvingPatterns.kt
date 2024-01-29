package representation.passes.name_resolving

import representation.asts.parsed.ParsedFalliblePattern
import representation.asts.parsed.ParsedInfalliblePattern
import representation.asts.parsed.ParsedType
import representation.asts.resolved.ResolvedFalliblePattern
import representation.asts.resolved.ResolvedInfalliblePattern
import representation.asts.resolved.ResolvedType
import util.lookup

fun resolveInfalliblePattern(pattern: ParsedInfalliblePattern, currentMappings: EnvMembers): ResolvedInfalliblePattern = when (pattern) {
    is ParsedInfalliblePattern.Tuple -> {
        val resolvedElements = pattern.elements.map { resolveInfalliblePattern(it, currentMappings) }
        ResolvedInfalliblePattern.Tuple(pattern.loc, resolvedElements)
    }
    is ParsedInfalliblePattern.Empty -> {
        val resolvedType = pattern.typeAnnotation?.let { resolveType(it, currentMappings) }
        ResolvedInfalliblePattern.Empty(pattern.loc, resolvedType)
    }
    is ParsedInfalliblePattern.Binding -> {
        val resolvedType = pattern.typeAnnotation?.let { resolveType(it, currentMappings) }
        ResolvedInfalliblePattern.Binding(pattern.loc, pattern.name, pattern.isMut, resolvedType)
    }
}

fun resolveFalliblePattern(pattern: ParsedFalliblePattern, currentMappings: EnvMembers): ResolvedFalliblePattern = when (pattern) {
    is ParsedFalliblePattern.Tuple -> {
        val resolvedElements = pattern.elements.map { resolveFalliblePattern(it, currentMappings) }
        ResolvedFalliblePattern.Tuple(pattern.loc, resolvedElements)
    }
    is ParsedFalliblePattern.LiteralPattern -> ResolvedFalliblePattern.LiteralPattern(pattern.loc, pattern.value)
    is ParsedFalliblePattern.IsType -> {
        val resolvedType = resolveType(pattern.type, currentMappings)
        ResolvedFalliblePattern.IsType(pattern.loc, pattern.isMut, pattern.varName, resolvedType)
    }
}


// Returns the resolved type, as well as a set of types that were reached while resolving this type
fun resolveType(type: ParsedType, currentMappings: EnvMembers): ResolvedType = when (type) {
    is ParsedType.Basic -> {
        val resolvedBase = currentMappings.types.lookup(type.base) ?: throw UnknownTypeException(type.base, type.loc)
        val resolvedGenerics = type.generics.map { resolveType(it, currentMappings) }
        ResolvedType.Basic(type.loc, resolvedBase, resolvedGenerics)
    }
    is ParsedType.Tuple -> ResolvedType.Tuple(type.loc, type.elementTypes.map { resolveType(it, currentMappings) })
    is ParsedType.Func -> ResolvedType.Func(type.loc, type.paramTypes.map { resolveType(it, currentMappings) }, resolveType(type.returnType, currentMappings))
    is ParsedType.TypeGeneric -> ResolvedType.TypeGeneric(type.loc, type.name, type.index)
    is ParsedType.MethodGeneric -> ResolvedType.MethodGeneric(type.loc, type.name, type.index)
}