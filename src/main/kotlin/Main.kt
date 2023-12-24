import ast.import_resolution.resolveAST
import ast.parsing.parseFile
import ast.lexing.Lexer
import ast.parsing.ParsedAST
import ast.parsing.parseFileLazy
import ast.typing.typeAST
import builtins.BoolType
import util.ConsList

fun main() {

    val code = """
        let x: bool = true
        let y: bool = false
        let z: bool = x - y
    """.trimIndent()
    val lexer = Lexer("main", code)

    val file = parseFileLazy(lexer)
    val parsedAST = ParsedAST(mapOf("main" to file))
    parsedAST.debugReadAllFiles() // Remove lazy wrapping
    println(parsedAST)
    val resolvedAST = resolveAST(parsedAST, ConsList.of(BoolType))
    println(resolvedAST)
    val typedAST = typeAST(resolvedAST)
    println(typedAST)

}