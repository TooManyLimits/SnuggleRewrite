import representation.passes.name_resolving.resolveAST
import representation.passes.lexing.Lexer
import representation.asts.parsed.ParsedAST
import representation.passes.parsing.parseFileLazy
import representation.passes.typing.typeAST
import builtins.BoolType
import builtins.ObjectType
import representation.passes.lowering.lower
import util.ConsList

fun main() {

    val code = """
        let x: bool = true
        let y: bool = false
//        let z: bool = x + y + false
//        let w: Test = false // technically "isSubtype()" always returns true right now, which is why this is okay
//        class Test {
//            fn add(mut x: bool): bool false
//        }
    """.trimIndent()
    val lexer = Lexer("main", code)

    val file = parseFileLazy(lexer)
    val parsedAST = ParsedAST(mapOf("main" to file))
    parsedAST.debugReadAllFiles() // Remove lazy wrapping
    println(parsedAST)
    val resolvedAST = resolveAST(parsedAST, ConsList.of(BoolType, ObjectType))
    println(resolvedAST)
    val typedAST = typeAST(resolvedAST)
    println(typedAST)
    val ir = lower(typedAST)
    println(ir)

}