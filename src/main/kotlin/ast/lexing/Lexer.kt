package ast.lexing

import errors.ParsingException
import java.io.StringReader
import java.util.Scanner

class Lexer(val fileName: String, private val scanner: Scanner): Iterable<Token> {

    constructor(fileName: String, code: String): this(fileName, Scanner(StringReader(code)))

    // Fields tracking state
    private var line: Int = 1
    private var col: Int = 0
    private var last: Token? = null
    private var next: Token? = null

    //"current" loc, used for EOF errors
    val curLoc: Loc get() = Loc(line, col, line, col, fileName)

    // Helpers

    fun isDone(): Boolean = peek() == null // Check if we're out of tokens
    fun last(): Token = last!! // Get the previous token
    fun take(): Token? = peek().also { if (it != null) advance() } // Take a token
    fun check(type: TokenType): Boolean = peek()?.type == type // Check if the next token is the given type
    fun check(type1: TokenType, type2: TokenType): Boolean = peek()?.type == type1 || peek()?.type == type2 // Check if the next token is one of the given types
    fun check(vararg types: TokenType): Boolean = peek()?.type in types // Check if the next token is one of the given types
    fun consume(type: TokenType): Boolean = check(type).also { if (it) advance() } // Consume the next token if it's the given type
    fun consume(vararg types: TokenType): Boolean = check(*types).also { if (it) advance() } // Consume the next token if it's one of the given types
    fun expect(type: TokenType, extraInfo: String? = null): Token = take().let {// Expect the next token to be of the given type, if it isn't, then throw an error
        if (it?.type != type)
            throw ParsingException("$type${extraInfo?.prependIndent(" ")?:""}", "${it?.type ?: "End of file"}", it?.loc ?: curLoc)
        else it
    }
    override fun iterator(): Iterator<Token> = object : Iterator<Token> {
        override fun hasNext(): Boolean = !isDone()
        override fun next(): Token = take()!!
    }

    // Base operations, advance() and peek()

    fun advance() {
        if (next == null) peek()
        last = next
        next = null
    }

    fun peek(): Token? {
        while (next == null) {
            val match = scanner.findWithinHorizon(TOKEN_REGEX, 0) ?: return null
            val oldLine = line;
            val oldCol = col;
            val lastNewline = match.lastIndexOf('\n')
            if (lastNewline != -1) {
                line += match.count { it == '\n' }
                col = match.length - lastNewline - 1;
            } else {
                col += match.length
            }
            next = tokenOf(Loc(oldLine, oldCol, line, col, fileName), match)
        }
        return next
    }


}