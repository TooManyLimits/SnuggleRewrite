package builtins

import representation.asts.typed.TypeDef
import representation.passes.typing.TypeDefCache

open class FloatType(val bits: Int): BuiltinType {
    override val name: String = "f$bits"
    override val nameable: Boolean get() = true
    override val runtimeName: String? get() = null
    override val descriptor: List<String> = listOf(when (bits) {
        32 -> "F"
        64 -> "D"
        else -> throw IllegalStateException()
    })
    override fun stackSlots(generics: List<TypeDef>, typeCache: TypeDefCache): Int = if (bits == 64) 2 else 1
    override fun isPlural(generics: List<TypeDef>, typeCache: TypeDefCache): Boolean = false
    override fun isReferenceType(generics: List<TypeDef>, typeCache: TypeDefCache): Boolean = false

}

val FLOAT_TYPES = arrayOf(F32Type, F64Type)
object F32Type: FloatType(32)
object F64Type: FloatType(64)