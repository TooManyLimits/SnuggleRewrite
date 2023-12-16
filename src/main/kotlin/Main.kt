import ast.import_resolution.resolveAST
import ast.parsing.parseFile
import ast.lexing.Lexer
import ast.parsing.ParsedAST
import ast.parsing.parseFileLazy
import builtins.BoolType
import util.ConsList

fun main(args: Array<String>) {

    val code = "import \"main\""
    val lexer = Lexer("main", code)

    val file = parseFileLazy(lexer)
    val parsedAST = ParsedAST(mapOf("main" to file))
    parsedAST.debug_readAllFiles() // Remove lazy wrapping
    println(parsedAST)
    val resolvedAST = resolveAST(parsedAST, ConsList.of(BoolType));
    println(resolvedAST)

}