package representation.passes.name_resolving

import errors.CompilationException
import errors.ParsingException
import representation.asts.parsed.ParsedAST
import representation.asts.parsed.ParsedElement
import representation.asts.parsed.ParsedFile
import representation.asts.resolved.ResolvedExpr
import representation.asts.resolved.ResolvedImplBlock
import representation.asts.resolved.ResolvedType
import representation.asts.resolved.ResolvedTypeDef
import representation.passes.lexing.Loc
import util.lookup
import util.union

class ResolutionResult(
    val expr: ResolvedExpr,
    val filesReached: Set<ParsedFile>,
    val exposedMembers: EnvMembers
) {
    fun mapExpr(func: (ResolvedExpr) -> ResolvedExpr)
        = ResolutionResult(func(expr), filesReached, exposedMembers)
}

private fun just(expr: ResolvedExpr) = ResolutionResult(expr, setOf(), EnvMembers())
private fun join(results: Iterable<ResolutionResult>, func: (List<ResolvedExpr>) -> ResolvedExpr): ResolutionResult {
    val unitedFiles = union(results.map { it.filesReached })
    val unitedExposedTypes = EnvMembers.join(results.map { it.exposedMembers })
    return ResolutionResult(func(results.map { it.expr }), unitedFiles, unitedExposedTypes)
}

fun resolveExpr(expr: ParsedElement.ParsedExpr, startingMappings: EnvMembers, currentMappings: EnvMembers, ast: ParsedAST, cache: ResolutionCache): ResolutionResult = when (expr) {

    is ParsedElement.ParsedExpr.Import -> {
        // Get the other file, or error if it doesn't exist
        val otherFile = ast.files[expr.path]?.value
            ?: throw ResolutionException(expr.path, expr.loc)
        // Resolve the other file's public members (or fetch from cache)
        val otherPubMembers = cache.publicMembers.get(otherFile) {
            getPublicMembersAndInitIndirections(it.block, cache)
        }
        // Return an import, as well as all the things that were imported from the other file
        ResolutionResult(
            ResolvedExpr.Import(expr.loc, expr.path),
            setOf(otherFile),
            otherPubMembers
        )
    }

    is ParsedElement.ParsedExpr.Block -> {
        var filesReached: Set<ParsedFile> = setOf()
        var exposedMembers = getPublicMembersAndInitIndirections(expr, cache)
        val innerExprs: ArrayList<ResolvedExpr> = ArrayList()
        // Shadow currentMappings for mutability, and set up the indirections in the cache
        var currentMappings = currentMappings.extend(getAllMembers(expr, cache))

        for (element in expr.elements) when (element) {
            is ParsedElement.ParsedExpr -> {
                // Resolve the inner expr
                val resolved = resolveExpr(element, startingMappings, currentMappings, ast, cache)
                // Add the new data into our variables
                currentMappings = currentMappings.extend(resolved.exposedMembers)
                filesReached = union(filesReached, resolved.filesReached)
                exposedMembers = exposedMembers.extend(resolved.exposedMembers)
                innerExprs += resolved.expr
            }
            is ParsedElement.ParsedTypeDef -> {
                // Get its indirection (it should exist!)
                val indirect = cache.typeDefIndirections.getOrThrow(element)
                // Resolve the type and fill the indirection
                val resolved = resolveTypeDef(element, startingMappings, currentMappings, ast, cache)
                indirect.promise.fulfill(resolved.typeDef)
                filesReached = union(filesReached, resolved.filesReached)
            }
            is ParsedElement.ParsedImplBlock -> {
                // Similar to above
                val indirect = cache.implBlockIndirections.getOrThrow(element)
                val resolved = resolveImplBlock(element, startingMappings, currentMappings, ast, cache)
                indirect.promise.fulfill(resolved.implBlock)
                filesReached = union(filesReached, resolved.filesReached)
            }
        }

        innerExprs.trimToSize()
        ResolutionResult(ResolvedExpr.Block(expr.loc, innerExprs), filesReached, exposedMembers)
    }

    // Field accesses and method calls need special handling for static/super

    is ParsedElement.ParsedExpr.FieldAccess -> run {
        when (expr.receiver) {
            is ParsedElement.ParsedExpr.Variable -> {
                val type = currentMappings.types.lookup(expr.receiver.name)
                if (type != null) {
                    return@run just(ResolvedExpr.StaticFieldAccess(
                        expr.loc,
                        ResolvedType.Basic(expr.receiver.loc, type, listOf()),
                        expr.fieldName
                    ))
                }
            }
            else -> {}
        }
        // Otherwise, this is a non-static field access.
        // Resolve the receiver and return.
        resolveExpr(expr.receiver, startingMappings, currentMappings, ast, cache)
            .mapExpr { ResolvedExpr.FieldAccess(expr.loc, it, expr.fieldName) }
    }

    is ParsedElement.ParsedExpr.MethodCall -> run {
        val resolvedArgs = expr.args.map { resolveExpr(it, startingMappings, currentMappings, ast, cache) }
        val genericArgs = expr.genericArgs.map { resolveType(it, currentMappings) }

        when (expr.receiver) {
            // Check if this is a static method call; we do that by seeing if
            // the receiver is the name of some type.
            is ParsedElement.ParsedExpr.Variable -> {
                val type = currentMappings.types.lookup(expr.receiver.name)
                if (type != null) {
                    // Return a static method call result
                    return@run join(resolvedArgs) {
                        val receiverType = ResolvedType.Basic(expr.receiver.loc, type, listOf())
                        ResolvedExpr.StaticMethodCall(expr.loc, receiverType, expr.methodName, genericArgs, it, currentMappings.implBlocks)
                    }
                }
            }
            // Check if this is a super method call - pretty straight forward,
            // just see if the receiver is a Super.
            is ParsedElement.ParsedExpr.Super -> {
                // This is a super method call.
                return@run join(resolvedArgs) {
                    ResolvedExpr.SuperMethodCall(expr.loc, expr.methodName, genericArgs, it, currentMappings.implBlocks)
                }
            }
            else -> {}
        }

        val resolvedReceiver = resolveExpr(expr.receiver, startingMappings, currentMappings, ast, cache)
        join(resolvedArgs + resolvedReceiver) {
            ResolvedExpr.MethodCall(expr.loc, it.last(), expr.methodName, genericArgs, it.dropLast(1), currentMappings.implBlocks)
        }
    }

    // The rest are simply collecting the filesReached/exposedTypes and emitting. This is
    // made easier with the helper join().

    is ParsedElement.ParsedExpr.ConstructorCall -> {
        val resolvedType = expr.type?.let { resolveType(it, currentMappings) }
        join(expr.args.map { resolveExpr(it, startingMappings, currentMappings, ast, cache) }) {
            ResolvedExpr.ConstructorCall(expr.loc, resolvedType, it, currentMappings.implBlocks)
        }
    }
    is ParsedElement.ParsedExpr.RawStructConstructor -> {
        val resolvedType = expr.type?.let { resolveType(it, currentMappings) }
        join(expr.fieldValues.map { resolveExpr(it, startingMappings, currentMappings, ast, cache) }) {
            ResolvedExpr.RawStructConstructor(expr.loc, resolvedType, it)
        }
    }
    is ParsedElement.ParsedExpr.Tuple -> {
        join(expr.elements.map { resolveExpr(it, startingMappings, currentMappings, ast, cache) }) {
            ResolvedExpr.Tuple(expr.loc, it)
        }
    }
    is ParsedElement.ParsedExpr.Lambda -> {
        val params = expr.params.map { resolveInfalliblePattern(it, currentMappings) }
        resolveExpr(expr.body, startingMappings, currentMappings, ast, cache)
            .mapExpr { ResolvedExpr.Lambda(expr.loc, params, it) }
    }
    is ParsedElement.ParsedExpr.Declaration -> {
        val pattern = resolveInfalliblePattern(expr.lhs, currentMappings)
        resolveExpr(expr.initializer, startingMappings, currentMappings, ast, cache)
            .mapExpr { ResolvedExpr.Declaration(expr.loc, pattern, it) }
    }
    is ParsedElement.ParsedExpr.Assignment -> {
        join(listOf(expr.lhs, expr.rhs).map { resolveExpr(it, startingMappings, currentMappings, ast, cache) }) {
            ResolvedExpr.Assignment(expr.loc, it[0], it[1])
        }
    }
    is ParsedElement.ParsedExpr.Return -> {
        resolveExpr(expr.rhs, startingMappings, currentMappings, ast, cache)
            .mapExpr { ResolvedExpr.Return(expr.loc, it) }
    }
    is ParsedElement.ParsedExpr.If -> {
        join(listOfNotNull(expr.cond, expr.ifTrue, expr.ifFalse).map { resolveExpr(it, startingMappings, currentMappings, ast, cache) }) {
            ResolvedExpr.If(expr.loc, it[0], it[1], it.getOrNull(2))
        }
    }
    is ParsedElement.ParsedExpr.While -> {
        join(listOf(expr.cond, expr.body).map { resolveExpr(it, startingMappings, currentMappings, ast, cache) }) {
            ResolvedExpr.While(expr.loc, it[0], it[1])
        }
    }
    is ParsedElement.ParsedExpr.For -> {
        val pattern = resolveInfalliblePattern(expr.pattern, currentMappings)
        join(listOf(expr.iterable, expr.body).map { resolveExpr(it, startingMappings, currentMappings, ast, cache) }) {
            ResolvedExpr.For(expr.loc, pattern, it[0], it[1])
        }
    }

    is ParsedElement.ParsedExpr.Is -> {
        val pattern = resolveFalliblePattern(expr.pattern, currentMappings)
        resolveExpr(expr.lhs, startingMappings, currentMappings, ast, cache)
            .mapExpr { ResolvedExpr.Is(expr.loc, expr.negated, it, pattern) }
    }

    is ParsedElement.ParsedExpr.Literal -> just(ResolvedExpr.Literal(expr.loc, expr.value))
    is ParsedElement.ParsedExpr.Variable -> just(ResolvedExpr.Variable(expr.loc, expr.name))
    is ParsedElement.ParsedExpr.Parenthesized -> resolveExpr(expr.inner, startingMappings, currentMappings, ast, cache)

    is ParsedElement.ParsedExpr.Super ->
        throw ParsingException("Unexpected \"super\" - should only be used for method calls on a superclass.", expr.loc)
}

fun getAllMembers(block: ParsedElement.ParsedExpr.Block, cache: ResolutionCache): EnvMembers {
    var members = EnvMembers()
    block.elements.forEach {
        when (it) {
            is ParsedElement.ParsedTypeDef -> {
                val ind = cache.typeDefIndirections.get(it) { ResolvedTypeDef.Indirection() }
                members = members.extendType(it.name, ind)
            }
            is ParsedElement.ParsedImplBlock -> {
                val ind = cache.implBlockIndirections.get(it) { ResolvedImplBlock.Indirect() }
                members = members.extendImplBlock(ind)
            }
            is ParsedElement.ParsedExpr.Block -> {
                val rest = getAllMembers(it, cache)
                members = members.extend(rest)
            }
            else -> {}
        }
    }
    return members
}


class ResolutionException(filePath: String, loc: Loc)
    : CompilationException("No file with name \"$filePath\" was provided", loc)
class UnknownTypeException(typeName: String, loc: Loc)
    : CompilationException("No type with name \"$typeName\" is in the current scope", loc)