package representation.passes.parsing

import representation.asts.parsed.ParsedPattern
import representation.passes.lexing.Lexer
import representation.passes.lexing.TokenType

fun parsePattern(lexer: Lexer, typeGenerics: List<String>, methodGenerics: List<String>): ParsedPattern {
    return if (lexer.consume(TokenType.LEFT_PAREN)) {
        val loc = lexer.last().loc
        val elems = commaSeparated(lexer, TokenType.RIGHT_PAREN) { parsePattern(it, typeGenerics, methodGenerics) }
        ParsedPattern.TuplePattern(loc.merge(lexer.last().loc), elems)
    } else if (lexer.consume(TokenType.UNDERSCORE)) {
        val loc = lexer.last().loc
        val annotation = if (lexer.consume(TokenType.COLON)) parseType(lexer, typeGenerics, methodGenerics) else null
        ParsedPattern.EmptyPattern(loc, annotation)
    } else {
        val isMut = lexer.consume(TokenType.MUT)
        val ident = lexer.expect(TokenType.IDENTIFIER)
        val annotation = if (lexer.consume(TokenType.COLON)) parseType(lexer, typeGenerics, methodGenerics) else null
        ParsedPattern.BindingPattern(ident.loc, ident.string(), isMut, annotation)
    }
}