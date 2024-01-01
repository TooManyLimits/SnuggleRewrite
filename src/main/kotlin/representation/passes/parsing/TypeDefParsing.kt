package representation.passes.parsing

import representation.asts.parsed.ParsedElement
import representation.asts.parsed.ParsedFieldDef
import representation.asts.parsed.ParsedMethodDef
import representation.asts.parsed.ParsedType
import representation.passes.lexing.Lexer
import representation.passes.lexing.Loc
import representation.passes.lexing.TokenType

// The "class" token was just consumed
fun parseClass(lexer: Lexer, isPub: Boolean): ParsedElement.ParsedTypeDef.Class {
    val classLoc = lexer.last().loc
    val className = lexer.expect(TokenType.IDENTIFIER, extraInfo = "after \"class\"").string()
    val typeGenerics = parseGenerics(lexer)
    val supertype = if (lexer.consume(TokenType.COLON))
        parseType(lexer, typeGenerics, extraInfo = "for superclass")
    else
        ParsedType.Basic(Loc.NEVER, "Object", listOf()) // Default superclass to Object if none is given
    lexer.expect(TokenType.LEFT_CURLY, "to begin class definition")
    val members = parseMembers(lexer, typeGenerics)
    return ParsedElement.ParsedTypeDef.Class(classLoc, isPub, className, typeGenerics.size, supertype, members.first, members.second)
}

private fun parseGenerics(lexer: Lexer): List<String> {
    if (lexer.consume(TokenType.LESS))
        return commaSeparated(lexer, TokenType.GREATER) { it.expect(TokenType.IDENTIFIER, "for generic").string() }
    return listOf()
}

// Parses members. The { was just consumed. Continues until finding a }.
private fun parseMembers(lexer: Lexer, typeGenerics: List<String>): Pair<List<ParsedFieldDef>, List<ParsedMethodDef>> {
    val fields = ArrayList<ParsedFieldDef>()
    val methods = ArrayList<ParsedMethodDef>()
    while (!lexer.consume(TokenType.RIGHT_CURLY)) {
        // Collect modifiers, pub and static
        val isPub = lexer.consume(TokenType.PUB)
        val isStatic = lexer.consume(TokenType.STATIC)

        if (lexer.consume(TokenType.FN)) {
            // This is a method definition. Parse its name, params, and return type...
            val methodName = lexer.expect(TokenType.IDENTIFIER, "for function name")
            val params = parseParams(lexer, typeGenerics)
            val returnType = if (lexer.consume(TokenType.COLON)) parseType(lexer, typeGenerics) else TODO()
            // Now parse its body:
            val body = parseExpr(lexer, typeGenerics)
            // And add it to the list.
            methods += ParsedMethodDef(methodName.loc, isPub, isStatic, methodName.string(), params, returnType, body)
        } else {
            // Otherwise, we must have field(s).
            // TODO: Parse fields
            TODO()
        }
    }
    return fields to methods
}

