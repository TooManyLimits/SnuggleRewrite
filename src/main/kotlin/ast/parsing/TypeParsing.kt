package ast.passes.parsing

import ast.ParsedType
import ast.lexing.Lexer
import ast.lexing.TokenType
import util.ConsList.Companion.nil

internal fun parseType(lexer: Lexer): ParsedType {

//    if (lexer.consume(LEFT_PAREN)) {
//
//    }

    val ident = lexer.expect(TokenType.IDENTIFIER)
    return ParsedType.Basic(ident.loc, ident.string(), nil())

}