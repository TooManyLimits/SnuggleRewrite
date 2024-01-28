package builtins

import builtins.helpers.constBinary
import builtins.helpers.constUnary
import builtins.primitive.CharType
import builtins.primitive.U32Type
import org.objectweb.asm.Opcodes
import representation.asts.typed.MethodDef
import representation.asts.typed.TypeDef
import representation.passes.typing.TypingCache
import representation.passes.typing.getBasicBuiltin

object StringType: BuiltinType {
    override val baseName: String get() = "String"
    override fun name(generics: List<TypeDef>, typeCache: TypingCache): String = baseName
    override val nameable: Boolean get() = true
    override fun runtimeName(generics: List<TypeDef>, typeCache: TypingCache): String = "java/lang/String"
    override fun descriptor(generics: List<TypeDef>, typeCache: TypingCache): List<String> = listOf("Ljava/lang/String;")

    override fun stackSlots(generics: List<TypeDef>, typeCache: TypingCache): Int = 1
    override fun isPlural(generics: List<TypeDef>, typeCache: TypingCache): Boolean = false
    override fun isReferenceType(generics: List<TypeDef>, typeCache: TypingCache): Boolean = true
    override fun hasStaticConstructor(generics: List<TypeDef>, typeCache: TypingCache): Boolean = true
    override fun getPrimarySupertype(generics: List<TypeDef>, typeCache: TypingCache): TypeDef = getBasicBuiltin(ObjectType, typeCache)

    override fun getMethods(generics: List<TypeDef>, typeCache: TypingCache): List<MethodDef> {
        val stringType = getBasicBuiltin(StringType, typeCache)
        val u32Type = getBasicBuiltin(U32Type, typeCache)
        val charType = getBasicBuiltin(CharType, typeCache)
        return listOf(
            constBinary(false, stringType, "add", stringType, stringType, String::plus)
                orBytecode { it.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "concat", "(Ljava/lang/String;)Ljava/lang/String;", false) },
            constUnary(false, stringType, "size", u32Type, String::length)
                orBytecode { it.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "length", "()I", false) },
            constBinary(false, stringType, "get", charType, u32Type, String::get)
                orBytecode { it.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "charAt", "(I)C", false) }
        )
    }
}