package errors

import builtins.IntType
import representation.passes.lexing.Loc
import representation.asts.typed.MethodDef
import representation.asts.typed.TypeDef
import java.math.BigInteger

open class CompilationException(message: String, loc: Loc): Exception("$message at $loc")

class LexingException(message: String, loc: Loc)
    : CompilationException(message, loc)
class UnknownTokenException(unrecognized: String, loc: Loc)
    : CompilationException("Unrecognized ${if (unrecognized.length == 1) "character" else "token"} \"$unrecognized\"", loc)
class ParsingException(message: String, loc: Loc): CompilationException(message, loc) {
    constructor(expected: String, found: String, loc: Loc): this("Expected $expected, found $found", loc)
}
class ResolutionException(filePath: String, loc: Loc)
    : CompilationException("No file with name \"$filePath\" was provided", loc)
class UnknownTypeException(typeName: String, loc: Loc)
    : CompilationException("No type with name \"$typeName\" is in the current scope", loc)
class TypeCheckingException(expectedType: TypeDef, actualType: TypeDef, situation: String, loc: Loc)
    : CompilationException("Expected $situation to have type ${expectedType.name}, but found ${actualType.name}", loc)
class NumberRangeException(expectedType: IntType, value: BigInteger, loc: Loc)
    : CompilationException("Expected ${expectedType.name}, but literal $value is out of range " +
        "(${expectedType.minValue} to ${expectedType.maxValue}).", loc)
class InferenceException(message: String, loc: Loc): CompilationException(message, loc)
class NoSuchVariableException(varName: String, loc: Loc): CompilationException("No variable with name \"$varName\" is in the current scope", loc)
class NoApplicableMethodException(methodName: String, argTypes: List<TypeDef?>, returnType: TypeDef?, tried: List<MethodDef>, loc: Loc): CompilationException(
    "Unable to find valid overload for method call\n  ${
        overloadString(methodName, argTypes, returnType)
    }${
        if (tried.isNotEmpty()) "\nTried:\n${
            tried.joinToString { "- " + overloadString(it.name, it.argTypes, it.returnType) + "\n" }
        }"
        else "\n"
    }", loc
)
class TooManyMethodsException(potential: List<MethodDef>, loc: Loc): CompilationException(
    "Too many valid overloads for method call; cannot choose between:\n ${
        potential.joinToString { "- " + overloadString(it.name, it.argTypes, it.returnType) + "\n" }
    }",
    loc
)

private fun overloadString(name: String, argTypes: List<TypeDef?>, returnType: TypeDef?): String {
    return "$name(${
        argTypes.joinToString(separator = ", ") { it?.name ?: "<unknown type>" }
    }) -> ${returnType?.name ?: "<Anything>"}"
}