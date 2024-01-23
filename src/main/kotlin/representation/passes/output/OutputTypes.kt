package representation.passes.output

import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import representation.asts.ir.GeneratedField
import representation.asts.ir.GeneratedMethod
import representation.asts.ir.GeneratedType
import representation.asts.typed.TypeDef

fun outputType(type: GeneratedType): Pair<String, ByteArray> = type.runtimeName to when (type) {
    is GeneratedType.GeneratedClass -> {
        // Create a class writer
        val writer = ClassWriter(ClassWriter.COMPUTE_MAXS + ClassWriter.COMPUTE_FRAMES)
        // Create the basic properties of the class
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, type.runtimeName, null, type.supertypeName, null)
        // Compile each field and method
        type.fields.forEach { outputField(it, writer) }
        type.methods.forEach { outputMethod(it, writer) }
        // Visit end, and return
        writer.visitEnd()
        writer.toByteArray()
    }
    is GeneratedType.GeneratedValueType -> {
        val writer = ClassWriter(ClassWriter.COMPUTE_MAXS + ClassWriter.COMPUTE_FRAMES)
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, type.runtimeName, null, "java/lang/Object", null)
        type.returningFields.forEach { outputField(it, writer) }
        type.fields.forEach { outputField(it, writer) }
        type.methods.forEach { outputMethod(it, writer) }
        writer.visitEnd()
        writer.toByteArray()
    }
    is GeneratedType.GeneratedFuncType -> {
        val writer = ClassWriter(ClassWriter.COMPUTE_MAXS + ClassWriter.COMPUTE_FRAMES)
        val access = Opcodes.ACC_PUBLIC + Opcodes.ACC_INTERFACE + Opcodes.ACC_ABSTRACT // Interface flags
        writer.visit(Opcodes.V17, access, type.runtimeName, null, "java/lang/Object", null)
        type.methods.forEach { outputMethod(it, writer) }
        writer.visitEnd()
        writer.toByteArray()
    }
    is GeneratedType.GeneratedFuncImpl -> {
        val writer = ClassWriter(ClassWriter.COMPUTE_MAXS + ClassWriter.COMPUTE_FRAMES)
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, type.runtimeName, null, "java/lang/Object", arrayOf(type.supertypeName))
        type.fields.forEach { outputField(it, writer) }
        type.methods.forEach { outputMethod(it, writer) }
        writer.visitEnd()
        writer.toByteArray()
    }
}

private fun outputField(field: GeneratedField, classWriter: ClassWriter) {
    val access = Opcodes.ACC_PUBLIC + (if (field.runtimeStatic) Opcodes.ACC_STATIC else 0)
    val desc = field.fieldDef.type.descriptor
    if (desc.size != 1) throw IllegalStateException("Attempt to output plural field? Bug in compiler, please report")
    classWriter.visitField(access, field.runtimeName, desc[0], null, null)
}

private fun outputMethod(method: GeneratedMethod, classWriter: ClassWriter) = when (method) {
    is GeneratedMethod.GeneratedSnuggleMethod -> {
        // If the method is static, or part of a StructDef, then tag it with static
        var access = Opcodes.ACC_PUBLIC
        if (method.methodDef.static || method.methodDef.owningType.unwrap() is TypeDef.StructDef)
            access += Opcodes.ACC_STATIC
        // Create the writer
        val writer = classWriter.visitMethod(
            access, method.methodDef.runtimeName, getMethodDescriptor(method.methodDef),
            null, null
        )
        writer.visitCode()
        outputInstruction(method.body, writer)
        writer.visitInsn(Opcodes.RETURN) // TODO: Other types
        writer.visitMaxs(0, 0)
        writer.visitEnd()
    }
    is GeneratedMethod.GeneratedCustomMethod -> {
        method.methodDef.outputter(classWriter)
    }
    is GeneratedMethod.GeneratedInterfaceMethod -> {
        val access = Opcodes.ACC_PUBLIC + Opcodes.ACC_ABSTRACT
        val writer = classWriter.visitMethod(
            access, method.methodDef.runtimeName, getMethodDescriptor(method.methodDef),
        null, null
        )
        writer.visitEnd()
    }
}