package representation.passes.name_resolving

import representation.asts.parsed.ParsedAST
import representation.asts.parsed.ParsedFile
import builtins.BuiltinType
import representation.asts.resolved.ResolvedAST
import representation.asts.resolved.ResolvedFile
import representation.asts.resolved.ResolvedTypeDef
import util.*
import util.caching.IdentityCache
import java.util.IdentityHashMap
import java.util.LinkedList
import java.util.Queue

/**
 *
 * Convert a ParsedAST into a ResolvedAST.
 * Details about the difference between the two can be found in their
 * respective files.
 * - Requires builtin type information. This information is injected into the AST.
 */
fun resolveAST(ast: ParsedAST, builtinTypes: ConsList<BuiltinType>): ResolvedAST {

    // Get the starting map of type definitions.
    val startingMappings: ConsMap<String, ResolvedTypeDef.Builtin> = builtinTypes
        .map { ResolvedTypeDef.Builtin(it) } // Wrap in Builtin()
        .associateBy { t -> if (t.builtin.nameable) t.name else "" } // Associate by name (or empty string if not nameable)

    val mainFile: ParsedFile = ast.files["main"]?.value ?: throw IllegalArgumentException("Expected \"main\" file, but did not find one")

    // Create the cache
    val publicMemberCache: IdentityCache<ParsedFile, PublicMembers> = IdentityCache()
    // Repeatedly resolve all new files that were reached during the
    // last resolution phase, until convergence
    val alreadyResolved: IdentityCache<ParsedFile, FileResolutionResult> = IdentityCache()
    val resolveQueue: Queue<ParsedFile> = LinkedList()
    resolveQueue.add(mainFile) // Start out with main file
    while (resolveQueue.isNotEmpty()) {
        val parsedFile = resolveQueue.poll()
        // If we  haven't already resolved it, do so
        alreadyResolved.get(parsedFile) {
            val res = resolveFile(it, startingMappings, ast, publicMemberCache)
            // Also, add its "otherFiles" to the queue.
            resolveQueue.addAll(res.otherFiles)
            res
        }
    }

    // Once that's done, the alreadyResolved cache contains what we need.
    val allFiles = alreadyResolved.freeze()
        .mapKeys { it.key.name }
        .mapValues { it.value.file }

    // Also create the map of builtins, out of the starting mappings.
    val builtinMap = startingMappings
        .map { it.second } // Select the values, the Builtin ResolvedTypeDefs
        .associateByTo(IdentityHashMap()) { it.builtin } // Map them according to their builtin

    return ResolvedAST(allFiles, builtinMap)
}

// Result of resolving a file:
// - The resolved file
// - A set of other files which were reached while resolving
//   it, yet to be resolved themselves
private data class FileResolutionResult(
    val file: ResolvedFile, // The resolved file.
    val otherFiles: Set<ParsedFile>, // A set of other files reached while resolving this one.
)

private fun resolveFile(
    file: ParsedFile, // The file to resolve
    startingMappings: ConsMap<String, ResolvedTypeDef>, // The starting mappings, containing the built-in types
    ast: ParsedAST, // The AST containing the file we're resolving
    publicMemberCache: IdentityCache<ParsedFile, PublicMembers> // Memoize the set of public members of some file
): FileResolutionResult {
    // Update public member cache if necessary
    publicMemberCache.get(file) { getPubMembers(it.block, startingMappings, ast, publicMemberCache) }

    val resolvedBlock = resolveExpr(file.block, startingMappings, startingMappings, ast, publicMemberCache)
    return FileResolutionResult(
        ResolvedFile(file.name, resolvedBlock.expr),
        resolvedBlock.files,
    )
}