import builtins.*
import representation.passes.name_resolving.resolveAST
import representation.passes.lexing.Lexer
import representation.asts.parsed.ParsedAST
import representation.passes.parsing.parseFileLazy
import representation.passes.typing.typeAST
import representation.passes.lowering.lower
import representation.passes.output.output
import runtime.SnuggleInstance
import util.ConsList

fun main() {

    val code = """
        let x: i32 = 10
        let y = 20i32
        // let z = 30 // error, doesnt know which int type
        print(x)
        print(y)
        print(30) // fine since there's only one print() for any numeric type
    """.trimIndent()
    val lexer = Lexer("main", code)

    val file = parseFileLazy(lexer)
    val parsedAST = ParsedAST(mapOf("main" to file))
    parsedAST.debugReadAllFiles() // Remove lazy wrapping
    println(parsedAST)
    val resolvedAST = resolveAST(parsedAST, ConsList.of(BoolType, ObjectType, PrintType, IntLiteralType, *INT_TYPES))
    println(resolvedAST)
    val typedAST = typeAST(resolvedAST)
    println(typedAST)
    val ir = lower(typedAST)
    println(ir)
    val instance = SnuggleInstance(output(ir))
    instance.runtime.runCode()

}