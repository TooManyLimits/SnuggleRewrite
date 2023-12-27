package representation.asts.ir

import org.objectweb.asm.MethodVisitor
import representation.asts.typed.FieldDef
import representation.asts.typed.MethodDef
import representation.asts.typed.TypeDef
import util.ConsList

data class Program(
    // The things used to generate Java classes.
    val generatedClasses: List<GeneratedType>,
    // The top-level code of each file.
    val topLevelCode: Map<String, Instruction.CodeBlock>
)


sealed interface GeneratedType {
    data class GeneratedClass(val name: String, val supertype: TypeDef,
                              val fields: List<GeneratedField>,
                              val methods: List<GeneratedMethod>): GeneratedType
}

@JvmInline value class GeneratedField(val fieldDef: FieldDef)

sealed interface GeneratedMethod {
    data class GeneratedSnuggleMethod(val methodDef: MethodDef, val body: Instruction.CodeBlock): GeneratedMethod
}

sealed interface Instruction {
    // A collection of other instructions
    data class CodeBlock(val instructions: ConsList<Instruction>): Instruction
    // Some raw bytecodes supplied by the enclosing program
    data class Bytecodes(val cost: Long, val bytecodes: (MethodVisitor) -> Unit): Instruction
    // Import the file of the given name
    data class RunImport(val fileName: String): Instruction
    // A virtual method call on the given method
    data class VirtualCall(val methodToCall: MethodDef): Instruction

    // Push the given value onto the stack
    data class Push(val valueToPush: Any): Instruction
    // Pop the given type from the top of the stack
    data class Pop(val typeToPop: TypeDef): Instruction

    // Store a value of the given type as a local variable at the given index
    data class StoreLocal(val index: Int, val type: TypeDef): Instruction
    // Load a value of the given type from a local variable at the given index
    data class LoadLocal(val index: Int, val type: TypeDef): Instruction
}