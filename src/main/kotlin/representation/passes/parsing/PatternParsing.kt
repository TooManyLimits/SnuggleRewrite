package representation.passes.parsing

import representation.asts.parsed.ParsedInfalliblePattern
import representation.passes.lexing.Lexer
import representation.passes.lexing.TokenType

fun parsePattern(lexer: Lexer, typeGenerics: List<String>, methodGenerics: List<String>): ParsedInfalliblePattern {
    return if (lexer.consume(TokenType.LEFT_PAREN)) {
        val loc = lexer.last().loc
        val elems = commaSeparated(lexer, TokenType.RIGHT_PAREN) { parsePattern(it, typeGenerics, methodGenerics) }
        ParsedInfalliblePattern.Tuple(loc.merge(lexer.last().loc), elems)
    } else if (lexer.consume(TokenType.UNDERSCORE)) {
        val loc = lexer.last().loc
        val annotation = if (lexer.consume(TokenType.COLON)) parseType(lexer, typeGenerics, methodGenerics) else null
        ParsedInfalliblePattern.Empty(loc, annotation)
    } else {
        val isMut = lexer.consume(TokenType.MUT)
        val ident = lexer.expect(TokenType.IDENTIFIER)
        val annotation = if (lexer.consume(TokenType.COLON)) parseType(lexer, typeGenerics, methodGenerics) else null
        ParsedInfalliblePattern.Binding(ident.loc, ident.string(), isMut, annotation)
    }
}

// Helper local function to check if a pattern is explicitly typed,
// which it must be for function parameters.
fun isExplicitlyTyped(pattern: ParsedInfalliblePattern): Boolean = when (pattern) {
    // Binding: explicitly typed if it has a type annotation
    is ParsedInfalliblePattern.Empty -> pattern.typeAnnotation != null
    is ParsedInfalliblePattern.Binding -> pattern.typeAnnotation != null
    is ParsedInfalliblePattern.Tuple -> pattern.elements.all(::isExplicitlyTyped)
}