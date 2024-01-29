package snuggle.toomanylimits.representation.passes.name_resolving

import snuggle.toomanylimits.representation.asts.parsed.*
import snuggle.toomanylimits.representation.asts.resolved.ResolvedFieldDef
import snuggle.toomanylimits.representation.asts.resolved.ResolvedImplBlock
import snuggle.toomanylimits.representation.asts.resolved.ResolvedMethodDef
import snuggle.toomanylimits.representation.asts.resolved.ResolvedTypeDef
import snuggle.toomanylimits.util.union

class TypeDefResolutionResult(
    val typeDef: ResolvedTypeDef,
    val filesReached: Set<ParsedFile>,
)

class ImplBlockResolutionResult(
    val implBlock: ResolvedImplBlock,
    val filesReached: Set<ParsedFile>,
)

fun resolveTypeDef(
    typeDef: ParsedElement.ParsedTypeDef,
    startingMappings: EnvMembers,
    currentMappings: EnvMembers,
    ast: ParsedAST,
    cache: ResolutionCache
): TypeDefResolutionResult = when (typeDef) {
    is ParsedElement.ParsedTypeDef.Class -> {
        val resolvedSupertype = resolveType(typeDef.superType, currentMappings)
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

fun resolveImplBlock(implBlock: ParsedElement.ParsedImplBlock, startingMappings: EnvMembers, currentMappings: EnvMembers, ast: ParsedAST, cache: ResolutionCache): ImplBlockResolutionResult {
    val resolvedImplType = resolveType(implBlock.implType, currentMappings)
    val resolvedMethods = implBlock.methods.map { resolveMethodDef(it, startingMappings, currentMappings, ast, cache) }
    return ImplBlockResolutionResult(
        ResolvedImplBlock.Base(
            implBlock.loc, implBlock.pub, implBlock.numGenerics,
            resolvedImplType, resolvedMethods.map { it.first }
        ),
        union(resolvedMethods.map { it.second })
    )
}

private fun resolveFieldDef(fieldDef: ParsedFieldDef,
                            startingMappings: EnvMembers,
                            currentMappings: EnvMembers,
                            ast: ParsedAST,
                            cache: ResolutionCache
): Pair<ResolvedFieldDef, Set<ParsedFile>> {
    val resolvedType = resolveType(fieldDef.annotatedType, currentMappings)
    return ResolvedFieldDef(fieldDef.loc, fieldDef.pub, fieldDef.static, fieldDef.mutable, fieldDef.name, resolvedType) to setOf()
}

private fun resolveMethodDef(
    methodDef: ParsedMethodDef,
    startingMappings: EnvMembers,
    currentMappings: EnvMembers,
    ast: ParsedAST,
    cache: ResolutionCache
): Pair<ResolvedMethodDef, Set<ParsedFile>> {
    val resolvedParams = methodDef.params.map { resolveInfalliblePattern(it, currentMappings) }
    val resolvedReturnType = resolveType(methodDef.returnType, currentMappings)
    val resolvedBody = resolveExpr(methodDef.body, startingMappings, currentMappings, ast, cache)
    return ResolvedMethodDef(
        methodDef.loc, methodDef.pub, methodDef.static, methodDef.numGenerics, methodDef.name, resolvedParams, resolvedReturnType, resolvedBody.expr
    ) to resolvedBody.filesReached
}