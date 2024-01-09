package representation.passes.parsing

import representation.asts.parsed.ParsedType
import representation.passes.lexing.Lexer
import representation.passes.lexing.TokenType

internal fun parseType(lexer: Lexer, typeGenerics: List<String>, extraInfo: String = ""): ParsedType {

    if (lexer.consume(TokenType.LEFT_PAREN)) {
        val loc = lexer.last().loc
        // This is a tuple or func
        val elements = commaSeparated(lexer, TokenType.RIGHT_PAREN) { parseType(it, typeGenerics, extraInfo = "for tuple/func type") }
        // TODO: Check for arrow, func type
        return ParsedType.Tuple(loc.merge(lexer.last().loc), elements)
    }

    // Grab identifier
    val ident = lexer.expect(TokenType.IDENTIFIER, "for type${if (extraInfo != "") " $extraInfo" else ""}")

    // Check if it's a known generic:
    val typeGenericIndex = typeGenerics.indexOf(ident.string())
    if (typeGenericIndex != -1)
        return ParsedType.TypeGeneric(ident.loc, ident.string(), typeGenericIndex)

    // Interpret as a basic type. Parse generics, then return it.
    val basicGenerics = if (lexer.consume(TokenType.LESS))
        commaSeparated(lexer, TokenType.GREATER) { parseType(it, typeGenerics, "for generic args") }
    else listOf()
    return ParsedType.Basic(ident.loc.merge(lexer.last().loc), ident.string(), basicGenerics)
}