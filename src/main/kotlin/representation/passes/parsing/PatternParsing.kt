package representation.passes.parsing

import representation.asts.parsed.ParsedPattern
import representation.passes.lexing.Lexer
import representation.passes.lexing.TokenType

fun parsePattern(lexer: Lexer, typeGenerics: List<String>): ParsedPattern {

    val isMut = lexer.consume(TokenType.MUT)
    val ident = lexer.expect(TokenType.IDENTIFIER)
    val annotation = if (lexer.consume(TokenType.COLON)) parseType(lexer, typeGenerics) else null

    return ParsedPattern.BindingPattern(ident.loc, ident.string(), isMut, annotation)
}