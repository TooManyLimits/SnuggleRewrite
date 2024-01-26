package representation.passes.parsing

import errors.ParsingException
import representation.asts.parsed.ParsedElement
import representation.asts.parsed.ParsedPattern
import representation.passes.lexing.Lexer
import representation.passes.lexing.TokenType

/**
 * Element parsing !
 */

fun parseElement(lexer: Lexer, typeGenerics: List<String>, methodGenerics: List<String>): ParsedElement {
    return when {
        lexer.consume(TokenType.PUB) -> when {
            lexer.consume(TokenType.CLASS) -> parseClass(lexer, true)
            lexer.consume(TokenType.STRUCT) -> parseStruct(lexer, true)
            lexer.consume(TokenType.IMPL) -> parseImpl(lexer, true)
            else -> throw ParsingException("Expected type definition after \"pub\"", lexer.last().loc)
        }
        lexer.consume(TokenType.CLASS) -> parseClass(lexer, false)
        lexer.consume(TokenType.STRUCT) -> parseStruct(lexer, false)
        lexer.consume(TokenType.IMPL) -> parseImpl(lexer, false)
        else -> parseExpr(lexer, typeGenerics, methodGenerics)
    }
}

// Helper functions:

// Params are a list of patterns separated by commas, inside parentheses
fun parseParams(lexer: Lexer, typeGenerics: List<String>, methodGenerics: List<String>): List<ParsedPattern> {
    lexer.expect(TokenType.LEFT_PAREN, "to begin params list")
    return commaSeparated(lexer, TokenType.RIGHT_PAREN) {
        val pat = parsePattern(it, typeGenerics, methodGenerics)
        if (!isExplicitlyTyped(pat))
            throw ParsingException("Function parameters must be explicitly typed", pat.loc)
        pat
    }
}