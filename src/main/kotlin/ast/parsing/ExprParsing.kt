package ast.passes.parsing

import ast.ParsedElement.*
import errors.ParsingException
import ast.lexing.Lexer
import ast.lexing.TokenType
import util.ConsList

/**
 * Expression parsing !
 */

fun parseExpr(lexer: Lexer): ParsedExpr {
    return parseBinary(lexer, 0)
}

private fun parseBinary(lexer: Lexer, precedence: Int): ParsedExpr {
    if (precedence >= TOKS_BY_PRECEDENCE.size)
        return parseUnary(lexer)
    var lhs = parseBinary(lexer, precedence + 1)
    while (lexer.consume(*TOKS_BY_PRECEDENCE[precedence])) {
        val tok = lexer.last()
        val rhsPrecedence = if (isRightAssociative(tok.type)) precedence else precedence + 1
        val methodName = when(tok.type) {
            TokenType.PLUS -> "add"
            TokenType.MINUS -> "sub"
            else -> throw IllegalStateException("Unexpected binary operator ${tok.type}. Bug in compiler, please report")
        }
        val rhs = parseBinary(lexer, rhsPrecedence)
        val loc = lhs.loc.merge(rhs.loc)
        lhs = ParsedExpr.MethodCall(loc, lhs, methodName, ConsList.of(rhs))
    }
    return lhs
}

private fun parseUnary(lexer: Lexer): ParsedExpr {
    if (lexer.consume(TokenType.MINUS)) {
        val tok = lexer.last()
        val methodName = when(tok.type) {
            TokenType.MINUS -> "neg"
            else -> throw IllegalStateException("Unexpected unary operator ${tok.type}. Bug in compiler, please report")
        }
        val operand = parseUnary(lexer)
        val loc = tok.loc.merge(operand.loc)
        return ParsedExpr.MethodCall(loc, operand, methodName, ConsList.of())
    }
    return parseUnit(lexer)
}

private fun parseUnit(lexer: Lexer): ParsedExpr {
    return when(lexer.take()?.type) {
        null -> throw ParsingException(expected = "Expression", found = "End of file", loc = lexer.curLoc)

        TokenType.LITERAL -> ParsedExpr.Literal(lexer.last().loc, lexer.last().value ?: println(lexer.last().type))
        TokenType.IDENTIFIER -> ParsedExpr.Variable(lexer.last().loc, lexer.last().string())

        TokenType.LET -> parseDeclaration(lexer)


        else -> throw ParsingException(expected = "Expression", found = lexer.last().type.toString(), loc = lexer.last().loc)
    }
}

private fun parseDeclaration(lexer: Lexer): ParsedExpr {
    val let = lexer.last()
    val pat = parsePattern(lexer)
    lexer.expect(TokenType.EQUALS)
    val initializer = parseExpr(lexer)
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
    arrayOf(TokenType.PLUS, TokenType.MINUS)
)

private fun isRightAssociative(op: TokenType): Boolean {
    return false; // the only one is exponent operator, currently
}
