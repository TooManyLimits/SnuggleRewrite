package errors

import representation.passes.lexing.Loc

open class CompilationException(message: String, loc: Loc): Exception("$message at $loc")

class ParsingException(message: String, loc: Loc): CompilationException(message, loc) {
    constructor(expected: String, found: String, loc: Loc): this("Expected $expected, found $found", loc)
}

// An exception specifically thrown because of a type checking issue, not because the code
// is completely incorrectly formatted. If there might not have been an error if the expected
// type were different in the context, then throw this exception (or a subclass). However, if
// the error is unavoidable no matter what the expectedType is, throw a different CompilationException.
open class TypeCheckingException(message: String, loc: Loc): CompilationException(message, loc)