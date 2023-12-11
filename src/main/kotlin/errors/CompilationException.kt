package errors

import ast.lexing.Loc

open class CompilationException(message: String, loc: Loc): Exception("$message at $loc")

class LexingException(unrecognized: String, loc: Loc): CompilationException("Unrecognized ${if (unrecognized.isEmpty()) "character" else "token"} \"$unrecognized\"", loc)
class ParsingException(expected: String, found: String, loc: Loc): CompilationException("Expected $expected, found $found", loc)