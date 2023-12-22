package ast.import_resolution

import ast.parsing.*
import errors.ResolutionException
import errors.UnknownTypeException
import util.*
import util.ConsList.Companion.nil

/**
 * The result of resolving an expression or a block.
 * After resolving the main block for a file, this should come back with everything done by that.
 */
data class ExprResolutionResult(
    // The resolved expression.
    val expr: ImportResolvedExpr,
    // A set of files reached while resolving this expression.
    val files: Set<ParsedFile>,
    // The type definitions which are exposed by this expression
    // to the surrounding environment.
    // Example: The import expression "exposes" all pub types in the file
    // it refers to, to the context where the import occurs.
    val exposedTypes: ConsMap<String, ImportResolvedTypeDef>
) {
    constructor(expr: ImportResolvedExpr): this(expr, setOf(), ConsMap(nil()))
}

// A class representing the public members of a block.
// Cached for each file, so imports can bring them in.
data class PublicMembers(
    val pubTypes: ConsMap<String, ImportResolvedTypeDef>
)

// Scan a block to get its public members. Used for both regular blocks
// and on entire files.
fun getPubMembers(block: ParsedElement.ParsedExpr.Block): PublicMembers {
    var pubTypes: ConsMap<String, ImportResolvedTypeDef> = ConsMap(nil());
    for (elem: ParsedElement in block.elements) {

    }
    return PublicMembers(pubTypes)
}

/**
 * Resolve an expression.
 */
fun resolveExpr(
    expr: ParsedElement.ParsedExpr,
    startingMappings: ConsMap<String, ImportResolvedTypeDef>,
    currentMappings: ConsMap<String, ImportResolvedTypeDef>,
    ast: ParsedAST,
    cache: IdentityCache<ParsedFile, PublicMembers>
): ExprResolutionResult = when (expr) {
    // Block, one of the 2 important expressions
    is ParsedElement.ParsedExpr.Block -> {
        // Shadow currentMappings
        var currentMappings = currentMappings
        var files: Set<ParsedFile> = setOf()
//            var types: Set<ImportResolvedTypeDef> = setOf()
        var exposedTypes: ConsMap<String, ImportResolvedTypeDef> = ConsMap(nil())
        val innerExprs: ArrayList<ImportResolvedExpr> = ArrayList()

        // TODO Scan the block once to find type declarations, etc. Update currentMappings/types/exposedTypes to reflect this.
        // TODO currentMappings/types: All types at the top level of this block
        // TODO exposedTypes: All *pub* types at the top level of this block

        // For each element, call recursively, and update our vars
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
            }
        }
        ExprResolutionResult(ImportResolvedExpr.Block(expr.loc, innerExprs), files, exposedTypes)
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
            ImportResolvedExpr.Import(expr.loc, expr.path),
            setOf(otherFile),
            otherPubMembers.pubTypes
        )
    }

    // Others are fairly straightforward; resolve the things inside, collect the results, and return.

    // Method calls:
    is ParsedElement.ParsedExpr.MethodCall -> {
        // Resolve the receiver and the args, collect results, and return.
        val resolvedReceiver = resolveExpr(expr.receiver, startingMappings, currentMappings, ast, cache)
        val resolvedArgs = expr.args.map { resolveExpr(it, startingMappings, currentMappings, ast, cache) }
        val unitedFiles = union(resolvedReceiver.files, union(resolvedArgs.map { it.files }))
        val unitedExposedTypes = resolvedReceiver.exposedTypes.extendMany(resolvedArgs.map { it.exposedTypes })
        ExprResolutionResult(
            ImportResolvedExpr.MethodCall(expr.loc, resolvedReceiver.expr, expr.methodName, resolvedArgs.map { it.expr }),
            unitedFiles, unitedExposedTypes
        )
    }

    // Declarations:
    is ParsedElement.ParsedExpr.Declaration -> {
        val pattern = resolvePattern(expr.lhs, currentMappings)
        val initializer = resolveExpr(expr.initializer, startingMappings, currentMappings, ast, cache)
        ExprResolutionResult(
            ImportResolvedExpr.Declaration(expr.loc, pattern.first, initializer.expr),
            initializer.files,
            // union(pattern.second, initializer.types),
            initializer.exposedTypes
        )
    }

    // For ones where there's no nested expressions or other things, it's just a 1-liner.
    is ParsedElement.ParsedExpr.Literal -> ExprResolutionResult(ImportResolvedExpr.Literal(expr.loc, expr.value))
    is ParsedElement.ParsedExpr.Variable -> ExprResolutionResult(ImportResolvedExpr.Variable(expr.loc, expr.name))

}

// Returns the resolved pattern, as well as a set of types which have been reached by the pattern
fun resolvePattern(pattern: ParsedPattern, currentMappings: ConsMap<String, ImportResolvedTypeDef>): Pair<ImportResolvedPattern, Set<ImportResolvedTypeDef>> = when (pattern) {
    is ParsedPattern.Binding -> {
        val resolved = pattern.typeAnnotation?.let { resolveType(it, currentMappings) }
        ImportResolvedPattern.BindingPattern(pattern.loc, pattern.name, pattern.isMut, resolved?.first) to (resolved?.second ?: setOf())
    }
}

// Returns the resolved type, as well as a set of types that were reached while resolving this type
fun resolveType(type: ParsedType, currentMappings: ConsMap<String, ImportResolvedTypeDef>): Pair<ImportResolvedType, Set<ImportResolvedTypeDef>> = when (type) {
    is ParsedType.Basic -> {
        val resolvedBase = currentMappings.lookup(type.base) ?: throw UnknownTypeException(type.toString(), type.loc)
        val resolvedGenerics = type.generics.map { resolveType(it, currentMappings) }
        val usedTypes = union(setOf(resolvedBase), union(resolvedGenerics.map { it.second }))
        ImportResolvedType.Basic(type.loc, resolvedBase, resolvedGenerics.map { it.first }) to usedTypes
    }
}