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
        class List<T> {
            fn new() super()
            fn double(): List<T> this
            fn first(): T? new()
            fn rest(): List<T>? new()
        }
        class Nil<T>: List<T> {fn new() super()}
        class Cons<T>: List<T> {
            mut elem: T
            mut rest: List<T>
            fn new(elem: T, rest: List<T>) {
                super()
                this.elem = elem
                this.rest = rest;
            }
            fn double(): List<T> new Cons<T>(this.elem * 2, this.rest.double())
            fn first(): T? new(this.elem)
            fn rest(): List<T>? new(this.rest)
        }

        let x: List<i32> = new Cons<i32>(5, new Cons<i32>(6, new Cons<i32>(7, new Nil<i32>())))
        print(x.first().get())
        let y = x.double()
        print(y.first().get())
                
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