package builtins

import builtins.helpers.constBinary
import representation.asts.typed.MethodDef
import representation.asts.typed.TypeDef
import representation.asts.typed.TypedExpr
import representation.passes.typing.TypeDefCache
import representation.passes.typing.getBasicBuiltin
import java.math.BigInteger

object IntLiteralType: BuiltinType {
    override val baseName: String get() = "IntLiteral"
    override fun name(generics: List<TypeDef>, typeCache: TypeDefCache): String = baseName // Name used for error messages
    override val nameable: Boolean get() = false // Not nameable, can't refer to type "IntLiteral" in code
    override fun runtimeName(generics: List<TypeDef>, typeCache: TypeDefCache): String? = null
    override fun descriptor(generics: List<TypeDef>, typeCache: TypeDefCache): List<String> = listOf()

    // If we ever use this value anyway, something has already gone terribly wrong
    override fun stackSlots(generics: List<TypeDef>, typeCache: TypeDefCache): Int = -1000
    override fun isPlural(generics: List<TypeDef>, typeCache: TypeDefCache): Boolean = false
    override fun isReferenceType(generics: List<TypeDef>, typeCache: TypeDefCache): Boolean = false

    // Supertypes are the various int types and float types
    override fun getAllSupertypes(generics: List<TypeDef>, typeCache: TypeDefCache): List<TypeDef> =
        INT_TYPES.map { getBasicBuiltin(it, typeCache) } + FLOAT_TYPES.map { getBasicBuiltin(it, typeCache) }

    override fun getMethods(generics: List<TypeDef>, typeCache: TypeDefCache): List<MethodDef> {
        val intLiteralType = getBasicBuiltin(IntLiteralType, typeCache)
        return listOf(
            // IntLiteral, IntLiteral -> IntLiteral
            constBinary(false, intLiteralType, "add", intLiteralType, listOf(intLiteralType), BigInteger::add).orThrow(),
            constBinary(false, intLiteralType, "sub", intLiteralType, listOf(intLiteralType), BigInteger::subtract).orThrow(),
            constBinary(false, intLiteralType, "mul", intLiteralType, listOf(intLiteralType), BigInteger::multiply).orThrow(),
            constBinary(false, intLiteralType, "div", intLiteralType, listOf(intLiteralType), BigInteger::divide).orThrow(),
            constBinary(false, intLiteralType, "rem", intLiteralType, listOf(intLiteralType), BigInteger::remainder).orThrow(),

            *(INT_TYPES.map { getBasicBuiltin(it, typeCache) }.flatMap {listOf(
                // IntLiteral, IntLiteral -> Concrete Int
                constBinary(false, intLiteralType, "add", it, listOf(intLiteralType), BigInteger::add).orThrow(),
                constBinary(false, intLiteralType, "sub", it, listOf(intLiteralType), BigInteger::subtract).orThrow(),
                constBinary(false, intLiteralType, "mul", it, listOf(intLiteralType), BigInteger::multiply).orThrow(),
                constBinary(false, intLiteralType, "div", it, listOf(intLiteralType), BigInteger::divide).orThrow(),
                constBinary(false, intLiteralType, "rem", it, listOf(intLiteralType), BigInteger::remainder).orThrow(),

                // IntLiteral, Concrete Int -> Concrete Int
                constBinary(false, intLiteralType, "add", it, listOf(it), BigInteger::add)
                        orExpr { call -> literalConcreteToConcrete(it, "add", call) },
                constBinary(false, intLiteralType, "sub", it, listOf(it), BigInteger::subtract)
                        orExpr { call -> literalConcreteToConcrete(it, "sub", call) },
                constBinary(false, intLiteralType, "mul", it, listOf(it), BigInteger::multiply)
                        orExpr { call -> literalConcreteToConcrete(it, "mul", call) },
                constBinary(false, intLiteralType, "div", it, listOf(it), BigInteger::divide)
                        orExpr { call -> literalConcreteToConcrete(it, "div", call) },
                constBinary(false, intLiteralType, "rem", it, listOf(it), BigInteger::remainder)
                        orExpr { call -> literalConcreteToConcrete(it, "rem", call) }
            )}).toTypedArray(),
        )
    }
}

private fun literalConcreteToConcrete(intType: TypeDef, methodName: String, call: TypedExpr.MethodCall): TypedExpr {
    val receiverValue = (call.receiver as TypedExpr.Literal).value as BigInteger
    val newReceiver = TypedExpr.Literal(call.receiver.loc, receiverValue, intType)
    return TypedExpr.MethodCall(
        call.loc, newReceiver, methodName, call.args,
        intType.methods.find { method -> method.name == methodName }!!, intType
    )
}