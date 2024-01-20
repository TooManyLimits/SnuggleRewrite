package representation.passes.name_resolving

import representation.asts.parsed.*
import representation.asts.resolved.ResolvedFieldDef
import representation.asts.resolved.ResolvedMethodDef
import representation.asts.resolved.ResolvedTypeDef
import util.ConsMap
import util.caching.IdentityCache
import util.union

data class TypeDefResolutionResult(
    val resolvedTypeDef: ResolvedTypeDef,
    val files: Set<ParsedFile>
)

fun resolveTypeDef(
    typeDef: ParsedElement.ParsedTypeDef,
    startingMappings: ConsMap<String, ResolvedTypeDef>,
    currentMappings: ConsMap<String, ResolvedTypeDef>,
    ast: ParsedAST,
    cache: IdentityCache<ParsedFile, PublicMembers>
): TypeDefResolutionResult = when (typeDef) {
    is ParsedElement.ParsedTypeDef.Class -> {
        val resolvedSupertype = typeDef.superType.let { resolveType(it, currentMappings) }
        val resolvedFields = typeDef.fields.map { resolveFieldDef(it, startingMappings, currentMappings, ast, cache) }
        val resolvedMethods = typeDef.methods.map { resolveMethodDef(it, startingMappings, currentMappings, ast, cache) }
        TypeDefResolutionResult(
            ResolvedTypeDef.Class(typeDef.loc, typeDef.pub, typeDef.name, typeDef.numGenerics,
                resolvedSupertype,
                resolvedFields.map { it.first },
                resolvedMethods.map { it.first }
            ),
            union(resolvedFields.map { it.second }, resolvedMethods.map { it.second })
        )
    }
    // Same as above, just without the supertype. Who ever said not to repeat yourself, smh
    is ParsedElement.ParsedTypeDef.Struct -> {
        val resolvedFields = typeDef.fields.map { resolveFieldDef(it, startingMappings, currentMappings, ast, cache) }
        val resolvedMethods = typeDef.methods.map { resolveMethodDef(it, startingMappings, currentMappings, ast, cache) }
        TypeDefResolutionResult(
            ResolvedTypeDef.Struct(typeDef.loc, typeDef.pub, typeDef.name, typeDef.numGenerics,
                resolvedFields.map { it.first },
                resolvedMethods.map { it.first }
            ),
            union(resolvedFields.map { it.second }, resolvedMethods.map { it.second })
        )
    }
}

private fun resolveFieldDef(fieldDef: ParsedFieldDef,
                            startingMappings: ConsMap<String, ResolvedTypeDef>,
                            currentMappings: ConsMap<String, ResolvedTypeDef>,
                            ast: ParsedAST,
                            cache: IdentityCache<ParsedFile, PublicMembers>
): Pair<ResolvedFieldDef, Set<ParsedFile>> {
    val resolvedType = resolveType(fieldDef.annotatedType, currentMappings)
    return ResolvedFieldDef(fieldDef.loc, fieldDef.pub, fieldDef.static, fieldDef.mutable, fieldDef.name, resolvedType) to setOf()
}

private fun resolveMethodDef(
    methodDef: ParsedMethodDef,
    startingMappings: ConsMap<String, ResolvedTypeDef>,
    currentMappings: ConsMap<String, ResolvedTypeDef>,
    ast: ParsedAST,
    cache: IdentityCache<ParsedFile, PublicMembers>
): Pair<ResolvedMethodDef, Set<ParsedFile>> {
    val resolvedParams = methodDef.params.map { resolvePattern(it, currentMappings) }
    val resolvedReturnType = resolveType(methodDef.returnType, currentMappings)
    val resolvedBody = resolveExpr(methodDef.body, startingMappings, currentMappings, ast, cache)
    return ResolvedMethodDef(
        methodDef.loc, methodDef.pub, methodDef.static, methodDef.numGenerics, methodDef.name, resolvedParams, resolvedReturnType, resolvedBody.expr
    ) to resolvedBody.files
}