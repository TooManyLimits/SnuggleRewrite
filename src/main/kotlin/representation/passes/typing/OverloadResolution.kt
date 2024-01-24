package representation.passes.typing

import errors.CompilationException
import errors.TypeCheckingException
import representation.asts.resolved.ResolvedExpr
import representation.asts.typed.MethodDef
import representation.asts.typed.TypeDef
import representation.asts.typed.TypedExpr
import representation.passes.lexing.Loc
import util.ConsMap
import util.allIndexed
import util.caching.EqualityCache
import util.insertionSort
import java.util.*

/**
 * This calls the other helper functions in the file, in order
 * to provide an optimal result, or error out.
 */
fun getBestMethod(
    // The methods to choose the best from.
    // Should be different depending on if the
    // call is virtual/static/super.
    methodsToCheck: Collection<MethodDef>,
    // Data for understanding the call site
    callLoc: Loc,
    methodName: String,
    genericArguments: List<TypeDef>,
    arguments: List<ResolvedExpr>,
    expectedResult: TypeDef?,
    scope: ConsMap<String, VariableBinding>,
    typeCache: TypingCache,
    returnType: TypeDef?,
    currentType: TypeDef?,
    currentTypeGenerics: List<TypeDef>,
    currentMethodGenerics: List<TypeDef>
): BestMethodData {
    // Find which methods are applicable.
    val applicableResult = getApplicableMethods(methodsToCheck, methodName, genericArguments, arguments, expectedResult, scope, typeCache, returnType, currentType, currentTypeGenerics, currentMethodGenerics)
    // Try to choose the best from among them.
    return tryChooseBestMethod(applicableResult, callLoc, methodName, arguments, expectedResult, scope, typeCache, returnType, currentType, currentTypeGenerics, currentMethodGenerics)
}

// Return type encapsulating the best method
// as well as its type-checked arguments.
data class BestMethodData(
    val method: MethodDef,
    val checkedArgs: List<TypedExpr>
)

/**
 * Data class encapsulating a group of applicable methods,
 * as well as some additional data which can be used for
 * error reporting.
 *
 * It would be possible to avoid needing this class, if
 * we chose to put the entire implementation of the method
 * choosing into one monolithic method. However, this would
 * be difficult to read, because there's a large amount of
 * code in this file, so it's split into multiple methods,
 * and this data structure is used to transmit data between
 * them.
 */
private data class ApplicableMethodsResult(
    // The actual applicable methods
    val applicableMethods: List<MethodDef>,
    // The type-checked arguments to the applicable methods,
    // to avoid unnecessary repeated computations
    val typeCheckedArguments: List<List<TypedExpr>>,
    // The methods that we tried, but failed in the
    // argument type checking phase. Used to report a
    // NoApplicableMethodException.
    val triedMethods: List<MethodDef>
)

/**
 * Given an input collection of methods and some data,
 * filter out the ones which could potentially be chosen.
 */
private fun getApplicableMethods(
    // The methods which could potentially be selected from.
    // This should be different based on whether the call is
    // a regular virtual call, a static call, or a super call.
    methodsToCheck: Collection<MethodDef>,
    // The name of the method.
    methodName: String,
    // The (explicit) generic arguments to the call
    genericArguments: List<TypeDef>,
    // The arguments to the method call.
    arguments: List<ResolvedExpr>,
    // The expected output of the method call.
    // If there is no expected output (we're infer()ring), it's null.
    expectedResult: TypeDef?,
    // The scope of the current call.
    scope: ConsMap<String, VariableBinding>,

    // The TypeDefCache holding data for all types in this AST.
    typeCache: TypingCache,
    // The desired return type at the site of the method call.
    returnType: TypeDef?,
    // The current type which our method call is inside.
    currentType: TypeDef?,
    // The type generics we're currently instantiating with.
    currentTypeGenerics: List<TypeDef>,
    // The method generics we're currently instantiating with.
    currentMethodGenerics: List<TypeDef>
): ApplicableMethodsResult {

    // Create a cache for storing type-checked arguments,
    // removing redundant calculations
    // List: one for each argument
    // Map keys: "What we tried checking this argument as"
    // Optional none: "The check failed"
    // Optional filled: "The check succeeded, and resulted in this typed expr"
    val argCheckingCache: List<EqualityCache<TypeDef, Optional<TypedExpr>>> = List(arguments.size) { EqualityCache() }

    val triedMethods: ArrayList<MethodDef> = ArrayList()

    // Filter out methods that don't match, and return the ones that do.
    val applicableMethods = methodsToCheck.mapNotNull { method ->
        // Wrong name:
        if (method.name != methodName) { return@mapNotNull null }

        var method = method // Enable reassignment

        // Method is generic
        if (method is MethodDef.GenericMethodDef<*>) {
            // Call site supplies explicit args
            if (genericArguments.isNotEmpty()) {
                // Call site specified explicit args.
                // Wrong number of generics:
                if (method.numGenerics != genericArguments.size) { return@mapNotNull null }
                // Correct # of generics, and explicit. Specialize the method.
                method = method.getSpecialization(genericArguments)
            } else {
                // Call site did not specify explicit args, so we'll try to infer
                TODO() // Type inference for generic args
            }
        }

        // Wrong argument count:
        if (method.paramTypes.size != arguments.size) { return@mapNotNull null }

        // If we have an expected result,
        // Wrong return type:
        if (expectedResult != null && !method.returnType.isSubtype(expectedResult)) { return@mapNotNull null }

        // Final filter: check that all arguments match the expected type.
        if (!arguments.allIndexed { index, arg ->
            // Get information
            val cacheForThisArg = argCheckingCache[index]
            val expectedArgType = method.paramTypes[index]
            // Get the result of the check, either from the cache or by computing it
            val checkResult = cacheForThisArg.get(expectedArgType) {
                try {
                    val checked = checkExpr(arg, expectedArgType, scope, typeCache, returnType, currentType, currentTypeGenerics, currentMethodGenerics).expr
                    Optional.of(checked)
                } catch (ex: TypeCheckingException) {
                    // If it failed, then add this method to the tried methods set and quit
                    triedMethods.add(method)
                    Optional.empty()
                }
            }
            // If the result is present, then the argument was successful
            checkResult.isPresent
        }) { return@mapNotNull null }

        // If we passed everything, output the successful method
        return@mapNotNull method
    }

    // Return everything
    return ApplicableMethodsResult(
        applicableMethods,
        applicableMethods.map {
            it.paramTypes.mapIndexed { index, typeDef ->
                argCheckingCache[index].getOrThrow(typeDef).get()
        }},
        triedMethods
    )
}

/**
 * Given a collection of methods, try to choose the "best" method in
 * the collection. This generally means the "most specific".
 * If no best method can be chosen (either because of a tie, or
 * because there are no methods at all) then an error is thrown.
 */
private fun tryChooseBestMethod(
    // Result of calling applicableMethods()
    applicableResult: ApplicableMethodsResult,
    // The location of the call, for emitting error messages
    loc: Loc,

    // Auxiliary info used to generate better error messages.
    methodName: String,
    arguments: List<ResolvedExpr>,
    expectedResult: TypeDef?,
    scope: ConsMap<String, VariableBinding>,
    typeCache: TypingCache,
    returnType: TypeDef?,
    currentType: TypeDef?,
    currentTypeGenerics: List<TypeDef>,
    currentMethodGenerics: List<TypeDef>
): BestMethodData {
    // If there's only 1 method, just return it.
    if (applicableResult.applicableMethods.size == 1)
        return BestMethodData(applicableResult.applicableMethods[0], applicableResult.typeCheckedArguments[0])
    // If there are multiple potential methods, try choosing a most specific one.
    if (applicableResult.applicableMethods.size > 1) {
        return getMostSpecific(applicableResult)
            ?: // Could not find most specific, error.
            throw TooManyMethodsException(applicableResult.applicableMethods, loc)
    }
    // Otherwise, there are no applicable methods, so error with that.
    // Attempt to infer the types of the arguments, to include in the error message.
    val inferredArguments = arguments.map { try {
        inferExpr(it, scope, typeCache, returnType, currentType, currentTypeGenerics, currentMethodGenerics).expr.type
    } catch (e: CompilationException) {
        null
    }}
    throw NoApplicableMethodException(methodName, inferredArguments, expectedResult, applicableResult.triedMethods, loc)
}

/**
 * Get the most specific method out of a given list.
 * If there is no most specific one, return null.
 */
private fun getMostSpecific(applicable: ApplicableMethodsResult): BestMethodData? {
    val methods = applicable.applicableMethods
    val sortedIndices = MutableList(methods.size){it}
    sortedIndices.insertionSort { a, b -> compareMethods(methods[a], methods[b]) }
    val first = sortedIndices[0]
    val second = sortedIndices[1]
    if (compareMethods(methods[first], methods[second]) == 0)
        return null // If the first 2 are equally specific, return null
    // Otherwise, the first is most specific.
    return BestMethodData(methods[first], applicable.typeCheckedArguments[first])
}

/**
 * Comparator to decide which of the methods is more specific.
 * If A is more specific than B, output a negative number.
 */
private fun compareMethods(a: MethodDef, b: MethodDef): Int {
    // Return -1 if t1 is more specific than t2, etc.
    fun compareTypes(t1: TypeDef, t2: TypeDef) =
        if (t1.isSubtype(t2)) {
            if (t2.isSubtype(t1))
                0
            else
                -1
        } else {
            if (t2.isSubtype(t1))
                0
            else
                1
        }
    // Compare each arg, also negation of the return types
    val comparedArgs = a.paramTypes.asSequence().zip(b.paramTypes.asSequence()).map {
        compareTypes(it.first, it.second)
    } + -compareTypes(a.returnType, b.returnType)
    val hasM1 = -1 in comparedArgs
    val has1 = 1 in comparedArgs
    // If there's some both ways, then neither is more specific
    if (hasM1 && has1) return 0
    if (hasM1) return -1
    if (has1) return 1
    return 0
}


class NoApplicableMethodException(methodName: String, argTypes: List<TypeDef?>, returnType: TypeDef?, tried: List<MethodDef>, loc: Loc): TypeCheckingException(
    "Unable to find valid overload for method call\n  ${
        overloadString(methodName, argTypes, returnType)
    }${
        if (tried.isNotEmpty()) "\nTried:\n${
            tried.joinToString(separator = "") { "- " + overloadString(it.name, it.paramTypes, it.returnType) + "\n" }
        }"
        else "\n"
    }", loc
)
class TooManyMethodsException(potential: List<MethodDef>, loc: Loc): TypeCheckingException(
    "Too many valid overloads for method call; cannot choose between:\n ${
        potential.joinToString(separator = "") { "- " + overloadString(it.name, it.paramTypes, it.returnType) + "\n" }
    }",
    loc
)

private fun overloadString(name: String, argTypes: List<TypeDef?>, returnType: TypeDef?): String {
    return "$name(${
        argTypes.joinToString(separator = ", ") { it?.name ?: "<unknown type>" }
    }) -> ${returnType?.name ?: "<Anything>"}"
}