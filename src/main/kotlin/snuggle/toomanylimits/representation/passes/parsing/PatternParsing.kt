package snuggle.toomanylimits.representation.passes.parsing

import snuggle.toomanylimits.errors.ParsingException
import snuggle.toomanylimits.representation.asts.parsed.ParsedFalliblePattern
import snuggle.toomanylimits.representation.asts.parsed.ParsedInfalliblePattern
import snuggle.toomanylimits.representation.asts.parsed.ParsedType
import snuggle.toomanylimits.representation.passes.lexing.Lexer
import snuggle.toomanylimits.representation.passes.lexing.TokenType

// Helper local function to check if a pattern is explicitly typed,
// which it must be for function parameters.
fun isExplicitlyTyped(pattern: ParsedInfalliblePattern): Boolean = when (pattern) {
    // Binding: explicitly typed if it has a type annotation
    is ParsedInfalliblePattern.Empty -> pattern.typeAnnotation != null
    is ParsedInfalliblePattern.Binding -> pattern.typeAnnotation != null
    is ParsedInfalliblePattern.Tuple -> pattern.elements.all(::isExplicitlyTyped)
}

fun parseInfalliblePattern(lexer: Lexer, typeGenerics: List<String>, methodGenerics: List<String>): ParsedInfalliblePattern = when  {
    lexer.consume(TokenType.LEFT_PAREN) -> {
        val loc = lexer.last().loc
        val elems = commaSeparated(lexer, TokenType.RIGHT_PAREN) { parseInfalliblePattern(it, typeGenerics, methodGenerics) }
        ParsedInfalliblePattern.Tuple(loc.merge(lexer.last().loc), elems)
    }
    lexer.consume(TokenType.UNDERSCORE) -> {
        val loc = lexer.last().loc
        val annotation = if (lexer.consume(TokenType.COLON)) parseType(lexer, typeGenerics, methodGenerics) else null
        ParsedInfalliblePattern.Empty(loc, annotation)
    }
    else -> {
        val isMut = lexer.consume(TokenType.MUT)
        val ident = lexer.expect(TokenType.IDENTIFIER)
        val annotation = if (lexer.consume(TokenType.COLON)) parseType(lexer, typeGenerics, methodGenerics) else null
        ParsedInfalliblePattern.Binding(ident.loc, ident.string(), isMut, annotation)
    }
}

fun parseFalliblePattern(lexer: Lexer, typeGenerics: List<String>, methodGenerics: List<String>): ParsedFalliblePattern = when {
    lexer.consume(TokenType.LEFT_PAREN) -> {
        val loc = lexer.last().loc
        val elems = commaSeparated(lexer, TokenType.RIGHT_PAREN) { parseFalliblePattern(it, typeGenerics, methodGenerics) }
        ParsedFalliblePattern.Tuple(loc.merge(lexer.last().loc), elems)
    }
    lexer.consume(TokenType.LITERAL, TokenType.STRING_LITERAL) -> {
        ParsedFalliblePattern.LiteralPattern(lexer.last().loc, lexer.last().value!!)
    }
    lexer.consume(TokenType.MUT) -> {
        val loc = lexer.last().loc
        val name = lexer.expect(TokenType.IDENTIFIER, "after \"mut\"").string()
        lexer.expect(TokenType.COLON, "after \"mut $name\"")
        val type = parseType(lexer, typeGenerics, methodGenerics)
        ParsedFalliblePattern.IsType(loc.merge(lexer.last().loc), true, name, type)
    }
    else -> {
        val type = parseType(lexer, typeGenerics, methodGenerics)
        if (lexer.consume(TokenType.COLON)) {
            // Interpret said type as being a name, and now parse the *real* type
            val varName = when {
                type is ParsedType.TypeGeneric -> type.name
                type is ParsedType.MethodGeneric -> type.name
                type is ParsedType.Basic && type.generics.isEmpty() -> type.base
                else -> throw ParsingException("Expected identifier before \":\", got $type", type.loc)
            }
            val realType = parseType(lexer, typeGenerics, methodGenerics)
            ParsedFalliblePattern.IsType(type.loc.merge(realType.loc), false, varName, realType)
        } else {
            ParsedFalliblePattern.IsType(type.loc, false, null, type)
        }
    }
}