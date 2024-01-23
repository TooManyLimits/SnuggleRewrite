package representation.passes.output

import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import representation.asts.ir.Program

/**
 * Create the importer class for this program. This class
 * contains, for each file in the original program, a method
 * to run that file's contents, as well as a boolean field
 * for whether the file has been run yet.
 */
fun outputImporter(ir: Program): Pair<String, ByteArray> {
    // Create the class writer
    val writer = ClassWriter(ClassWriter.COMPUTE_MAXS + ClassWriter.COMPUTE_FRAMES)
    writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, getImporterClassName(), null, "java/lang/Object", null)
    // For each piece of top level code:
    ir.topLevelCode.forEach {
        val fileName = it.key
        val fileCode = it.value
        // Create the boolean field:
        writer.visitField(
            Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC,
            getImporterFieldName(fileName),
            "Z", null, null)
        // Create the method:
        val methodWriter = writer.visitMethod(
            Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC,
            getImporterMethodName(fileName),
            "()V", null, null)
        // Fill the method body:
        methodWriter.visitCode()
        outputInstruction(fileCode, methodWriter)
        methodWriter.visitInsn(Opcodes.RETURN)
        methodWriter.visitMaxs(0, 0)
        methodWriter.visitEnd()
    }
    // The class writer is done, return its byte array
    writer.visitEnd()
    return getImporterClassName() to writer.toByteArray()
}