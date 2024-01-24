package representation.passes.name_resolving

import builtins.BuiltinType
import representation.asts.parsed.ParsedAST
import representation.asts.parsed.ParsedElement
import representation.asts.parsed.ParsedFile
import representation.asts.resolved.ResolvedAST
import representation.asts.resolved.ResolvedFile
import representation.asts.resolved.ResolvedImplBlock
import representation.asts.resolved.ResolvedTypeDef
import util.*
import util.caching.IdentityCache
import java.util.*


fun resolveAST(ast: ParsedAST, builtinTypes: ConsList<BuiltinType>): ResolvedAST {
    // Get the starting values.
    val startingMappings = EnvMembers(
        types = builtinTypes
            .map { ResolvedTypeDef.Builtin(it) } // Wrap in Builtin()
            .associateBy { t -> if (t.builtin.nameable) t.name else "" }, // Associate by name (or empty string if not nameable)
        implBlocks = ConsList.nil()
    )
    val resolutionCache = ResolutionCache()
    val mainFile: ParsedFile = ast.files["main"]?.value ?: throw IllegalArgumentException("Expected \"main\" file, but did not find one")

    // Begin the resolve queue.
    // Repeatedly resolve all new files that were reached during the
    // last resolution phase, until convergence
    val alreadyResolved: IdentityCache<ParsedFile, FileResolutionResult> = IdentityCache()
    val resolveQueue: Queue<ParsedFile> = LinkedList()
    resolveQueue.add(mainFile) // Start out with main file
    while (resolveQueue.isNotEmpty()) {
        val parsedFile = resolveQueue.poll()
        // If we  haven't already resolved it, do so
        alreadyResolved.get(parsedFile) {
            val res = resolveFile(it, startingMappings, ast, resolutionCache)
            // Also, add its "otherFiles" to the queue.
            resolveQueue.addAll(res.filesReached)
            res
        }
    }

    // Once that's done, the alreadyResolved cache contains what we need.
    val allFiles = alreadyResolved.freeze()
        .mapKeys { it.key.name }
        .mapValues { it.value.file }

    // Also create the map of builtins, out of the starting mappings.
    val builtinMap = startingMappings
        .types
        .map { it.second as ResolvedTypeDef.Builtin } // Select the values, the Builtin ResolvedTypeDefs
        .associateByTo(IdentityHashMap()) { it.builtin } // Map them according to their builtin

    return ResolvedAST(allFiles, builtinMap)
}

// Result of resolving a file:
// - The resolved file
// - A set of other files which were reached while resolving
//   it, yet to be resolved themselves
private data class FileResolutionResult(
    val file: ResolvedFile, // The resolved file.
    val filesReached: Set<ParsedFile>, // A set of other files reached while resolving this one.
)

private fun resolveFile(
    file: ParsedFile, // The file to resolve
    startingMappings: EnvMembers, // The starting mappings, containing the built-in types
    ast: ParsedAST, // The AST containing the file we're resolving
    resolutionCache: ResolutionCache // Memoize the set of public members of some file
): FileResolutionResult {
    // Update public member cache if necessary
    resolutionCache.publicMembers.get(file) { getPublicMembersAndInitIndirections(it.block, resolutionCache) }
    // Resolve the block and return the result
    val resolvedBlock = resolveExpr(file.block, startingMappings, startingMappings, ast, resolutionCache)
    return FileResolutionResult(
        ResolvedFile(file.name, resolvedBlock.expr),
        resolvedBlock.filesReached,
    )
}

fun getPublicMembersAndInitIndirections(block: ParsedElement.ParsedExpr.Block, cache: ResolutionCache): EnvMembers {
    var pubMembers = EnvMembers()
    block.elements.forEach {
        if (it is ParsedElement.ParsedTypeDef) {
            val ind = cache.typeDefIndirections.get(it) { ResolvedTypeDef.Indirection() }
            if (it.pub)
                pubMembers = pubMembers.extendType(it.name, ind)
        } else if (it is ParsedElement.ParsedImplBlock) {
            val ind = cache.implBlockIndirections.get(it) { ResolvedImplBlock.Indirect() }
            if (it.pub)
                pubMembers = pubMembers.extendImplBlock(ind)
        } else if (it is ParsedElement.ParsedExpr.Block) {
            val rest = getPublicMembersAndInitIndirections(it, cache)
            if (it.pub)
                pubMembers = pubMembers.extend(rest)
        }
    }
    return pubMembers
}

class ResolutionCache(
    val typeDefIndirections: IdentityCache<ParsedElement.ParsedTypeDef, ResolvedTypeDef.Indirection> = IdentityCache(),
    val implBlockIndirections: IdentityCache<ParsedElement.ParsedImplBlock, ResolvedImplBlock.Indirect> = IdentityCache(),
    val publicMembers: IdentityCache<ParsedFile, EnvMembers> = IdentityCache(),
)

// Contains the set of types, implBlocks, and potentially
// other things, which are "accessible" by some environment.
// These are passed up and down through function calls and
// are handled in the caches.
class EnvMembers(
    val types: ConsMap<String, ResolvedTypeDef> = ConsMap.of(),
    val implBlocks: ConsList<ResolvedImplBlock> = ConsList.nil()
) {
    fun extend(other: EnvMembers) = EnvMembers(types.extend(other.types), other.implBlocks.append(implBlocks))
    fun extendType(name: String, type: ResolvedTypeDef) = EnvMembers(types.extend(name, type), implBlocks)
    fun extendImplBlock(block: ResolvedImplBlock) = EnvMembers(types, Cons(block, implBlocks))

    companion object {
        fun join(elements: Iterable<EnvMembers>): EnvMembers {
            var cur = EnvMembers()
            for (element in elements) cur = cur.extend(element)
            return cur
        }
    }

}