package representation.passes.output

import errors.SnuggleException
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import representation.asts.ir.Program
import runtime.SnuggleRuntime

/**
 * File that creates the runtime class. This is in its
 * own file since the SnuggleRuntime interface will
 * later have many methods to implement.
 */
fun outputRuntime(ir: Program): ByteArray {
    // Create the class writer
    val writer = ClassWriter(ClassWriter.COMPUTE_MAXS + ClassWriter.COMPUTE_FRAMES)
    writer.visit(
        Opcodes.V17, Opcodes.ACC_PUBLIC, getRuntimeClassName(), null, "java/lang/Object",
        arrayOf(Type.getInternalName(SnuggleRuntime::class.java))) // SnuggleRuntime interface
    // Create its methods
    addConstructor(writer)
    addRunCode(writer)
    // Return it
    return writer.toByteArray()
}

// Create the runCode() implementation
private fun addRunCode(classWriter: ClassWriter) {
    val runCode = classWriter.visitMethod(Opcodes.ACC_PUBLIC, "runCode","()V", null,
        arrayOf(Type.getInternalName(SnuggleException::class.java))) // Throws SnuggleException
    runCode.visitCode()
    // Just import main
    runCode.visitInsn(Opcodes.ICONST_1) // [true]
    runCode.visitFieldInsn(Opcodes.PUTSTATIC, getImporterClassName(), getImporterFieldName("main"), "Z") // []
    runCode.visitMethodInsn(Opcodes.INVOKESTATIC, getImporterClassName(), getImporterMethodName("main"), "()V", false)
    runCode.visitInsn(Opcodes.RETURN)
    runCode.visitMaxs(0, 0)
    runCode.visitEnd()
}

// Create the default empty constructor:
private fun addConstructor(classWriter: ClassWriter) {
    val constructor = classWriter.visitMethod(Opcodes.ACC_PUBLIC, "<init>","()V", null, null)
    constructor.visitCode()
    // Load this on the stack
    constructor.visitVarInsn(Opcodes.ALOAD, 0)
    // Call super()
    constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
    constructor.visitInsn(Opcodes.RETURN)
    constructor.visitMaxs(0, 0)
    constructor.visitEnd()
}