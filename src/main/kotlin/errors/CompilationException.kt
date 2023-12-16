package errors

import ast.lexing.Loc

open class CompilationException(message: String, loc: Loc): Exception("$message at $loc")

class LexingException(unrecognized: String, loc: Loc): CompilationException("Unrecognized ${if (unrecognized.isEmpty()) "character" else "token"} \"$unrecognized\"", loc)
class ParsingException(expected: String, found: String, loc: Loc): CompilationException("Expected $expected, found $found", loc)
class ResolutionException(filePath: String, loc: Loc): CompilationException("No file with name \"$filePath\" was provided", loc)
class UnknownTypeException(typeName: String, loc: Loc): CompilationException("No type with name \"$typeName\" is in the current scope", loc)
class NoSuchVariableException(varName: String, loc: Loc): CompilationException("No variable with name \"$varName\" is in the current scope", loc)