package representation.passes.typing

import builtins.*
import errors.FieldCountException
import representation.asts.resolved.ResolvedExpr
import errors.NoSuchVariableException
import errors.ParsingException
import representation.asts.typed.MethodDef
import representation.asts.typed.TypeDef
import representation.asts.typed.TypedExpr
import representation.asts.typed.TypedPattern
import representation.passes.lexing.IntLiteralData
import util.ConsList
import util.ConsMap
import util.extend
import util.lookup
import java.math.BigInteger

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
 * @param typeCache The cache used for keeping track of the types that have been instantiated.
 *                  (A small amount of mutable state.)
 * @param currentType The type which is currently being type-checked inside. Deals with things
 *                    like the "super" keyword, since it needs to know where it's used in order
 *                    to be able to understand its meaning.
 * @param currentTypeGenerics The generics of the current type that we're instantiating with.
 *                            If inside a generic class List<T>, for example, and we're currently
 *                            inferring expressions inside of List<String>, then this param will
 *                            be the list of 1 element [String TypeDef].
 */
fun inferExpr(expr: ResolvedExpr, scope: ConsMap<String, VariableBinding>, typeCache: TypeDefCache, currentType: TypeDef?, currentTypeGenerics: List<TypeDef>): TypingResult = when (expr) {

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
            val inferred = inferExpr(it, scope, typeCache, currentType, currentTypeGenerics)
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
            typedPattern = inferPattern(expr.pattern, typeCache, currentTypeGenerics)
            // Check the right side against that:
            typedInitializer = checkExpr(expr.initializer, typedPattern.type, scope, typeCache, currentType, currentTypeGenerics).expr
        } else {
            // The pattern is not explicitly typed, so instead infer the RHS:
            typedInitializer = inferExpr(expr.initializer, scope, typeCache, currentType, currentTypeGenerics).expr
            // Check the pattern against the inferred type:
            typedPattern = checkPattern(expr.pattern, typedInitializer.type, typeCache, currentTypeGenerics)
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
        val typedReceiver = inferExpr(expr.receiver, scope, typeCache, currentType, currentTypeGenerics).expr
        // Gather the set of non-static methods on the receiver
        val methods = typedReceiver.type.methods.filter { !it.static }
        // Choose the best method from among them
        val best = getBestMethod(methods, expr.loc, expr.methodName, expr.args, null, scope, typeCache, currentType, currentTypeGenerics)
        // Create a method call
        val call = TypedExpr.MethodCall(
            expr.loc, typedReceiver, expr.methodName, best.checkedArgs, best.method, best.method.returnType)

        // Repeatedly replace const method calls until done
        var resultExpr: TypedExpr = call
        while (resultExpr is TypedExpr.MethodCall && resultExpr.methodDef is MethodDef.ConstMethodDef) {
            // If the best method is a const method, then we essentially
            // apply it like a macro, replacing the expression with a new one.
            resultExpr = (resultExpr.methodDef as MethodDef.ConstMethodDef).replacer(resultExpr)
        }

        just(resultExpr)
    }

    is ResolvedExpr.StaticMethodCall -> {
        // Largely same as above, MethodCall
        val receiverType = getTypeDef(expr.receiverType, typeCache, currentTypeGenerics)
        val methods = receiverType.methods.filter { it.static }
        val best = getBestMethod(methods, expr.loc, expr.methodName, expr.args, null, scope, typeCache, currentType, currentTypeGenerics)
        just(TypedExpr.StaticMethodCall(expr.loc, receiverType, expr.methodName, best.checkedArgs, best.method, best.method.returnType))
    }

    is ResolvedExpr.ConstructorCall -> {
        val type = getTypeDef(expr.type, typeCache, currentTypeGenerics)

        // Different situations depending on the type.
        if (/*type.hasSpecialConstructor*/ false) {
            // Special constructor. Some builtins
            // have these.
            TODO()
        } else if (/*type is TypeDef.StructDef*/ false) {
            // StructDef constructors work differently.
            // Instead of being nonstatic methods that initialize
            // an object, they're static methods that return a struct.
            TODO()
        } else {
            // Regular ol' java style constructor
            // Look for a non-static method "new"
            val methods = type.methods.filter { !it.static }
            val expectedResult = null //TODO: that returns unit
            val best = getBestMethod(methods, expr.loc, "new", expr.args, expectedResult, scope, typeCache, currentType, currentTypeGenerics)
            just(TypedExpr.ClassConstructorCall(expr.loc, best.method, best.checkedArgs, type))
        }
    }

    is ResolvedExpr.Literal -> {
        var value = expr.value
        val type = when (expr.value) {
            is Boolean -> getBasicBuiltin(BoolType, typeCache)
            is BigInteger -> getBasicBuiltin(IntLiteralType, typeCache)
            is IntLiteralData -> {
                value = expr.value.value // Unwrap value
                getBasicBuiltin(when (expr.value.bits) {
                    8 -> if (expr.value.signed) I8Type else U8Type
                    16 -> if (expr.value.signed) I16Type else U16Type
                    32 -> if (expr.value.signed) I32Type else U32Type
                    64 -> if (expr.value.signed) I64Type else U64Type
                    else -> throw IllegalStateException()
                }, typeCache)
            }
            else -> throw IllegalStateException("Unrecognized literal type: ${expr.value.javaClass.name}")
        }
        just(TypedExpr.Literal(expr.loc, value, type))
    }

}