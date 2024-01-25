package representation.passes.output

import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.util.CheckClassAdapter
import representation.asts.ir.Program
import representation.asts.typed.MethodDef
import representation.asts.typed.TypeDef
import util.ConsList

class CompiledProgram(
    val classes: Map<String, ByteArray>,
//    val runtimeClass: ByteArray,
//    val otherClasses: List<ByteArray>
)

/**
 * The final stage - deals with converting our lowered IR structure
 * into JVM bytecode.
 */
fun output(ir: Program, staticInstances: ConsList<Any>): CompiledProgram {
    // Create the runtime
    val runtime = outputRuntime(ir, staticInstances)
    // Create the importer
    val importer = outputImporter(ir)
    // Create the other types
    val otherTypes = ir.generatedTypes.map { outputType(it) } + importer + runtime
    // Return the compiled program
    return CompiledProgram(mapOf(*otherTypes.toTypedArray()))
}

public var DEBUG_CLASS_WRITERS = true
fun getClassWriter(): ClassVisitor {
    val writer = ClassWriter(ClassWriter.COMPUTE_MAXS + ClassWriter.COMPUTE_FRAMES)
    return if (DEBUG_CLASS_WRITERS) CheckClassAdapter(writer)
    else writer
}
fun ClassVisitor.toByteArray(): ByteArray {
    return if (this is ClassWriter) this.toByteArray()
    else this.delegate.toByteArray()
}

/**
 * Helpers to get the string names of various things when outputting.
 */
fun getRuntimeClassName() = "Runtime"
fun getStaticObjectName(index: Int) = "#STATIC_OBJECT#$index"
fun getImporterClassName() = "Importer"
fun getImporterFieldName(fileName: String) = "hasImported_$fileName"
fun getImporterMethodName(fileName: String) = "runFile_$fileName"

// Helper to get method descriptor
fun getMethodDescriptor(methodDef: MethodDef): String {
    // Join all the descriptors of the args
    var argString = methodDef.paramTypes.joinToString(separator = "") { it.descriptor.joinToString(separator = "") }
    // If it's a method on a struct, and it's non-static, add the struct's descriptor as the first arg
    if (methodDef.owningType.unwrap() is TypeDef.StructDef && !methodDef.static)
        argString = methodDef.owningType.unwrap().descriptor.joinToString(separator = "") + argString
    // Otherwise, if it's a snuggle method with a static override, add the override receiver type as the first arg
    else if (methodDef is MethodDef.SnuggleMethodDef && methodDef.staticOverrideReceiverType != null)
        argString = methodDef.staticOverrideReceiverType.descriptor.joinToString(separator = "") + argString
    // Get the return type; if it has at least 1 element use that, otherwise use void return
    val returnTypeDesc = methodDef.returnType.descriptor
    val returnTypeString = returnTypeDesc.getOrNull(0) ?: "V"
    // Return the descriptor, "(args)returnType"
    return "($argString)$returnTypeString"
}