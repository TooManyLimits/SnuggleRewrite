package builtins

import representation.passes.typing.MethodDef
import representation.passes.typing.TypeDef
import representation.passes.typing.TypeDefCache
import representation.passes.typing.getBasicBuiltin

// Bool type.
object BoolType: BuiltinType {

    override val name: String get() = "bool"
    override val nameable: Boolean get() = true
    override val runtimeName: String? get() = null

    override fun getMethods(generics: List<TypeDef>, typeCache: TypeDefCache): List<MethodDef> {
        val boolType = getBasicBuiltin(BoolType, typeCache)
        return listOf(
            MethodDef.BytecodeMethodDef(true, false, "add", boolType, listOf(boolType)) {}
        )
    }

}