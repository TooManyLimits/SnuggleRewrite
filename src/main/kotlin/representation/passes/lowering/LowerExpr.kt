package representation.passes.lowering

import representation.asts.ir.Instruction
import representation.asts.typed.MethodDef
import representation.asts.typed.TypedExpr
import representation.passes.typing.isFallible

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
            yield(Instruction.Push(true, expr.type))
        }
    // Load the local variable, simple
    is TypedExpr.Variable -> sequenceOf(Instruction.LoadLocal(expr.variableIndex, expr.type))
    // Push the literal
    is TypedExpr.Literal -> sequenceOf(Instruction.Push(expr.value, expr.type))
    // Compile arguments, make call
    is TypedExpr.MethodCall -> sequence {
        yieldAll(lowerExpr(expr.receiver))
        for (arg in expr.args)
            yieldAll(lowerExpr(arg))
        when (expr.methodDef) {
            is MethodDef.BytecodeMethodDef -> yield(Instruction.Bytecodes(0, expr.methodDef.bytecode)) //TODO: Cost
            is MethodDef.SnuggleMethodDef -> yield(Instruction.MethodCall.Virtual(expr.methodDef))
            is MethodDef.ConstMethodDef,
            is MethodDef.StaticConstMethodDef -> throw IllegalStateException("Cannot lower const method def - bug in compiler, please report")
        }
    }
    is TypedExpr.StaticMethodCall -> sequence {
        for (arg in expr.args)
            yieldAll(lowerExpr(arg))
        when (expr.methodDef) {
            is MethodDef.BytecodeMethodDef -> yield(Instruction.Bytecodes(0, expr.methodDef.bytecode)) // TODO: Cost
            is MethodDef.SnuggleMethodDef -> yield(Instruction.MethodCall.Static(expr.methodDef))
            is MethodDef.ConstMethodDef,
            is MethodDef.StaticConstMethodDef -> throw IllegalStateException("Cannot lower const method def - bug in compiler, please report")
        }
    }
    // Call the super() section, then set fields
    is TypedExpr.ClassConstructorCall -> sequence {
        // Push and dup the receiver
        yield(Instruction.NewRefAndDup(expr.type))
        // Push args
        for (arg in expr.args)
            yieldAll(lowerExpr(arg))
        // Invoke the constructor, ✨special✨
        yield(Instruction.MethodCall.Special(expr.constructorMethodDef))
    }
}