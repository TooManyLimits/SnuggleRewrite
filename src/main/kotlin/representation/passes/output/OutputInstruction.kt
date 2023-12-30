package representation.passes.output

import builtins.BoolType
import builtins.ObjectType
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import representation.asts.ir.Instruction
import representation.asts.typed.TypeDef

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
    // Make the call
    is Instruction.VirtualCall -> {
        val owner = inst.methodToCall.owningType.runtimeName!!
        val name = inst.methodToCall.name
        val descriptor = getMethodDescriptor(inst.methodToCall)
        writer.visitMethodInsn(Opcodes.INVOKEVIRTUAL, owner, name, descriptor, false)
    }
    is Instruction.StaticCall -> {
        val owner = inst.methodToCall.owningType.runtimeName!!
        val name = inst.methodToCall.name
        val descriptor = getMethodDescriptor(inst.methodToCall)
        writer.visitMethodInsn(Opcodes.INVOKESTATIC, owner, name, descriptor, false)
    }
    // Helper function for push, it has lots of logic
    is Instruction.Push -> outputPush(inst, writer)
    // Pop the correct number of stack slots
    is Instruction.Pop -> {
        // TODO: plural case, recurse
        when {
            inst.typeToPop.stackSlots == 2 -> writer.visitInsn(Opcodes.POP2)
            inst.typeToPop.stackSlots == 1 -> writer.visitInsn(Opcodes.POP)
            else -> throw IllegalStateException("Types should always be 1, 2, or plural slots")
        }
    }
    // Store/load local variables. Have a helper function for it, because of repetition
    is Instruction.StoreLocal -> handleLocal(inst.index, inst.type, store = true, writer)
    is Instruction.LoadLocal -> handleLocal(inst.index, inst.type, store = false, writer)
}

private fun handleLocal(index: Int, type: TypeDef, store: Boolean, writer: MethodVisitor) {
    // Unwrap the type def's indirections
    fun unwrap(typeDef: TypeDef): TypeDef =
        if (typeDef is TypeDef.Indirection) typeDef.promise.expect() else typeDef
    // Helper to pick the correct opcode
    fun choose(storeVersion: Int, loadVersion: Int) =
        if (store) storeVersion else loadVersion

    val type = unwrap(type) // Unwrap type
    // TODO: Plural case, recurse
    when (type) {
        is TypeDef.InstantiatedBuiltin -> when (type.builtin) {
            // Builtin types:
            is BoolType -> writer.visitVarInsn(choose(Opcodes.ISTORE, Opcodes.ILOAD), index)

            is ObjectType -> writer.visitVarInsn(choose(Opcodes.ASTORE, Opcodes.ALOAD), index)
            else -> throw RuntimeException("Unknown builtin type by LoadLocal \"${type.builtin.name}, bug in compiler, please report")
        }
        is TypeDef.ClassDef -> writer.visitVarInsn(choose(Opcodes.ASTORE, Opcodes.ALOAD), index)
        is TypeDef.Indirection -> throw IllegalStateException("Should not be indirect here, bug in compiler, please report")
    }
}

// Separate helper for a push instruction, since they have lots of logic
private fun outputPush(inst: Instruction.Push, writer: MethodVisitor) = when (inst.valueToPush) {
    // Separate case for each literal object type

    // Boolean, push 1 or 0
    is Boolean -> writer.visitInsn(if (inst.valueToPush) Opcodes.ICONST_1 else Opcodes.ICONST_0)

    else -> throw IllegalStateException("Unrecognized literal class: ${inst.valueToPush.javaClass.name}")
}