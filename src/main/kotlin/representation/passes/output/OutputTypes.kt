package representation.passes.output

import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import representation.asts.ir.GeneratedField
import representation.asts.ir.GeneratedMethod
import representation.asts.ir.GeneratedType

fun outputType(type: GeneratedType): ByteArray = when (type) {
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
}

private fun outputField(field: GeneratedField, classWriter: ClassWriter, prefix: String = "") {

}

private fun outputMethod(method: GeneratedMethod, classWriter: ClassWriter) {
    val writer = classWriter.visitMethod(
        Opcodes.ACC_PUBLIC + if (method.methodDef.static) Opcodes.ACC_STATIC else 0,
        method.methodDef.name,
        getMethodDescriptor(method.methodDef), null, null)
    writer.visitCode()
    outputInstruction(method.body, writer)
    writer.visitInsn(Opcodes.RETURN) // TODO: Other types
    writer.visitMaxs(0, 0)
    writer.visitEnd()
}