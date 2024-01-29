package representation.passes.output

import builtins.*
import builtins.helpers.Fraction
import builtins.helpers.basicLocal
import builtins.helpers.popType
import builtins.helpers.swapBasic
import builtins.primitive.*
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import representation.asts.ir.Instruction
import java.math.BigInteger
import java.lang.String

fun outputInstruction(inst: Instruction, writer: MethodVisitor): Unit = when (inst) {

    // Code blocks deal with instruction counts, and they output all
    // the instructions contained within.
    is Instruction.CodeBlock -> {
        // TODO Deal with instruction counting
        for (innerInst in inst.instructions)
            outputInstruction(innerInst, writer)
    }
    // Just pass the writer to the consumer
    is Instruction.Bytecodes -> inst.bytecodes(writer)
    // Check the boolean field, run the import if needed
    is Instruction.RunImport -> {
        val alreadyImported = Label()
        // Load field on the stack
        writer.visitFieldInsn(Opcodes.GETSTATIC, getImporterClassName(), getImporterFieldName(inst.fileName), "Z")
        // If it's false, jump to the end and push false
        writer.visitJumpInsn(Opcodes.IFNE, alreadyImported)
        // Set the field to true
        writer.visitInsn(Opcodes.ICONST_1)
        writer.visitFieldInsn(Opcodes.PUTSTATIC, getImporterClassName(), getImporterFieldName(inst.fileName), "Z")
        // Call the method
        writer.visitMethodInsn(Opcodes.INVOKESTATIC, getImporterClassName(), getImporterMethodName(inst.fileName), "()V", false)
        // Emit ending label
        writer.visitLabel(alreadyImported)
    }
    // Make the call with the proper bytecode
    is Instruction.MethodCall -> {
        val owner = inst.methodToCall.owningType.runtimeName!!
        val name = inst.methodToCall.runtimeName
        val descriptor = getMethodDescriptor(inst.methodToCall)
        writer.visitMethodInsn(inst.invokeBytecode, owner, name, descriptor, inst.isInterface)
    }

    // Helper function for return
    is Instruction.Return -> outputReturn(inst, writer)
    is Instruction.IrLabel -> writer.visitLabel(inst.label)
    is Instruction.Jump -> writer.visitJumpInsn(Opcodes.GOTO, inst.label)
    is Instruction.JumpIfFalse -> writer.visitJumpInsn(Opcodes.IFEQ, inst.label)
    is Instruction.JumpIfTrue -> writer.visitJumpInsn(Opcodes.IFNE, inst.label)

    // Check if top 2 elements of stack are equal. They have the given type (should be primitive)
    is Instruction.TestEquality -> when(inst.type.builtin) {
        is IntType -> (inst.type.builtin as IntType).bytecodeCompareInt(Opcodes.IFEQ)(writer)
        is FloatType -> (inst.type.builtin as FloatType).bytecodeCompareFloat(Opcodes.IFNE)(writer) // Yes, it is supposed to be NE here. The code is scuffed
        StringType -> writer.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false)
        BoolType -> I32Type.bytecodeCompareInt(Opcodes.IFEQ)(writer)
        else -> throw IllegalStateException("Unrecognized literal builtin: ${inst.type.name}")
    }
    // Perform an instanceof on the top stack element, turning it into a bool. Assumed to be ref type.
    is Instruction.InstanceOf -> writer.visitTypeInsn(Opcodes.INSTANCEOF, inst.type.runtimeName)

    // Helper function for push, it has lots of logic
    is Instruction.Push -> outputPush(inst, writer)
    // Pop the correct number of stack slots
    is Instruction.Pop -> popType(inst.typeToPop, writer)
    // Swap the basic types
    is Instruction.SwapBasic -> swapBasic(inst.top, inst.second, writer)
    // Create a new reference and dup it
    is Instruction.NewRefAndDup -> {
        writer.visitTypeInsn(Opcodes.NEW, inst.typeToCreate.runtimeName!!)
        writer.visitInsn(Opcodes.DUP)
    }
    is Instruction.DupRef -> writer.visitInsn(Opcodes.DUP)
    is Instruction.LoadRefType -> writer.visitVarInsn(Opcodes.ALOAD, inst.index)

    // Store/load local variables. Can only deal with basic variables, not plural types.
    is Instruction.StoreLocal -> basicLocal(inst.index, inst.type, store = true, writer)
    is Instruction.LoadLocal -> basicLocal(inst.index, inst.type, store = false, writer)

    // Field instructions
    is Instruction.GetReferenceTypeField -> {
        if (inst.fieldType.descriptor.size != 1) throw IllegalStateException("Attempt to GetReferenceTypeField with plural type? Bug in compiler, please report")
        writer.visitFieldInsn(Opcodes.GETFIELD, inst.owningType.runtimeName, inst.runtimeFieldName, inst.fieldType.descriptor[0])
    }
    is Instruction.PutReferenceTypeField -> {
        if (inst.fieldType.descriptor.size != 1) throw IllegalStateException("Attempt to GetReferenceTypeField with plural type? Bug in compiler, please report")
        writer.visitFieldInsn(Opcodes.PUTFIELD, inst.owningType.runtimeName, inst.runtimeFieldName, inst.fieldType.descriptor[0])
    }
    is Instruction.GetStaticField -> {
        if (inst.fieldType.descriptor.size != 1) throw IllegalStateException("Attempt to GetStaticField with plural type? Bug in compiler, please report")
        writer.visitFieldInsn(Opcodes.GETSTATIC, inst.owningType.runtimeName, inst.runtimeFieldName, inst.fieldType.descriptor[0])
    }
    is Instruction.PutStaticField -> {
        if (inst.fieldType.descriptor.size != 1) throw IllegalStateException("Attempt to PutStaticField with plural type? Bug in compiler, please report")
        writer.visitFieldInsn(Opcodes.PUTSTATIC, inst.owningType.runtimeName, inst.runtimeFieldName, inst.fieldType.descriptor[0])
    }
}

private fun outputReturn(inst: Instruction.Return, writer: MethodVisitor) {
    if (inst.basicTypeToReturn == null)
        writer.visitInsn(Opcodes.RETURN)
    else {
        val type = inst.basicTypeToReturn.unwrap()
        val builtin = type.builtin
        if (builtin == BoolType) writer.visitInsn(Opcodes.IRETURN)
        else if (builtin is IntType) writer.visitInsn(if (builtin.bits <= 32) Opcodes.IRETURN else Opcodes.LRETURN)
        else if (builtin is FloatType) writer.visitInsn(if (builtin.bits <= 32) Opcodes.FRETURN else Opcodes.DRETURN)
        else if (type.isReferenceType || (type.builtin == OptionType && type.generics[0].isReferenceType)) writer.visitInsn(Opcodes.ARETURN)
        else throw IllegalStateException("Unexpected type \"${type.name}\" made it to IR.Return - bug in compiler, please report")
    }
}

// Separate helper for a push instruction, since they have lots of logic
private fun outputPush(inst: Instruction.Push, writer: MethodVisitor) {
    var value = inst.valueToPush

    // Fraction. Convert to either Float or Double ahead of time to save code
    if (value is Fraction) {
        val floatType = inst.type.builtin as FloatType
        value = when(floatType.bits) {
            32 -> value.toFloat()
            64 -> value.toDouble()
            else -> throw IllegalStateException()
        }
    }
    // Same with char
    if (value is Char)
        value = BigInteger.valueOf(value.code.toLong())

    // Separate case for each literal object type
    return when (value) {
        // Boolean, push 1 or 0
        is Boolean -> writer.visitInsn(if (value) Opcodes.ICONST_1 else Opcodes.ICONST_0)

        // Big integer. Output proper thing based on the type
        is BigInteger -> {
            val intType = inst.type.builtin as IntType
            val v = value
            if (intType.bits <= 32) {
                if (!intType.fits(v))
                    throw IllegalStateException("Number literal out of range - but this should have been caught earlier. Bug in compiler, please report")
                when {
                    // LDC, SIPUSH
                    !I16Type.fits(v) -> writer.visitLdcInsn(v.intValueExact())
                    !I8Type.fits(v) -> writer.visitIntInsn(Opcodes.SIPUSH, v.shortValueExact().toInt())
                    // ICONST
                    v == BigInteger.ZERO -> writer.visitInsn(Opcodes.ICONST_0)
                    v == BigInteger.ONE -> writer.visitInsn(Opcodes.ICONST_1)
                    v == BigInteger.TWO -> writer.visitInsn(Opcodes.ICONST_2)
                    v == BigInteger.valueOf(-1) -> writer.visitInsn(Opcodes.ICONST_M1)
                    v == BigInteger.valueOf(3) -> writer.visitInsn(Opcodes.ICONST_3)
                    v == BigInteger.valueOf(4) -> writer.visitInsn(Opcodes.ICONST_4)
                    v == BigInteger.valueOf(5) -> writer.visitInsn(Opcodes.ICONST_5)
                    // BIPUSH
                    else -> writer.visitIntInsn(Opcodes.BIPUSH, v.byteValueExact().toInt())
                }
            } else {
                when (v) {
                    BigInteger.ZERO -> writer.visitInsn(Opcodes.LCONST_0)
                    BigInteger.ONE -> writer.visitInsn(Opcodes.LCONST_1)
                    else -> writer.visitLdcInsn(v.longValueExact())
                }
            }
        }

        is Float -> when (value) {
            0f -> writer.visitInsn(Opcodes.FCONST_0)
            1f -> writer.visitInsn(Opcodes.FCONST_1)
            2f -> writer.visitInsn(Opcodes.FCONST_2)
            else -> writer.visitLdcInsn(value)
        }

        is Double -> when (value) {
            0.0 -> writer.visitInsn(Opcodes.DCONST_0)
            1.0 -> writer.visitInsn(Opcodes.DCONST_1)
            else -> writer.visitLdcInsn(value)
        }

        is String -> writer.visitLdcInsn(value)

        else -> throw IllegalStateException("Unrecognized literal class: ${inst.valueToPush.javaClass.name}")
    }
}