package representation.passes.output

import builtins.*
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import representation.asts.ir.Instruction
import representation.asts.typed.MethodDef
import representation.asts.typed.TypeDef
import java.math.BigInteger

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
        val afterImport = Label()
        // Load field on the stack
        writer.visitFieldInsn(Opcodes.GETSTATIC, getImporterClassName(), getImporterFieldName(inst.fileName), "Z")
        // If it's false, jump to the end
        writer.visitJumpInsn(Opcodes.IFNE, afterImport)
        // Set the field to true
        writer.visitInsn(Opcodes.ICONST_1)
        writer.visitFieldInsn(Opcodes.PUTSTATIC, getImporterClassName(), getImporterFieldName(inst.fileName), "Z")
        // Call the method
        writer.visitMethodInsn(Opcodes.INVOKESTATIC, getImporterClassName(), getImporterMethodName(inst.fileName), "()V", false)
        // End of the import
        writer.visitLabel(afterImport)
    }
    // Make the call with the proper bytecode
    is Instruction.MethodCall -> {
        val owner = inst.methodToCall.owningType.runtimeName!!
        val name = inst.methodToCall.runtimeName
        val descriptor = getMethodDescriptor(inst.methodToCall)
        writer.visitMethodInsn(inst.invokeBytecode, owner, name, descriptor, false)
    }

    // Helper function for return
    is Instruction.Return -> outputReturn(inst, writer)
    // Helper function for push, it has lots of logic
    is Instruction.Push -> outputPush(inst, writer)
    // Pop the correct number of stack slots
    is Instruction.Pop -> {
        // Need to recurse for plural types
        fun popRecursive(writer: MethodVisitor, type: TypeDef): Unit = when {
            type.isPlural -> type.nonStaticFields.forEach { popRecursive(writer, it.type) }
            type.stackSlots == 2 -> writer.visitInsn(Opcodes.POP2)
            type.stackSlots == 1 -> writer.visitInsn(Opcodes.POP)
            else -> throw IllegalStateException("Types should always be 1, 2, or plural slots")
        }
        popRecursive(writer, inst.typeToPop)
    }
    is Instruction.NewRefAndDup -> {
        writer.visitTypeInsn(Opcodes.NEW, inst.typeToCreate.runtimeName!!)
        writer.visitInsn(Opcodes.DUP)
    }
    is Instruction.DupRef -> writer.visitInsn(Opcodes.DUP)
    is Instruction.LoadRefType -> writer.visitVarInsn(Opcodes.ALOAD, inst.index)

    // Store/load local variables. Have a helper function for it, because of repetition
    is Instruction.StoreLocal -> handleLocal(inst.index, inst.type, store = true, writer)
    is Instruction.LoadLocal -> handleLocal(inst.index, inst.type, store = false, writer)

    // Field instructions
    is Instruction.GetReferenceTypeField -> {
        if (inst.fieldType.descriptor.size != 1) throw IllegalStateException("Attempt to GetReferenceTypeField with plural type? Bug in compiler, please report")
        writer.visitFieldInsn(Opcodes.GETFIELD, inst.owningType.runtimeName, inst.runtimeFieldName, inst.fieldType.descriptor[0])
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

private fun handleLocal(index: Int, type: TypeDef, store: Boolean, writer: MethodVisitor) {
    val type = type.unwrap() // Unwrap type
    if (type.isPlural) {
        // Plural case, we recurse
        if (store) {
            // Iterate backwards through the fields, decrementing index
            var currentIndex = index + type.stackSlots
            type.nonStaticFields.asReversed().forEach {
                // Decrement before handling
                currentIndex -= it.type.stackSlots
                handleLocal(currentIndex, it.type, true, writer)
            }
        } else {
            var currentIndex = index
            type.nonStaticFields.forEach {
                // Increment after handling
                handleLocal(currentIndex, it.type, false, writer)
                currentIndex += it.type.stackSlots
            }
        }
    } else when (type) {
        is TypeDef.InstantiatedBuiltin -> when (type.builtin) {
            // Builtin types:
            is BoolType -> writer.visitVarInsn(if (store) Opcodes.ISTORE else Opcodes.ILOAD, index)
            is IntType -> if (type.builtin.bits <= 32)
                writer.visitVarInsn(if (store) Opcodes.ISTORE else Opcodes.ILOAD, index)
            else
                writer.visitVarInsn(if (store) Opcodes.LSTORE else Opcodes.LLOAD, index)
            else -> {
                if (type.isReferenceType)
                    writer.visitVarInsn(if (store) Opcodes.ASTORE else Opcodes.ALOAD, index)
                else
                    throw IllegalStateException("Unknown builtin type by LoadLocal \"${type.builtin.name}\", bug in compiler, please report")
            }
        }
        is TypeDef.ClassDef -> writer.visitVarInsn(if (store) Opcodes.ASTORE else Opcodes.ALOAD, index)
        // Indirection should have been unwrapped, others are plural. Should not happen
        is TypeDef.Indirection, is TypeDef.Tuple, is TypeDef.StructDef -> throw IllegalStateException("Unexpected TypeDef here, bug in compiler, please report")
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
        else if (type.isReferenceType) writer.visitInsn(Opcodes.ARETURN)
        else throw IllegalStateException("Unexpected type \"${type.name}\" made it to IR.Return - bug in compiler, please report")
    }
}

// Separate helper for a push instruction, since they have lots of logic
private fun outputPush(inst: Instruction.Push, writer: MethodVisitor) = when (inst.valueToPush) {
    // Separate case for each literal object type

    // Boolean, push 1 or 0
    is Boolean -> writer.visitInsn(if (inst.valueToPush) Opcodes.ICONST_1 else Opcodes.ICONST_0)
    // Big integer. Output proper thing based on the type
    is BigInteger -> {
        val intType = inst.type.builtin as IntType
        val v = inst.valueToPush
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
    is String -> writer.visitLdcInsn(inst.valueToPush)

    else -> throw IllegalStateException("Unrecognized literal class: ${inst.valueToPush.javaClass.name}")
}