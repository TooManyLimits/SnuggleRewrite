package representation.asts.ir

import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import representation.asts.typed.FieldDef
import representation.asts.typed.MethodDef
import representation.asts.typed.TypeDef
import util.ConsList

data class Program(
    // Types that have been used and generated by the program.
    val generatedTypes: List<GeneratedType>,
    // The top-level code of each file.
    val topLevelCode: Map<String, Instruction.CodeBlock>
)


sealed interface GeneratedType {
    val runtimeName: String
    data class GeneratedClass(override val runtimeName: String, val supertypeName: String,
                              val fields: List<GeneratedField>, val methods: List<GeneratedMethod>): GeneratedType
    data class GeneratedValueType(override val runtimeName: String,
                                  val returningFields: List<GeneratedField>,
                                  val fields: List<GeneratedField>, val methods: List<GeneratedMethod>): GeneratedType
}

data class GeneratedField(val fieldDef: FieldDef, val runtimeStatic: Boolean, val runtimeName: String)
data class GeneratedMethod(val methodDef: MethodDef.SnuggleMethodDef, val body: Instruction.CodeBlock)

sealed interface Instruction {
    // A collection of other instructions
    data class CodeBlock(val instructions: ConsList<Instruction>): Instruction
    // Some raw bytecodes supplied by the enclosing program
    data class Bytecodes(val cost: Long, val bytecodes: (MethodVisitor) -> Unit): Instruction
    // Import the file of the given name
    data class RunImport(val fileName: String): Instruction

    // Call the given method
    sealed interface MethodCall: Instruction {
        val methodToCall: MethodDef
        val invokeBytecode: Int
        data class Virtual(override val methodToCall: MethodDef): MethodCall {
            override val invokeBytecode: Int get() = Opcodes.INVOKEVIRTUAL
        }
        data class Static(override val methodToCall: MethodDef): MethodCall {
            override val invokeBytecode: Int get() = Opcodes.INVOKESTATIC
        }
        data class Special(override val methodToCall: MethodDef): MethodCall {
            override val invokeBytecode: Int get() = Opcodes.INVOKESPECIAL
        }
    }

    // If null, return void
    data class Return(val basicTypeToReturn: TypeDef?): Instruction

    // Push the given value onto the stack
    data class Push(val valueToPush: Any, val type: TypeDef): Instruction
    // Pop the given type from the top of the stack
    data class Pop(val typeToPop: TypeDef): Instruction
    // Swap the two given basic values
    data class SwapBasic(val top: TypeDef, val second: TypeDef): Instruction
    // Create a new, uninitialized instance of the given type on the stack
    data class NewRefAndDup(val typeToCreate: TypeDef): Instruction
    // Dup a reference type on top of the stack
    object DupRef: Instruction
    // Load a reference type. Used by SuperMethodCall to load the receiver on the stack,
    // and the receiver should always be a reference type.
    data class LoadRefType(val index: Int): Instruction

    // Store a value of the given type as a local variable at the given index
    data class StoreLocal(val index: Int, val type: TypeDef): Instruction
    // Load a value of the given type from a local variable at the given index
    data class LoadLocal(val index: Int, val type: TypeDef): Instruction
    // Get/put a field from a reference type. Reference type is on the stack
    data class GetReferenceTypeField(val owningType: TypeDef, val fieldType: TypeDef, val runtimeFieldName: String): Instruction
    data class PutReferenceTypeField(val owningType: TypeDef, val fieldType: TypeDef, val runtimeFieldName: String): Instruction
    // Get/Put a static field.
    data class GetStaticField(val owningType: TypeDef, val fieldType: TypeDef, val runtimeFieldName: String): Instruction
    data class PutStaticField(val owningType: TypeDef, val fieldType: TypeDef, val runtimeFieldName: String): Instruction

}