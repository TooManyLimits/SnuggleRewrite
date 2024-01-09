package representation.passes.name_resolving

import errors.CompilationException
import errors.ParsingException
import representation.asts.resolved.ResolvedExpr
import representation.asts.resolved.ResolvedType
import representation.asts.resolved.ResolvedTypeDef
import representation.asts.parsed.ParsedAST
import representation.asts.parsed.ParsedElement
import representation.asts.parsed.ParsedFile
import representation.asts.parsed.ParsedType
import representation.passes.lexing.Loc
import util.*
import util.ConsList.Companion.fromIterable
import util.ConsList.Companion.nil
import util.caching.IdentityCache

/**
 * The result of resolving an expression or a block.
 * After resolving the main block for a file, this should come back with everything done by that.
 */
data class ExprResolutionResult(
    // The resolved expression.
    val expr: ResolvedExpr,
    // A set of files reached while resolving this expression.
    val files: Set<ParsedFile>,
    // The type definitions which are exposed by this expression
    // to the surrounding environment.
    // Example: The import expression "exposes" all pub types in the file
    // it refers to, to the context where the import occurs.
    val exposedTypes: ConsMap<String, ResolvedTypeDef>
) {
    constructor(expr: ResolvedExpr): this(expr, setOf(), ConsMap(nil()))
}

// A class representing the public members of a block.
// Cached for each file, so imports can bring them in.
data class PublicMembers(
    val pubTypes: ConsMap<String, ResolvedTypeDef>
)

// Scan a block to get its public members. Used for both regular blocks
// and on entire files.
fun getPubMembers(block: ParsedElement.ParsedExpr.Block): PublicMembers {
    var pubTypes: ConsMap<String, ResolvedTypeDef> = ConsMap(nil());
    for (elem: ParsedElement in block.elements) {

    }
    return PublicMembers(pubTypes)
}

/**
 * Resolve an expression.
 */
fun resolveExpr(
    expr: ParsedElement.ParsedExpr,
    startingMappings: ConsMap<String, ResolvedTypeDef>,
    currentMappings: ConsMap<String, ResolvedTypeDef>,
    ast: ParsedAST,
    cache: IdentityCache<ParsedFile, PublicMembers>
): ExprResolutionResult = when (expr) {
    // Block, one of the 2 important expressions
    is ParsedElement.ParsedExpr.Block -> {
        // Shadow currentMappings
        var currentMappings = currentMappings
        var files: Set<ParsedFile> = setOf()
        var exposedTypes: ConsMap<String, ResolvedTypeDef> = ConsMap(nil())
        val innerExprs: ArrayList<ResolvedExpr> = ArrayList()

        // For self-referencing concerns, create a list of type indirections first.
        // Each entry is a pair (Parsed type def, Indirection yet to be filled)
        val indirections: ConsMap<ParsedElement.ParsedTypeDef, ResolvedTypeDef.Indirection> = ConsMap(fromIterable(
            expr.elements
                .filterIsInstance<ParsedElement.ParsedTypeDef>()
                .map { it to ResolvedTypeDef.Indirection() }
        ))

        //Extend the current mappings and exposed types
        currentMappings = currentMappings.extend(indirections.mapKeys { it.name })
        exposedTypes = exposedTypes.extend(indirections.filterKeys { it.pub }.mapKeys { it.name })

        //Now resolve all the type defs, with the extended mappings.
        indirections.forEach {
            val resolved = resolveTypeDef(it.first, startingMappings, currentMappings, ast, cache)
            // Once we resolve, fill the promise
            it.second.promise.fulfill(resolved.resolvedTypeDef)
            // Track the files
            files = union(files, resolved.files)
        }

        // For each expr, call recursively, and update our vars
        for (element in expr.elements) {
            when (element) {
                is ParsedElement.ParsedExpr -> {
                    // Resolve the inner expr
                    val resolved = resolveExpr(element, startingMappings, currentMappings, ast, cache)
                    // Add the new data into our variables
                    currentMappings = currentMappings.extend(resolved.exposedTypes)
                    files = union(files, resolved.files)
                    innerExprs += resolved.expr
                }
                else -> {}
            }
        }
        ExprResolutionResult(ResolvedExpr.Block(expr.loc, innerExprs), files, exposedTypes)
    }
    // Import, the other of the 2 important expressions
    is ParsedElement.ParsedExpr.Import -> {
        // Get the other file, or error if it doesn't exist
        val otherFile = ast.files[expr.path]?.value
            ?: throw ResolutionException(expr.path, expr.loc)
        // Resolve the other file
        val otherPubMembers = cache.get(otherFile) { getPubMembers(it.block) }
        // Return an import, as well as all the things imported from the other file
        ExprResolutionResult(
            ResolvedExpr.Import(expr.loc, expr.path),
            setOf(otherFile),
            otherPubMembers.pubTypes
        )
    }

    // Others are fairly straightforward; resolve the things inside, collect the results, and return.

    is ParsedElement.ParsedExpr.FieldAccess -> run {
        when (expr.receiver) {
            is ParsedElement.ParsedExpr.Variable -> {
                val type = currentMappings.lookup(expr.receiver.name)
                if (type != null) {
                    return@run ExprResolutionResult(
                        ResolvedExpr.StaticFieldAccess(
                            expr.loc,
                            ResolvedType.Basic(expr.receiver.loc, type, listOf()),
                            expr.fieldName
                        ), setOf(), ConsMap.of()
                    )
                }
            }
            else -> {}
        }
        // Otherwise, this is a non-static field access.
        // Resolve the receiver and return.
        val resolvedReceiver = resolveExpr(expr.receiver, startingMappings, currentMappings, ast, cache)
        ExprResolutionResult(
            ResolvedExpr.FieldAccess(expr.loc, resolvedReceiver.expr, expr.fieldName),
            resolvedReceiver.files, resolvedReceiver.exposedTypes
        )
    }

    // Method calls:
    is ParsedElement.ParsedExpr.MethodCall -> run {

        // Always resolve the arguments and collect the files and exposed types first
        val resolvedArgs = expr.args.map { resolveExpr(it, startingMappings, currentMappings, ast, cache) }
        val unitedFiles = union(resolvedArgs.map { it.files })
        val unitedExposedTypes = ConsMap.of<String, ResolvedTypeDef>()
            .extendMany(resolvedArgs.map { it.exposedTypes })

        // Now, we need to check for other types of method calls.
        when (expr.receiver) {
            // Check if this is a static method call; we do that by seeing if
            // the receiver is the name of some type.
            is ParsedElement.ParsedExpr.Variable -> {
                val type = currentMappings.lookup(expr.receiver.name)
                if (type != null) {
                    // Return a static method call result
                    return@run ExprResolutionResult(
                        ResolvedExpr.StaticMethodCall(
                            expr.loc,
                            ResolvedType.Basic(expr.receiver.loc, type, listOf()),
                            expr.methodName,
                            resolvedArgs.map { it.expr }
                        ), unitedFiles, unitedExposedTypes
                    )
                }
            }
            // Check if this is a super method call - pretty straight forward,
            // just see if the receiver is a Super.
            is ParsedElement.ParsedExpr.Super -> {
                // This is a super method call.
                return@run ExprResolutionResult(
                    ResolvedExpr.SuperMethodCall(expr.loc, expr.methodName, resolvedArgs.map { it.expr }),
                    unitedFiles, unitedExposedTypes
                )
            }
            else -> {}
        }
        // Otherwise, this is a non-static method call.
        // Resolve the receiver and return.
        val resolvedReceiver = resolveExpr(expr.receiver, startingMappings, currentMappings, ast, cache)
        ExprResolutionResult(
            ResolvedExpr.MethodCall(expr.loc, resolvedReceiver.expr, expr.methodName, resolvedArgs.map { it.expr }),
            union(resolvedReceiver.files, unitedFiles), // Add its files
            resolvedReceiver.exposedTypes.extend(unitedExposedTypes) // Add its exposed types
        )
    }

    // Constructor calls:
    is ParsedElement.ParsedExpr.ConstructorCall -> {
        // Resolve, collect, return.
        val resolvedType = expr.type?.let { resolveType(it, currentMappings) }
        val resolvedArgs = expr.args.map { resolveExpr(it, startingMappings, currentMappings, ast, cache) }
        val unitedFiles = union(resolvedArgs.map { it.files })
        val unitedExposedTypes = ConsMap.of<String, ResolvedTypeDef>()
            .extendMany(resolvedArgs.map { it.exposedTypes })

        ExprResolutionResult(
            ResolvedExpr.ConstructorCall(expr.loc, resolvedType, resolvedArgs.map { it.expr }),
            unitedFiles, unitedExposedTypes
        )
    }

    is ParsedElement.ParsedExpr.RawStructConstructor -> {
        // Same as regular constructor, really.
        val resolvedType = expr.type?.let { resolveType(it, currentMappings) }
        val resolvedArgs = expr.fieldValues.map { resolveExpr(it, startingMappings, currentMappings, ast, cache) }
        val unitedFiles = union(resolvedArgs.map { it.files })
        val unitedExposedTypes = ConsMap.of<String, ResolvedTypeDef>()
            .extendMany(resolvedArgs.map { it.exposedTypes })
        ExprResolutionResult(
            ResolvedExpr.RawStructConstructor(expr.loc, resolvedType, resolvedArgs.map { it.expr }),
            unitedFiles, unitedExposedTypes
        )
    }

    // Declarations:
    is ParsedElement.ParsedExpr.Declaration -> {
        val pattern = resolvePattern(expr.lhs, currentMappings)
        val initializer = resolveExpr(expr.initializer, startingMappings, currentMappings, ast, cache)
        ExprResolutionResult(
            ResolvedExpr.Declaration(expr.loc, pattern, initializer.expr),
            initializer.files,
            initializer.exposedTypes
        )
    }

    is ParsedElement.ParsedExpr.Return -> resolveExpr(expr.rhs, startingMappings, currentMappings, ast, cache).let {
        ExprResolutionResult(ResolvedExpr.Return(it.expr.loc, it.expr), it.files, it.exposedTypes)
    }

    // For ones where there's no nested expressions or other things, it's just a 1-liner.
    is ParsedElement.ParsedExpr.Literal -> ExprResolutionResult(ResolvedExpr.Literal(expr.loc, expr.value))
    is ParsedElement.ParsedExpr.Variable -> ExprResolutionResult(ResolvedExpr.Variable(expr.loc, expr.name))

    // Should not encounter a Super unless as a receiver of a ParsedMethodCall.
    is ParsedElement.ParsedExpr.Super
        -> throw ParsingException("Unexpected \"super\" - should only be used for method calls in a superclass.", expr.loc)
}

// Returns the resolved type, as well as a set of types that were reached while resolving this type
fun resolveType(type: ParsedType, currentMappings: ConsMap<String, ResolvedTypeDef>): ResolvedType = when (type) {
    is ParsedType.Basic -> {
        val resolvedBase = currentMappings.lookup(type.base) ?: throw UnknownTypeException(type.toString(), type.loc)
        val resolvedGenerics = type.generics.map { resolveType(it, currentMappings) }
        ResolvedType.Basic(type.loc, resolvedBase, resolvedGenerics)
    }
    is ParsedType.Tuple -> ResolvedType.Tuple(type.loc, type.elementTypes.map { resolveType(it, currentMappings) })
    is ParsedType.TypeGeneric -> ResolvedType.TypeGeneric(type.loc, type.name, type.index)
}

class ResolutionException(filePath: String, loc: Loc)
    : CompilationException("No file with name \"$filePath\" was provided", loc)
class UnknownTypeException(typeName: String, loc: Loc)
    : CompilationException("No type with name \"$typeName\" is in the current scope", loc)