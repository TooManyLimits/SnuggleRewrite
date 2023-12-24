package errors

import ast.lexing.Loc
import ast.typing.MethodDef
import ast.typing.TypeDef

open class CompilationException(message: String, loc: Loc): Exception("$message at $loc")

class LexingException(unrecognized: String, loc: Loc): CompilationException("Unrecognized ${if (unrecognized.isEmpty()) "character" else "token"} \"$unrecognized\"", loc)
class ParsingException(string: String, loc: Loc): CompilationException(string, loc) {
    constructor(expected: String, found: String, loc: Loc): this("Expected $expected, found $found", loc)
}
class ResolutionException(filePath: String, loc: Loc): CompilationException("No file with name \"$filePath\" was provided", loc)
class UnknownTypeException(typeName: String, loc: Loc): CompilationException("No type with name \"$typeName\" is in the current scope", loc)
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