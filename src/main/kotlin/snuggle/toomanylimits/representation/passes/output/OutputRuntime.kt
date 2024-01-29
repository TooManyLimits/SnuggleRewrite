package snuggle.toomanylimits.representation.passes.output

import snuggle.toomanylimits.errors.SnuggleException
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import snuggle.toomanylimits.representation.asts.ir.Program
import snuggle.toomanylimits.runtime.SnuggleRuntime
import snuggle.toomanylimits.util.ConsList

/**
 * File that creates the runtime class. This is in its
 * own file since the SnuggleRuntime interface will
 * later have many methods to implement.
 */
fun outputRuntime(ir: Program, staticInstances: ConsList<Any>): Pair<String, ByteArray> {
    // Create the class writer
    val writer = getClassWriter()
    writer.visit(
        Opcodes.V17, Opcodes.ACC_PUBLIC, getRuntimeClassName(), null, "java/lang/Object",
        arrayOf(Type.getInternalName(SnuggleRuntime::class.java))) // SnuggleRuntime interface
    // Create its methods
    addConstructor(writer)
    addRunCode(writer)
    // Create its static instance fields
    addStaticInstanceFields(writer, staticInstances)
    // Return it
    return getRuntimeClassName() to writer.toByteArray()
}

// Create the runCode() implementation
private fun addRunCode(classWriter: ClassVisitor) {
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
private fun addConstructor(classWriter: ClassVisitor) {
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

private fun addStaticInstanceFields(writer: ClassVisitor, staticInstances: ConsList<Any>) {
    staticInstances.forEachIndexed { index, instance ->
        val access = Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC
        val name = getStaticObjectName(index)
        val descriptor = Type.getDescriptor(instance.javaClass)
        writer.visitField(access, name, descriptor, null, null)
    }
}