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
        struct Vec4 {
            x: i32 
            y: i32
            zw: (i32, i32)
            fn add(o: Vec4): Vec4 
                new { this.x + o.x, this.y + o.y, (this.zw.v0 + o.zw.v0, this.zw.v1 + o.zw.v1) }
        }
        let x = new Vec4 { 10, 20, (30, 40) }
        let y = new Vec4 { 30, 40, (50, 60) }
        let z = x + y
        print(z.x)
        print(z.y)
        print(z.zw.v0)
        print(z.zw.v1)
    """.trimIndent()
    val lexer = Lexer("main", code)

    val file = parseFileLazy(lexer)
    val parsedAST = ParsedAST(mapOf("main" to file))
//    parsedAST.debugReadAllFiles() // Remove lazy wrapping
//    println(parsedAST)
    val resolvedAST = resolveAST(parsedAST, ConsList.of(BoolType, ObjectType, PrintType, IntLiteralType, *INT_TYPES))
//    println(resolvedAST)
    val typedAST = typeAST(resolvedAST)
//    println(typedAST)
    val ir = lower(typedAST)
//    println(ir)
    val instance = SnuggleInstance(output(ir))
    instance.runtime.runCode()

}