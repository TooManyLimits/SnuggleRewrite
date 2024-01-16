package builtins

import builtins.helpers.constBinary
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import representation.asts.typed.MethodDef
import representation.asts.typed.TypeDef
import representation.passes.lexing.IntLiteralData
import representation.passes.typing.TypeDefCache
import representation.passes.typing.getBasicBuiltin
import java.math.BigInteger

open class IntType(val signed: Boolean, val bits: Int): BuiltinType {
    // -2^bits-1 or 0
    val minValue = if (signed) BigInteger.TWO.pow(bits - 1).negate() else BigInteger.ZERO
    // (2^bits-1)-1 or (2^bits)-1
    val maxValue = BigInteger.TWO.pow(if (signed) bits - 1 else bits).subtract(BigInteger.ONE)

    // If the given BigInteger fits in this int type
    fun fits(value: BigInteger) = value >= minValue && value <= maxValue

    override val baseName: String = (if (signed) "i" else "u") + bits
    override fun name(generics: List<TypeDef>, typeCache: TypeDefCache): String = baseName
    override val nameable: Boolean get() = true
    override fun runtimeName(generics: List<TypeDef>, typeCache: TypeDefCache): String? = null
    override fun descriptor(generics: List<TypeDef>, typeCache: TypeDefCache): List<String> = listOf(when (bits) {
        8 -> "B"
        16 -> "S"
        32 -> "I"
        64 -> "J"
        else -> throw IllegalStateException()
    })
    override fun stackSlots(generics: List<TypeDef>, typeCache: TypeDefCache): Int = if (bits == 64) 2 else 1
    override fun isPlural(generics: List<TypeDef>, typeCache: TypeDefCache): Boolean = false
    override fun isReferenceType(generics: List<TypeDef>, typeCache: TypeDefCache): Boolean = false
    override fun hasStaticConstructor(generics: List<TypeDef>, typeCache: TypeDefCache): Boolean = true

    override fun getMethods(generics: List<TypeDef>, typeCache: TypeDefCache): List<MethodDef> {
        val intType = getBasicBuiltin(this, typeCache)
        return listOf(
            constBinary(false, intType, "add", intType, listOf(intType), BigInteger::add)
                    orBytecode doThenConvert { it.visitInsn(if (this.bits <= 32) Opcodes.IADD else Opcodes.LADD) },
            constBinary(false, intType, "sub", intType, listOf(intType), BigInteger::add)
                    orBytecode doThenConvert { it.visitInsn(if (this.bits <= 32) Opcodes.ISUB else Opcodes.LSUB) },
            constBinary(false, intType, "mul", intType, listOf(intType), BigInteger::add)
                    orBytecode doThenConvert { it.visitInsn(if (this.bits <= 32) Opcodes.IMUL else Opcodes.LMUL) },
            // Division and remainder need special handling for signed/unsigned.
            // Unsigned operations are generally intrinsified anyway
            constBinary(false, intType, "div", intType, listOf(intType), BigInteger::add)
                    orBytecode doThenConvert {
                        if (bits <= 32) {
                            if (signed) it.visitInsn(Opcodes.IDIV)
                            else it.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Integer", "divideUnsigned", "(II)I", false)
                        } else {
                            if (signed) it.visitInsn(Opcodes.LDIV)
                            else it.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Long", "divideUnsigned", "(JJ)J", false)
                        }},
            constBinary(false, intType, "rem", intType, listOf(intType), BigInteger::add)
                    orBytecode doThenConvert {
                        if (bits <= 32) {
                            if (signed) it.visitInsn(Opcodes.IREM)
                            else it.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Integer", "remainderUnsigned", "(II)I", false)
                        } else {
                            if (signed) it.visitInsn(Opcodes.LREM)
                            else it.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Long", "remainderUnsigned", "(JJ)J", false)
                        }},
        )
    }

    private inline fun doThenConvert(crossinline func: (MethodVisitor) -> Unit): (MethodVisitor) -> Unit = { writer ->
        func(writer)
        convert(writer)
    }

    // Shorten result, and convert to unsigned if needed
    private fun convert(writer: MethodVisitor) {
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