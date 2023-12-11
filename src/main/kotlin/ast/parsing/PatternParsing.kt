package ast.parsing

import ast.lexing.Lexer
import ast.lexing.TokenType

//Currently, only parses a single identifier.
fun parsePattern(lexer: Lexer): ParsedPattern {

    val ident = lexer.expect(TokenType.IDENTIFIER)
    return ParsedPattern.Binding(ident.loc, ident.string(), false, null)
}