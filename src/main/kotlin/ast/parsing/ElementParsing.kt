package ast.parsing

import ast.lexing.Lexer
import ast.lexing.TokenType
import errors.ParsingException

/**
 * Element parsing !
 */

fun parseElement(lexer: Lexer): ParsedElement {
    return when {
        lexer.consume(TokenType.PUB) -> when {
            lexer.consume(TokenType.CLASS) -> parseClass(lexer, true)
            else -> throw ParsingException("Expected type definition after \"pub\"", lexer.last().loc)
        }
        lexer.consume(TokenType.CLASS) -> parseClass(lexer, false)
        else -> parseExpr(lexer)
    }
}

// Helper functions:

// Params are a list of patterns separated by commas, inside parentheses
fun parseParams(lexer: Lexer): List<ParsedPattern> {

    // Helper local function to check if a pattern is explicitly typed,
    // which it must be for function parameters.
    fun isExplicitlyTyped(pattern: ParsedPattern): Boolean = when (pattern) {
        // Binding: explicitly typed if it has a type annotation
        is ParsedPattern.BindingPattern -> pattern.typeAnnotation != null
    }

    lexer.expect(TokenType.LEFT_PAREN, "to begin params list")
    return commaSeparated(lexer, TokenType.RIGHT_PAREN) {
        val pat = parsePattern(it)
        if (!isExplicitlyTyped(pat))
            throw ParsingException("Function parameters must be explicitly typed", pat.loc)
        pat
    }

}