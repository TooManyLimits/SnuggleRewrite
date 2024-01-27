package builtins.primitive

import builtins.BuiltinType
import builtins.helpers.Fraction
import builtins.helpers.constBinaryWithConverter
import builtins.helpers.constUnaryWithConverter
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import representation.asts.typed.MethodDef
import representation.asts.typed.TypeDef
import representation.passes.typing.TypingCache
import representation.passes.typing.getBasicBuiltin

open class FloatType(val bits: Int): BuiltinType {
    override val baseName: String = "f$bits"
    override fun name(generics: List<TypeDef>, typeCache: TypingCache): String = baseName
    override val nameable: Boolean get() = true
    override fun runtimeName(generics: List<TypeDef>, typeCache: TypingCache): String? = null
    override fun descriptor(generics: List<TypeDef>, typeCache: TypingCache): List<String> = listOf(when (bits) {
        32 -> "F"
        64 -> "D"
        else -> throw IllegalStateException()
    })
    override fun stackSlots(generics: List<TypeDef>, typeCache: TypingCache): Int = if (bits == 64) 2 else 1
    override fun isPlural(generics: List<TypeDef>, typeCache: TypingCache): Boolean = false
    override fun isReferenceType(generics: List<TypeDef>, typeCache: TypingCache): Boolean = false
    override fun hasStaticConstructor(generics: List<TypeDef>, typeCache: TypingCache): Boolean = true

    override fun getMethods(generics: List<TypeDef>, typeCache: TypingCache): List<MethodDef> {
        val myType = getBasicBuiltin(this, typeCache)
        val boolType = getBasicBuiltin(BoolType, typeCache)

        val floatConverter: (Any) -> Float = {a -> if (a is Fraction) a.toFloat() else a as Float}
        val doubleConverter: (Any) -> Double = {a -> if (a is Fraction) a.toDouble() else a as Double}

        fun binHelper(name: String, floatFunc: (Float, Float) -> Float, doubleFunc: (Double, Double) -> Double, floatBytecode: (MethodVisitor) -> Unit, doubleBytecode: (MethodVisitor) -> Unit): MethodDef {
            return when (this) {
                F32Type -> constBinaryWithConverter(false, myType, name, myType, myType, floatConverter, floatConverter, floatFunc) orBytecode floatBytecode
                F64Type -> constBinaryWithConverter(false, myType, name, myType, myType, doubleConverter, doubleConverter, doubleFunc) orBytecode doubleBytecode
                else -> throw IllegalStateException()
            }
        }

        fun unaryHelper(name: String, floatFunc: (Float) -> Float, doubleFunc: (Double) -> Double, floatBytecode: (MethodVisitor) -> Unit, doubleBytecode: (MethodVisitor) -> Unit): MethodDef {
            return when (this) {
                F32Type -> constUnaryWithConverter(false, myType, name, myType, floatConverter, floatFunc) orBytecode floatBytecode
                F64Type -> constUnaryWithConverter(false, myType, name, myType, doubleConverter, doubleFunc) orBytecode doubleBytecode
                else -> throw IllegalStateException()
            }
        }

        fun cmpHelper(name: String, floatFunc: (Float, Float) -> Boolean, doubleFunc: (Double, Double) -> Boolean, ifOp: Int): MethodDef {
            return when (this) {
                F32Type -> constBinaryWithConverter(false, myType, name, boolType, myType, floatConverter, floatConverter, floatFunc) orBytecode bytecodeCompare(ifOp)
                F64Type -> constBinaryWithConverter(false, myType, name, boolType, myType, doubleConverter, doubleConverter, doubleFunc) orBytecode bytecodeCompare(ifOp)
                else -> throw IllegalStateException()
            }
        }

        return listOf(
            binHelper("add", Float::plus, Double::plus, {it.visitInsn(Opcodes.FADD)}, {it.visitInsn(Opcodes.DADD)}),
            binHelper("sub", Float::minus, Double::minus, {it.visitInsn(Opcodes.FSUB)}, {it.visitInsn(Opcodes.DSUB)}),
            binHelper("mul", Float::times, Double::times, {it.visitInsn(Opcodes.FMUL)}, {it.visitInsn(Opcodes.DMUL)}),
            binHelper("div", Float::div, Double::div, {it.visitInsn(Opcodes.FDIV)}, {it.visitInsn(Opcodes.DDIV)}),
            binHelper("rem", Float::rem, Double::rem, {it.visitInsn(Opcodes.FREM)}, {it.visitInsn(Opcodes.DREM)}),

            unaryHelper("neg", Float::unaryMinus, Double::unaryMinus, {it.visitInsn(Opcodes.FNEG)}, {it.visitInsn(Opcodes.DNEG)}),

            cmpHelper("gt", {a,b -> a > b}, {a,b -> a > b}, Opcodes.IFLE),
            cmpHelper("lt", {a,b -> a < b}, {a,b -> a < b}, Opcodes.IFGE),
            cmpHelper("ge", {a,b -> a >= b}, {a,b -> a >= b}, Opcodes.IFLT),
            cmpHelper("le", {a,b -> a <= b}, {a,b -> a <= b}, Opcodes.IFGT),
            cmpHelper("eq", {a,b -> a == b}, {a,b -> a == b}, Opcodes.IFNE),
        )
    }

    //the 2 args are on the stack.
    //ifOp should be the "opposite" of what you actually want:
    //for the less than operator, we want GE.
    //for the less-equal operator, we want GT. Etc
    private fun bytecodeCompare(opcode: Int): (MethodVisitor) -> Unit {
        return { v ->
            val pushFalse = Label()
            val end = Label()
            val floatComparisonOp = when (opcode) {
                Opcodes.IFLT, Opcodes.IFLE, Opcodes.IFNE -> if (bits == 32) Opcodes.FCMPG else Opcodes.DCMPG
                Opcodes.IFGT, Opcodes.IFGE -> if (bits == 32) Opcodes.FCMPL else Opcodes.DCMPL
                else -> throw IllegalStateException("Unexpected compare op: bug in compiler, please report!")
            }

            //Perform the float comparison op. Because of the above switch statement,
            //in the case of NaN, something which will cause a jump to "pushFalse" is pushed.
            v.visitInsn(floatComparisonOp)
            v.visitJumpInsn(opcode, pushFalse)
            v.visitInsn(Opcodes.ICONST_1)
            v.visitJumpInsn(Opcodes.GOTO, end)
            v.visitLabel(pushFalse)
            v.visitInsn(Opcodes.ICONST_0)
            v.visitLabel(end)
        }
    }


}

val FLOAT_TYPES = arrayOf(F32Type, F64Type)
object F32Type: FloatType(32)
object F64Type: FloatType(64)