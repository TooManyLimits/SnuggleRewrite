package builtins

import builtins.helpers.constBinary
import org.objectweb.asm.Opcodes
import representation.asts.typed.MethodDef
import representation.asts.typed.TypeDef
import representation.passes.typing.TypeDefCache
import representation.passes.typing.getBasicBuiltin

object StringType: BuiltinType {
    override val name: String get() = "String"
    override val nameable: Boolean get() = true
    override val runtimeName: String get() = "java/lang/String"
    override val descriptor: List<String> get() = listOf("L$runtimeName;")

    override fun stackSlots(generics: List<TypeDef>, typeCache: TypeDefCache): Int = 1
    override fun isPlural(generics: List<TypeDef>, typeCache: TypeDefCache): Boolean = false
    override fun isReferenceType(generics: List<TypeDef>, typeCache: TypeDefCache): Boolean = true
    override fun getPrimarySupertype(generics: List<TypeDef>, typeCache: TypeDefCache): TypeDef = getBasicBuiltin(ObjectType, typeCache)

    override fun getMethods(generics: List<TypeDef>, typeCache: TypeDefCache): List<MethodDef> {
        val stringType = getBasicBuiltin(StringType, typeCache)
        return listOf(
            constBinary(false, stringType, "add", stringType, listOf(stringType), String::plus)
                orBytecode {
                    it.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "concat", "(Ljava/lang/String;)Ljava/lang/String;", false)
                }
        )
    }
}