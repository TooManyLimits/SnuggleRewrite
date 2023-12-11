import ast.parsing.parseFile
import ast.lexing.Lexer
import ast.parsing.parseFileLazy

fun main(args: Array<String>) {

    val code = "let x = 5 x + 3"
    val lexer = Lexer("main", code)

    val file = parseFileLazy(lexer)
    file.value.elements.forEach(::println)

}