package errors

import representation.passes.lexing.Loc

open class CompilationException(message: String, loc: Loc): Exception("$message at $loc")


class ParsingException(message: String, loc: Loc): CompilationException(message, loc) {
    constructor(expected: String, found: String, loc: Loc): this("Expected $expected, found $found", loc)
}