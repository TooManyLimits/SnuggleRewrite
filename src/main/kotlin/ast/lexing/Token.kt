package lexing

import errors.LexingException
import java.math.BigInteger
import java.util.regex.Pattern

data class Token(val loc: Loc, val type: TokenType, val value: Any?) {
    fun string(): String {
        return value as String
    }
}

val TOKEN_REGEX = Pattern.compile(
    //Single line comments
    "//.*" + "|" +
    //Multi line comments
    "/\\*(\\*(?!/)|[^*])*\\*/" + "|" + //https://stackoverflow.com/questions/16160190/regular-expression-to-find-c-style-block-comments
    //Unique 2-character operators and ASSIGN variants, ^^, &&, ||, ^^=
    "(?:&&|\\|\\||\\^\\^)=?" + "|" +
    //Assign variants for bit shifts, <<= and >>=
    ">>=|<<=" + "|" +
    //Other 2-character symbols, .. :: -> =>
    "\\.\\.|::|->|=>" + "|" +
    //1-character operators and versions with = after
    //ex. + and +=, < and <=, ! and !=, = and ==, even though these are very different situations
    "[-+*/%=&|^><!]=?" + "|" +
    //Other punctuation, () [] {} . : ; ? # ~
    "[()\\[\\]{}.:;,?#~]" + "|" +
    //Number literals
    "\\d+(?:(\\.\\d+(?:f32|f64)?)|(?:i8|u8|i16|u16|i32|u32|i64|u64|f32|f64)?)?" + "|" +
    //Identifiers
    "[a-zA-Z_]\\w*" + "|" +
    //Char literals (quote, (escape sequence | regular character), quote)
    "'(?:\\\\(?:[btnfr\"'\\\\]|u[\\da-fA-F]{4}|(?:[0-3][0-7]{2}|[0-7]{2}|[0-7]))|[^\\\\'])'" + "|" +
    //String literals (quote, (escape sequence | regular character)*, quote)
    "\"(?:\\\\(?:[btnfr\"'\\\\]|u[\\da-fA-F]{4}|(?:[0-3][0-7]{2}|[0-7]{2}|[0-7]))|[^\\\\\"])*+\"" + "|" +
    //Newlines
    "\n" + "|" +
    //Anything else
    "."
)

private val WORD_REGEX = Pattern.compile("[a-z_][a-z\\d_]*")

fun tokenOf(loc: Loc, string: String): Token? {
    if (string.isBlank() || string.startsWith("//") || string.startsWith("/*"))
        return null

    val type: TokenType = when(string) {
        "+" -> TokenType.PLUS
        "-" -> TokenType.MINUS

        else -> return when {
            //Identifiers
            (string[0].isLetter() || string[0] == '_') && WORD_REGEX.matcher(string).matches() -> Token(loc, TokenType.IDENTIFIER, string)
            //Numbers
            string[0].isDigit() -> when {
                string.contains('f') -> when {
                    string.endsWith("f32") -> Token(loc, TokenType.LITERAL, string.substring(0, string.length - 3).toFloat())
                    string.endsWith("f64") -> Token(loc, TokenType.LITERAL, string.substring(0, string.length - 3).toDouble())
                    else -> throw IllegalStateException("Unexpected")
                }
                string.contains('.') -> throw IllegalStateException("Exact floating point values not yet re-implemented, annotate them")
                string.contains('i') -> Token(loc, TokenType.LITERAL, IntLiteralData(BigInteger(string.substring(0, string.indexOf('i'))), true, string.substring(string.indexOf('i') + 1).toInt()))
                string.contains('u') -> Token(loc, TokenType.LITERAL, IntLiteralData(BigInteger(string.substring(0, string.indexOf('u'))), false, string.substring(string.indexOf('u') + 1).toInt()))
                else -> Token(loc, TokenType.LITERAL, BigInteger(string))
            }
            else -> throw LexingException(string, loc)
        }
    }

    return Token(loc, type, null)
}

enum class TokenType {
    LITERAL,
    IDENTIFIER,

    PLUS,
    MINUS
}

data class IntLiteralData(val value: BigInteger, val signed: Boolean, val bits: Int)