package snuggle.toomanylimits.builtins.primitive

import snuggle.toomanylimits.builtins.BuiltinType
import snuggle.toomanylimits.builtins.helpers.constBinary
import snuggle.toomanylimits.builtins.helpers.constUnary
import org.objectweb.asm.Opcodes
import snuggle.toomanylimits.representation.asts.typed.MethodDef
import snuggle.toomanylimits.representation.asts.typed.TypeDef
import snuggle.toomanylimits.representation.passes.typing.TypingCache
import snuggle.toomanylimits.representation.passes.typing.getBasicBuiltin

// Bool type.
object BoolType: BuiltinType {

    override val baseName: String get() = "bool"
    override fun name(generics: List<TypeDef>, typeCache: TypingCache): String = baseName
    override val nameable: Boolean get() = true
    override fun runtimeName(generics: List<TypeDef>, typeCache: TypingCache): String? = null
    override fun descriptor(generics: List<TypeDef>, typeCache: TypingCache): List<String> = listOf("Z")
    override fun stackSlots(generics: List<TypeDef>, typeCache: TypingCache): Int = 1
    override fun isPlural(generics: List<TypeDef>, typeCache: TypingCache): Boolean = false
    override fun isReferenceType(generics: List<TypeDef>, typeCache: TypingCache): Boolean = false
    override fun extensible(generics: List<TypeDef>, typeCache: TypingCache): Boolean = false
    override fun hasStaticConstructor(generics: List<TypeDef>, typeCache: TypingCache): Boolean = true

    override fun getMethods(generics: List<TypeDef>, typeCache: TypingCache): List<MethodDef> {
        val boolType = getBasicBuiltin(BoolType, typeCache)
        return listOf(
            // Add/mul is equivalent to or/and. May remove
            constBinary(static = false, boolType, "add", boolType, boolType, Boolean::or)
                    orBytecode { it.visitInsn(Opcodes.IOR) },
            constBinary(static = false, boolType, "mul", boolType, boolType, Boolean::and)
                    orBytecode { it.visitInsn(Opcodes.IAND) },
            // Not is XOR with 1
            constUnary<Boolean, Boolean>(static = false, boolType, "not", boolType) {!it}
                    orBytecode { it.visitInsn(Opcodes.ICONST_1); it.visitInsn(Opcodes.IXOR) },
            constUnary<Boolean, Boolean>(static = false, boolType, "bool", boolType) {it}
                    orBytecode {} // No op, a bool is already a bool
        )
    }

}