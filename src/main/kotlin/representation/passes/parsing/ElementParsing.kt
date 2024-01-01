package representation.passes.parsing

import representation.passes.lexing.Lexer
import representation.passes.lexing.TokenType
import errors.ParsingException
import representation.asts.parsed.ParsedElement
import representation.asts.parsed.ParsedPattern

/**
 * Element parsing !
 */

fun parseElement(lexer: Lexer, typeGenerics: List<String>): ParsedElement {
    return when {
        lexer.consume(TokenType.PUB) -> when {
            lexer.consume(TokenType.CLASS) -> parseClass(lexer, true)
            else -> throw ParsingException("Expected type definition after \"pub\"", lexer.last().loc)
        }
        lexer.consume(TokenType.CLASS) -> parseClass(lexer, false)
        else -> parseExpr(lexer, typeGenerics)
    }
}

// Helper functions:

// Params are a list of patterns separated by commas, inside parentheses
fun parseParams(lexer: Lexer, typeGenerics: List<String>): List<ParsedPattern> {

    // Helper local function to check if a pattern is explicitly typed,
    // which it must be for function parameters.
    fun isExplicitlyTyped(pattern: ParsedPattern): Boolean = when (pattern) {
        // Binding: explicitly typed if it has a type annotation
        is ParsedPattern.BindingPattern -> pattern.typeAnnotation != null
    }

    lexer.expect(TokenType.LEFT_PAREN, "to begin params list")
    return commaSeparated(lexer, TokenType.RIGHT_PAREN) {
        val pat = parsePattern(it, typeGenerics)
        if (!isExplicitlyTyped(pat))
            throw ParsingException("Function parameters must be explicitly typed", pat.loc)
        pat
    }

}