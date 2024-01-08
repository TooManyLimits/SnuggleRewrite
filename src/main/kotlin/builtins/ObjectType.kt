package builtins

import org.objectweb.asm.Opcodes
import representation.asts.typed.MethodDef
import representation.asts.typed.TypeDef
import representation.passes.typing.TypeDefCache
import representation.passes.typing.getBasicBuiltin
import representation.passes.typing.getUnit

// Object type.
// java/lang/Object underneath.
object ObjectType: BuiltinType {

    override val name: String get() = "Object"
    override val nameable: Boolean get() = true
    override val runtimeName: String get() = "java/lang/Object"
    override val descriptor: List<String> = listOf("L$runtimeName;")
    override fun stackSlots(generics: List<TypeDef>, typeCache: TypeDefCache): Int = 1
    override fun isPlural(generics: List<TypeDef>, typeCache: TypeDefCache): Boolean = false

    override fun getMethods(generics: List<TypeDef>, typeCache: TypeDefCache): List<MethodDef> {
        val objType = getBasicBuiltin(ObjectType, typeCache)
        val unitType = getUnit(typeCache)
        return listOf(
            MethodDef.BytecodeMethodDef(true, false, objType, "new", unitType, listOf()) {
                it.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
            }
        )
    }

}