package ast.parsing

import ast.lexing.Lexer
import ast.lexing.TokenType

// The "class" token was just consumed
fun parseClass(lexer: Lexer, isPub: Boolean): ParsedElement.ParsedTypeDef.Class {
    val classLoc = lexer.last().loc
    val className = lexer.expect(TokenType.IDENTIFIER, extraInfo = "after \"class\"").string()
    val supertype = if (lexer.consume(TokenType.COLON)) parseType(lexer, extraInfo = "for superclass") else null
    lexer.expect(TokenType.LEFT_CURLY, "to begin class definition")
    val members = parseMembers(lexer)
    return ParsedElement.ParsedTypeDef.Class(classLoc, isPub, className, supertype, members.first, members.second)
}

// Parses members. The { was just consumed. Continues until finding a }.
fun parseMembers(lexer: Lexer): Pair<List<ParsedFieldDef>, List<ParsedMethodDef>> {
    val fields = ArrayList<ParsedFieldDef>()
    val methods = ArrayList<ParsedMethodDef>()
    while (!lexer.consume(TokenType.RIGHT_CURLY)) {
        // Collect modifiers, pub and static
        val isPub = lexer.consume(TokenType.PUB)
        val isStatic = lexer.consume(TokenType.STATIC)

        if (lexer.consume(TokenType.FN)) {
            // This is a method definition. Parse its name, params, and return type...
            val methodName = lexer.expect(TokenType.IDENTIFIER, "for function name")
            val params = parseParams(lexer)
            val returnType = if (lexer.consume(TokenType.COLON)) parseType(lexer) else throw RuntimeException("Unit not yet implemented")
            // Now parse its body:
            val body = parseExpr(lexer)
            // And add it to the list.
            methods += ParsedMethodDef(methodName.loc, isPub, isStatic, methodName.string(), params, returnType, body)
        } else {
            // Otherwise, we must have field(s).
            // TODO: Parse fields
            throw RuntimeException("Fields not yet implemented")
        }
    }
    return fields to methods
}

