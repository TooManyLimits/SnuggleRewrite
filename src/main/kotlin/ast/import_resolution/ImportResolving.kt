package ast.import_resolution

import ast.parsing.ParsedAST
import ast.parsing.ParsedFile
import ast.parsing.parseExpr
import builtins.BuiltinType
import util.*

/**
 * Resolve a ParsedAST into an ImportResolvedAST.
 * Details about the difference between the two can be found in their
 * respective files.
 *
 * - Requires builtin type information. This information is injected into the AST.
 */
fun resolveAST(ast: ParsedAST, builtinTypes: ConsList<BuiltinType>): ImportResolvedAST {

    // Get the starting map of type definitions.
    val startingMappings: ConsMap<String, ImportResolvedTypeDef> = builtinTypes
        .filter(BuiltinType::nameable) // Only nameable types should be in here
        .map(ImportResolvedTypeDef::Builtin) // Wrap in Builtin()
        .associateBy { t -> t.builtin.name } // Associate by name

    val mainFile: ParsedFile = ast.files["main"]?.value ?: throw IllegalArgumentException("Expected \"main\" file, but did not find one")
    val resolvedMainFile = resolveFile(mainFile, startingMappings, ast, IdentityCache())

    val allFiles = resolvedMainFile.otherFiles + resolvedMainFile.file
//    val allTypes = resolvedMainFile.allTypes

    return ImportResolvedAST(allFiles.associateBy { it.name }) //, allTypes)
}

data class FileResolutionResult(
    val file: ImportResolvedFile, // The resolved file.
    val otherFiles: Set<ImportResolvedFile>, // A set of other files reached while resolving this one.
//    val allTypes: Set<ImportResolvedTypeDef>, // All type definitions reached while resolving this file.
    val myPubTypes: ConsMap<String, ImportResolvedTypeDef> // The pub types defined in this file.
)

fun resolveFile(
    file: ParsedFile,
    startingMappings: ConsMap<String, ImportResolvedTypeDef>,
    ast: ParsedAST,
    cache: IdentityCache<ParsedFile, FileResolutionResult> // Just a pinch of mutability used for memoizing calls to this function, resolveFile.
): FileResolutionResult = cache.get(file) {
    val resolvedBlock = resolveExpr(file.block, startingMappings, startingMappings, ast, cache)
    FileResolutionResult(
        ImportResolvedFile(file.name, resolvedBlock.expr),
        resolvedBlock.files,
//        resolvedBlock.types,
        resolvedBlock.exposedTypes
    )
}