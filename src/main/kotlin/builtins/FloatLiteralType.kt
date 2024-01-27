package builtins

import builtins.helpers.Fraction
import builtins.helpers.constBinary
import builtins.helpers.constUnary
import representation.asts.typed.MethodDef
import representation.asts.typed.TypeDef
import representation.asts.typed.TypedExpr
import representation.passes.typing.TypingCache
import representation.passes.typing.getBasicBuiltin

object FloatLiteralType: BuiltinType {
    override val baseName: String get() = "FloatLiteral"
    override fun name(generics: List<TypeDef>, typeCache: TypingCache): String = baseName // Name used for error messages
    override val nameable: Boolean get() = false // Not nameable, can't refer to type "FloatLiteral" in code
    override fun runtimeName(generics: List<TypeDef>, typeCache: TypingCache): String? = null
    override fun descriptor(generics: List<TypeDef>, typeCache: TypingCache): List<String> = listOf()

    // If we ever use this value anyway, something has already gone terribly wrong
    override fun stackSlots(generics: List<TypeDef>, typeCache: TypingCache): Int = 999999999
    override fun isPlural(generics: List<TypeDef>, typeCache: TypingCache): Boolean = false
    override fun isReferenceType(generics: List<TypeDef>, typeCache: TypingCache): Boolean = false
    override fun hasStaticConstructor(generics: List<TypeDef>, typeCache: TypingCache): Boolean = true

    // Supertypes are the various int types and float types
    override fun getAllSupertypes(generics: List<TypeDef>, typeCache: TypingCache): List<TypeDef> =
        FLOAT_TYPES.map { getBasicBuiltin(it, typeCache) }

    override fun getMethods(generics: List<TypeDef>, typeCache: TypingCache): List<MethodDef> {
        val floatLiteralType = getBasicBuiltin(FloatLiteralType, typeCache)
        val f32Type = getBasicBuiltin(F32Type, typeCache)
        val f64Type = getBasicBuiltin(F64Type, typeCache)
        val boolType = getBasicBuiltin(BoolType, typeCache)
        return listOf(
            // FloatLiteral -> FloatLiteral
            constUnary(false, floatLiteralType, "neg", floatLiteralType, Fraction::unaryMinus).orThrow(),

            // FloatLiteral, FloatLiteral -> FloatLiteral
            constBinary(false, floatLiteralType, "add", floatLiteralType, floatLiteralType, Fraction::plus).orThrow(),
            constBinary(false, floatLiteralType, "sub", floatLiteralType, floatLiteralType, Fraction::minus).orThrow(),
            constBinary(false, floatLiteralType, "mul", floatLiteralType, floatLiteralType, Fraction::times).orThrow(),
            constBinary(false, floatLiteralType, "div", floatLiteralType, floatLiteralType, Fraction::div).orThrow(),
            constBinary(false, floatLiteralType, "rem", floatLiteralType, floatLiteralType, Fraction::rem).orThrow(),

            // FloatLiteral, FloatLiteral -> Boolean (compares)
            constBinary<Fraction, Fraction, Boolean>(false, floatLiteralType, "gt", boolType, floatLiteralType) { a, b -> a > b}.orThrow(),
            constBinary<Fraction, Fraction, Boolean>(false, floatLiteralType, "lt", boolType, floatLiteralType) { a, b -> a < b}.orThrow(),
            constBinary<Fraction, Fraction, Boolean>(false, floatLiteralType, "ge", boolType, floatLiteralType) { a, b -> a >= b}.orThrow(),
            constBinary<Fraction, Fraction, Boolean>(false, floatLiteralType, "le", boolType, floatLiteralType) { a, b -> a <= b}.orThrow(),
            constBinary<Fraction, Fraction, Boolean>(false, floatLiteralType, "eq", boolType, floatLiteralType) { a, b -> a == b}.orThrow(),

            *(FLOAT_TYPES.map { getBasicBuiltin(it, typeCache) }.flatMap { listOf(
                // FloatLiteral -> Concrete Float
                constUnary(false, floatLiteralType, "neg", it, Fraction::unaryMinus).orThrow(),

                // FloatLiteral, FloatLiteral -> Concrete Float
                constBinary(false, floatLiteralType, "add", it, floatLiteralType, Fraction::plus).orThrow(),
                constBinary(false, floatLiteralType, "sub", it, floatLiteralType, Fraction::minus).orThrow(),
                constBinary(false, floatLiteralType, "mul", it, floatLiteralType, Fraction::times).orThrow(),
                constBinary(false, floatLiteralType, "div", it, floatLiteralType, Fraction::div).orThrow(),
                constBinary(false, floatLiteralType, "rem", it, floatLiteralType, Fraction::rem).orThrow()
            )}).toTypedArray(),

            // FloatLiteral, Float -> Float
            constBinary<Fraction, Float, Float>(false, floatLiteralType, "add", f32Type, f32Type) {a, b -> a.toFloat() + b}
                    orExpr { call -> convertToRuntimeFunction(f32Type, "add", call) },
            constBinary<Fraction, Float, Float>(false, floatLiteralType, "sub", f32Type, f32Type) {a, b -> a.toFloat() - b}
                    orExpr { call -> convertToRuntimeFunction(f32Type, "sub", call) },
            constBinary<Fraction, Float, Float>(false, floatLiteralType, "mul", f32Type, f32Type) {a, b -> a.toFloat() * b}
                    orExpr { call -> convertToRuntimeFunction(f32Type, "mul", call) },
            constBinary<Fraction, Float, Float>(false, floatLiteralType, "div", f32Type, f32Type) {a, b -> a.toFloat() / b}
                    orExpr { call -> convertToRuntimeFunction(f32Type, "div", call) },
            constBinary<Fraction, Float, Float>(false, floatLiteralType, "rem", f32Type, f32Type) {a, b -> a.toFloat() % b}
                    orExpr { call -> convertToRuntimeFunction(f32Type, "rem", call) },

            // FloatLiteral, Float -> Boolean (compares)
            constBinary<Fraction, Float, Boolean>(false, floatLiteralType, "gt", boolType, f32Type) { a, b -> a.toFloat() > b}
                    orExpr { call -> convertToRuntimeFunction(f32Type, "gt", call) },
            constBinary<Fraction, Float, Boolean>(false, floatLiteralType, "lt", boolType, f32Type) { a, b -> a.toFloat() < b}
                    orExpr { call -> convertToRuntimeFunction(f32Type, "lt", call) },
            constBinary<Fraction, Float, Boolean>(false, floatLiteralType, "ge", boolType, f32Type) { a, b -> a.toFloat() >= b}
                    orExpr { call -> convertToRuntimeFunction(f32Type, "ge", call) },
            constBinary<Fraction, Float, Boolean>(false, floatLiteralType, "le", boolType, f32Type) { a, b -> a.toFloat() <= b}
                    orExpr { call -> convertToRuntimeFunction(f32Type, "le", call) },
            constBinary<Fraction, Float, Boolean>(false, floatLiteralType, "eq", boolType, f32Type) { a, b -> a.toFloat() == b}
                    orExpr { call -> convertToRuntimeFunction(f32Type, "eq", call) },

            // FloatLiteral, Double -> Double
            constBinary<Fraction, Double, Double>(false, floatLiteralType, "add", f64Type, f64Type) {a, b -> a.toDouble() + b}
                    orExpr { call -> convertToRuntimeFunction(f64Type, "add", call) },
            constBinary<Fraction, Double, Double>(false, floatLiteralType, "sub", f64Type, f64Type) {a, b -> a.toDouble() - b}
                    orExpr { call -> convertToRuntimeFunction(f64Type, "sub", call) },
            constBinary<Fraction, Double, Double>(false, floatLiteralType, "mul", f64Type, f64Type) {a, b -> a.toDouble() * b}
                    orExpr { call -> convertToRuntimeFunction(f64Type, "mul", call) },
            constBinary<Fraction, Double, Double>(false, floatLiteralType, "div", f64Type, f64Type) {a, b -> a.toDouble() / b}
                    orExpr { call -> convertToRuntimeFunction(f64Type, "div", call) },
            constBinary<Fraction, Double, Double>(false, floatLiteralType, "rem", f64Type, f64Type) {a, b -> a.toDouble() % b}
                    orExpr { call -> convertToRuntimeFunction(f64Type, "rem", call) },

            // FloatLiteral, Double -> Boolean (compares)
            constBinary<Fraction, Double, Boolean>(false, floatLiteralType, "gt", boolType, f64Type) { a, b -> a.toDouble() > b}
                    orExpr { call -> convertToRuntimeFunction(f64Type, "gt", call) },
            constBinary<Fraction, Double, Boolean>(false, floatLiteralType, "lt", boolType, f64Type) { a, b -> a.toDouble() < b}
                    orExpr { call -> convertToRuntimeFunction(f64Type, "lt", call) },
            constBinary<Fraction, Double, Boolean>(false, floatLiteralType, "ge", boolType, f64Type) { a, b -> a.toDouble() >= b}
                    orExpr { call -> convertToRuntimeFunction(f64Type, "ge", call) },
            constBinary<Fraction, Double, Boolean>(false, floatLiteralType, "le", boolType, f64Type) { a, b -> a.toDouble() <= b}
                    orExpr { call -> convertToRuntimeFunction(f64Type, "le", call) },
            constBinary<Fraction, Double, Boolean>(false, floatLiteralType, "eq", boolType, f64Type) { a, b -> a.toDouble() == b}
                    orExpr { call -> convertToRuntimeFunction(f64Type, "eq", call) },
        )
    }
}

private fun convertToRuntimeFunction(floatType: TypeDef, methodName: String, call: TypedExpr.MethodCall): TypedExpr {
    val receiverValue = (call.receiver as TypedExpr.Literal).value as Fraction
    val newReceiver = TypedExpr.Literal(call.receiver.loc, receiverValue, floatType)
    return TypedExpr.MethodCall(
        call.loc, newReceiver, methodName, call.args,
        floatType.methods.find { method -> method.name == methodName }!!, -10000, floatType
    )
}