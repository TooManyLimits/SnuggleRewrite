package representation.passes.output

import representation.asts.ir.Program
import representation.asts.typed.MethodDef
import representation.asts.typed.TypeDef

class CompiledProgram(
    val runtimeClass: ByteArray,
    val otherClasses: List<ByteArray>
)

/**
 * The final stage - deals with converting our lowered IR structure
 * into JVM bytecode.
 */
fun output(ir: Program): CompiledProgram {
    // Create the runtime
    val runtimeClass = outputRuntime(ir)
    // Create the importer
    val importer = outputImporter(ir)
    // Create the other types
//    println(ir.generatedTypes.map { it.runtimeName })
    val otherTypes = ir.generatedTypes.map { outputType(it) } + importer
    // Return the compiled program
    return CompiledProgram(runtimeClass, otherTypes)
}

/**
 * Helpers to get the string names of various things when outputting.
 */
fun getRuntimeClassName() = "Runtime"
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
    // Get the return type; if it has 1 element use that, otherwise use void return
    val returnTypeDesc = methodDef.returnType.descriptor
    val returnTypeString = if (returnTypeDesc.isNotEmpty()) returnTypeDesc[0] else "V"
    // Return the descriptor, "(args)returnType"
    return "($argString)$returnTypeString"
}