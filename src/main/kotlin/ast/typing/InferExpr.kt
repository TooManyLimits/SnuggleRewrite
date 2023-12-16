package ast.typing

import ast.import_resolution.ImportResolvedExpr
import builtins.BoolType
import errors.NoSuchVariableException
import util.ConsList
import util.ConsMap
import util.extend
import util.lookup
import kotlin.math.exp

/**
 * The output of type-checking an expression. Always results in a TypedExpr,
 * may sometimes result in some new variables.
 */
sealed interface TypingResult {
    val expr: TypedExpr
    val newVarsIfTrue: ConsMap<String, TypeDef>
    val newVarsIfFalse: ConsMap<String, TypeDef>

    data class WithVars(override val expr: TypedExpr, override val newVarsIfTrue: ConsMap<String, TypeDef>, override val newVarsIfFalse: ConsMap<String, TypeDef>): TypingResult
    @JvmInline value class JustExpr(override val expr: TypedExpr): TypingResult {
        override val newVarsIfTrue: ConsMap<String, TypeDef> get() = ConsMap(ConsList.nil())
        override val newVarsIfFalse: ConsMap<String, TypeDef> get() = ConsMap(ConsList.nil())
    }
}

private fun just(expr: TypedExpr): TypingResult.JustExpr = TypingResult.JustExpr(expr)

/**
 * Infer an expression's type, and output a TypingResult.
 * @param expr The expression we want to infer the type of and convert to a TypedExpr.
 * @param scope The current set of variables in scope. Immutable, notably.
 * @param typeCache The cache used for keeping track of the types that have been instantiated. (A small amount of mutable state.)
 */
fun infer(expr: ImportResolvedExpr, scope: ConsMap<String, TypeDef>, typeCache: TypeDefCache): TypingResult = when (expr) {

    // Import has type bool
    is ImportResolvedExpr.Import -> just(TypedExpr.Import(expr.loc, expr.file, getBasicBuiltin(BoolType, typeCache)))

    // Variable looks up its name in scope, errors if none is there
    is ImportResolvedExpr.Variable -> just(TypedExpr.Variable(expr.loc, expr.name,
        scope.lookup(expr.name) ?: throw NoSuchVariableException(expr.name, expr.loc),
    ))

    // Blocks maintain their own scope, infer each element. Type of block is type of last expr inside
    is ImportResolvedExpr.Block -> {
        var scope = scope // Shadow scope
        val inferredExprs = expr.exprs.map {
            // Infer the inner expression
            val inferred = infer(it, scope, typeCache)
            // Check its new vars. If neither side is empty, add the vars in.
            if (inferred.newVarsIfTrue.isEmpty() || inferred.newVarsIfFalse.isEmpty()) {
                // TODO: Warn; this is a useless binding
            } else {
                // Extend the scope with the new vars
                assert(inferred.newVarsIfTrue == inferred.newVarsIfFalse) { "Failed assertion - different vars ifTrue vs ifFalse, but neither is empty? Bug in compiler, please report!" }
                scope = scope.extend(inferred.newVarsIfTrue)
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
    is ImportResolvedExpr.Declaration -> {
        throw RuntimeException()
    }

    is ImportResolvedExpr.MethodCall -> {
        // Cant wait to get to this :DDDDDDD
        throw RuntimeException()
    }

    is ImportResolvedExpr.Literal -> just(TypedExpr.Literal(expr.loc, expr.value, when (expr.value) {
        is Boolean -> getBasicBuiltin(BoolType, typeCache)

        else -> throw IllegalStateException("Unrecognized literal type: ${expr.value.javaClass.name}")
    }))



}