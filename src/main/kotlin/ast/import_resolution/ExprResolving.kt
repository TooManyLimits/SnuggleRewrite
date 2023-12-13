package ast.import_resolution

import ast.lexing.Loc
import ast.parsing.ParsedAST
import ast.parsing.ParsedElement
import ast.parsing.ParsedFile
import errors.ResolutionException
import util.*

/**
 * The result of resolving an expression or a block.
 * After resolving the main block for a file, this should come back with everything done by that.
 */
data class ExprResolutionResult(
    val expr: ImportResolvedExpr, // The resolved expression.
    val files: ConsList<ImportResolvedFile>, // A set of files reached while resolving this.
    val types: ConsList<ImportResolvedTypeDef> // The type definitions reached while resolving this.
)

/**
 * The starting point for resolving expressions, since an entire file is really just a block.
 */

tailrec fun resolveBlock(
    // The location of the block.
    loc: Loc,
    // The block elements we're to resolve.
    blockElements: ConsList<ParsedElement>,
    // The starting type mappings (builtins).
    // These are used when we recursively decide to resolve a new file,
    // when importing it.
    startingTypeMappings: ConsMap<String, ImportResolvedTypeDef>,
    // The current type mappings, which are the builtins, plus
    // additional things that have been defined or imported.
    typeMappings: ConsMap<String, ImportResolvedTypeDef>,
    // The AST, so we can find the parsed files which we are to import/resolve.
    ast: ParsedAST,
    // A cache, so we don't resolve the same file multiple times.
    cache: IdentityCache<ParsedFile, ImportResolvedFile>,

    //Tail recursion variables:
    // The files we've reached cumulatively while resolving this block.
    reachedFiles: ConsList<ImportResolvedFile>,
    // The types we've reached cumulatively while resolving this block.
    reachedTypes: ConsList<ImportResolvedTypeDef>,
    // The code we've already resolved so far, contrasted with the blockElements,
    // which is the code we've yet to resolve. As we recurse, we remove things
    // from the blockElements argument, and add them to the resolvedCode argument.
    // Stored in backwards order. Reverse to return when reaching the base case.
    resolvedCode: ConsList<ImportResolvedExpr>
): ExprResolutionResult {
    return when (blockElements) {
        // Base case, nil. Return our tail recursion variables.
        is Nil -> ExprResolutionResult(
            ImportResolvedExpr.Block(loc, resolvedCode.reverse()),
            reachedFiles, reachedTypes
        )
        // We have expressions still to go.
        is Cons -> {
            // Get expr and rest
            val expr = blockElements.elem
            val rest = blockElements.rest

            // Resolve the expression recursively:


        }
    }
}

tailrec fun resolveExpr(
    expr: ParsedElement.ParsedExpr,
    startingTypeMappings: ConsMap<String, ImportResolvedTypeDef>,
    typeMappings: ConsMap<String, ImportResolvedTypeDef>,
    ast: ParsedAST,
    cache: IdentityCache<ParsedFile, ImportResolvedFile>,
    reachedFiles: ConsList<ImportResolvedFile>,
    reachedTypes: ConsList<ImportResolvedTypeDef>,
    resolvedCode: ConsList<ImportResolvedExpr>
): ExprResolutionResult {
    when (expr) {
        is ParsedElement.ParsedExpr.Block -> resolveBlock(expr.loc, expr.elements, startingTypeMappings, typeMappings, ast, cache, reachedFiles, reachedTypes, resolvedCode)
        is ParsedElement.ParsedExpr.Import -> {
            // Get the other file, or error if it doesn't exist
            val otherFile = ast.files.lookup(expr.path)?.value
                ?: throw ResolutionException(expr.path, expr.loc)
            // Resolve the other file recursively (making use of the cache)
            val otherResolved = resolveFile(otherFile, startingTypeMappings, ast, cache)
        }
    }
}