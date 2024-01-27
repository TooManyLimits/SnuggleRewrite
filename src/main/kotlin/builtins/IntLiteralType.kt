package builtins

import builtins.helpers.constBinary
import builtins.helpers.constUnary
import representation.asts.typed.MethodDef
import representation.asts.typed.TypeDef
import representation.asts.typed.TypedExpr
import representation.passes.typing.TypingCache
import representation.passes.typing.getBasicBuiltin
import java.math.BigInteger

object IntLiteralType: BuiltinType {
    override val baseName: String get() = "IntLiteral"
    override fun name(generics: List<TypeDef>, typeCache: TypingCache): String = baseName // Name used for error messages
    override val nameable: Boolean get() = false // Not nameable, can't refer to type "IntLiteral" in code
    override fun runtimeName(generics: List<TypeDef>, typeCache: TypingCache): String? = null
    override fun descriptor(generics: List<TypeDef>, typeCache: TypingCache): List<String> = listOf()

    // If we ever use this value anyway, something has already gone terribly wrong
    override fun stackSlots(generics: List<TypeDef>, typeCache: TypingCache): Int = 999999999
    override fun isPlural(generics: List<TypeDef>, typeCache: TypingCache): Boolean = false
    override fun isReferenceType(generics: List<TypeDef>, typeCache: TypingCache): Boolean = false
    override fun hasStaticConstructor(generics: List<TypeDef>, typeCache: TypingCache): Boolean = true

    // Supertypes are the various int types and float types
    override fun getAllSupertypes(generics: List<TypeDef>, typeCache: TypingCache): List<TypeDef> =
        INT_TYPES.map { getBasicBuiltin(it, typeCache) } + FLOAT_TYPES.map { getBasicBuiltin(it, typeCache) }

    override fun getMethods(generics: List<TypeDef>, typeCache: TypingCache): List<MethodDef> {
        val intLiteralType = getBasicBuiltin(IntLiteralType, typeCache)
        val boolType = getBasicBuiltin(BoolType, typeCache)
        return listOf(
            // IntLiteral -> IntLiteral
            constUnary(false, intLiteralType, "neg", intLiteralType, BigInteger::negate).orThrow(),
            constUnary(false, intLiteralType, "bnot", intLiteralType, BigInteger::not).orThrow(),

            // IntLiteral, IntLiteral -> IntLiteral
            constBinary(false, intLiteralType, "add", intLiteralType, intLiteralType, BigInteger::add).orThrow(),
            constBinary(false, intLiteralType, "sub", intLiteralType, intLiteralType, BigInteger::subtract).orThrow(),
            constBinary(false, intLiteralType, "mul", intLiteralType, intLiteralType, BigInteger::multiply).orThrow(),
            constBinary(false, intLiteralType, "div", intLiteralType, intLiteralType, BigInteger::divide).orThrow(),
            constBinary(false, intLiteralType, "rem", intLiteralType, intLiteralType, BigInteger::remainder).orThrow(),

            // IntLiteral, IntLiteral -> Boolean (compares)
            constBinary<BigInteger, BigInteger, Boolean>(false, intLiteralType, "gt", boolType, intLiteralType) {a, b -> a > b}.orThrow(),
            constBinary<BigInteger, BigInteger, Boolean>(false, intLiteralType, "lt", boolType, intLiteralType) {a, b -> a < b}.orThrow(),
            constBinary<BigInteger, BigInteger, Boolean>(false, intLiteralType, "ge", boolType, intLiteralType) {a, b -> a >= b}.orThrow(),
            constBinary<BigInteger, BigInteger, Boolean>(false, intLiteralType, "le", boolType, intLiteralType) {a, b -> a <= b}.orThrow(),
            constBinary<BigInteger, BigInteger, Boolean>(false, intLiteralType, "eq", boolType, intLiteralType) {a, b -> a == b}.orThrow(),

            *(INT_TYPES.map { getBasicBuiltin(it, typeCache) }.flatMap {listOf(
                // IntLiteral -> Concrete Int
                constUnary(false, intLiteralType, "neg", it, BigInteger::negate).orThrow(),
                constUnary(false, intLiteralType, "bnot", it, BigInteger::not).orThrow(),

                // IntLiteral, IntLiteral -> Concrete Int
                constBinary(false, intLiteralType, "add", it, intLiteralType, BigInteger::add).orThrow(),
                constBinary(false, intLiteralType, "sub", it, intLiteralType, BigInteger::subtract).orThrow(),
                constBinary(false, intLiteralType, "mul", it, intLiteralType, BigInteger::multiply).orThrow(),
                constBinary(false, intLiteralType, "div", it, intLiteralType, BigInteger::divide).orThrow(),
                constBinary(false, intLiteralType, "rem", it, intLiteralType, BigInteger::remainder).orThrow(),

                // IntLiteral, Concrete Int -> Concrete Int
                constBinary(false, intLiteralType, "add", it, it, BigInteger::add)
                        orExpr { call -> convertToRuntimeFunction(it, "add", call) },
                constBinary(false, intLiteralType, "sub", it, it, BigInteger::subtract)
                        orExpr { call -> convertToRuntimeFunction(it, "sub", call) },
                constBinary(false, intLiteralType, "mul", it, it, BigInteger::multiply)
                        orExpr { call -> convertToRuntimeFunction(it, "mul", call) },
                constBinary(false, intLiteralType, "div", it, it, BigInteger::divide)
                        orExpr { call -> convertToRuntimeFunction(it, "div", call) },
                constBinary(false, intLiteralType, "rem", it, it, BigInteger::remainder)
                        orExpr { call -> convertToRuntimeFunction(it, "rem", call) },

                // IntLiteral, Concrete Int -> Boolean (compares)
                constBinary<BigInteger, BigInteger, Boolean>(false, intLiteralType, "gt", boolType, it) {a, b -> a > b}
                    orExpr { call -> convertToRuntimeFunction(it, "gt", call) },
                constBinary<BigInteger, BigInteger, Boolean>(false, intLiteralType, "lt", boolType, it) {a, b -> a < b}
                        orExpr { call -> convertToRuntimeFunction(it, "lt", call) },
                constBinary<BigInteger, BigInteger, Boolean>(false, intLiteralType, "ge", boolType, it) {a, b -> a >= b}
                        orExpr { call -> convertToRuntimeFunction(it, "ge", call) },
                constBinary<BigInteger, BigInteger, Boolean>(false, intLiteralType, "le", boolType, it) {a, b -> a <= b}
                        orExpr { call -> convertToRuntimeFunction(it, "le", call) },
                constBinary<BigInteger, BigInteger, Boolean>(false, intLiteralType, "eq", boolType, it) {a, b -> a == b}
                        orExpr { call -> convertToRuntimeFunction(it, "eq", call) }
            )}).toTypedArray(),
        )
    }
}

private fun convertToRuntimeFunction(intType: TypeDef, methodName: String, call: TypedExpr.MethodCall): TypedExpr {
    val receiverValue = (call.receiver as TypedExpr.Literal).value as BigInteger
    val newReceiver = TypedExpr.Literal(call.receiver.loc, receiverValue, intType)
    return TypedExpr.MethodCall(
        call.loc, newReceiver, methodName, call.args,
        intType.methods.find { method -> method.name == methodName }!!, -10000, intType
    )
}