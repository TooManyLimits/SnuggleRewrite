package representation.passes.parsing

import representation.asts.parsed.ParsedType
import representation.passes.lexing.Lexer
import representation.passes.lexing.TokenType

internal fun parseType(lexer: Lexer, extraInfo: String? = null): ParsedType {

//    if (lexer.consume(LEFT_PAREN)) {
//
//    }

    val ident = lexer.expect(TokenType.IDENTIFIER, extraInfo)
    return ParsedType.Basic(ident.loc, ident.string(), listOf())

}