package snuggle.toomanylimits.representation.passes.lexing

import snuggle.toomanylimits.builtins.helpers.Fraction
import snuggle.toomanylimits.builtins.primitive.*
import snuggle.toomanylimits.errors.CompilationException
import snuggle.toomanylimits.representation.asts.typed.TypeDef
import snuggle.toomanylimits.representation.passes.typing.TypingCache
import snuggle.toomanylimits.representation.passes.typing.getBasicBuiltin
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
    //Special 3-character operators
    "!is|as!" + "|" +
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

private val WORD_REGEX = Pattern.compile("[a-zA-Z_][a-zA-Z\\d_]*")

fun tokenOf(loc: Loc, string: String): Token? {
    if (string.isBlank() || string.startsWith("//") || string.startsWith("/*"))
        return null

    val type: TokenType = when(string) {

        "pub" -> TokenType.PUB
        "static" -> TokenType.STATIC
        "import" -> TokenType.IMPORT
        "impl" -> TokenType.IMPL
        "class" -> TokenType.CLASS
        "struct" -> TokenType.STRUCT
        "fn" -> TokenType.FN

        "new" -> TokenType.NEW
        "super" -> TokenType.SUPER

        "let" -> TokenType.LET
        "mut" -> TokenType.MUT
        "=" -> TokenType.ASSIGN
        "is" -> TokenType.IS
        "!is" -> TokenType.NOT_IS
        "as" -> TokenType.AS
        "as!" -> TokenType.AS_BANG

        "return" -> TokenType.RETURN
        "if" -> TokenType.IF
        "else" -> TokenType.ELSE
        "while" -> TokenType.WHILE
        "for" -> TokenType.FOR
        "in" -> TokenType.IN

        ":" -> TokenType.COLON
        "::" -> TokenType.DOUBLE_COLON
        ";" -> TokenType.SEMICOLON
        "," -> TokenType.COMMA
        "." -> TokenType.DOT
        "?" -> TokenType.QUESTION_MARK
        "->", "=>" -> TokenType.ARROW
        "_" -> TokenType.UNDERSCORE
        "(" -> TokenType.LEFT_PAREN
        ")" -> TokenType.RIGHT_PAREN
        "[" -> TokenType.LEFT_SQUARE
        "]" -> TokenType.RIGHT_SQUARE
        "{" -> TokenType.LEFT_CURLY
        "}" -> TokenType.RIGHT_CURLY
        "<" -> TokenType.LESS
        ">" -> TokenType.GREATER

        "!" -> TokenType.BANG
        "#" -> TokenType.HASHTAG

        "+" -> TokenType.PLUS
        "-" -> TokenType.MINUS
        "*" -> TokenType.STAR
        "/" -> TokenType.SLASH
        "%" -> TokenType.PERCENT

        "==" -> TokenType.EQUALS
        "!=" -> TokenType.NOT_EQUALS
        ">=" -> TokenType.GREATER_EQUAL
        "<=" -> TokenType.LESS_EQUAL

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
                string.contains('.') -> Token(loc, TokenType.LITERAL, Fraction.parse(string))
                string.contains('i') -> Token(loc, TokenType.LITERAL, IntLiteralData(BigInteger(string.substring(0, string.indexOf('i'))), true, string.substring(string.indexOf('i') + 1).toInt()))
                string.contains('u') -> Token(loc, TokenType.LITERAL, IntLiteralData(BigInteger(string.substring(0, string.indexOf('u'))), false, string.substring(string.indexOf('u') + 1).toInt()))
                else -> Token(loc, TokenType.LITERAL, BigInteger(string))
            }
            //Chars
            (string[0] == '\'') -> when {
                string.length == 1 || !string.endsWith('\'') -> throw LexingException("Encountered unmatched quote", loc)
                else -> {
                    val c = string[1]
                    if (c == '\\')
                        return Token(loc, TokenType.LITERAL, handleEscape(string, 1, loc).first)
                    else
                        return Token(loc, TokenType.LITERAL, c)
                }
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
            else -> throw UnknownTokenException(string, loc)
        }
    }

    return Token(loc, type, null)
}

enum class TokenType {
    // "values"
    LITERAL,
    STRING_LITERAL,
    IDENTIFIER,

    // Type declarations and program structure
    PUB,
    STATIC,
    IMPORT,
    IMPL,
    CLASS,
    STRUCT,
    FN,

    // Object things
    NEW,
    SUPER,

    // Declarations and variables
    LET,
    MUT,
    ASSIGN,
    IS,
    NOT_IS,
    AS,
    AS_BANG,

    // Control flow
    RETURN,
    IF,
    ELSE,
    WHILE,
    FOR,
    IN, // Also used as binary operator (TODO)

    // Punctuation (not operator)
    COLON,
    DOUBLE_COLON,
    SEMICOLON,
    COMMA,
    DOT,
    QUESTION_MARK,
    ARROW,
    UNDERSCORE,
    LEFT_PAREN,
    RIGHT_PAREN,
    LEFT_SQUARE,
    RIGHT_SQUARE,
    LEFT_CURLY,
    RIGHT_CURLY,
    LESS,
    GREATER,

    // Unary (except minus)
    BANG,
    HASHTAG,

    // Binary
    PLUS,
    MINUS,
    STAR,
    SLASH,
    PERCENT,

    // Comparison
    EQUALS,
    NOT_EQUALS,
    GREATER_EQUAL,
    LESS_EQUAL,

}

class IntLiteralData(val value: BigInteger, val signed: Boolean, val bits: Int) {

    fun getBuiltin(typeCache: TypingCache): TypeDef {
        return getBasicBuiltin(when (bits) {
            8 -> if (signed) I8Type else U8Type
            16 -> if (signed) I16Type else U16Type
            32 -> if (signed) I32Type else U32Type
            64 -> if (signed) I64Type else U64Type
            else -> throw IllegalStateException()
        }, typeCache)
    }

}


// Parse an escape character
private fun handleEscape(string: String, startIndex: Int, loc: Loc): Pair<Char, Int> {
    var index = startIndex + 1
    return when (val next = string[index]) {
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
        else -> throw LexingException("Illegal escape character \"\\$next\"", loc)
    } to index
}

class LexingException(message: String, loc: Loc)
    : CompilationException(message, loc)
class UnknownTokenException(unrecognized: String, loc: Loc)
    : CompilationException("Unrecognized ${if (unrecognized.length == 1) "character" else "token"} \"$unrecognized\"", loc)