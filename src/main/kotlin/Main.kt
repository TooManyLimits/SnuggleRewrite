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
//        let empty: String? = new()
//        let full: String? = new("hii")
//        print(full.get())
////        print(empty.get())
//
//        let emptyInt: i32? = new()
//        let fullInt: i32? = new(10)
//        print(fullInt.get())
//        print(emptyInt.get())
        
        let whuh: String??? = new(new(new()))
        print(whuh.bool()) // bool() checks if present
        print(whuh.get().bool())
        print(whuh.get().get().bool())
        
    """.trimIndent()
    val lexer = Lexer("main", code)

    val file = parseFileLazy(lexer)
    val parsedAST = ParsedAST(mapOf("main" to file))
//    parsedAST.debugReadAllFiles() // Remove lazy wrapping
//    println(parsedAST)
    val resolvedAST = resolveAST(parsedAST, ConsList.of(BoolType, ObjectType, StringType, OptionType, PrintType, IntLiteralType, *INT_TYPES, *FLOAT_TYPES))
//    println(resolvedAST)
    val typedAST = typeAST(resolvedAST)
//    println(typedAST)
    val ir = lower(typedAST)
//    println(ir)
    val instance = SnuggleInstance(output(ir))
    instance.runtime.runCode()

}