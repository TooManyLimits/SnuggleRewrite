package snuggle.toomanylimits.representation.passes.typing

import snuggle.toomanylimits.builtins.*
import snuggle.toomanylimits.builtins.helpers.Fraction
import snuggle.toomanylimits.builtins.primitive.*
import snuggle.toomanylimits.builtins.reflected.SnuggleString
import snuggle.toomanylimits.errors.CompilationException
import snuggle.toomanylimits.errors.ParsingException
import snuggle.toomanylimits.errors.TypeCheckingException
import snuggle.toomanylimits.representation.asts.resolved.ResolvedExpr
import snuggle.toomanylimits.representation.asts.resolved.ResolvedFalliblePattern
import snuggle.toomanylimits.representation.asts.typed.*
import snuggle.toomanylimits.representation.passes.lexing.IntLiteralData
import snuggle.toomanylimits.representation.passes.lexing.Loc
import snuggle.toomanylimits.util.ConsList
import snuggle.toomanylimits.util.ConsMap
import snuggle.toomanylimits.util.extend
import snuggle.toomanylimits.util.lookup
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
 * @param returnType If we're inside a method definition, the expected return type of the method
 *                   definition. Used for checking "return ..." expressions. If not inside a method
 *                   definition, is null.
 * @param currentType The type which is currently being type-checked inside. Deals with things
 *                    like the "super" keyword, since it needs to know where it's used in order
 *                    to be able to understand its meaning.
 * @param currentTypeGenerics The generics of the current type that we're instantiating with.
 *                            If inside a generic class List<T>, for example, and we're currently
 *                            inferring expressions inside of List<String>, then this param will
 *                            be the list of 1 element [String TypeDef].
 * @param currentMethodGenerics The generics of the current method that we're instantiating with.
 *                              Works essentially the same as currentTypeGenerics, except with
 *                              method generics instead.
 */
fun inferExpr(expr: ResolvedExpr, scope: ConsMap<String, VariableBinding>, typeCache: TypingCache, returnType: TypeDef?, currentType: TypeDef?, currentTypeGenerics: List<TypeDef>, currentMethodGenerics: List<TypeDef>, allowLiteral: Boolean = false): TypingResult {
    // Calculate with a big when-expression.
    // Afterwards, we'll ensure that the output was not an unrepresentable type.
    // (Unless the allowLiteral param is set!)
    val res = when (expr) {

        // Import has type unit
        is ResolvedExpr.Import -> just(TypedExpr.Import(expr.loc, expr.file, getUnit(typeCache)))

        // Variable looks up its name in scope, if not found, does additional handling
        is ResolvedExpr.Variable -> run {
            val binding = scope.lookup(expr.name)
            val thisBinding = scope.lookup("this")
            val insideLambda = thisBinding?.type?.unwrap() is TypeDef.FuncImplementation

            when {
                insideLambda && expr.name == "this" -> {
                    // We're in a lambda, and looking for a field "this". Repeatedly look for fields
                    // on this, "this.this", "this.this.this", etc, until either we stop being in a lambda,
                    // or we run out of fields.
                    var curExpr: TypedExpr = TypedExpr.Variable(expr.loc, thisBinding!!.mutable, "this", thisBinding.index, thisBinding.type)
                    while (true) {
                        // If the current expr is not a lambda, we found it! Return the current expr.
                        if (curExpr.type.unwrap() !is TypeDef.FuncImplementation)
                            return@run just(curExpr)
                        // Look for a field "this" on the current expr:
                        val thisField = curExpr.type.nonStaticFields.find { it.name == "this" }
                        // If there is no such field, error out:
                        if (thisField == null)
                            throw NoSuchFieldOrVariableException(expr.name, expr.loc)
                        // If there is such a field, set a field access as the current expr, and repeat.
                        curExpr = TypedExpr.FieldAccess(expr.loc, curExpr, "this", thisField, getTopIndex(scope), thisField.type)
                    }
                    throw IllegalStateException("unreachable")
                }
                binding != null -> {
                    // The variable exists. Nice and simple, let's just return it here and now.
                    just(TypedExpr.Variable(expr.loc, binding.mutable, expr.name, binding.index, binding.type))
                }
                thisBinding == null -> {
                    // There is no "this" to look at. Error, the variable doesn't exist.
                    throw NoSuchVariableException(expr.name, expr.loc)
                }
                insideLambda -> {
                    // We're inside a lambda, the variable doesn't exist, and it isn't named "this".
                    // Check "this.name", "this.this.name", etc, until we either find it or run out of
                    // this to try.
                    var curExpr: TypedExpr = TypedExpr.Variable(expr.loc, thisBinding!!.mutable, "this", thisBinding.index, thisBinding.type)
                    while (true) {
                        // If the current expr has an accessible field with the proper name, use it! We're done.
                        val field = curExpr.type.allNonStaticFields.find { it.name == expr.name }
                        if (field != null && (field.pub || (field is FieldDef.SnuggleFieldDef && field.loc.fileName == expr.loc.fileName))) {
                            return@run just(TypedExpr.FieldAccess(expr.loc, curExpr, expr.name, field, getTopIndex(scope), field.type))
                        }
                        // If not, then look for a field "this" on the current expr:
                        val thisField = curExpr.type.nonStaticFields.find { it.name == "this" }
                        // If there is no such field, error out:
                        if (thisField == null)
                            throw NoSuchFieldOrVariableException(expr.name, expr.loc)
                        // If there is such a field, set a field access as the current expr, and repeat.
                        curExpr = TypedExpr.FieldAccess(expr.loc, curExpr, "this", thisField, getTopIndex(scope), thisField.type)
                    }
                    throw IllegalStateException("Unreachable")
                }
                else -> {
                    // The last possible case. We're not inside a lambda, but there is a variable named "this". Try "this.name".
                    try {
                        val resolvedFieldAccess = ResolvedExpr.FieldAccess(expr.loc, ResolvedExpr.Variable(expr.loc, "this"), expr.name)
                        just(inferExpr(resolvedFieldAccess, scope, typeCache, returnType, currentType, currentTypeGenerics, currentMethodGenerics).expr)
                    } catch (e: NoSuchFieldException) {
                        throw NoSuchFieldOrVariableException(expr.name, expr.loc)
                    } catch (e: NoSuchVariableException) {
                        throw NoSuchFieldOrVariableException(expr.name, expr.loc)
                    } catch (e: NoSuchFieldOrVariableException) {
                        throw NoSuchFieldOrVariableException(expr.name, expr.loc)
                    }
                }
            }
        }

        // Blocks maintain their own scope, infer each element. Type of block is type of last expr inside
        is ResolvedExpr.Block -> {
            if (expr.exprs.isEmpty())
                just(TypedExpr.RawStructConstructor(expr.loc, listOf(), getUnit(typeCache)))
            else {
                var scope = scope // Shadow scope
                val inferredExprs = expr.exprs.map {
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
                val lastExprType = inferredExprs.last().type
                just(TypedExpr.Block(expr.loc, inferredExprs, lastExprType))
            }
        }

        // Declarations need to work with patterns. The resulting type is unit.
        // Both the ifTrue and ifFalse branches contain the bindings, because
        // patterns in let are infallible.
        is ResolvedExpr.Declaration -> {
            val typedPattern: TypedInfalliblePattern
            val typedInitializer: TypedExpr
            // Choose how to initialize these, based on whether
            // the pattern is explicitly typed or not.
            if (isExplicitlyTyped(expr.pattern)) {
                // If the pattern is explicitly typed, then infer its type:
                typedPattern = inferInfalliblePattern(expr.pattern, getTopIndex(scope), typeCache, currentTypeGenerics, currentMethodGenerics)
                // Check the right side against that:
                typedInitializer = checkExpr(expr.initializer, typedPattern.type, scope, typeCache, returnType, currentType, currentTypeGenerics, currentMethodGenerics).expr
            } else {
                // The pattern is not explicitly typed, so instead infer the RHS:
                typedInitializer = inferExpr(expr.initializer, scope, typeCache, returnType, currentType, currentTypeGenerics, currentMethodGenerics).expr
                // Check the pattern against the inferred type:
                typedPattern = checkInfalliblePattern(expr.pattern, typedInitializer.type, getTopIndex(scope), typeCache, currentTypeGenerics, currentMethodGenerics)
            }
            // Fetch the bindings
            val patternBindings = bindings(typedPattern)
            // Create the new typed expr, type is always unit.
            // Use the index that's the second output of bindings().
            val typed = TypedExpr.Declaration(expr.loc, typedPattern, typedInitializer, getUnit(typeCache))

            // Output with vars!
            TypingResult.WithVars(typed, patternBindings, patternBindings)
        }

        is ResolvedExpr.Assignment -> {
            val typedLhs = inferExpr(expr.lhs, scope, typeCache, returnType, currentType, currentTypeGenerics, currentMethodGenerics).expr
            checkMutable(typedLhs, expr.loc) // Checks if mutable, errors if not
            val lhsType = typedLhs.type
            val typedRhs = checkExpr(expr.rhs, lhsType, scope, typeCache, returnType, currentType, currentTypeGenerics, currentMethodGenerics).expr
            // Type of assignment is Unit
            val maxVariable = scope.sumOf { it.second.type.stackSlots }
            just(TypedExpr.Assignment(expr.loc, typedLhs, typedRhs, maxVariable, getUnit(typeCache)))
        }

        is ResolvedExpr.FieldAccess -> {
            val receiver = inferExpr(expr.receiver, scope, typeCache, returnType, currentType, currentTypeGenerics, currentMethodGenerics).expr
            val bestField = findNonStaticField(receiver.type, expr.fieldName, expr.loc)
            val maxVariable = scope.sumOf { it.second.type.stackSlots }
            just(TypedExpr.FieldAccess(expr.loc, receiver, expr.fieldName, bestField, maxVariable, bestField.type))
        }

        is ResolvedExpr.StaticFieldAccess -> {
            val receiverType = getTypeDef(expr.receiverType, typeCache, currentTypeGenerics, currentMethodGenerics)
            val bestField = findStaticField(receiverType, expr.fieldName, expr.loc)
            just(TypedExpr.StaticFieldAccess(expr.loc, receiverType, expr.fieldName, bestField, bestField.type))
        }

        // Vars from the receiver escape
        is ResolvedExpr.MethodCall -> {
            // Infer the type of the receiver.
            // Literals are allowed because literals can still have const methods on them.
            val typedReceiver = inferExpr(expr.receiver, scope, typeCache, returnType, currentType, currentTypeGenerics, currentMethodGenerics, allowLiteral = true)

            // Gather the set of non-static methods on the receiver
            val methods = typedReceiver.expr.type.allNonStaticMethods + getNonStaticExtensions(typedReceiver.expr.type, expr.implBlocks, typeCache)
            // Choose the best method from among them
            val mappedGenerics = expr.genericArgs.map { getTypeDef(it, typeCache, currentTypeGenerics, currentMethodGenerics) }
            val best = getBestMethod(methods, expr.loc, expr.methodName, mappedGenerics, expr.args, null, scope, typeCache, returnType, currentType, currentTypeGenerics, currentMethodGenerics)
            // Create a method call
            val maxVariable = scope.sumOf { it.second.type.stackSlots }
            val call = TypedExpr.MethodCall(
                expr.loc, typedReceiver.expr, expr.methodName, best.checkedArgs, best.method, maxVariable, best.method.returnType)

            // Special case: If this is a bool, and we're doing .not(), then swap the receiver's outputs.
            // This is how we deal with the "is" and "!is" expressions.
            // The if-true bindings introduced by the "is" should be carried through as the "if-false" bindings
            // instead, if we call .not().
            // Likewise, if we are just calling .bool() on a boolean, the bindings should be carried through without
            // swapping.
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

            // Return with bindings based on the receiver and the call.
            // SPECIAL CASE BOOLEANS. This is slightly annoying but... can't think of a
            // super-clean way to do it right now.
            when {
                isBoolBool -> TypingResult.WithVars(resultExpr, typedReceiver.newVarsIfTrue, typedReceiver.newVarsIfFalse)
                isBoolNot -> TypingResult.WithVars(resultExpr, typedReceiver.newVarsIfFalse, typedReceiver.newVarsIfTrue)
                else -> just(resultExpr)
            }
        }

        is ResolvedExpr.StaticMethodCall -> {
            // Largely same as above, MethodCall
            val receiverType = getTypeDef(expr.receiverType, typeCache, currentTypeGenerics, currentMethodGenerics)
            val methods = receiverType.staticMethods + getStaticExtensions(receiverType, expr.implBlocks, typeCache)
            val mappedGenerics = expr.genericArgs.map { getTypeDef(it, typeCache, currentTypeGenerics, currentMethodGenerics) }
            val best = getBestMethod(methods, expr.loc, expr.methodName, mappedGenerics, expr.args, null, scope, typeCache, returnType, currentType, currentTypeGenerics, currentMethodGenerics)
            // TODO: Const static method calls
            val maxVariable = scope.sumOf { it.second.type.stackSlots }
            just(TypedExpr.StaticMethodCall(expr.loc, receiverType, expr.methodName, best.checkedArgs, best.method, maxVariable, best.method.returnType))
        }

        is ResolvedExpr.SuperMethodCall -> {
            // Ensure we can use super here
            val superType = (currentType ?: throw ParsingException("Cannot use keyword \"super\" outside of a type definition", expr.loc))
                .primarySupertype ?: throw ParsingException("Cannot use keyword \"super\" here. Type \"${currentType.name} does not have a supertype.", expr.loc)
            val methods = superType.nonStaticMethods + getNonStaticExtensions(superType, expr.implBlocks, typeCache)
            val mappedGenerics = expr.genericArgs.map { getTypeDef(it, typeCache, currentTypeGenerics, currentMethodGenerics) }
            val best = getBestMethod(methods, expr.loc, expr.methodName, mappedGenerics, expr.args, null, scope, typeCache, returnType, currentType, currentTypeGenerics, currentMethodGenerics)
            val thisIndex = scope.lookup("this")?.index ?: throw IllegalStateException("Failed to locate \"this\" variable when typing super - but there should always be one? Bug in compiler, please report")
            val maxVariable = scope.sumOf { it.second.type.stackSlots }
            just(TypedExpr.SuperMethodCall(expr.loc, thisIndex, best.method, best.checkedArgs, maxVariable, best.method.returnType))
        }

        is ResolvedExpr.ConstructorCall -> {
            if (expr.type == null)
                throw CompilationException("Cannot infer type of constructor - try making it explicitly typed, or adding more type annotations", expr.loc)
            val type = getTypeDef(expr.type, typeCache, currentTypeGenerics, currentMethodGenerics)
            typeCheckConstructor(expr, type, scope, typeCache, returnType, currentType, currentTypeGenerics, currentMethodGenerics)
        }

        is ResolvedExpr.RawStructConstructor -> {
            if (expr.type == null)
                throw CompilationException("Cannot infer type of raw struct constructor - try making it explicitly typed, or adding more type annotations", expr.loc)
            val type = getTypeDef(expr.type, typeCache, currentTypeGenerics, currentMethodGenerics)
            if (type.unwrap() !is TypeDef.StructDef)
                throw CompilationException("Raw struct constructors can only create structs, but type \"${type.name}\" is not a struct", expr.loc)
            if (type.nonStaticFields.size != expr.fieldValues.size)
                throw CompilationException("Struct \"${type.name}\" has ${type.nonStaticFields.size} non-static fields, but ${expr.fieldValues.size} fields were provided in the raw constructor!", expr.loc)
            val checkedFieldValues = type.nonStaticFields.zip(expr.fieldValues).map { (field, value) ->
                checkExpr(value, field.type, scope, typeCache, returnType, currentType, currentTypeGenerics, currentMethodGenerics).expr
            }
            just(TypedExpr.RawStructConstructor(expr.loc, checkedFieldValues, type))
        }

        is ResolvedExpr.Tuple -> {
            // Infer each element, emit result.
            val inferredElems = expr.elements.map { inferExpr(it, scope, typeCache, returnType, currentType, currentTypeGenerics, currentMethodGenerics).expr }
            val tupleType = getTuple(inferredElems.map { it.type }, typeCache)
            // Tuples are really the same as any other struct, so emit a raw struct constructor
            just(TypedExpr.RawStructConstructor(expr.loc, inferredElems, tupleType))
        }

        is ResolvedExpr.Lambda -> throw TypeCheckingException("Cannot infer type of lambda. Try adding type annotations, or using in a context where a lambda is expected.", expr.loc)

        is ResolvedExpr.Return -> {
            if (returnType == null)
                throw ParsingException("Can only use \"return\" inside of a method definition", expr.loc)
            val typedRhs = checkExpr(expr.rhs, returnType, scope, typeCache, returnType, currentType, currentTypeGenerics, currentMethodGenerics)
            just(TypedExpr.Return(expr.loc, typedRhs.expr, typedRhs.expr.type))
        }

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
                            just(inferExpr(expr.ifTrue, scope.extend(typedCond.newVarsIfTrue), typeCache, returnType, currentType, currentTypeGenerics, currentMethodGenerics).expr)
                        else
                            just(inferExpr(expr.ifFalse, scope.extend(typedCond.newVarsIfFalse), typeCache, returnType, currentType, currentTypeGenerics, currentMethodGenerics).expr)
                    }
                    // There is no false branch.
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
            val typedTrueBranch = inferExpr(expr.ifTrue, scope.extend(typedCond.newVarsIfTrue), typeCache, returnType, currentType, currentTypeGenerics, currentMethodGenerics).expr
            just(if (expr.ifFalse != null) {
                // If there's a false branch, check it to match the true branch, and return.
                val typedFalseBranch = checkExpr(expr.ifFalse, typedTrueBranch.type, scope.extend(typedCond.newVarsIfFalse), typeCache, returnType, currentType, currentTypeGenerics, currentMethodGenerics).expr
                TypedExpr.If(expr.loc, typedCondExpr, typedTrueBranch, typedFalseBranch, typedTrueBranch.type)
            } else {
                // Otherwise, generate a false branch which is just a Unit, and make the true branch into a block with a unit added.
                val wrappedTrueBranch = TypedExpr.Block(expr.loc, listOf(
                    typedTrueBranch,
                    TypedExpr.RawStructConstructor(expr.loc, listOf(), getUnit(typeCache))
                ), getUnit(typeCache))
                val generatedFalseBranch = TypedExpr.RawStructConstructor(expr.loc, listOf(), getUnit(typeCache))
                TypedExpr.If(expr.loc, typedCondExpr, wrappedTrueBranch, generatedFalseBranch, getUnit(typeCache))
            })
        }

        is ResolvedExpr.While -> {
            // Check cond as bool
            val typedCond = checkExpr(expr.cond, getBasicBuiltin(BoolType, typeCache), scope, typeCache, returnType, currentType, currentTypeGenerics, currentMethodGenerics)
            val newScope = scope.extend(typedCond.newVarsIfTrue)
            // Infer body and return
            val inferredBody = inferExpr(expr.body, newScope, typeCache, returnType, currentType, currentTypeGenerics, currentMethodGenerics).expr
            just(TypedExpr.While(expr.loc, typedCond.expr, inferredBody, getUnit(typeCache)))
        }

        // Inferring a for loop will just output a block containing a while loop and some
        // extra sugar, it's unnecessary to have a TypedExpr.For exist when it can just be
        // desugared into a while loop. The reason we need to wait until this phase to desugar
        // it is because here we can do type-checking things we couldn't before, like on the
        // pattern. Desugar is as follows:
        //
        // for PAT in ITER BODY
        // ==
        // {
        //     let $iter = ITER.iter(); // Iterator
        //     let mut $forTemp: Option<patType> = new() // empty option
        //     while { $forTemp = $iter() $forTemp.bool() } {
        //         let PAT = $forTemp.get()
        //         BODY
        //     }
        // }
        is ResolvedExpr.For -> {
            // If explicit pattern, check() the iterable against the pattern.
            // Otherwise, if implicit pattern, infer() the iterable and use that for the pattern.
            val typedPattern: TypedInfalliblePattern
            val typedIterator: TypedExpr
            if (isExplicitlyTyped(expr.pattern)) {
                // Infer pattern, check expr
                val inferredPattern = inferInfalliblePattern(expr.pattern, getTopIndex(scope), typeCache, currentTypeGenerics, currentMethodGenerics)
                // Check the iterable as a function () -> T?, where T is the type of the explicit pattern
                val iteratorType = getFunc(listOf(), getGenericBuiltin(OptionType, listOf(inferredPattern.type), typeCache), typeCache)
                val checkedIterator = checkExpr(expr.iterable, iteratorType, scope, typeCache, returnType, currentType, currentTypeGenerics, currentMethodGenerics).expr
                typedPattern = inferredPattern; typedIterator = checkedIterator
            } else {
                // Infer expr, check pattern
                val inferredIterator = inferExpr(expr.iterable, scope, typeCache, returnType, currentType, currentTypeGenerics, currentMethodGenerics).expr
                // Ensure iterator resulted in some () -> T?
                val iteratorType = inferredIterator.type.unwrap()
                if (iteratorType !is TypeDef.Func || iteratorType.paramTypes.isNotEmpty() || iteratorType.returnType.builtin !== OptionType)
                    throw TypeCheckingException("Expected iterable method .iter(), to result in type \"() -> T?\" for some T, but instead found \"${iteratorType.name}\"", expr.loc)
                val innerType = iteratorType.returnType.generics[0]
                // Check the pattern against this
                val checkedPattern = checkInfalliblePattern(expr.pattern, innerType, getTopIndex(scope), typeCache, currentTypeGenerics, currentMethodGenerics)
                typedPattern = checkedPattern; typedIterator = inferredIterator
            }
            // Begin generating the sugar:
            val iterTempName = "\$forIter"
            val valueTempName = "\$forValue"
            val iterTempPat = TypedInfalliblePattern.Binding(expr.loc, typedIterator.type, iterTempName, false, getTopIndex(scope))
            val iterTempVar = TypedExpr.Variable(expr.loc, false, iterTempName, iterTempPat.variableIndex, iterTempPat.type)
            val extendedScope = scope.extend(bindings(iterTempPat))
            val valueTempType = getGenericBuiltin(OptionType, listOf(typedPattern.type), typeCache)
            val valueTempPat = TypedInfalliblePattern.Binding(expr.loc, valueTempType, valueTempName, true, getTopIndex(extendedScope))
            val valueTempVar = TypedExpr.Variable(expr.loc, true, valueTempName, valueTempPat.variableIndex, valueTempPat.type)
            val extendedScope2 = extendedScope.extend(bindings(valueTempPat))
            // Shift the typed pattern index accordingly to these new variables we've added
            val newTypedPattern = shiftPatternIndex(typedPattern, getTopIndex(extendedScope2) - getTopIndex(scope))
            val finalScope = extendedScope2.extend(bindings(newTypedPattern))
            // Generate more helpers
            val optionConstructor = valueTempType.methods.find { it.name == "new" && it.paramTypes.isEmpty() }!!
            val emptyOptionExpr = TypedExpr.StaticMethodCall(expr.loc, valueTempType, "new", listOf(), optionConstructor, getTopIndex(extendedScope), valueTempType)
            val iterInvoke = typedIterator.type.methods.find { it.name == "invoke" }!!
            val iterCallExpr = TypedExpr.MethodCall(expr.loc, iterTempVar, "invoke", listOf(), iterInvoke, getTopIndex(extendedScope2), valueTempType)
            val boolFunc = valueTempType.methods.find { it.name == "bool" }!!
            val boolCallExpr = TypedExpr.MethodCall(expr.loc, valueTempVar, "bool", listOf(), boolFunc, getTopIndex(extendedScope2), getBasicBuiltin(
                BoolType, typeCache))
            val getFunc = valueTempType.methods.find { it.name == "get" && it.paramTypes.isEmpty() }!!
            val getCallExpr = TypedExpr.MethodCall(expr.loc, valueTempVar, "get", listOf(), getFunc, getTopIndex(finalScope), valueTempType.generics[0])
            val condExpr = TypedExpr.Block(expr.loc, listOf(
                TypedExpr.Assignment(expr.loc, valueTempVar, iterCallExpr, getTopIndex(extendedScope2), getUnit(typeCache)),
                boolCallExpr
            ), getBasicBuiltin(BoolType, typeCache))
            val patDecl = TypedExpr.Declaration(expr.loc, newTypedPattern, getCallExpr, getUnit(typeCache))
            val whileBody = TypedExpr.Block(expr.loc, listOf(
                patDecl,
                inferExpr(expr.body, finalScope, typeCache, returnType, currentType, currentTypeGenerics, currentMethodGenerics).expr
            ), getUnit(typeCache))
            // Finally create the block.
            just(TypedExpr.Block(expr.loc, listOf(
                TypedExpr.Declaration(expr.loc, iterTempPat, typedIterator, getUnit(typeCache)),
                TypedExpr.Declaration(expr.loc, valueTempPat, emptyOptionExpr, getUnit(typeCache)),
                TypedExpr.While(expr.loc, condExpr, whileBody, getUnit(typeCache))
            ), getUnit(typeCache)))
        }

        is ResolvedExpr.Is -> {
            val inferredLhs = inferExpr(expr.lhs, scope, typeCache, returnType, currentType, currentTypeGenerics, currentMethodGenerics)

            //Add a "smart cast" binding into the scope here if necessary/possible
            val modifiedPattern = if (inferredLhs.expr is TypedExpr.Variable && expr.pattern is ResolvedFalliblePattern.IsType && expr.pattern.varName == null) {
                ResolvedFalliblePattern.IsType(expr.pattern.loc, false, (inferredLhs.expr as TypedExpr.Variable).name, expr.pattern.type)
            } else expr.pattern

            val topIndex = getTopIndex(scope)
            val checkedPattern = checkFalliblePattern(modifiedPattern, inferredLhs.expr.type, topIndex, typeCache, currentTypeGenerics, currentMethodGenerics)
            val bindingsIfTrue = bindingsIfTrue(checkedPattern)
            TypingResult.WithVars(
                TypedExpr.Is(expr.loc, inferredLhs.expr, checkedPattern, getBasicBuiltin(BoolType, typeCache)),
                bindingsIfTrue, ConsMap.of()
            )
        }

        is ResolvedExpr.As -> {
            val inferredLhs = inferExpr(expr.lhs, scope, typeCache, returnType, currentType, currentTypeGenerics, currentMethodGenerics).expr
            val type = getTypeDef(expr.type, typeCache, currentTypeGenerics, currentMethodGenerics)

            // Ensure the types match up
            fun isJvmPrimitive(type: TypeDef) = type.builtin is IntType || type.builtin is FloatType || type.builtin === BoolType
            when {
                // Internally, primitive values can be cast to one another
                isJvmPrimitive(inferredLhs.type) && isJvmPrimitive(type) -> {
                    just(TypedExpr.As(expr.loc, expr.forced, inferredLhs, type))
                }
                // A reference type can be cast to a supertype or subtype
                type.isReferenceType && (type.isSubtype(inferredLhs.type) || inferredLhs.type.isSubtype(type)) -> {
                    just(TypedExpr.As(expr.loc, expr.forced, inferredLhs, type))
                }
                // Types are the same, always succeeds, just emit the lhs
                type == inferredLhs.type -> {
                    just(inferredLhs)
                }
                // Otherwise, fail.
                else -> {
                    throw TypeCheckingException("Cast from type \"${inferredLhs.type.name}\" to \"${type.name}\" can never succeed!", expr.loc)
                }
            }
        }

        is ResolvedExpr.Literal -> {
            var resValue = expr.value
            val type = when (expr.value) {
                is Boolean -> getBasicBuiltin(BoolType, typeCache)
                is BigInteger -> getBasicBuiltin(IntLiteralType, typeCache)
                is Fraction -> getBasicBuiltin(FloatLiteralType, typeCache)
                is IntLiteralData -> {
                    resValue = expr.value.value // Steal the BigInteger from inside
                    expr.value.getBuiltin(typeCache)
                }
                is Float -> getBasicBuiltin(F32Type, typeCache)
                is Double -> getBasicBuiltin(F64Type, typeCache)
                is String -> getReflectedBuiltin(SnuggleString::class.java, typeCache)
                is Char -> getBasicBuiltin(CharType, typeCache)
                else -> throw IllegalStateException("Unrecognized literal type: ${expr.value.javaClass.name}")
            }
            just(TypedExpr.Literal(expr.loc, resValue, type))
        }
    }

    // Error if we don't allow literals, and this is a literal.
    // Literals are allowed in a few places, but not most.
    if (!allowLiteral && (res.expr.type.builtin == IntLiteralType || res.expr.type.builtin == FloatLiteralType))
        throw CompilationException("Cannot infer type of literal - try adding more annotations", expr.loc)

    return res
}

class NoSuchVariableException(varName: String, loc: Loc)
    : CompilationException("No variable with name \"$varName\" is in the current scope", loc)

class NoSuchFieldOrVariableException(varName: String, loc: Loc)
    : CompilationException("Unable to find \"$varName\" as either local variable or field in current class", loc)

// Check whether an assignment to this lvalue is okay or not
private fun checkMutable(lvalue: TypedExpr, assignLoc: Loc): Unit = when (lvalue) {
    is TypedExpr.Variable -> if (!lvalue.mutable)
        throw CompilationException("Cannot assign to immutable variable \"${lvalue.name}\"", assignLoc) else {}
    is TypedExpr.StaticFieldAccess -> if (!lvalue.fieldDef.mutable)
        throw CompilationException("Cannot assign to immutable static field \"${lvalue.fieldName}\"", assignLoc) else {}
    is TypedExpr.FieldAccess -> when {
        lvalue.receiver.type.isReferenceType -> if (!lvalue.fieldDef.mutable)
            throw CompilationException("Cannot assign to immutable field \"${lvalue.fieldName}\"", assignLoc) else {}
        else -> checkMutable(lvalue.receiver, assignLoc)
    }
    else -> throw CompilationException("Illegal assignment - can only assign to variables, fields, or [] results.", assignLoc)
}