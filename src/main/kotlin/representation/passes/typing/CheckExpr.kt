package representation.passes.typing

import builtins.BoolType
import builtins.IntLiteralType
import builtins.IntType
import builtins.OptionType
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
        // Infer the type of the receiver
        val typedReceiver = inferExpr(expr.receiver, scope, typeCache, returnType, currentType, currentTypeGenerics, currentMethodGenerics)
        // Gather the set of non-static methods on the receiver
        val methods = typedReceiver.expr.type.allNonStaticMethods + getNonStaticExtensions(typedReceiver.expr.type, expr.implBlocks, typeCache)
        // Choose the best method from among them
        val mappedGenerics = expr.genericArgs.map { getTypeDef(it, typeCache, currentTypeGenerics, currentMethodGenerics) }
        val best = getBestMethod(methods, expr.loc, expr.methodName, mappedGenerics, expr.args, expectedType, scope, typeCache, returnType, currentType, currentTypeGenerics, currentMethodGenerics)
        val maxVariable = scope.sumOf { it.second.type.stackSlots }
        val call = TypedExpr.MethodCall(expr.loc, typedReceiver.expr, expr.methodName, best.checkedArgs, best.method, maxVariable, best.method.returnType)

        // Repeatedly replace const method calls until done
        var resultExpr: TypedExpr = call
        while (resultExpr is TypedExpr.MethodCall && resultExpr.methodDef is MethodDef.ConstMethodDef) {
            // If the best method is a const method, then we essentially
            // apply it like a macro, replacing the expression with a new one.
            resultExpr = (resultExpr.methodDef as MethodDef.ConstMethodDef).replacer(resultExpr)
        }
        pullUpLiteral(TypingResult.WithVars(resultExpr, typedReceiver.newVarsIfTrue, typedReceiver.newVarsIfFalse), expectedType)
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
                // If there is an else branch, ensure the correct branch is as expected, and return it
                if (expr.ifFalse != null) {
                    if (typedCondExpr.value)
                        return@run checkExpr(expr.ifTrue, expectedType, scope.extend(typedCond.newVarsIfTrue), typeCache, returnType, currentType, currentTypeGenerics, currentMethodGenerics)
                    else
                        return@run checkExpr(expr.ifFalse, expectedType, scope.extend(typedCond.newVarsIfFalse), typeCache, returnType, currentType, currentTypeGenerics, currentMethodGenerics)
                }
                // There was no false branch. Ensure that the expected type was an Option.
                if (expectedType.builtin != OptionType)
                    throw TypeCheckingException("Expected type \"${expectedType.name}\", but if-expression without else outputs Option", expr.loc)
                val innerType = expectedType.generics[0]
                val res = if (typedCondExpr.value) {
                    // Check the true branch and wrap in an option
                    val newScope = scope.extend(typedCond.newVarsIfTrue)
                    val checkedTrueBranch = checkExpr(expr.ifTrue, innerType, newScope, typeCache, returnType, currentType, currentTypeGenerics, currentMethodGenerics).expr
                    wrapInOption(checkedTrueBranch, scope, typeCache)
                } else {
                    // Just an empty option
                    emptyOption(innerType, scope, typeCache)
                }
                return@run just(res)
            } else throw IllegalStateException("Should have checked to boolean? Bug in compiler, please report")
        }
        // Can't constant fold, so let's check both branches and ouput TypedExpr.If
        if (expr.ifFalse != null) {
            val typedTrueBranch = checkExpr(expr.ifTrue, expectedType, scope.extend(typedCond.newVarsIfTrue), typeCache, returnType, currentType, currentTypeGenerics, currentMethodGenerics).expr
            val typedFalseBranch = checkExpr(expr.ifFalse, expectedType, scope.extend(typedCond.newVarsIfFalse), typeCache, returnType, currentType, currentTypeGenerics, currentMethodGenerics).expr
            just(TypedExpr.If(expr.loc, typedCondExpr, typedTrueBranch, typedFalseBranch, expectedType))
        } else {
            if (expectedType.builtin != OptionType)
                throw TypeCheckingException("Expected type \"${expectedType.name}\", but if-expression without else outputs Option", expr.loc)
            val innerType = expectedType.generics[0]
            val typedTrueBranch = checkExpr(expr.ifTrue, innerType, scope.extend(typedCond.newVarsIfTrue), typeCache, returnType, currentType, currentTypeGenerics, currentMethodGenerics).expr
            val wrappedTrueBranch = wrapInOption(typedTrueBranch, scope, typeCache)
            val generatedFalseBranch = emptyOption(typedTrueBranch.type, scope, typeCache)
            just(TypedExpr.If(expr.loc, typedCondExpr, wrappedTrueBranch, generatedFalseBranch, expectedType))
        }
    }

    // Similar to infer(), but with checking the body instead of inferring
    is ResolvedExpr.While -> {
        val typedCond = checkExpr(expr.cond, getBasicBuiltin(BoolType, typeCache), scope, typeCache, returnType, currentType, currentTypeGenerics, currentMethodGenerics)
        val newScope = scope.extend(typedCond.newVarsIfTrue)
        if (expectedType.builtin != OptionType)
            throw TypeCheckingException("Expected type \"${expectedType.name}\", but while-expression outputs Option", expr.loc)
        val innerType = expectedType.generics[0]
        val typedBody = checkExpr(expr.body, innerType, newScope, typeCache, returnType, currentType, currentTypeGenerics, currentMethodGenerics).expr
        val wrappedBody = wrapInOption(typedBody, scope, typeCache)
        val emptyOption = emptyOption(innerType, scope, typeCache)
        just(TypedExpr.While(expr.loc, typedCond.expr, wrappedBody, emptyOption, expectedType))
    }

    is ResolvedExpr.For -> TODO()

    // Some expressions can just be inferred,
    // check their type matches, and proceed.
    // e.g. Import, Literal, Variable, Declaration.
    // Usually, inferring and checking work similarly.
    else -> {
        val res = inferExpr(expr, scope, typeCache, returnType, currentType, currentTypeGenerics, currentMethodGenerics)
        if (!res.expr.type.isSubtype(expectedType))
            throw IncorrectTypeException(expectedType, res.expr.type, when(expr) {
                is ResolvedExpr.Import -> "Import expression"
                is ResolvedExpr.Literal -> "Literal"
                is ResolvedExpr.Variable -> "Variable \"${expr.name}\""
                is ResolvedExpr.Assignment -> "Assignment"
                is ResolvedExpr.FieldAccess -> "Field access \"${expr.fieldName}"
                is ResolvedExpr.StaticFieldAccess -> "Static field access \"${expr.fieldName}"
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
            var topIndex = 0
            val paramPatterns = lambda.params.zip(expectedType.paramTypes).map { (pat, requiredType) ->
                checkPattern(pat, requiredType, topIndex, typeCache, currentTypeGenerics, currentMethodGenerics)
                    .also { topIndex += it.type.stackSlots }
            }

            // Create body bindings, including "this"
            var bodyBindings = ConsMap.of("this" to VariableBinding(indirect, false, 0))
            // Declare the params
            for (param in paramPatterns)
                bodyBindings = bodyBindings.extend(bindings(param, getTopIndex(bodyBindings)))
            // TODO: Add closure-ing
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
    is TypedExpr.While -> union(findThisFieldAccesses(expr.cond), findThisFieldAccesses(expr.body), findThisFieldAccesses(expr.neverRanAlternative))
    is TypedExpr.For -> union(findThisFieldAccesses(expr.iterable), findThisFieldAccesses(expr.body))
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
    : TypeCheckingException("Expected $situation to have type ${expectedType.name}, but found ${actualType.name}", loc)

class NumberRangeException(expectedType: IntType, value: BigInteger, loc: Loc)
    : TypeCheckingException("Expected ${expectedType.baseName}, but literal $value is out of range " +
        "(${expectedType.minValue} to ${expectedType.maxValue}).", loc)