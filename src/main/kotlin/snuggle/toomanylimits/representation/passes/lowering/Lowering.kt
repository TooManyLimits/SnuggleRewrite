package snuggle.toomanylimits.representation.passes.lowering

import snuggle.toomanylimits.representation.asts.ir.GeneratedType
import snuggle.toomanylimits.representation.asts.ir.Instruction
import snuggle.toomanylimits.representation.asts.ir.Program
import snuggle.toomanylimits.representation.asts.typed.TypeDef
import snuggle.toomanylimits.representation.asts.typed.TypedAST
import snuggle.toomanylimits.representation.asts.typed.TypedExpr
import snuggle.toomanylimits.representation.asts.typed.TypedFile
import snuggle.toomanylimits.util.ConsList
import snuggle.toomanylimits.util.caching.EqualityIncrementalCalculator
import snuggle.toomanylimits.util.insertionSort

/**
 * Near final stage - lowering the TypedAST into
 * the IR representation.
 */

fun lower(ast: TypedAST): Program {
    // Create an incremental calculator for the types
    val typeCalc = EqualityIncrementalCalculator<TypeDef, GeneratedType>()
    // Figure out which files actually do anything when run:
    val filesWithEffects = filesWithEffects(ast.allFiles)
    // Compile the top-level code into instructions
    val topLevelCode = ast.allFiles.mapValues {
        // Lower the expr and wrap it in a code block
        val loweredCode = lowerExpr(it.value.code, ConsList.of(ConsList.nil()), filesWithEffects, typeCalc)
        Instruction.CodeBlock(loweredCode.toList())
    }
    // Get the types list, and sort it so subtypes come after their supertypes
    val typesList = typeCalc.freeze().toList().filter { it.second != null }.toMutableList()
//    println(typesList.map { it.second!!.runtimeName })
    typesList.insertionSort { pair, pair2 ->
        val a = pair.first
        val b = pair2.first
        if (a.isSubtype(b))
            if (b.isSubtype(a)) 0
            else 1
        else
            if (b.isSubtype(a)) - 1
            else 0
    }
//    println(typesList.map { it.second!!.runtimeName })
    // Return the program
    return Program(typesList.map { it.second!! }, topLevelCode)
}

/**
 * Find the set of files which have effects when run. This is true if either:
 * - The file has any expression inside other than imports.
 * - Some import in this file has effects when run.
 * The solution is found iteratively using a graph structure.
 */
private fun filesWithEffects(files: Map<String, TypedFile>): Set<String> {
    // Null = has effects
    // Non-null: the list of edges leading to other files
    var nodes: Map<String, List<String>?> = files.mapValues { when(it.value.code) {
        is TypedExpr.Block -> {
            (it.value.code as TypedExpr.Block).exprs.map {
                if (it is TypedExpr.Import)
                    it.file// Add imports to the list
                else
                    return@mapValues null // Non-imports go to null
            }
        }
        is TypedExpr.RawStructConstructor -> listOf()
        else -> throw IllegalStateException("Unexpected state for file body? Should always be block or unit constructor, but found ${it.value.code}. Bug in compiler, please report")
    }}
    // Whether the set of nodes changed last time
    var changed = true

    while (changed) {
        changed = false
        nodes = nodes.mapValues { (_, v) -> when {
            v == null -> null // Null goes to null
            else -> {
                // Check if any of the dependencies are null
                val anyNullDependency = v.any { nodes[it] == null }
                if (anyNullDependency) {
                    // If so, then mark changed, and update this to null as well.
                    changed = true
                    null
                } else {
                    // Otherwise, this stays the same.
                    v
                }
            }
        }}
    }

    // Now, we have the final set of nodes; just collect the ones where
    // the value is null.
    return nodes.filterValues { it == null }.keys
}
