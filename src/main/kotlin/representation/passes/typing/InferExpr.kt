package representation.passes.typing

import representation.asts.resolved.ResolvedExpr
import builtins.BoolType
import errors.NoSuchVariableException
import representation.asts.typed.TypedExpr
import representation.asts.typed.TypedPattern
import util.ConsList
import util.ConsMap
import util.extend
import util.lookup

/**
 * The output of type-checking an expression. Always results in a TypedExpr,
 * may sometimes result in some new variables.
 */
sealed interface TypingResult {
    val expr: TypedExpr
    val newVarsIfTrue: ConsMap<String, VariableBinding>
    val newVarsIfFalse: ConsMap<String, VariableBinding>

    data class WithVars(override val expr: TypedExpr, override val newVarsIfTrue: ConsMap<String, VariableBinding>, override val newVarsIfFalse: ConsMap<String, VariableBinding>): TypingResult
    @JvmInline value class JustExpr(override val expr: TypedExpr): TypingResult {
        override val newVarsIfTrue: ConsMap<String, VariableBinding> get() = ConsMap(ConsList.nil())
        override val newVarsIfFalse: ConsMap<String, VariableBinding> get() = ConsMap(ConsList.nil())
    }
}

fun just(expr: TypedExpr): TypingResult.JustExpr = TypingResult.JustExpr(expr)

/**
 * Infer an expression's type, and output a TypingResult.
 * @param expr The expression we want to infer the type of and convert to a TypedExpr.
 * @param scope The current set of variables in scope. Immutable, notably.
 * @param typeCache The cache used for keeping track of the types that have been instantiated. (A small amount of mutable state.)
 */
fun inferExpr(expr: ResolvedExpr, scope: ConsMap<String, VariableBinding>, typeCache: TypeDefCache): TypingResult = when (expr) {

    // Import has type bool
    is ResolvedExpr.Import -> just(TypedExpr.Import(expr.loc, expr.file, getBasicBuiltin(BoolType, typeCache)))

    // Variable looks up its name in scope, errors if none is there
    is ResolvedExpr.Variable -> {
        val binding = scope.lookup(expr.name) ?: throw NoSuchVariableException(expr.name, expr.loc)
        just(TypedExpr.Variable(expr.loc, expr.name, binding.index, binding.type))
    }

    // Blocks maintain their own scope, infer each element. Type of block is type of last expr inside
    is ResolvedExpr.Block -> {
        var scope = scope // Shadow scope
        val inferredExprs = expr.exprs.map {
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
        val lastExprType = if (inferredExprs.isNotEmpty()) inferredExprs.last().type else throw IllegalStateException("Unit not yet implemented")
        just(TypedExpr.Block(expr.loc, inferredExprs, lastExprType))
    }

    // Declarations need to work with patterns. The resulting type is bool.
    // If the pattern is refutable, only the ifTrue branch contains the bindings.
    // If irrefutable, both branches contain the bindings.
    is ResolvedExpr.Declaration -> {
        val typedPattern: TypedPattern
        val typedInitializer: TypedExpr
        // Choose how to initialize these, based on whether
        // the pattern is explicitly typed or not.
        if (isExplicitlyTyped(expr.pattern)) {
            // If the pattern is explicitly typed, then infer its type:
            typedPattern = inferPattern(expr.pattern, typeCache)
            // Check the right side against that:
            typedInitializer = checkExpr(expr.initializer, typedPattern.type, scope, typeCache).expr
        } else {
            // The pattern is not explicitly typed, so instead infer the RHS:
            typedInitializer = inferExpr(expr.initializer, scope, typeCache).expr
            // Check the pattern against the inferred type:
            typedPattern = checkPattern(expr.pattern, typedInitializer.type, typeCache)
        }
        // Fetch the bindings if this "let" succeeds
        val patternBindings = bindings(typedPattern, scope)
        // Create the new typed expr, type is always bool.
        // Use the index that's the second output of bindings().
        val typed = TypedExpr.Declaration(expr.loc, typedPattern, patternBindings.second,
            typedInitializer, getBasicBuiltin(BoolType, typeCache))

        // If it's fallible, only output the results if true.
        // Otherwise, always output the results.
        if (isFallible(typedPattern))
            TypingResult.WithVars(typed, patternBindings.first, ConsMap.of())
        else
            TypingResult.WithVars(typed, patternBindings.first, patternBindings.first)
    }

    is ResolvedExpr.MethodCall -> {
        // Infer the type of the receiver
        val typedReceiver = inferExpr(expr.receiver, scope, typeCache).expr
        // Gather the set of non-static methods on the receiver
        val methods = typedReceiver.type.methods.filter { !it.static }
        // Choose the best method from among them
        val best = getBestMethod(methods, expr.loc, expr.methodName, expr.args, null, scope, typeCache)
        // Return the typed method call
        just(TypedExpr.MethodCall(expr.loc, typedReceiver, expr.methodName, best.checkedArgs, best.method, best.method.returnType))
    }

    is ResolvedExpr.StaticMethodCall -> {
        // Largely same as above, MethodCall
        val receiverType = getTypeDef(expr.receiverType, typeCache)
        val methods = receiverType.methods.filter { it.static }
        val best = getBestMethod(methods, expr.loc, expr.methodName, expr.args, null, scope, typeCache)
        just(TypedExpr.StaticMethodCall(expr.loc, receiverType, expr.methodName, best.checkedArgs, best.method, best.method.returnType))
    }

    is ResolvedExpr.Literal -> just(
        TypedExpr.Literal(expr.loc, expr.value, when (expr.value) {
        is Boolean -> getBasicBuiltin(BoolType, typeCache)

        else -> throw IllegalStateException("Unrecognized literal type: ${expr.value.javaClass.name}")
    }))

}