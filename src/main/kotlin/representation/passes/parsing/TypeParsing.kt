package representation.passes.parsing

import representation.asts.parsed.ParsedType
import representation.passes.lexing.Lexer
import representation.passes.lexing.TokenType

fun parseType(lexer: Lexer, typeGenerics: List<String>, methodGenerics: List<String>, extraInfo: String = ""): ParsedType
    = addModifiers(parseBaseType(lexer, typeGenerics, methodGenerics, extraInfo), lexer, typeGenerics, methodGenerics)

private fun parseBaseType(lexer: Lexer, typeGenerics: List<String>, methodGenerics: List<String>, extraInfo: String = ""): ParsedType {

    if (lexer.consume(TokenType.LEFT_PAREN)) {
        val loc = lexer.last().loc
        // This is a tuple
        val elements = commaSeparated(lexer, TokenType.RIGHT_PAREN) { parseType(it, typeGenerics, methodGenerics, extraInfo = "for tuple/func type") }
        return ParsedType.Tuple(loc.merge(lexer.last().loc), elements)
    }

    // Grab identifier
    val ident = lexer.expect(TokenType.IDENTIFIER, "for type${if (extraInfo != "") " $extraInfo" else ""}")

    // Check if it's a known generic:
    val methodGenericIndex = methodGenerics.indexOf(ident.string())
    if (methodGenericIndex != -1)
        return ParsedType.MethodGeneric(ident.loc, ident.string(), methodGenericIndex)
    val typeGenericIndex = typeGenerics.indexOf(ident.string())
    if (typeGenericIndex != -1)
        return ParsedType.TypeGeneric(ident.loc, ident.string(), typeGenericIndex)

    // Interpret as a basic type. Parse generics, then return it.
    val basicGenerics = if (lexer.consume(TokenType.LESS))
        commaSeparated(lexer, TokenType.GREATER) { parseType(it, typeGenerics, methodGenerics, "for generic args") }
    else listOf()
    return ParsedType.Basic(ident.loc.merge(lexer.last().loc), ident.string(), basicGenerics)
}

private fun addModifiers(parsedType: ParsedType, lexer: Lexer, typeGenerics: List<String>, methodGenerics: List<String>): ParsedType {
    var parsedType = parsedType
    while (lexer.consume(TokenType.QUESTION_MARK, TokenType.LEFT_SQUARE)) {
        parsedType = if (lexer.last().type == TokenType.QUESTION_MARK) {
            ParsedType.Basic(parsedType.loc.merge(lexer.last().loc), "Option", listOf(parsedType))
        } else {
            lexer.expect(TokenType.RIGHT_SQUARE, "for array type")
            ParsedType.Basic(parsedType.loc.merge(lexer.last().loc), "Array", listOf(parsedType))
        }
    }

    return if (lexer.consume(TokenType.ARROW)) {
        val arrowLoc = lexer.last().loc
        if (parsedType is ParsedType.Tuple) {
            ParsedType.Func(arrowLoc, parsedType.elementTypes, parseType(lexer, typeGenerics, methodGenerics))
        } else {
            ParsedType.Func(arrowLoc, listOf(parsedType), parseType(lexer, typeGenerics, methodGenerics))
        }
    } else {
        parsedType
    }
}