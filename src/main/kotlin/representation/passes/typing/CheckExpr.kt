package representation.passes.typing

import builtins.IntLiteralType
import builtins.IntType
import errors.NumberRangeException
import errors.TypeCheckingException
import representation.asts.resolved.ResolvedExpr
import representation.asts.typed.MethodDef
import representation.asts.typed.TypeDef
import representation.asts.typed.TypedExpr
import representation.passes.name_resolving.ExprResolutionResult
import util.ConsMap
import util.extend
import java.math.BigInteger
import kotlin.math.max


fun checkExpr(expr: ResolvedExpr, expectedType: TypeDef, scope: ConsMap<String, VariableBinding>, typeCache: TypeDefCache, currentTypeGenerics: List<TypeDef>): TypingResult = when(expr) {

    // Very similar to inferring a block; we just need to
    // checkExpr() on the last expression, not infer it.
    is ResolvedExpr.Block -> {
        var scope = scope // Shadow scope
        // DIFFERENCE: Map _all but the last expression_ to their inferred forms.
        val typedExprs = expr.exprs.subList(0, max(0, expr.exprs.size - 1)).mapTo(ArrayList(expr.exprs.size+1)) {
            // Infer the inner expression
            val inferred = inferExpr(it, scope, typeCache, currentTypeGenerics)
            // Check its new vars. If neither side is empty, add the vars in.
            if (inferred.newVarsIfTrue.isNotEmpty() && inferred.newVarsIfFalse.isNotEmpty()) {
                // Extend the scope with the new vars
                assert(inferred.newVarsIfTrue == inferred.newVarsIfFalse) { "Failed assertion - different vars ifTrue vs ifFalse, but neither is empty? Bug in compiler, please report!" }
                scope = scope.extend(inferred.newVarsIfTrue)
            } else {
                // TODO: Warn; this is a useless binding
            }
            // Output the inferred expr from the map
            inferred.expr
        }
        // DIFFERENCE: On the last expr, check it instead.
        val lastExpr = if (expr.exprs.isNotEmpty())
            checkExpr(expr.exprs.last(), expectedType, scope, typeCache, currentTypeGenerics).expr
        else throw IllegalStateException("Unit not yet implemented")
        typedExprs.add(lastExpr)
        // Return as usual.
        just(TypedExpr.Block(expr.loc, typedExprs, lastExpr.type))
    }

    is ResolvedExpr.MethodCall -> {
        // Largely the same as the infer() version, just passes the "expectedType" parameter
        // Infer the type of the receiver
        val typedReceiver = inferExpr(expr.receiver, scope, typeCache, currentTypeGenerics).expr
        // Gather the set of non-static methods on the receiver
        val methods = typedReceiver.type.methods.filter { !it.static }
        // Choose the best method from among them
        val best = getBestMethod(methods, expr.loc, expr.methodName, expr.args, expectedType, scope, typeCache, currentTypeGenerics)
        val call = TypedExpr.MethodCall(expr.loc, typedReceiver, expr.methodName, best.checkedArgs, best.method, best.method.returnType)

        // Repeatedly replace const method calls until done
        var resultExpr: TypedExpr = call
        while (resultExpr is TypedExpr.MethodCall && resultExpr.methodDef is MethodDef.ConstMethodDef) {
            // If the best method is a const method, then we essentially
            // apply it like a macro, replacing the expression with a new one.
            resultExpr = (resultExpr.methodDef as MethodDef.ConstMethodDef).replacer(resultExpr)
        }
        pullUpLiteral(just(resultExpr), expectedType)
    }

    is ResolvedExpr.StaticMethodCall -> {
        // Largely same as MethodCall above ^
        val receiverType = getTypeDef(expr.receiverType, typeCache, currentTypeGenerics)
        val methods = receiverType.methods.filter { it.static }
        val best = getBestMethod(methods, expr.loc, expr.methodName, expr.args, expectedType, scope, typeCache, currentTypeGenerics)
        pullUpLiteral(just(TypedExpr.StaticMethodCall(expr.loc, receiverType, expr.methodName, best.checkedArgs, best.method, best.method.returnType)), expectedType)
    }

    // Some expressions can just be inferred,
    // check their type matches, and proceed.
    // e.g. Import, Literal, Variable, Declaration.
    // Usually, inferring and checking work similarly.
    else -> {
        val res = inferExpr(expr, scope, typeCache, currentTypeGenerics)
        if (!res.expr.type.isSubtype(expectedType))
            throw TypeCheckingException(expectedType, res.expr.type, when(expr) {
                is ResolvedExpr.Import -> "Import expression"
                is ResolvedExpr.Literal -> "Literal"
                is ResolvedExpr.Variable -> "Variable \"${expr.name}\""
                is ResolvedExpr.Declaration -> "Let expression"
                else -> throw IllegalStateException("Failed to create error message - unexpected expression type ${res.expr.javaClass.simpleName}")
            }, expr.loc)
        pullUpLiteral(res, expectedType)
    }
}

/**
 * Pull a literal upwards into having a different type
 */
fun pullUpLiteral(result: TypingResult, expectedType: TypeDef): TypingResult {
    return if (result.expr is TypedExpr.Literal && result.expr.type.builtin == IntLiteralType && expectedType.builtin != IntLiteralType) {
        // If not in range, throw
        if (expectedType.builtin is IntType && !(expectedType.builtin as IntType).fits((result.expr as TypedExpr.Literal).value as BigInteger))
            throw NumberRangeException(expectedType.builtin as IntType, (result.expr as TypedExpr.Literal).value as BigInteger, result.expr.loc)
        // Otherwise, return with altered type
        if (result is TypingResult.JustExpr)
            just(TypedExpr.Literal(result.expr.loc, (result.expr as TypedExpr.Literal).value, expectedType))
        else
            TypingResult.WithVars(
                TypedExpr.Literal(result.expr.loc, (result.expr as TypedExpr.Literal).value, expectedType),
                result.newVarsIfTrue,
                result.newVarsIfFalse
            )
    }
    else result
}