package representation.passes.name_resolving

import representation.asts.parsed.ParsedPattern
import representation.asts.parsed.ParsedType
import representation.asts.resolved.ResolvedPattern
import representation.asts.resolved.ResolvedType
import util.lookup

fun resolvePattern(pattern: ParsedPattern, currentMappings: EnvMembers): ResolvedPattern = when (pattern) {
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