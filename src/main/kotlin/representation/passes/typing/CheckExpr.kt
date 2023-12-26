package representation.passes.typing

import representation.asts.resolved.ResolvedExpr
import util.ConsMap
import util.extend
import kotlin.math.max


fun checkExpr(expr: ResolvedExpr, expectedType: TypeDef, scope: ConsMap<String, VariableBinding>, typeCache: TypeDefCache): TypingResult = when(expr) {

    // Very similar to inferring a block; we just need to
    // checkExpr() on the last expression, not infer it.
    is ResolvedExpr.Block -> {
        var scope = scope // Shadow scope
        // DIFFERENCE: Map _all but the last expression_ to their inferred forms.
        val typedExprs = expr.exprs.subList(0, max(0, expr.exprs.size - 1)).mapTo(ArrayList(expr.exprs.size+1)) {
            // Infer the inner expression
            val inferred = inferExpr(it, scope, typeCache)
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
            checkExpr(expr.exprs.last(), expectedType, scope, typeCache).expr
        else throw IllegalStateException("Unit not yet implemented")
        typedExprs.add(lastExpr)
        // Return as usual.
        just(TypedExpr.Block(expr.loc, typedExprs, lastExpr.type))
    }

    is ResolvedExpr.MethodCall -> {
        // Largely the same as the infer() version, just passes the "expectedType" parameter
        // Infer the type of the receiver
        val typedReceiver = inferExpr(expr.receiver, scope, typeCache).expr
        // Gather the set of non-static methods on the receiver
        val methods = typedReceiver.type.methods.filter { !it.static }
        // Choose the best method from among them
        val best = getBestMethod(methods, expr.loc, expr.methodName, expr.args, expectedType, scope, typeCache)
        // Return the typed method call
        just(TypedExpr.MethodCall(expr.loc, typedReceiver, expr.methodName, best.checkedArgs, best.method, best.method.returnType))
    }

    // Some expressions can just be inferred,
    // check their type matches, and proceed.
    // e.g. Import, Variable, Literal, Declaration.
    // Usually, inferring and checking work similarly.
    else -> {
        val res = inferExpr(expr, scope, typeCache)
        // TODO: Ensure that res.expr.type is a subtype of expectedType
        res
    }
}