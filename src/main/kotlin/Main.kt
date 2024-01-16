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
        struct Vec3 { x: i32 y: i32 z: i32 }
        let x: Vec3?[] = new(10)
        x[8] = new(new { 1, 2, 3 })
        print(x[8].get().y)
        print(x.size())
    """.trimIndent()
    val lexer = Lexer("main", code)

    val file = parseFileLazy(lexer)
    val parsedAST = ParsedAST(mapOf("main" to file, "list" to parseFileLazy(Lexer("list", list))))
//    parsedAST.debugReadAllFiles() // Remove lazy wrapping
//    println(parsedAST)
    val resolvedAST = resolveAST(parsedAST, ConsList.of(BoolType, ObjectType, StringType, OptionType, ArrayType, PrintType, IntLiteralType, *INT_TYPES, *FLOAT_TYPES))
//    println(resolvedAST)
    val typedAST = typeAST(resolvedAST)
//    println(typedAST)
    val ir = lower(typedAST)
//    println(ir)
    val instance = SnuggleInstance(output(ir))
    instance.runtime.runCode()

}


val list = """
    
""".trimIndent()