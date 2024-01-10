package representation.passes.typing

import builtins.IntLiteralType
import builtins.IntType
import errors.CompilationException
import errors.ParsingException
import representation.asts.resolved.ResolvedExpr
import representation.asts.typed.MethodDef
import representation.asts.typed.TypeDef
import representation.asts.typed.TypedExpr
import representation.passes.lexing.Loc
import util.ConsMap
import util.extend
import util.lookup
import java.math.BigInteger
import kotlin.math.max


/**
 * For explanation of parameters, see inferExpr(), the sister function.
 *
 * This one adds one additional parameter, expectedType. It makes sure
 * that the expr's type is a subtype of expectedType, or else it will error.
 */
fun checkExpr(expr: ResolvedExpr, expectedType: TypeDef, scope: ConsMap<String, VariableBinding>, typeCache: TypeDefCache, returnType: TypeDef?, currentType: TypeDef?, currentTypeGenerics: List<TypeDef>): TypingResult = when(expr) {

    // Very similar to inferring a block; we just need to
    // checkExpr() on the last expression, not infer it.
    is ResolvedExpr.Block -> {
        var scope = scope // Shadow scope
        // DIFFERENCE: Map _all but the last expression_ to their inferred forms.
        val typedExprs = expr.exprs.subList(0, max(0, expr.exprs.size - 1)).mapTo(ArrayList(expr.exprs.size+1)) {
            // Infer the inner expression
            val inferred = inferExpr(it, scope, typeCache, returnType, currentType, currentTypeGenerics)
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
            checkExpr(expr.exprs.last(), expectedType, scope, typeCache, returnType, currentType, currentTypeGenerics).expr
        else throw IllegalStateException("Unit not yet implemented")
        typedExprs.add(lastExpr)
        // Return as usual.
        just(TypedExpr.Block(expr.loc, typedExprs, lastExpr.type))
    }

    // Checking vs inferring method calls is very similar.
    // The only real difference is that check() passes expectedType
    // as the required result, while infer() passes null.

    is ResolvedExpr.MethodCall -> {
        // Largely the same as the infer() version, just passes the "expectedType" parameter
        // Infer the type of the receiver
        val typedReceiver = inferExpr(expr.receiver, scope, typeCache, returnType, currentType, currentTypeGenerics).expr
        // Gather the set of non-static methods on the receiver
        val methods = typedReceiver.type.nonStaticMethods
        // Choose the best method from among them
        val best = getBestMethod(methods, expr.loc, expr.methodName, expr.args, expectedType, scope, typeCache, returnType, currentType, currentTypeGenerics)
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
        val receiverType = getTypeDef(expr.receiverType, typeCache, currentTypeGenerics)
        val methods = receiverType.staticMethods
        val best = getBestMethod(methods, expr.loc, expr.methodName, expr.args, expectedType, scope, typeCache, returnType, currentType, currentTypeGenerics)
        // TODO: Const static method calls
        pullUpLiteral(just(TypedExpr.StaticMethodCall(expr.loc, receiverType, expr.methodName, best.checkedArgs, best.method, best.method.returnType)), expectedType)
    }

    is ResolvedExpr.SuperMethodCall -> {
        val superType = (currentType ?: throw ParsingException("Cannot use keyword \"super\" outside of a type definition", expr.loc))
            .primarySupertype ?: throw ParsingException("Cannot use keyword \"super\" here. Type \"${currentType.name} does not have a supertype.", expr.loc)
        val methods = superType.nonStaticMethods
        val best = getBestMethod(methods, expr.loc, expr.methodName, expr.args, expectedType, scope, typeCache, returnType, currentType, currentTypeGenerics)
        val thisIndex = scope.lookup("this")?.index ?: throw IllegalStateException("Failed to locate \"this\" variable when typing super - but there should always be one? Bug in compiler, please report")
        just(TypedExpr.SuperMethodCall(expr.loc, thisIndex, best.method, best.checkedArgs, best.method.returnType))
    }

    // Constructor and raw constructor, again almost exactly the same as infer().
    is ResolvedExpr.ConstructorCall -> {

        val type = expr.type?.let { getTypeDef(it, typeCache, currentTypeGenerics) } ?: expectedType

        // Different situations depending on the type.
        val res = if (type.unwrap() is TypeDef.InstantiatedBuiltin && !type.isReferenceType) {
            // Special constructor. Non-reference type builtins have these.
            val methods = type.staticMethods
            val expectedResult = type.unwrap()
            val best = getBestMethod(methods, expr.loc, "new", expr.args, expectedResult, scope, typeCache, returnType, currentType, currentTypeGenerics)
            if (best.method !is MethodDef.BytecodeMethodDef)
                throw IllegalStateException("Assumed that builtin, non-reference type constructors are defined in bytecode - but type \"${type.name} breaks this? Bug in compiler, please report")
            // Doesn't matter what type of method call we return here,
            // since the method is bytecode anyway, so we'll pick a static call.
            just(TypedExpr.StaticMethodCall(
                expr.loc, type, "new", best.checkedArgs, best.method, best.method.returnType
            ))
        } else if (type.unwrap() is TypeDef.StructDef) {
            // StructDef constructors work differently.
            // Instead of being nonstatic methods that initialize
            // an object, they're static methods that return the struct.
            val methods = type.staticMethods
            val expectedResult = type.unwrap()
            val best = getBestMethod(methods, expr.loc, "new", expr.args, expectedResult, scope, typeCache, returnType, currentType, currentTypeGenerics)
            just(TypedExpr.StaticMethodCall(
                expr.loc, type, "new", best.checkedArgs, best.method, best.method.returnType
            ))
        } else {
            // Regular ol' java style constructor
            // Look for a non-static method "new" that returns unit
            val methods = type.nonStaticMethods
            val expectedResult = getUnit(typeCache)
            val best = getBestMethod(methods, expr.loc, "new", expr.args, expectedResult, scope, typeCache, returnType, currentType, currentTypeGenerics)
            just(TypedExpr.ClassConstructorCall(expr.loc, best.method, best.checkedArgs, type))
        }
        if (!res.expr.type.isSubtype(expectedType))
            throw TypeCheckingException(expectedType, res.expr.type, "Constructor", expr.loc)
        res
    }

    is ResolvedExpr.RawStructConstructor -> {
        val type = expr.type?.let { getTypeDef(it, typeCache, currentTypeGenerics) } ?: expectedType
        if (type.unwrap() !is TypeDef.StructDef)
            throw CompilationException("Raw struct constructors can only create structs, but type \"${type.name}\" is not a struct", expr.loc)
        if (type.nonStaticFields.size != expr.fieldValues.size)
            throw CompilationException("Struct \"${type.name}\" has ${type.nonStaticFields.size} non-static fields, but ${expr.fieldValues.size} fields were provided in the raw constructor!", expr.loc)
        val checkedFieldValues = type.nonStaticFields.zip(expr.fieldValues).map { (field, value) ->
            checkExpr(value, field.type, scope, typeCache, returnType, currentType, currentTypeGenerics).expr
        }
        val res = just(TypedExpr.RawStructConstructor(expr.loc, checkedFieldValues, type))
        if (!res.expr.type.isSubtype(expectedType))
            throw TypeCheckingException(expectedType, res.expr.type, "Constructor", expr.loc)
        res
    }

    is ResolvedExpr.Tuple -> {
        val expectedType = expectedType.unwrap()
        if (expectedType !is TypeDef.Tuple)
            throw CompilationException("Expected type \"${expectedType.name}\", but found tuple", expr.loc)
        if (expectedType.innerTypes.size != expr.elements.size)
            throw CompilationException("Expected tuple with ${expectedType.innerTypes.size} elements, but found one with ${expr.elements.size} instead", expr.loc)
        val typeCheckedElements = expectedType.innerTypes.zip(expr.elements).map { (expected, expr) ->
            checkExpr(expr, expected, scope, typeCache, returnType, currentType, currentTypeGenerics).expr
        }
        just(TypedExpr.RawStructConstructor(expr.loc, typeCheckedElements, expectedType))
    }

    // Return: just infer the expression. Don't bother checking the return type,
    // since technically this should output a bottom type (a subtype of everything).
    // (though we don't have a representation of such)
    is ResolvedExpr.Return -> inferExpr(expr, scope, typeCache, returnType, currentType, currentTypeGenerics)

    // Some expressions can just be inferred,
    // check their type matches, and proceed.
    // e.g. Import, Literal, Variable, Declaration.
    // Usually, inferring and checking work similarly.
    else -> {
        val res = inferExpr(expr, scope, typeCache, returnType, currentType, currentTypeGenerics)
        if (!res.expr.type.isSubtype(expectedType))
            throw TypeCheckingException(expectedType, res.expr.type, when(expr) {
                is ResolvedExpr.Import -> "Import expression"
                is ResolvedExpr.Literal -> "Literal"
                is ResolvedExpr.Variable -> "Variable \"${expr.name}\""
                is ResolvedExpr.FieldAccess -> "Field access \"${expr.fieldName}"
                is ResolvedExpr.StaticFieldAccess -> "Static field access \"${expr.fieldName}"
                is ResolvedExpr.ConstructorCall -> "Constructor call"
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

class TypeCheckingException(expectedType: TypeDef, actualType: TypeDef, situation: String, loc: Loc)
    : CompilationException("Expected $situation to have type ${expectedType.name}, but found ${actualType.name}", loc)

class NumberRangeException(expectedType: IntType, value: BigInteger, loc: Loc)
    : CompilationException("Expected ${expectedType.baseName}, but literal $value is out of range " +
        "(${expectedType.minValue} to ${expectedType.maxValue}).", loc)