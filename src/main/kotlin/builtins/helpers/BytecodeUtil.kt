package builtins.helpers

import builtins.BoolType
import builtins.FloatType
import builtins.IntType
import builtins.OptionType
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import representation.asts.typed.TypeDef


/**
 * Pop an object of the given type from the top of the stack
 */
fun popType(type: TypeDef, writer: MethodVisitor): Unit = when {
    type.isPlural -> type.nonStaticFields.forEach { popType(it.type, writer) }
    type.stackSlots == 2 -> writer.visitInsn(Opcodes.POP2)
    type.stackSlots == 1 -> writer.visitInsn(Opcodes.POP)
    else -> throw IllegalStateException("Types should always be 1, 2, or plural slots")
}

/**
 * Swap two "basic" types on top of the stack. Neither type may be plural.
 */
fun swapBasic(top: TypeDef, second: TypeDef, writer: MethodVisitor): Unit = when {
    top.isPlural || second.isPlural -> throw IllegalStateException("Calling swapBasic with plural types - bug in compiler, please report")
    top.stackSlots == 1 && second.stackSlots == 1 -> writer.visitInsn(Opcodes.SWAP)
    top.stackSlots == 2 && second.stackSlots == 1 -> { writer.visitInsn(Opcodes.DUP2_X1); writer.visitInsn(Opcodes.POP2) }
    top.stackSlots == 1 && second.stackSlots == 2 -> { writer.visitInsn(Opcodes.DUP_X2); writer.visitInsn(Opcodes.POP) }
    top.stackSlots == 2 && second.stackSlots == 2 -> { writer.visitInsn(Opcodes.DUP2_X2); writer.visitInsn(Opcodes.POP2) }
    else -> throw IllegalStateException("Types should be plural, or have 1 or 2 stack slots - bug in compiler, please report")
}

fun basicLocal(index: Int, type: TypeDef, store: Boolean, writer: MethodVisitor): Unit = when {
    type.isPlural -> throw IllegalStateException("Calling basicLocal with plural type - bug in compiler, please report")
    type.isReferenceType || type.isOptionalReferenceType ->
        writer.visitVarInsn(if (store) Opcodes.ASTORE else Opcodes.ALOAD, index)
    type.builtin is IntType -> when {
        (type.builtin as IntType).bits <= 32 -> writer.visitVarInsn(if (store) Opcodes.ISTORE else Opcodes.ILOAD, index)
        else -> writer.visitVarInsn(if (store) Opcodes.LSTORE else Opcodes.LLOAD, index)
    }
    type.builtin is FloatType -> when {
        (type.builtin as FloatType).bits <= 32 -> writer.visitVarInsn(if (store) Opcodes.FSTORE else Opcodes.FLOAD, index)
        else -> writer.visitVarInsn(if (store) Opcodes.DSTORE else Opcodes.DLOAD, index)
    }
    type.builtin == BoolType -> writer.visitVarInsn(if (store) Opcodes.ISTORE else Opcodes.ILOAD, index)
    type.isArrayOfZeroSizes -> writer.visitVarInsn(if (store) Opcodes.ISTORE else Opcodes.ILOAD, index)
    else -> throw IllegalStateException("Unrecognized type \"${type.name}\" did not meet any condition for basicLocal? Bug in compiler, please report")
}

/**
 * Push a default, uninitialized value of the given type onto the top
 * of the stack.
 */
fun pushDefaultValue(type: TypeDef, writer: MethodVisitor): Unit = when {
    type.isPlural -> type.nonStaticFields.forEach { pushDefaultValue(it.type, writer) }
    type.isReferenceType || (type.builtin == OptionType && type.generics[0].isReferenceType) -> writer.visitInsn(Opcodes.ACONST_NULL)
    type.builtin is IntType -> writer.visitInsn(if ((type.builtin as IntType).bits <= 32) Opcodes.ICONST_0 else Opcodes.LCONST_0)
    type.builtin is FloatType -> writer.visitInsn(if ((type.builtin as FloatType).bits <= 32) Opcodes.FCONST_0 else Opcodes.DCONST_0)
    type.builtin == BoolType -> writer.visitInsn(Opcodes.ICONST_0)
    else -> throw IllegalStateException("Unrecognized type \"${type.name}\" did not meet any condition for default value? Bug in compiler, please report")
}