package representation.passes.parsing

import representation.asts.parsed.ParsedElement.*
import representation.passes.lexing.Lexer
import representation.passes.lexing.TokenType
import errors.ParsingException

/**
 * Expression parsing !
 */

fun parseExpr(lexer: Lexer, typeGenerics: List<String>): ParsedExpr {
    return parseBinary(lexer, typeGenerics, 0)
}

private fun parseBinary(lexer: Lexer, typeGenerics: List<String>, precedence: Int): ParsedExpr {
    if (precedence >= TOKS_BY_PRECEDENCE.size)
        return parseUnary(lexer, typeGenerics)
    var lhs = parseBinary(lexer, typeGenerics, precedence + 1)
    while (lexer.consume(*TOKS_BY_PRECEDENCE[precedence])) {
        val tok = lexer.last()
        val rhsPrecedence = if (isRightAssociative(tok.type)) precedence else precedence + 1
        val methodName = when(tok.type) {
            TokenType.PLUS -> "add"
            TokenType.MINUS -> "sub"
            TokenType.STAR -> "mul"
            TokenType.SLASH -> "div"
            else -> throw IllegalStateException("Unexpected binary operator ${tok.type}. Bug in compiler, please report")
        }
        val rhs = parseBinary(lexer, typeGenerics, rhsPrecedence)
        val loc = lhs.loc.merge(rhs.loc)
        lhs = ParsedExpr.MethodCall(loc, lhs, methodName, listOf(rhs))
    }
    return lhs
}

private fun parseUnary(lexer: Lexer, typeGenerics: List<String>): ParsedExpr {
    if (lexer.consume(TokenType.MINUS)) {
        val tok = lexer.last()
        val methodName = when(tok.type) {
            TokenType.MINUS -> "neg"
            else -> throw IllegalStateException("Unexpected unary operator ${tok.type}. Bug in compiler, please report")
        }
        val operand = parseUnary(lexer, typeGenerics)
        val loc = tok.loc.merge(operand.loc)
        return ParsedExpr.MethodCall(loc, operand, methodName, listOf())
    }
    return parseFieldAccessOrMethodCall(lexer, typeGenerics)
}

private fun parseFieldAccessOrMethodCall(lexer: Lexer, typeGenerics: List<String>): ParsedExpr {
    var expr = parseUnit(lexer, typeGenerics)
    while (lexer.consume(TokenType.DOT, TokenType.LEFT_PAREN)) when (lexer.last().type) {
        TokenType.DOT -> {
            val name = lexer.expect(TokenType.IDENTIFIER, "after DOT")
            if (lexer.consume(TokenType.LEFT_PAREN)) {
                val args = commaSeparated(lexer, TokenType.RIGHT_PAREN) { parseExpr(it, typeGenerics) }
                expr = ParsedExpr.MethodCall(name.loc, expr, name.string(), args)
            } else {
                TODO() // Field
            }
        }
        //invoke() overload
        TokenType.LEFT_PAREN -> {
            val loc = lexer.last().loc
            val args = commaSeparated(lexer, TokenType.RIGHT_PAREN) { parseExpr(it, typeGenerics) }
            expr = ParsedExpr.MethodCall(loc, expr, "invoke", args)
        }
        else -> throw IllegalStateException()
    }
    return expr
}

private fun parseUnit(lexer: Lexer, typeGenerics: List<String>): ParsedExpr {
    return when(lexer.take()?.type) {
        null -> throw ParsingException(expected = "Expression", found = "End of file", loc = lexer.curLoc)

        TokenType.IMPORT -> parseImport(lexer)

        TokenType.LITERAL, TokenType.STRING_LITERAL -> ParsedExpr.Literal(lexer.last().loc, lexer.last().value!!)
        TokenType.IDENTIFIER -> ParsedExpr.Variable(lexer.last().loc, lexer.last().string())

        TokenType.NEW -> parseConstructor(lexer, typeGenerics)

        TokenType.LET -> parseDeclaration(lexer, typeGenerics)

        else -> throw ParsingException(expected = "Expression", found = lexer.last().type.toString(), loc = lexer.last().loc)
    }
}

private fun parseImport(lexer: Lexer): ParsedExpr {
    val loc = lexer.last().loc
    val lit = lexer.expect(TokenType.STRING_LITERAL)
    return ParsedExpr.Import(loc, lit.string())
}

private fun parseConstructor(lexer: Lexer, typeGenerics: List<String>): ParsedExpr {
    val type = parseType(lexer, typeGenerics, "for constructor")
    lexer.expect(TokenType.LEFT_PAREN)
    val args = commaSeparated(lexer, TokenType.RIGHT_PAREN) { parseExpr(it, typeGenerics) }
    return ParsedExpr.ConstructorCall(type.loc, type, args)
}

private fun parseDeclaration(lexer: Lexer, typeGenerics: List<String>): ParsedExpr {
    val let = lexer.last()
    val pat = parsePattern(lexer, typeGenerics)
    lexer.expect(TokenType.EQUALS)
    val initializer = parseExpr(lexer, typeGenerics)
    return ParsedExpr.Declaration(let.loc, pat, initializer)
}

/**
 * Binary operator precedence util
 */

private val TOKS_BY_PRECEDENCE: Array<Array<TokenType>> = arrayOf(
    arrayOf(),
    arrayOf(),
    arrayOf(),
    arrayOf(),
    arrayOf(TokenType.PLUS, TokenType.MINUS),
    arrayOf(TokenType.STAR, TokenType.SLASH)
)

private fun isRightAssociative(op: TokenType): Boolean {
    return false; // the only one is exponent operator, currently
}
