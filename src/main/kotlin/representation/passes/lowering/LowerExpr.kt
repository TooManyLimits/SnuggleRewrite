package representation.passes.lowering

import representation.asts.ir.GeneratedType
import representation.asts.ir.Instruction
import representation.asts.typed.MethodDef
import representation.asts.typed.TypeDef
import representation.asts.typed.TypedExpr
import representation.passes.typing.isFallible
import util.IdentityIncrementalCalculator

/**
 * Lowering an expression into IR.
 */

// I hope these "sequence {}" blocks aren't slow since they're really convenient
fun lowerExpr(expr: TypedExpr, typeCalc: IdentityIncrementalCalculator<TypeDef, GeneratedType>): Sequence<Instruction> = when (expr) {
    // Just a RunImport
    is TypedExpr.Import -> sequenceOf(Instruction.RunImport(expr.file))
    // Sequence the operations inside the block
    is TypedExpr.Block -> sequence {
        for (i in 0 until expr.exprs.size - 1) {
            yieldAll(lowerExpr(expr.exprs[i], typeCalc))
            yield(Instruction.Pop(expr.exprs[i].type))
        }
        yieldAll(lowerExpr(expr.exprs.last(), typeCalc))
    }
    // What to do for a declaration depends on the type of pattern
    is TypedExpr.Declaration -> sequence {
        // Compute the types involved in the pattern
        computeTypes(expr.pattern, typeCalc)
        // Yield the things
        if (isFallible(expr.pattern)) {
            TODO()
        } else {
            // Compile the initializer
            yieldAll(lowerExpr(expr.initializer, typeCalc))
            // Store/bind local variable
            yield(Instruction.StoreLocal(expr.variableIndex, expr.pattern.type))
            // Push true
            yield(Instruction.Push(true, expr.type))
        }
    }

    // Load the local variable, simple
    is TypedExpr.Variable -> sequenceOf(Instruction.LoadLocal(expr.variableIndex, expr.type))
    // Push the literal
    is TypedExpr.Literal -> sequenceOf(Instruction.Push(expr.value, expr.type))
    // Compile arguments, make call
    is TypedExpr.MethodCall -> sequence {
        yieldAll(lowerExpr(expr.receiver, typeCalc))
        for (arg in expr.args)
            yieldAll(lowerExpr(arg, typeCalc))
        when (expr.methodDef) {
            is MethodDef.BytecodeMethodDef -> yield(Instruction.Bytecodes(0, expr.methodDef.bytecode)) //TODO: Cost
            is MethodDef.SnuggleMethodDef -> yield(Instruction.MethodCall.Virtual(expr.methodDef))
            is MethodDef.ConstMethodDef,
            is MethodDef.StaticConstMethodDef -> throw IllegalStateException("Cannot lower const method def - bug in compiler, please report")
        }
    }
    is TypedExpr.StaticMethodCall -> sequence {
        lowerTypeDef(expr.receiverType, typeCalc)
        for (arg in expr.args)
            yieldAll(lowerExpr(arg, typeCalc))
        when (expr.methodDef) {
            is MethodDef.BytecodeMethodDef -> yield(Instruction.Bytecodes(0, expr.methodDef.bytecode)) // TODO: Cost
            is MethodDef.SnuggleMethodDef -> yield(Instruction.MethodCall.Static(expr.methodDef))
            is MethodDef.ConstMethodDef,
            is MethodDef.StaticConstMethodDef -> throw IllegalStateException("Cannot lower const method def - bug in compiler, please report")
        }
    }
    // Call the super() section, then set fields
    is TypedExpr.ClassConstructorCall -> sequence {
        lowerTypeDef(expr.type, typeCalc)
        // Push and dup the receiver
        yield(Instruction.NewRefAndDup(expr.type))
        // Push args
        for (arg in expr.args)
            yieldAll(lowerExpr(arg, typeCalc))
        when (expr.constructorMethodDef) {
            // Bytecode, emit the bytecode directly
            is MethodDef.BytecodeMethodDef -> yield(Instruction.Bytecodes(0, expr.constructorMethodDef.bytecode)) // TODO: Cost
            // Invoke the constructor, ✨special✨
            is MethodDef.SnuggleMethodDef -> yield(Instruction.MethodCall.Special(expr.constructorMethodDef))
            is MethodDef.ConstMethodDef,
            is MethodDef.StaticConstMethodDef -> throw IllegalStateException("Cannot lower const method def - bug in compiler, please report")
        }
    }
}