package representation.passes.typing

import builtins.helpers.Fraction
import builtins.primitive.*
import errors.CompilationException
import errors.ParsingException
import errors.TypeCheckingException
import representation.asts.resolved.ResolvedExpr
import representation.asts.resolved.ResolvedImplBlock
import representation.asts.typed.FieldDef
import representation.asts.typed.MethodDef
import representation.asts.typed.TypeDef
import representation.asts.typed.TypedExpr
import representation.passes.lexing.Loc
import util.*
import java.math.BigInteger
import kotlin.math.max


/**
 * For explanation of parameters, see inferExpr(), the sister function.
 *
 * This one adds one additional parameter, expectedType. It makes sure
 * that the expr's type is a subtype of expectedType, or else it will error.
 */
fun checkExpr(expr: ResolvedExpr, expectedType: TypeDef, scope: ConsMap<String, VariableBinding>, typeCache: TypingCache, returnType: TypeDef?, currentType: TypeDef?, currentTypeGenerics: List<TypeDef>, currentMethodGenerics: List<TypeDef>): TypingResult = when(expr) {

    // Very similar to inferring a block; we just need to
    // checkExpr() on the last expression, not infer it.
    is ResolvedExpr.Block -> {
        var scope = scope // Shadow scope
        // DIFFERENCE: Map _all but the last expression_ to their inferred forms.
        val typedExprs = expr.exprs.subList(0, max(0, expr.exprs.size - 1)).mapTo(ArrayList(expr.exprs.size+1)) {
            // Infer the inner expression
            val inferred = inferExpr(it, scope, typeCache, returnType, currentType, currentTypeGenerics, currentMethodGenerics)
            // Check its new vars. If neither side is empty, add the vars in.
            if (inferred.newVarsIfTrue.isNotEmpty() && inferred.newVarsIfFalse.isNotEmpty()) {
                // Extend the scope with the new vars
                if (inferred.newVarsIfTrue != inferred.newVarsIfFalse)
                    throw IllegalStateException("Failed assertion - different vars ifTrue vs ifFalse, but neither is empty? Bug in compiler, please report!")
                scope = scope.extend(inferred.newVarsIfTrue)
            } else {
                // TODO: Warn; this is a useless binding
            }
            // Output the inferred expr from the map
            inferred.expr
        }
        // DIFFERENCE: On the last expr, check it instead.
        val lastExpr = if (expr.exprs.isNotEmpty())
            checkExpr(expr.exprs.last(), expectedType, scope, typeCache, returnType, currentType, currentTypeGenerics, currentMethodGenerics).expr
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
        // Infer the type of the receiver.
        // Literals are allowed because literals can still have const methods on them.
        val typedReceiver = inferExpr(expr.receiver, scope, typeCache, returnType, currentType, currentTypeGenerics, currentMethodGenerics, allowLiteral = true)
        // Gather the set of non-static methods on the receiver
        val methods = typedReceiver.expr.type.allNonStaticMethods + getNonStaticExtensions(typedReceiver.expr.type, expr.implBlocks, typeCache)
        // Choose the best method from among them
        val mappedGenerics = expr.genericArgs.map { getTypeDef(it, typeCache, currentTypeGenerics, currentMethodGenerics) }
        val best = getBestMethod(methods, expr.loc, expr.methodName, mappedGenerics, expr.args, expectedType, scope, typeCache, returnType, currentType, currentTypeGenerics, currentMethodGenerics)
        val maxVariable = scope.sumOf { it.second.type.stackSlots }
        val call = TypedExpr.MethodCall(expr.loc, typedReceiver.expr, expr.methodName, best.checkedArgs, best.method, maxVariable, best.method.returnType)

        // Special case booleans. See infer() version for details.
        val isBooleanCall = typedReceiver.expr.type.builtin == BoolType
        val isBoolNot = isBooleanCall && expr.methodName == "not"
        val isBoolBool = isBooleanCall && expr.methodName == "bool"

        // Repeatedly replace const method calls until done
        var resultExpr: TypedExpr = call
        while (resultExpr is TypedExpr.MethodCall && resultExpr.methodDef is MethodDef.ConstMethodDef) {
            // If the best method is a const method, then we essentially
            // apply it like a macro, replacing the expression with a new one.
            resultExpr = (resultExpr.methodDef as MethodDef.ConstMethodDef).replacer(resultExpr)
        }

        // Special cased booleans. See infer() version.
        pullUpLiteral(when {
            isBoolBool -> TypingResult.WithVars(resultExpr, typedReceiver.newVarsIfTrue, typedReceiver.newVarsIfFalse)
            isBoolNot -> TypingResult.WithVars(resultExpr, typedReceiver.newVarsIfFalse, typedReceiver.newVarsIfTrue)
            else -> just(resultExpr)
        }, expectedType)
    }

    is ResolvedExpr.StaticMethodCall -> {
        val receiverType = getTypeDef(expr.receiverType, typeCache, currentTypeGenerics, currentMethodGenerics)
        val methods = receiverType.staticMethods + getStaticExtensions(receiverType, expr.implBlocks, typeCache)
        val mappedGenerics = expr.genericArgs.map { getTypeDef(it, typeCache, currentTypeGenerics, currentMethodGenerics) }
        val best = getBestMethod(methods, expr.loc, expr.methodName, mappedGenerics, expr.args, expectedType, scope, typeCache, returnType, currentType, currentTypeGenerics, currentMethodGenerics)
        // TODO: Const static method calls
        val maxVariable = scope.sumOf { it.second.type.stackSlots }
        pullUpLiteral(just(TypedExpr.StaticMethodCall(expr.loc, receiverType, expr.methodName, best.checkedArgs, best.method, maxVariable, best.method.returnType)), expectedType)
    }

    is ResolvedExpr.SuperMethodCall -> {
        val superType = (currentType ?: throw ParsingException("Cannot use keyword \"super\" outside of a type definition", expr.loc))
            .primarySupertype ?: throw ParsingException("Cannot use keyword \"super\" here. Type \"${currentType.name} does not have a supertype.", expr.loc)
        val methods = superType.nonStaticMethods + getNonStaticExtensions(superType, expr.implBlocks, typeCache)
        val mappedGenerics = expr.genericArgs.map { getTypeDef(it, typeCache, currentTypeGenerics, currentMethodGenerics) }
        val best = getBestMethod(methods, expr.loc, expr.methodName, mappedGenerics, expr.args, expectedType, scope, typeCache, returnType, currentType, currentTypeGenerics, currentMethodGenerics)
        val thisIndex = scope.lookup("this")?.index ?: throw IllegalStateException("Failed to locate \"this\" variable when typing super - but there should always be one? Bug in compiler, please report")
        val maxVariable = scope.sumOf { it.second.type.stackSlots }
        just(TypedExpr.SuperMethodCall(expr.loc, thisIndex, best.method, best.checkedArgs, maxVariable, best.method.returnType))
    }

    // Constructor and raw constructor, again almost exactly the same as infer().
    is ResolvedExpr.ConstructorCall -> {
        val type = expr.type?.let { getTypeDef(it, typeCache, currentTypeGenerics, currentMethodGenerics) } ?: expectedType
        val res = typeCheckConstructor(expr, type, scope, typeCache, returnType, currentType, currentTypeGenerics, currentMethodGenerics)
        if (!res.expr.type.isSubtype(expectedType))
            throw IncorrectTypeException(expectedType, res.expr.type, "Constructor", expr.loc)
        res
    }

    is ResolvedExpr.RawStructConstructor -> {
        // For reason behind explicitness check: See the definition of TypeCheckingException
        val isExplicit = expr.type != null
        val type = expr.type?.let { getTypeDef(it, typeCache, currentTypeGenerics, currentMethodGenerics) } ?: expectedType
        if (type.unwrap() !is TypeDef.StructDef)
            if (isExplicit)
                throw CompilationException("Raw struct constructors can only create structs, but type \"${type.name}\" is not a struct", expr.loc)
            else
                throw TypeCheckingException("Raw struct constructors can only create structs, but type \"${type.name}\" is not a struct", expr.loc)
        if (type.nonStaticFields.size != expr.fieldValues.size)
            if (isExplicit)
                throw CompilationException("Struct \"${type.name}\" has ${type.nonStaticFields.size} non-static fields, but ${expr.fieldValues.size} fields were provided in the raw constructor!", expr.loc)
            else
                throw TypeCheckingException("Struct \"${type.name}\" has ${type.nonStaticFields.size} non-static fields, but ${expr.fieldValues.size} fields were provided in the raw constructor!", expr.loc)
        val checkedFieldValues = type.nonStaticFields.zip(expr.fieldValues).map { (field, value) ->
            checkExpr(value, field.type, scope, typeCache, returnType, currentType, currentTypeGenerics, currentMethodGenerics).expr
        }
        val res = just(TypedExpr.RawStructConstructor(expr.loc, checkedFieldValues, type))
        if (!res.expr.type.isSubtype(expectedType))
            throw IncorrectTypeException(expectedType, res.expr.type, "Constructor", expr.loc) // Is TypeCheckingException
        res
    }

    is ResolvedExpr.Tuple -> {
        val expectedType = expectedType.unwrap()
        if (expectedType !is TypeDef.Tuple)
            throw TypeCheckingException("Expected type \"${expectedType.name}\", but found tuple", expr.loc)
        if (expectedType.innerTypes.size != expr.elements.size)
            throw TypeCheckingException("Expected tuple with ${expectedType.innerTypes.size} elements, but found one with ${expr.elements.size} instead", expr.loc)
        val typeCheckedElements = expectedType.innerTypes.zip(expr.elements).map { (expected, expr) ->
            checkExpr(expr, expected, scope, typeCache, returnType, currentType, currentTypeGenerics, currentMethodGenerics).expr
        }
        just(TypedExpr.RawStructConstructor(expr.loc, typeCheckedElements, expectedType))
    }

    // Most of it is in helper function, because this is a complicated operation
    is ResolvedExpr.Lambda -> {
        val expectedType = expectedType.unwrap()
        if (expectedType !is TypeDef.Func)
            throw TypeCheckingException("Expected type \"${expectedType.name}\", but found lambda", expr.loc)
        if (expectedType.paramTypes.size != expr.params.size)
            throw TypeCheckingException("Expected lambda with ${expectedType.paramTypes.size} params, but found lambda with ${expr.params.size} params", expr.loc)
        just(typeCheckLambdaExpression(expr, expectedType, scope, typeCache, returnType, currentType, currentTypeGenerics, currentMethodGenerics))
    }

    // Return: just infer the expression. Don't bother checking the return type,
    // since technically this should output a bottom type (a subtype of everything).
    // (though we don't have a representation of such)
    is ResolvedExpr.Return -> inferExpr(expr, scope, typeCache, returnType, currentType, currentTypeGenerics, currentMethodGenerics)

    // Similar to the infer() version, but with certain infer() calls replaced with check()
    is ResolvedExpr.If -> run {
        // Check cond as bool
        val typedCond = checkExpr(expr.cond, getBasicBuiltin(BoolType, typeCache), scope, typeCache, returnType, currentType, currentTypeGenerics, currentMethodGenerics)
        val typedCondExpr = typedCond.expr

        // If it's a literal boolean, fold it
        if (typedCondExpr is TypedExpr.Literal) {
            if (typedCondExpr.value is Boolean) {
                if (expr.ifFalse != null) {
                    // There is a false branch, so return the outcome of the correct branch
                    return@run if (typedCondExpr.value)
                        just(checkExpr(expr.ifTrue, expectedType, scope.extend(typedCond.newVarsIfTrue), typeCache, returnType, currentType, currentTypeGenerics, currentMethodGenerics).expr)
                    else
                        just(checkExpr(expr.ifFalse, expectedType, scope.extend(typedCond.newVarsIfFalse), typeCache, returnType, currentType, currentTypeGenerics, currentMethodGenerics).expr)
                }
                // There is no false branch. Ensure unit is the expected type:
                if (!getUnit(typeCache).isSubtype(expectedType))
                    throw IncorrectTypeException(expectedType, getUnit(typeCache), "if-expression without else branch", expr.loc)
                // If true, result in the true branch with unit appended.
                // If false, just emit a unit.
                return@run if (typedCondExpr.value) {
                    just(TypedExpr.Block(expr.loc, listOf(
                        inferExpr(expr.ifTrue, scope.extend(typedCond.newVarsIfTrue), typeCache, returnType, currentType, currentTypeGenerics, currentMethodGenerics).expr,
                        TypedExpr.RawStructConstructor(expr.loc, listOf(), getUnit(typeCache))
                    ), getUnit(typeCache)))
                } else {
                    just(TypedExpr.RawStructConstructor(expr.loc, listOf(), getUnit(typeCache)))
                }
            } else throw IllegalStateException("Should have checked to boolean? Bug in compiler, please report")
        }
        // Can't constant fold, so let's output a TypedIf:
        if (expr.ifFalse != null) {
            // There is a false branch, but we don't know which
            // Check both branches to be subtypes of expected.
            return@run just(TypedExpr.If(expr.loc,
                typedCondExpr,
                checkExpr(expr.ifTrue, expectedType, scope.extend(typedCond.newVarsIfTrue), typeCache, returnType, currentType, currentTypeGenerics, currentMethodGenerics).expr,
                checkExpr(expr.ifFalse, expectedType, scope.extend(typedCond.newVarsIfFalse), typeCache, returnType, currentType, currentTypeGenerics, currentMethodGenerics).expr,
                expectedType
            ))
        }
        // There is no false branch. Ensure unit is the expected type:
        if (!getUnit(typeCache).isSubtype(expectedType))
            throw IncorrectTypeException(expectedType, getUnit(typeCache), "if-expression without else branch", expr.loc)
        // True branch is block wrapping, false branch is just unit
        val wrappedTrueBranch = TypedExpr.Block(expr.loc, listOf(
            inferExpr(expr.ifTrue, scope.extend(typedCond.newVarsIfTrue), typeCache, returnType, currentType, currentTypeGenerics, currentMethodGenerics).expr,
            TypedExpr.RawStructConstructor(expr.loc, listOf(), getUnit(typeCache))
        ), getUnit(typeCache))
        val generatedFalseBranch = TypedExpr.RawStructConstructor(expr.loc, listOf(), getUnit(typeCache))
        just(TypedExpr.If(expr.loc, typedCondExpr, wrappedTrueBranch, generatedFalseBranch, getUnit(typeCache)))
    }

    // Some expressions can just be inferred,
    // check their type matches, and proceed.
    // e.g. Import, Literal, Variable, Declaration.
    // Usually, inferring and checking work similarly.
    else -> {
        // Literals are allowed here because we pull them up afterward!
        val res = inferExpr(expr, scope, typeCache, returnType, currentType, currentTypeGenerics, currentMethodGenerics, allowLiteral = true)
        if (!res.expr.type.isSubtype(expectedType))
            throw IncorrectTypeException(expectedType, res.expr.type, when(expr) {
                is ResolvedExpr.Import -> "Import expression"
                is ResolvedExpr.Literal -> "Literal"
                is ResolvedExpr.Variable -> "Variable \"${expr.name}\""
                is ResolvedExpr.Assignment -> "Assignment"
                is ResolvedExpr.FieldAccess -> "Field access \"${expr.fieldName}"
                is ResolvedExpr.StaticFieldAccess -> "Static field access \"${expr.fieldName}"
                is ResolvedExpr.Declaration -> "Let expression"
                is ResolvedExpr.While -> "While loop"
                is ResolvedExpr.For -> "For loop"
                is ResolvedExpr.Is -> "Is-expression"
                else -> throw IllegalStateException("Failed to create error message - unexpected expression type ${res.expr.javaClass.simpleName}")
            }, expr.loc)
        pullUpLiteral(res, expectedType)
    }
}

/**
 * Pull a literal upwards into having a different type
 */
fun pullUpLiteral(result: TypingResult, expectedType: TypeDef): TypingResult = when (result.expr) {
    is TypedExpr.Literal -> when (result.expr.type.builtin) {
        IntLiteralType -> {
            val value = (result.expr as TypedExpr.Literal).value as BigInteger
            // If we expected an int literal, we're fine
            when (expectedType.builtin) {
                IntLiteralType -> result // Have IntLiteral, expected IntLiteral, it's fine
                // Have int literal, expected float literal, just convert it
                FloatLiteralType -> just(TypedExpr.Literal(result.expr.loc, Fraction(value, BigInteger.ONE), expectedType))
                // Is some int type, check the bounds and convert
                is IntType -> {
                    if (!(expectedType.builtin as IntType).fits(value))
                        throw NumberRangeException(expectedType.builtin as IntType, value, result.expr.loc)
                    just(TypedExpr.Literal(result.expr.loc, value, expectedType))
                }
                // Float types don't care about range:
                F32Type -> just(TypedExpr.Literal(result.expr.loc, value.toFloat(), expectedType))
                F64Type -> just(TypedExpr.Literal(result.expr.loc, value.toDouble(), expectedType))
                else -> throw IllegalStateException("Unexpected pull-up for int literal? Bug in compiler, please report")
            }
        }
        FloatLiteralType -> {
            // Similar structure to above
            val value = (result.expr as TypedExpr.Literal).value as Fraction
            when (expectedType.builtin) {
                FloatLiteralType -> result
                F32Type -> just(TypedExpr.Literal(result.expr.loc, value.toFloat(), expectedType))
                F64Type -> just(TypedExpr.Literal(result.expr.loc, value.toDouble(), expectedType))
                else -> throw IllegalStateException("Unexpected pull-up for float literal? Bug in compiler, please report")
            }
        }
        else -> result
    }
    else -> result
}

fun getStaticExtensions(type: TypeDef, implBlocks: ConsList<ResolvedImplBlock>, typeCache: TypingCache): List<MethodDef> {
    return implBlocks
        .mapNotNull { implBlockMatches(it, type)?.let { generics -> getImplBlock(it, generics, typeCache) } }
        .flatMap { it.staticMethods }
}
fun getNonStaticExtensions(type: TypeDef, implBlocks: ConsList<ResolvedImplBlock>, typeCache: TypingCache): List<MethodDef> {
    return implBlocks
        .mapNotNull { implBlockMatches(it, type)?.let { generics -> getImplBlock(it, generics, typeCache) } }
        .flatMap { it.nonStaticMethods }
}

fun typeCheckConstructor(expr: ResolvedExpr.ConstructorCall, knownType: TypeDef, scope: ConsMap<String, VariableBinding>, typeCache: TypingCache, returnType: TypeDef?, currentType: TypeDef?, currentTypeGenerics: List<TypeDef>, currentMethodGenerics: List<TypeDef>): TypingResult {
    // Different situations depending on the type we're trying to construct
    return if (knownType.hasStaticConstructor) {
        // Some constructors work differently from java -
        // Instead of being nonstatic methods that initialize
        // an object, they're static methods that return the type.
        val methods = knownType.staticMethods + getStaticExtensions(knownType, expr.implBlocks, typeCache)
        val best = getBestMethod(methods, expr.loc, "new", listOf(), expr.args, knownType, scope, typeCache, returnType, currentType, currentTypeGenerics, currentMethodGenerics)
        val maxVariable = scope.sumOf { it.second.type.stackSlots }
        just(TypedExpr.StaticMethodCall(
            expr.loc, knownType, "new", best.checkedArgs, best.method, maxVariable, knownType
        ))
    } else {
        // Regular ol' java style constructor
        // Look for a non-static method "new" that returns unit
        val methods = knownType.nonStaticMethods
        val expectedResult = getUnit(typeCache)
        val best = getBestMethod(methods, expr.loc, "new", listOf(), expr.args, expectedResult, scope, typeCache, returnType, currentType, currentTypeGenerics, currentMethodGenerics)
        val maxVariable = scope.sumOf { it.second.type.stackSlots }
        just(TypedExpr.ClassConstructorCall(expr.loc, best.method, best.checkedArgs, maxVariable, knownType))
    }
}

private fun typeCheckLambdaExpression(lambda: ResolvedExpr.Lambda, expectedType: TypeDef.Func, scope: ConsMap<String, VariableBinding>, typeCache: TypingCache, returnType: TypeDef?, currentType: TypeDef?, currentTypeGenerics: List<TypeDef>, currentMethodGenerics: List<TypeDef>): TypedExpr {
    // Create a new artificial SnuggleMethodDef, binding the expected type's params as its variables,
    // and type check its body to be the return type
    val indirect = TypeDef.Indirection()
    val implementationMethod = MethodDef.SnuggleMethodDef(lambda.loc, pub = true, static = false, indirect, "invoke",
        staticOverrideReceiverType = null,
        returnTypeGetter = lazy { expectedType.returnType },
        paramTypesGetter = lazy { expectedType.paramTypes },
        runtimeNameGetter = lazy { "invoke" },
        lazyBody = lazy {
            var topIndex = indirect.stackSlots // this is first
            val paramPatterns = lambda.params.zip(expectedType.paramTypes).map { (pat, requiredType) ->
                checkInfalliblePattern(pat, requiredType, topIndex, typeCache, currentTypeGenerics, currentMethodGenerics)
                    .also { topIndex += it.type.stackSlots }
            }

            // Create body bindings, including "this"
            var bodyBindings = ConsMap.of("this" to VariableBinding(indirect, false, 0))
            // Declare the params
            for (param in paramPatterns)
                bodyBindings = bodyBindings.extend(bindings(param))
            // Check the body as the return type, with the given things
            val checkedBody = checkExpr(lambda.body, expectedType.returnType, bodyBindings, typeCache, expectedType.returnType, indirect, currentTypeGenerics, currentMethodGenerics).expr
            // Output a Return with the checked body
            TypedExpr.Return(checkedBody.loc, checkedBody, checkedBody.type)
        }
    )
    // Create the implementation type (and finish its creation)
    val implType = TypeDef.FuncImplementation(expectedType, scope, implementationMethod)
    indirect.promise.fulfill(implType)
    val generatedConstructor = implType.finishCreation(typeCache)
    // Now construct the lambda!
    val args = implType.fields.map {
        val resolvedVariable = ResolvedExpr.Variable(lambda.loc, it.name)
        checkExpr(resolvedVariable, it.type, scope, typeCache, returnType, currentType, currentTypeGenerics, currentMethodGenerics).expr
    }
    val maxVariable = scope.sumOf { it.second.type.stackSlots }
    return TypedExpr.ClassConstructorCall(lambda.loc, generatedConstructor, args, maxVariable, implType) // Output a constructor call
}

// Find the set of all fields on "this" that were accessed
fun findThisFieldAccesses(expr: TypedExpr): Set<FieldDef> = when (expr) {
    is TypedExpr.Block -> union(expr.exprs.asSequence().map(::findThisFieldAccesses).asIterable())
    is TypedExpr.Declaration -> findThisFieldAccesses(expr.initializer)
    is TypedExpr.Assignment -> union(findThisFieldAccesses(expr.lhs), findThisFieldAccesses(expr.rhs))
    is TypedExpr.Return -> findThisFieldAccesses(expr.rhs)
    is TypedExpr.If -> union(findThisFieldAccesses(expr.cond), findThisFieldAccesses(expr.ifTrue), findThisFieldAccesses(expr.ifFalse))
    is TypedExpr.While -> union(findThisFieldAccesses(expr.cond), findThisFieldAccesses(expr.body))
    is TypedExpr.Is -> findThisFieldAccesses(expr.lhs)
    is TypedExpr.FieldAccess -> if (expr.receiver is TypedExpr.Variable && expr.receiver.name == "this")
        setOf(expr.fieldDef) else findThisFieldAccesses(expr.receiver)
    is TypedExpr.MethodCall -> union(listOf(findThisFieldAccesses(expr.receiver)), expr.args.asSequence().map(::findThisFieldAccesses).asIterable())
    is TypedExpr.StaticMethodCall -> union(expr.args.asSequence().map(::findThisFieldAccesses).asIterable())
    is TypedExpr.SuperMethodCall -> union(expr.args.asSequence().map(::findThisFieldAccesses).asIterable())
    is TypedExpr.ClassConstructorCall -> union(expr.args.asSequence().map(::findThisFieldAccesses).asIterable())
    is TypedExpr.RawStructConstructor -> union(expr.fieldValues.asSequence().map(::findThisFieldAccesses).asIterable())

    is TypedExpr.Import, is TypedExpr.Literal, is TypedExpr.StaticFieldAccess, is TypedExpr.Variable -> setOf()
}


class IncorrectTypeException(expectedType: TypeDef, actualType: TypeDef, situation: String, loc: Loc)
    : TypeCheckingException("Expected $situation to result in type ${expectedType.name}, but it actually was ${actualType.name}", loc)

class NumberRangeException(expectedType: IntType, value: BigInteger, loc: Loc)
    : TypeCheckingException("Expected ${expectedType.baseName}, but literal $value is out of range " +
        "(${expectedType.minValue} to ${expectedType.maxValue}).", loc)