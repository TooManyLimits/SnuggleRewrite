package representation.passes.parsing

import representation.asts.parsed.ParsedType
import representation.passes.lexing.Lexer
import representation.passes.lexing.TokenType

internal fun parseType(lexer: Lexer, typeGenerics: List<String>, extraInfo: String? = null): ParsedType {

//    if (lexer.consume(LEFT_PAREN)) {
//
//    }

    // Grab identifier
    val ident = lexer.expect(TokenType.IDENTIFIER, extraInfo)

    // Check if it's a known generic:
    val typeGenericIndex = typeGenerics.indexOf(ident.string())
    if (typeGenericIndex != -1)
        return ParsedType.TypeGeneric(ident.loc, ident.string(), typeGenericIndex)

    // Interpret as a basic type. Parse generics, then return it.
    val basicGenerics = if (lexer.consume(TokenType.LESS))
        commaSeparated(lexer, TokenType.GREATER) { parseType(it, typeGenerics, "for generic args") }
    else listOf()
    return ParsedType.Basic(ident.loc, ident.string(), basicGenerics)
}