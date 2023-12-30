import representation.passes.name_resolving.resolveAST
import representation.passes.lexing.Lexer
import representation.asts.parsed.ParsedAST
import representation.passes.parsing.parseFileLazy
import representation.passes.typing.typeAST
import builtins.BoolType
import builtins.ObjectType
import builtins.PrintType
import representation.passes.lowering.lower
import representation.passes.output.output
import runtime.SnuggleInstance
import util.ConsList

fun main() {

    val code = """
        let x: bool = true
        let y: bool = false
        let z: bool = x * y + true
        // I dont have any method call syntax other than "a + b" -> "a.add(b)"
        // there's a type "print" with a static method "add(bool)" that prints the bool.
        print + z
        print + x * y
    """.trimIndent()
    val lexer = Lexer("main", code)

    val file = parseFileLazy(lexer)
    val parsedAST = ParsedAST(mapOf("main" to file))
    parsedAST.debugReadAllFiles() // Remove lazy wrapping
    println(parsedAST)
    val resolvedAST = resolveAST(parsedAST, ConsList.of(BoolType, ObjectType, PrintType))
    println(resolvedAST)
    val typedAST = typeAST(resolvedAST)
    println(typedAST)
    val ir = lower(typedAST)
    println(ir)
    val instance = SnuggleInstance(output(ir))
    instance.runtime.runCode()

}