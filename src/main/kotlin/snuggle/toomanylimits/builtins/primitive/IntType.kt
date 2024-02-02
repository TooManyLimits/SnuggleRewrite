package snuggle.toomanylimits.builtins.primitive

import snuggle.toomanylimits.builtins.BuiltinType
import snuggle.toomanylimits.builtins.helpers.constBinary
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import snuggle.toomanylimits.representation.asts.typed.MethodDef
import snuggle.toomanylimits.representation.asts.typed.TypeDef
import snuggle.toomanylimits.representation.passes.typing.TypingCache
import snuggle.toomanylimits.representation.passes.typing.getBasicBuiltin
import java.math.BigInteger

open class IntType(val signed: Boolean, val bits: Int): BuiltinType {
    // -2^bits-1 or 0
    val minValue = if (signed) BigInteger.TWO.pow(bits - 1).negate() else BigInteger.ZERO
    // (2^bits-1)-1 or (2^bits)-1
    val maxValue = BigInteger.TWO.pow(if (signed) bits - 1 else bits).subtract(BigInteger.ONE)

    // If the given BigInteger fits in this int type
    fun fits(value: BigInteger) = value >= minValue && value <= maxValue

    override val baseName: String = (if (signed) "i" else "u") + bits
    override fun name(generics: List<TypeDef>, typeCache: TypingCache): String = baseName
    override val nameable: Boolean get() = true
    override fun runtimeName(generics: List<TypeDef>, typeCache: TypingCache): String? = null
    override fun descriptor(generics: List<TypeDef>, typeCache: TypingCache): List<String> = listOf(when (bits) {
        8 -> "B"
        16 -> "S"
        32 -> "I"
        64 -> "J"
        else -> throw IllegalStateException()
    })
    override fun stackSlots(generics: List<TypeDef>, typeCache: TypingCache): Int = if (bits == 64) 2 else 1
    override fun isPlural(generics: List<TypeDef>, typeCache: TypingCache): Boolean = false
    override fun isReferenceType(generics: List<TypeDef>, typeCache: TypingCache): Boolean = false
    override fun extensible(generics: List<TypeDef>, typeCache: TypingCache): Boolean = false
    override fun hasStaticConstructor(generics: List<TypeDef>, typeCache: TypingCache): Boolean = true

    override fun getMethods(generics: List<TypeDef>, typeCache: TypingCache): List<MethodDef> {
        val intType = getBasicBuiltin(this, typeCache)
        val boolType = getBasicBuiltin(BoolType, typeCache)
        return listOf(
            constBinary(false, intType, "add", intType, intType, BigInteger::add)
                    orBytecode doThenConvert { it.visitInsn(if (this.bits <= 32) Opcodes.IADD else Opcodes.LADD) },
            constBinary(false, intType, "sub", intType, intType, BigInteger::subtract)
                    orBytecode doThenConvert { it.visitInsn(if (this.bits <= 32) Opcodes.ISUB else Opcodes.LSUB) },
            constBinary(false, intType, "mul", intType, intType, BigInteger::multiply)
                    orBytecode doThenConvert { it.visitInsn(if (this.bits <= 32) Opcodes.IMUL else Opcodes.LMUL) },
            // Division and remainder need special handling for signed/unsigned.
            // Unsigned operations are generally intrinsified anyway
            constBinary(false, intType, "div", intType, intType, BigInteger::divide)
                    orBytecode doThenConvert {
                        if (bits <= 32) {
                            if (signed) it.visitInsn(Opcodes.IDIV)
                            else it.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Integer", "divideUnsigned", "(II)I", false)
                        } else {
                            if (signed) it.visitInsn(Opcodes.LDIV)
                            else it.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Long", "divideUnsigned", "(JJ)J", false)
                        }},
            constBinary(false, intType, "rem", intType, intType, BigInteger::remainder)
                    orBytecode doThenConvert {
                        if (bits <= 32) {
                            if (signed) it.visitInsn(Opcodes.IREM)
                            else it.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Integer", "remainderUnsigned", "(II)I", false)
                        } else {
                            if (signed) it.visitInsn(Opcodes.LREM)
                            else it.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Long", "remainderUnsigned", "(JJ)J", false)
                        }},
            // Comparisons
            constBinary<BigInteger, BigInteger, Boolean>(false, intType, "gt", boolType, intType) {a, b -> a > b}
                    orBytecode bytecodeCompareInt(Opcodes.IF_ICMPGT),
            constBinary<BigInteger, BigInteger, Boolean>(false, intType, "lt", boolType, intType) {a, b -> a < b}
                    orBytecode bytecodeCompareInt(Opcodes.IF_ICMPLT),
            constBinary<BigInteger, BigInteger, Boolean>(false, intType, "ge", boolType, intType) {a, b -> a >= b}
                    orBytecode bytecodeCompareInt(Opcodes.IF_ICMPGE),
            constBinary<BigInteger, BigInteger, Boolean>(false, intType, "le", boolType, intType) {a, b -> a <= b}
                    orBytecode bytecodeCompareInt(Opcodes.IF_ICMPLE),
            constBinary<BigInteger, BigInteger, Boolean>(false, intType, "eq", boolType, intType) {a, b -> a == b}
                    orBytecode bytecodeCompareInt(Opcodes.IF_ICMPEQ)
        )
    }

    protected inline fun doThenConvert(crossinline func: (MethodVisitor) -> Unit): (MethodVisitor) -> Unit = { writer ->
        func(writer)
        convert(writer)
    }

    // Shorten result, and convert to unsigned if needed
    protected fun convert(writer: MethodVisitor) {
        when (bits) {
            8 -> {
                writer.visitInsn(Opcodes.I2B)
                if (!signed) {
                    writer.visitIntInsn(Opcodes.SIPUSH, 0xff) // Cannot BIPUSH, would sign extend
                    writer.visitInsn(Opcodes.IAND)
                }
            }
            16 -> {
                writer.visitInsn(Opcodes.I2S)
                if (!signed) {
                    writer.visitLdcInsn(0xffff) // Cannot SIPUSH, would sign extend
                    writer.visitInsn(Opcodes.IAND)
                }
            }
        }
    }

    fun bytecodeCompareInt(intCompareOp: Int): (MethodVisitor) -> Unit = { writer ->
        val pushTrue = Label()
        val end = Label()
        if (!signed && intCompareOp != Opcodes.IF_ICMPEQ) {
            //Add min_value to both args
            for (i in 0..1) {
                if (bits <= 32) {
                    when (bits) {
                        8 -> writer.visitIntInsn(Opcodes.BIPUSH, Byte.MIN_VALUE.toInt())
                        16 -> writer.visitIntInsn(Opcodes.SIPUSH, Short.MIN_VALUE.toInt())
                        32 -> writer.visitLdcInsn(Int.MIN_VALUE)
                    }
                    writer.visitInsn(Opcodes.IADD)
                    writer.visitInsn(Opcodes.SWAP)
                } else {
                    writer.visitLdcInsn(Long.MIN_VALUE)
                    writer.visitInsn(Opcodes.LADD)
                    writer.visitInsn(Opcodes.DUP2_X2)
                    writer.visitInsn(Opcodes.POP2)
                }
            }
        }
        if (bits == 64) {
            writer.visitInsn(Opcodes.LCMP)
            writer.visitInsn(Opcodes.ICONST_0)
        }
        writer.visitJumpInsn(intCompareOp, pushTrue)
        writer.visitInsn(Opcodes.ICONST_0)
        writer.visitJumpInsn(Opcodes.GOTO, end)
        writer.visitLabel(pushTrue)
        writer.visitInsn(Opcodes.ICONST_1)
        writer.visitLabel(end)
    }

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