package representation.passes.lowering

import representation.asts.ir.Instruction
import representation.asts.typed.MethodDef
import representation.asts.typed.TypeDef
import representation.asts.typed.TypedExpr
import representation.passes.typing.isFallible
import util.ConsMap

/**
 * Lowering an expression into IR.
 */

// I hope these "sequence {}" blocks aren't slow since they're really convenient
fun lowerExpr(expr: TypedExpr): Sequence<Instruction> = when (expr) {
    // Just a RunImport
    is TypedExpr.Import -> sequenceOf(Instruction.RunImport(expr.file))
    // Sequence the operations inside the block
    is TypedExpr.Block -> sequence {
        for (i in 0 until expr.exprs.size - 1) {
            yieldAll(lowerExpr(expr.exprs[i]))
            yield(Instruction.Pop(expr.exprs[i].type))
        }
        yieldAll(lowerExpr(expr.exprs.last()))
    }
    // What to do for a declaration depends on the type of pattern
    is TypedExpr.Declaration ->
        if (isFallible(expr.pattern)) sequence {
            TODO()
        } else sequence {
            // Compile the initializer
            yieldAll(lowerExpr(expr.initializer))
            // Store/bind local variable
            yield(Instruction.StoreLocal(expr.variableIndex, expr.pattern.type))
            // Push true
            yield(Instruction.Push(true))
        }
    // Load the local variable, simple
    is TypedExpr.Variable -> sequenceOf(Instruction.LoadLocal(expr.variableIndex, expr.type))
    // Push the literal
    is TypedExpr.Literal -> sequenceOf(Instruction.Push(expr.value))
    // Compile arguments, make call
    is TypedExpr.MethodCall -> sequence {
        yieldAll(lowerExpr(expr.receiver))
        for (arg in expr.args)
            yieldAll(lowerExpr(arg))
        when (expr.methodDef) {
            is MethodDef.BytecodeMethodDef -> yield(Instruction.Bytecodes(0, expr.methodDef.bytecode)) //TODO: Cost
            is MethodDef.SnuggleMethodDef -> yield(Instruction.VirtualCall(expr.methodDef))
            is MethodDef.ConstMethodDef -> TODO()
        }
    }
    is TypedExpr.StaticMethodCall -> sequence {
        for (arg in expr.args)
            yieldAll(lowerExpr(arg))
        when (expr.methodDef) {
            is MethodDef.BytecodeMethodDef -> yield(Instruction.Bytecodes(0, expr.methodDef.bytecode)) // TODO: Cost
            is MethodDef.SnuggleMethodDef -> yield(Instruction.StaticCall(expr.methodDef))
            is MethodDef.ConstMethodDef -> TODO()
        }
    }
}