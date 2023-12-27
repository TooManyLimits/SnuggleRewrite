package builtins

import representation.asts.typed.MethodDef
import representation.asts.typed.TypeDef
import representation.passes.typing.TypeDefCache
import representation.passes.typing.getBasicBuiltin

// Bool type.
object BoolType: BuiltinType {

    override val name: String get() = "bool"
    override val nameable: Boolean get() = true
    override val runtimeName: String? get() = null
    override val stackSlots: Int get() = 1

    override fun getMethods(generics: List<TypeDef>, typeCache: TypeDefCache): List<MethodDef> {
        val boolType = getBasicBuiltin(BoolType, typeCache)
        return listOf(
            MethodDef.BytecodeMethodDef(true, false, "add", boolType, listOf(boolType)) {}
        )
    }

}