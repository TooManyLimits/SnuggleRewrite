package builtins

import builtins.helpers.constBinary
import org.objectweb.asm.Opcodes
import representation.asts.typed.MethodDef
import representation.asts.typed.TypeDef
import representation.passes.typing.TypeDefCache
import representation.passes.typing.getBasicBuiltin

// Bool type.
object BoolType: BuiltinType {

    override val name: String get() = "bool"
    override val nameable: Boolean get() = true
    override val runtimeName: String? get() = null
    override val descriptor: List<String> = listOf("Z")
    override fun stackSlots(generics: List<TypeDef>, typeCache: TypeDefCache): Int = 1
    override fun isPlural(generics: List<TypeDef>, typeCache: TypeDefCache): Boolean = false
    override fun isReferenceType(generics: List<TypeDef>, typeCache: TypeDefCache): Boolean = false

    override fun getMethods(generics: List<TypeDef>, typeCache: TypeDefCache): List<MethodDef> {
        val boolType = getBasicBuiltin(BoolType, typeCache)
        return listOf(
            constBinary(static = false, boolType, "add", boolType, listOf(boolType), Boolean::or)
                    orBytecode {it.visitInsn(Opcodes.IOR)},
            constBinary(static = false, boolType, "mul", boolType, listOf(boolType), Boolean::and)
                    orBytecode {it.visitInsn(Opcodes.IAND)}
        )
    }

}