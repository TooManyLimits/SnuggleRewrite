package representation.passes.lowering

import representation.asts.ir.GeneratedType
import representation.asts.ir.Instruction
import representation.asts.ir.Program
import representation.asts.typed.TypeDef
import representation.asts.typed.TypedAST
import util.ConsList
import util.caching.IdentityIncrementalCalculator
import util.insertionSort

/**
 * Near final stage - lowering the TypedAST into
 * the IR representation.
 */

fun lower(ast: TypedAST): Program {
    // Create an incremental calculator for the types
    val typeCalc = IdentityIncrementalCalculator<TypeDef, GeneratedType>()
    // Compile the top-level code into instructions
    val topLevelCode = ast.allFiles.mapValues {
        // Lower the expr and wrap it in a code block
        val loweredCode = lowerExpr(it.value.code, ConsList.nil(), typeCalc)
        Instruction.CodeBlock(ConsList.fromIterable(loweredCode.asIterable()))
    }
    // Get the types list, and sort it so subtypes come after their supertypes
    val typesList = typeCalc.freeze().toList().filter { it.second != null }.toMutableList()
//    println(typesList.map { it.second!!.runtimeName })
    typesList.insertionSort { pair, pair2 ->
        val a = pair.first
        val b = pair2.first
        if (a.isSubtype(b))
            if (b.isSubtype(a)) {
                println("${a.name} and ${b.name} are the same?")
                0
            }
            else {
                println("${a.name} is a subtype of ${b.name}")
                1
            }
        else
            if (b.isSubtype(a)) {
                println("${b.name} is a subtype of ${a.name}")
                -1
            }
            else {
                println("${a.name} and ${b.name} are not related")
                0
            }
    }
//    println(typesList.map { it.second!!.runtimeName })
    // Return the program
    return Program(typesList.map { it.second!! }, topLevelCode)
}