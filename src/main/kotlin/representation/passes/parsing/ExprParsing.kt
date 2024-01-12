package representation.passes.parsing

import representation.asts.parsed.ParsedElement.*
import representation.passes.lexing.Lexer
import representation.passes.lexing.TokenType
import errors.ParsingException
import representation.asts.parsed.ParsedElement

/**
 * Expression parsing !
 */

fun parseExpr(lexer: Lexer, typeGenerics: List<String>, methodGenerics: List<String>): ParsedExpr {
    return parseBinary(lexer, typeGenerics, methodGenerics, 0)
}

private fun parseBinary(lexer: Lexer, typeGenerics: List<String>, methodGenerics: List<String>, precedence: Int): ParsedExpr {
    if (precedence >= TOKS_BY_PRECEDENCE.size)
        return parseUnary(lexer, typeGenerics, methodGenerics)
    var lhs = parseBinary(lexer, typeGenerics, methodGenerics, precedence + 1)
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
        val rhs = parseBinary(lexer, typeGenerics, methodGenerics, rhsPrecedence)
        val loc = lhs.loc.merge(rhs.loc)
        lhs = ParsedExpr.MethodCall(loc, lhs, methodName, listOf(), listOf(rhs))
    }
    return lhs
}

private fun parseUnary(lexer: Lexer, typeGenerics: List<String>, methodGenerics: List<String>): ParsedExpr {
    if (lexer.consume(TokenType.MINUS)) {
        val tok = lexer.last()
        val methodName = when(tok.type) {
            TokenType.MINUS -> "neg"
            else -> throw IllegalStateException("Unexpected unary operator ${tok.type}. Bug in compiler, please report")
        }
        val operand = parseUnary(lexer, typeGenerics, methodGenerics)
        val loc = tok.loc.merge(operand.loc)
        return ParsedExpr.MethodCall(loc, operand, methodName, listOf(), listOf())
    }
    return parseAssignment(lexer, typeGenerics, methodGenerics)
}

private fun parseAssignment(lexer: Lexer, typeGenerics: List<String>, methodGenerics: List<String>): ParsedExpr {
    var lhs = parseFieldAccessOrMethodCall(lexer, typeGenerics, methodGenerics)
    if (lexer.consume(TokenType.EQUALS)) {
        val eqLoc = lexer.last().loc
        val rhs = parseExpr(lexer, typeGenerics, methodGenerics)
        lhs = if (lhs is ParsedExpr.Variable || lhs is ParsedExpr.FieldAccess)
            ParsedExpr.Assignment(eqLoc, lhs, rhs)
        else if (lhs is ParsedExpr.MethodCall && lhs.methodName == "get")
            TODO()
        else
            throw ParsingException("Cannot have an assignment here - can only assign to variables, fields, or [] results", eqLoc)
    }
    return lhs
}

private fun parseFieldAccessOrMethodCall(lexer: Lexer, typeGenerics: List<String>, methodGenerics: List<String>): ParsedExpr {
    var expr = parseUnit(lexer, typeGenerics, methodGenerics)
    while (lexer.consume(TokenType.DOT, TokenType.LEFT_PAREN)) when (lexer.last().type) {
        TokenType.DOT -> {
            val name = lexer.expect(TokenType.IDENTIFIER, "after DOT")
            val genericArgs = if (lexer.consume(TokenType.DOUBLE_COLON)) {
                val lloc = lexer.expect(TokenType.LESS, "for turbofish method generics").loc
                commaSeparated(lexer, TokenType.GREATER) { parseType(it, typeGenerics, methodGenerics) }.also {
                    // Error if not at least one generic
                    if (it.isEmpty()) throw ParsingException("Expected at least one generic type in turbofish", lloc.merge(lexer.last().loc))
                }
            } else listOf()
            if (lexer.consume(TokenType.LEFT_PAREN)) {
                val args = commaSeparated(lexer, TokenType.RIGHT_PAREN) { parseExpr(it, typeGenerics, methodGenerics) }
                expr = ParsedExpr.MethodCall(name.loc, expr, name.string(), genericArgs, args)
            } else {
                if (genericArgs.isNotEmpty())
                    throw ParsingException("Expected LEFT_PAREN after turbofish; cannot have turbofish on field access", name.loc)
                expr = ParsedExpr.FieldAccess(name.loc, expr, name.string())
            }
        }
        //invoke() overload (or new(), if receiver is super)
        TokenType.LEFT_PAREN -> {
            val loc = lexer.last().loc
            val args = commaSeparated(lexer, TokenType.RIGHT_PAREN) { parseExpr(it, typeGenerics, methodGenerics) }
            // Special case: super() becomes super.new(), NOT super.invoke().
            val methodName = if (expr is ParsedExpr.Super) "new" else "invoke"
            expr = ParsedExpr.MethodCall(loc, expr, methodName, listOf(), args)
        }
        else -> throw IllegalStateException()
    }
    return expr
}

private fun parseUnit(lexer: Lexer, typeGenerics: List<String>, methodGenerics: List<String>): ParsedExpr {
    return when(lexer.take()?.type) {
        null -> throw ParsingException(expected = "Expression", found = "End of file", loc = lexer.curLoc)

        TokenType.IMPORT -> parseImport(lexer)

        TokenType.LEFT_CURLY -> parseBlock(lexer, typeGenerics, methodGenerics)
        TokenType.LEFT_PAREN -> parseParenOrTuple(lexer, typeGenerics, methodGenerics)

        TokenType.LITERAL, TokenType.STRING_LITERAL -> ParsedExpr.Literal(lexer.last().loc, lexer.last().value!!)
        TokenType.IDENTIFIER -> ParsedExpr.Variable(lexer.last().loc, lexer.last().string())

        TokenType.NEW -> parseConstructor(lexer, typeGenerics, methodGenerics)
        TokenType.SUPER -> ParsedExpr.Super(lexer.last().loc)

        TokenType.LET -> parseDeclaration(lexer, typeGenerics, methodGenerics)

        TokenType.RETURN -> ParsedExpr.Return(lexer.last().loc, parseExpr(lexer, typeGenerics, methodGenerics))

        else -> throw ParsingException(expected = "Expression", found = lexer.last().type.toString(), loc = lexer.last().loc)
    }
}

private fun parseImport(lexer: Lexer): ParsedExpr {
    val loc = lexer.last().loc
    val lit = lexer.expect(TokenType.STRING_LITERAL)
    return ParsedExpr.Import(loc, lit.string())
}

private fun parseBlock(lexer: Lexer, typeGenerics: List<String>, methodGenerics: List<String>): ParsedExpr {
    val startLoc = lexer.last().loc
    val elems = ArrayList<ParsedElement>()
    while (!lexer.consume(TokenType.LEFT_CURLY))
        elems.add(parseElement(lexer, typeGenerics, methodGenerics))
    return if (elems.isEmpty())
        ParsedExpr.Tuple(startLoc.merge(lexer.last().loc), listOf())
    else {
        elems.trimToSize()
        ParsedExpr.Block(startLoc.merge(lexer.last().loc), elems)
    }
}

private fun parseParenOrTuple(lexer: Lexer, typeGenerics: List<String>, methodGenerics: List<String>): ParsedExpr {
    val parenLoc = lexer.last().loc
    // If we immediately find closing paren, it's a tuple of 0 values (aka unit)
    if (lexer.consume(TokenType.RIGHT_PAREN))
        return ParsedExpr.Tuple(parenLoc.merge(lexer.last().loc), listOf())
    // Otherwise, parse the first expr
    val firstExpr = parseExpr(lexer, typeGenerics, methodGenerics)
    // If we find a comma, keep going. Otherwise, end here and emit parenthesized
    return if (lexer.consume(TokenType.COMMA)) {
        val rest = commaSeparated(lexer, TokenType.RIGHT_PAREN) { parseExpr(lexer, typeGenerics, methodGenerics) }
        ParsedExpr.Tuple(parenLoc.merge(lexer.last().loc), listOf(firstExpr) + rest)
    } else {
        lexer.expect(TokenType.RIGHT_PAREN, "to end parenthesized expression")
        ParsedExpr.Parenthesized(parenLoc.merge(lexer.last().loc), firstExpr)
    }
}

private fun parseConstructor(lexer: Lexer, typeGenerics: List<String>, methodGenerics: List<String>): ParsedExpr {
    val newTok = lexer.last()
    return when {
        // Check for new() or new {}, which will have their type inferred from context
        // Implicit regular constructor
        lexer.consume(TokenType.LEFT_PAREN) -> ParsedExpr.ConstructorCall(newTok.loc, null,
            commaSeparated(lexer, TokenType.RIGHT_PAREN) { parseExpr(it, typeGenerics, methodGenerics) })
        // Implicit raw struct
        lexer.consume(TokenType.LEFT_CURLY) -> ParsedExpr.RawStructConstructor(newTok.loc, null,
            commaSeparated(lexer, TokenType.RIGHT_CURLY) { parseExpr(it, typeGenerics, methodGenerics) })
        // Otherwise, type is explicit
        else -> {
            val type = parseType(lexer, typeGenerics, methodGenerics, "after \"new\"")
            when {
                // Explicit regular constructor
                lexer.consume(TokenType.LEFT_PAREN) -> ParsedExpr.ConstructorCall(
                    type.loc, type, commaSeparated(lexer, TokenType.RIGHT_PAREN) { parseExpr(it, typeGenerics, methodGenerics) })
                // Explicit raw struct
                lexer.consume(TokenType.LEFT_CURLY) -> ParsedExpr.RawStructConstructor(
                    type.loc, type, commaSeparated(lexer, TokenType.RIGHT_CURLY) { parseExpr(it, typeGenerics, methodGenerics) })
                else -> throw ParsingException("Expected parentheses or curly brace for constructor args", type.loc)
            }
        }
    }
}

private fun parseDeclaration(lexer: Lexer, typeGenerics: List<String>, methodGenerics: List<String>): ParsedExpr {
    val let = lexer.last()
    val pat = parsePattern(lexer, typeGenerics, methodGenerics)
    lexer.expect(TokenType.EQUALS)
    val initializer = parseExpr(lexer, typeGenerics, methodGenerics)
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
