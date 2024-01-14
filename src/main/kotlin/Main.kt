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
        class Box<T> {
            pub mut v: T
            fn new(elem: T) { super() this.v = elem; }
        }
        class A {
            static fn summer<T>(initial: T): (T -> (), () -> T) {
                let accumulator: Box<T> = new(initial);
                (fn(v) this.accumulator.v = this.accumulator.v + v, fn() this.accumulator.v)
            }
        }
        
        let (conc, val) = A.summer::<String>("")
        conc("a")
        conc("bb")
        conc("ccc")
        print(val())
        
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