package ast.lexing

import errors.LexingException
import java.math.BigInteger
import java.util.regex.Pattern

data class Token(val loc: Loc, val type: TokenType, val value: Any?) {
    fun string(): String {
        return value as String
    }
}

val TOKEN_REGEX: Pattern = Pattern.compile(
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

        "pub" -> TokenType.PUB
        "static" -> TokenType.STATIC
        "import" -> TokenType.IMPORT
        "class" -> TokenType.CLASS
        "struct" -> TokenType.STRUCT
        "fn" -> TokenType.FN

        "let" -> TokenType.LET

        ":" -> TokenType.COLON
        ";" -> TokenType.SEMICOLON
        "," -> TokenType.COMMA
        "(" -> TokenType.LEFT_PAREN
        ")" -> TokenType.RIGHT_PAREN
        "{" -> TokenType.LEFT_CURLY
        "}" -> TokenType.RIGHT_CURLY

        "+" -> TokenType.PLUS
        "-" -> TokenType.MINUS
        "=" -> TokenType.EQUALS

        "true" -> return Token(loc, TokenType.LITERAL, true)
        "false" -> return Token(loc, TokenType.LITERAL, false)

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
            //Strings
            (string[0] == '"') -> when {
                string.length == 1 || !string.endsWith('"') -> throw LexingException("Encountered unmatched quote", loc)
                else -> {
                    val builder: StringBuilder = StringBuilder()
                    var i = 1
                    while (i < string.length - 1) {
                        val c = string[i]
                        if (c == '\\') {
                            val escaped = handleEscape(string, i, loc)
                            i = escaped.second
                            builder.append(escaped.first)
                        } else {
                            builder.append(c)
                        }
                        i++
                    }
                    Token(loc, TokenType.STRING_LITERAL, builder.toString())
                }
            }
            else -> throw LexingException(string, loc)
        }
    }

    return Token(loc, type, null)
}

enum class TokenType {
    LITERAL,
    STRING_LITERAL,
    IDENTIFIER,

    PUB,
    STATIC,
    IMPORT,
    CLASS,
    STRUCT,
    FN,

    LET,

    COLON,
    SEMICOLON,
    COMMA,
    LEFT_PAREN,
    RIGHT_PAREN,
    LEFT_CURLY,
    RIGHT_CURLY,

    PLUS,
    MINUS,
    EQUALS,
}

data class IntLiteralData(val value: BigInteger, val signed: Boolean, val bits: Int)


// Parse an escape character
private fun handleEscape(string: String, startIndex: Int, loc: Loc): Pair<Char, Int> {
    var index = startIndex + 1
    val next = string[index]
    return when (next) {
        'b' -> '\b'
        't' -> '\t'
        'n' -> '\n'
        'f' -> '\u000C'
        'r' -> '\r'
        '\'' -> '\''
        '"' -> '"'
        '\\' -> '\\'
        'u' -> {
            var num = 0
            for (j: Int in 0 until 4) {
                index++
                num = num * 16 + string[index].digitToInt(16)
            }
            num.toChar()
        }
        '0', '1', '2', '3' -> {
            val digit2 = string[index + 1]
            if (digit2 in '0'..'7') {
                val digit3 = string[index + 2]
                if (digit3 in '0'..'7') {
                    index += 2
                    (next.digitToInt(8) * 64 + digit2.digitToInt(8) * 8 + digit3.digitToInt(8)).toChar()
                } else {
                    index += 1
                    (next.digitToInt(8) * 8 + digit2.digitToInt(8)).toChar()
                }
            } else {
                next.digitToInt(8).toChar()
            }
        }
        '4', '5', '6', '7' -> {
            val digit2 = string[index + 1]
            if (digit2 in '0'..'7') {
                index += 1
                (next.digitToInt(8) * 8 + digit2.digitToInt(8)).toChar();
            } else {
                next.digitToInt(8).toChar()
            }
        }
        else -> throw LexingException("Illegal escape character \"\\" + next + "\"", loc)
    } to index
}
