package snuggle.toomanylimits.builtins.helpers

import org.objectweb.asm.MethodVisitor
import snuggle.toomanylimits.representation.asts.typed.MethodDef
import snuggle.toomanylimits.representation.asts.typed.TypeDef
import snuggle.toomanylimits.representation.asts.typed.TypedExpr

/**
 * Helpers for creating Const Binary methods
 */

class ConstWrapperTemp(private val static: Boolean, private val owningType: TypeDef, private val name: String,
                       private val returnType: TypeDef, private val argTypes: List<TypeDef>,
                       private val replacerGetter: ((TypedExpr.MethodCall) -> TypedExpr) -> (TypedExpr.MethodCall) -> TypedExpr) {
    infix fun orBytecode(bytecode: (MethodVisitor) -> Unit): MethodDef.ConstMethodDef {
        return MethodDef.ConstMethodDef(true, owningType, name, returnType, argTypes, replacerGetter {
            TypedExpr.MethodCall(
                it.loc, it.receiver, it.methodName, it.args,
                MethodDef.BytecodeMethodDef(true, static, owningType, "native\$$name", returnType, argTypes, bytecode),
                -10000, // Should never matter here!
                it.type)
        })
    }
    infix fun orExpr(exprGetter: (TypedExpr.MethodCall) -> TypedExpr): MethodDef.ConstMethodDef {
        return MethodDef.ConstMethodDef(true, owningType, name, returnType, argTypes, replacerGetter(exprGetter))
    }
    fun orThrow(): MethodDef.ConstMethodDef {
        return MethodDef.ConstMethodDef(true, owningType, name, returnType, argTypes, replacerGetter {
            throw IllegalStateException("Calling const without fallback, on non-literals? Bug in compiler, please report")
        })
    }
}

inline fun <reified A, reified B, reified C> constBinary(static: Boolean, owningType: TypeDef, name: String, returnType: TypeDef, argType: TypeDef, crossinline replacer: (A, B) -> C): ConstWrapperTemp {
    return ConstWrapperTemp(static, owningType, name, returnType, listOf(argType)) {
        { call ->
            val receiver = call.receiver
            val arg = call.args[0]
            if (receiver is TypedExpr.Literal && arg is TypedExpr.Literal) {
                if (receiver.value !is A || arg.value !is B)
                    throw IllegalStateException("Unexpected types to constBinary")
                val replaced = replacer(receiver.value, arg.value)
                TypedExpr.Literal(call.loc, replaced as Any, call.type)
            } else {
                it(call)
            }
        }
    }
}

fun <A, B, C> constBinaryWithConverter(static: Boolean, owningType: TypeDef, name: String, returnType: TypeDef, argType: TypeDef, receiverConverter: (Any) -> A, argConverter: (Any) -> B, replacer: (A, B) -> C): ConstWrapperTemp {
    return ConstWrapperTemp(static, owningType, name, returnType, listOf(argType)) {
        { call ->
            val receiver = call.receiver
            val arg = call.args[0]
            if (receiver is TypedExpr.Literal && arg is TypedExpr.Literal) {
                val replaced = replacer(receiverConverter(receiver.value), argConverter(arg.value))
                TypedExpr.Literal(call.loc, replaced as Any, call.type)
            } else {
                it(call)
            }
        }
    }
}

inline fun <reified A, reified B> constUnary(static: Boolean, owningType: TypeDef, name: String, returnType: TypeDef, crossinline replacer: (A) -> B): ConstWrapperTemp {
    return ConstWrapperTemp(static, owningType, name, returnType, listOf()) {
        { call ->
            val receiver = call.receiver
            if (receiver is TypedExpr.Literal) {
                if (receiver.value !is A)
                    throw IllegalStateException("Unexpected types to constUnary")
                val replaced = replacer(receiver.value)
                TypedExpr.Literal(call.loc, replaced as Any, call.type)
            } else {
                it(call)
            }
        }
    }
}

fun <A, B> constUnaryWithConverter(static: Boolean, owningType: TypeDef, name: String, returnType: TypeDef, receiverConverter: (Any) -> A, replacer: (A) -> B): ConstWrapperTemp {
    return ConstWrapperTemp(static, owningType, name, returnType, listOf()) {
        { call ->
            val receiver = call.receiver
            val arg = call.args[0]
            if (receiver is TypedExpr.Literal && arg is TypedExpr.Literal) {
                val replaced = replacer(receiverConverter(receiver.value))
                TypedExpr.Literal(call.loc, replaced as Any, call.type)
            } else {
                it(call)
            }
        }
    }
}