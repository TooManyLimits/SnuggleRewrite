package ast.parsing

import ast.lexing.Lexer
import ast.lexing.TokenType
import util.ConsList.Companion.nil

internal fun parseType(lexer: Lexer, extraInfo: String? = null): ParsedType {

//    if (lexer.consume(LEFT_PAREN)) {
//
//    }

    val ident = lexer.expect(TokenType.IDENTIFIER, extraInfo)
    return ParsedType.Basic(ident.loc, ident.string(), listOf())

}