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