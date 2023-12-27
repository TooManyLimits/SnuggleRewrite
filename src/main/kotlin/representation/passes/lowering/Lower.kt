package representation.passes.lowering

import representation.asts.ir.GeneratedMethod
import representation.asts.ir.GeneratedType
import representation.asts.ir.Instruction
import representation.asts.ir.Program
import representation.asts.typed.MethodDef
import representation.asts.typed.TypeDef
import representation.asts.typed.TypedAST
import util.Cache
import util.ConsList
import util.IdentityCache

/**
 * Near final stage - lowering the TypedAST into
 * the IR representation.
 */

fun lower(ast: TypedAST): Program {
    // Create some caches, so we can keep track of methods and such that are converted.
    val typeCache = IdentityCache<TypeDef, GeneratedType>()
    val methodCache = IdentityCache<MethodDef, GeneratedMethod>()
    // Compile the top-level code into instructions
    val topLevelCode = ast.allFiles.mapValues {
        // Lower the expr and wrap it in a code block
        val loweredCode = lowerExpr(it.value.code)
        Instruction.CodeBlock(ConsList.fromIterable(loweredCode.asIterable()))
    }
    return Program(listOf(), topLevelCode)
}