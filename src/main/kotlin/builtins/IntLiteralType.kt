package builtins

import representation.asts.typed.TypeDef
import representation.passes.typing.TypeDefCache
import representation.passes.typing.getBasicBuiltin

object IntLiteralType: BuiltinType {
    override val name: String get() = "IntLiteral" // Name used for error messages
    override val nameable: Boolean get() = false // Not nameable, can't refer to type "IntLiteral" in code
    override val runtimeName: String? get() = null
    override val descriptor: List<String> get() = listOf()
    override val stackSlots: Int get() = -1000

    // Supertypes are the various int types
    override fun getAllSupertypes(generics: List<TypeDef>, typeCache: TypeDefCache): List<TypeDef> =
        INT_TYPES.map { getBasicBuiltin(it, typeCache) }
}

