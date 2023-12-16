package ast.parsing

import ast.lexing.Lexer
import ast.lexing.TokenType

fun parsePattern(lexer: Lexer): ParsedPattern {


    val ident = lexer.expect(TokenType.IDENTIFIER)
    val annotation = if (lexer.consume(TokenType.COLON)) parseType(lexer) else null

    return ParsedPattern.Binding(ident.loc, ident.string(), false, annotation)
}