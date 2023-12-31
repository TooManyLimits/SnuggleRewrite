package builtins

import representation.passes.lexing.IntLiteralData
import java.math.BigInteger

open class IntType(val signed: Boolean, val bits: Int): BuiltinType {
    // -2^bits-1 or 0
    val minValue = if (signed) BigInteger.TWO.pow(bits - 1).negate() else BigInteger.ZERO
    // (2^bits-1)-1 or (2^bits)-1
    val maxValue = BigInteger.TWO.pow(if (signed) bits - 1 else bits).subtract(BigInteger.ONE)

    // If the given BigInteger fits in this int type
    fun fits(value: BigInteger) = value >= minValue && value <= maxValue

    override val name: String = (if (signed) "i" else "u") + bits
    override val nameable: Boolean get() = true
    override val runtimeName: String? get() = null
    override val descriptor: List<String> = listOf(when (bits) {
        8 -> "B"
        16 -> "S"
        32 -> "I"
        64 -> "J"
        else -> throw IllegalStateException()
    })
    override val stackSlots: Int = if (bits == 64) 2 else 1
}

val INT_TYPES = arrayOf(I8Type, I16Type, I32Type, I64Type, U8Type, U16Type, U32Type, U64Type)
object I8Type: IntType(true, 8)
object I16Type: IntType(true, 16)
object I32Type: IntType(true, 32)
object I64Type: IntType(true, 64)
object U8Type: IntType(false, 8)
object U16Type: IntType(false, 16)
object U32Type: IntType(false, 32)
object U64Type: IntType(false, 64)