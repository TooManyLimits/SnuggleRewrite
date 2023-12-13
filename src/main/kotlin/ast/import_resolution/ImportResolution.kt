package ast.import_resolution

import ast.parsing.ParsedAST
import ast.parsing.ParsedElement
import ast.parsing.ParsedFile
import builtins.BuiltinType
import errors.ResolutionException
import util.*
import util.ConsList.Companion.nil

/**
 * Resolve a ParsedAST into an ImportResolvedAST.
 * Details about the difference between the two can be found in their
 * respective files.
 *
 * - Requires builtin type information. This information is injected into the AST.
 */
fun resolveAST(ast: ParsedAST, builtinTypes: ConsList<BuiltinType>): ImportResolvedAST {

    // Get the starting map of type definitions.
    val startingTypeDefs: ConsMap<String, ImportResolvedTypeDef> = builtinTypes
        .filter(BuiltinType::nameable) // Only nameable types should be in here
        .map(ImportResolvedTypeDef::Builtin) // Wrap in Builtin()
        .associate { t -> t.builtin.name } // Associate by name

    val mainFile: ParsedFile = ast.files.lookup("main")?.value ?: throw IllegalArgumentException("Expected \"main\" file, but did not find one")

    val resolvedMainFile = resolveFile(mainFile)

    throw RuntimeException()
}

/**
 * Resolve a file. The file is a list of ParsedElement, which can be one of several things.
 *
 * Takes in:
 * - The file to be resolved.
 * - The starting type mappings. When we import a new file, we don't want to resolve it while
 *   considering our current set of imports - it should have the same starter types as others.
 * - The AST, containing the mappings of String -> ParsedFile. If we come across an import,
 *   we need to be able to handle it by looking up its String.
 * - A mutable cache mapping inputs to outputs, reducing redundancy from resolving the same
 *   file multiple times. This is the only mutable data involved in the function.
 * Outputs:
 * - A list of ImportResolvedFile, which is all the files resolved by this process, recursively.
 *   This means that when resolving "main", we get back a set of all files that are imported by
 *   the program.
 */
fun resolveFile(
    file: ParsedFile,
    startingTypeMappings: ConsMap<String, ImportResolvedTypeDef>,
    parsedAST: ParsedAST,
    cache: IdentityCache<ParsedFile, ImportResolvedFile>
): ImportResolvedFile {
    // Get the file if cached. If not, resolve it recursively.
    return cache.get(file) {
        resolveExprsRecursive(file.name, file.elems, startingTypeMappings, startingTypeMappings, parsedAST, cache, nil(), nil())
    }
}

// Recursive function that actually does the work.
tailrec fun resolveExprsRecursive(
    // New param: The file name, just so we can create the file at the end.
    fileName: String,
    elems: ConsList<ParsedElement>,
    startingTypeMappings: ConsMap<String, ImportResolvedTypeDef>,
    // New param: The current type mappings, as they stand right now.
    // They may be updated as we make more recursive calls.
    typeMappings: ConsMap<String, ImportResolvedTypeDef>,
    parsedAST: ParsedAST,
    cache: IdentityCache<ParsedFile, Cons<ImportResolvedFile>>,
    // New param: other files reached during this traversal. Starts as nil.
    reachedFiles: ConsList<ImportResolvedFile>,
    // New param: the expressions that have been resolved so far. Starts as nil.
    // Stored in backwards order. Reverse when reaching the base case.
    resolvedCode: ConsList<ImportResolvedExpr>
): Cons<ImportResolvedFile> {
    return when (elems) {
        is Nil -> Cons(ImportResolvedFile(fileName, resolvedCode.reverse()), reachedFiles)
        is Cons -> {
            val elem = elems.elem
            val rest = elems.rest
            return when (elem) {

                // Expressions

                // Import
                is ParsedElement.ParsedExpr.Import -> {
                    // Get the other file, or error if it doesn't exist
                    val otherFile = parsedAST.files.lookup(elem.path)?.value
                        ?: throw ResolutionException(elem.path, elem.loc)
                    // Resolve the other file recursively (making use of the cache)
                    val otherResolved = resolveFile(otherFile, startingTypeMappings, parsedAST, cache)
                    // Return recurse
                    resolveExprsRecursive(fileName, rest, startingTypeMappings, typeMappings, parsedAST, cache, reachedFiles.append(otherResolved), Cons(ImportResolvedExpr.Import(elem.loc, otherResolved.elem), resolvedCode))
                }

                is ParsedElement.ParsedExpr.Block -> {
                    val innerResolved = resolveExprsRecursive(fileName, elem.exprs, startingTypeMappings, typeMappings, parsedAST, cache, reachedFiles, nil())
                }


                else -> throw IllegalStateException()
            }
        }
    }
}
