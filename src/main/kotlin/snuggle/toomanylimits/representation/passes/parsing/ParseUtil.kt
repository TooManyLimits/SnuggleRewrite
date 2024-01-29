package snuggle.toomanylimits.representation.passes.parsing

import snuggle.toomanylimits.representation.passes.lexing.Lexer
import snuggle.toomanylimits.representation.passes.lexing.TokenType

// The start token was just consumed. Continues until finding the end token.
// consumer is a function that expects a T to be present, and consumes/parses it.
// Trailing commas are allowed.
inline fun <T> commaSeparated(lexer: Lexer, endToken: TokenType, consumer: (Lexer) -> T): List<T> {
    val res = ArrayList<T>()
    while (true) {
        if (lexer.consume(endToken)) {
            res.trimToSize()
            return res
        }
        res += consumer(lexer)
        if (!lexer.consume(TokenType.COMMA)) {
            lexer.expect(endToken, "to end comma-separated list")
            res.trimToSize()
            return res
        }
    }
}