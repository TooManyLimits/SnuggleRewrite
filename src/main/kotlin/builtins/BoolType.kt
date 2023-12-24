package builtins

import ast.typing.MethodDef
import ast.typing.TypeDef
import ast.typing.TypeDefCache
import ast.typing.getBasicBuiltin

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