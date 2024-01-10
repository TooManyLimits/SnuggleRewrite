package builtins

import representation.asts.typed.TypeDef
import representation.passes.typing.TypeDefCache

open class FloatType(val bits: Int): BuiltinType {
    override val baseName: String = "f$bits"
    override fun name(generics: List<TypeDef>, typeCache: TypeDefCache): String = baseName
    override val nameable: Boolean get() = true
    override fun runtimeName(generics: List<TypeDef>, typeCache: TypeDefCache): String? = null
    override fun descriptor(generics: List<TypeDef>, typeCache: TypeDefCache): List<String> = listOf(when (bits) {
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