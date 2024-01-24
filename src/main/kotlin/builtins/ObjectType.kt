package builtins

import org.objectweb.asm.Opcodes
import representation.asts.typed.MethodDef
import representation.asts.typed.TypeDef
import representation.passes.typing.TypingCache
import representation.passes.typing.getBasicBuiltin
import representation.passes.typing.getUnit

// Object type.
// java/lang/Object underneath.
object ObjectType: BuiltinType {

    override val baseName: String get() = "Object"
    override fun name(generics: List<TypeDef>, typeCache: TypingCache): String = baseName
    override val nameable: Boolean get() = true
    override fun runtimeName(generics: List<TypeDef>, typeCache: TypingCache): String = "java/lang/Object"
    override fun descriptor(generics: List<TypeDef>, typeCache: TypingCache): List<String> = listOf("Ljava/lang/Object;")
    override fun stackSlots(generics: List<TypeDef>, typeCache: TypingCache): Int = 1
    override fun isPlural(generics: List<TypeDef>, typeCache: TypingCache): Boolean = false
    override fun isReferenceType(generics: List<TypeDef>, typeCache: TypingCache): Boolean = true
    override fun hasStaticConstructor(generics: List<TypeDef>, typeCache: TypingCache): Boolean = false

    override fun getMethods(generics: List<TypeDef>, typeCache: TypingCache): List<MethodDef> {
        val objType = getBasicBuiltin(ObjectType, typeCache)
        val unitType = getUnit(typeCache)
        return listOf(
            MethodDef.BytecodeMethodDef(true, false, objType, "new", unitType, listOf()) {
                it.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
            }
        )
    }

}