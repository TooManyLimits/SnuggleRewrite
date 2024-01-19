import builtins.*
import reflection.ReflectedBuiltinType
import representation.asts.parsed.ParsedAST
import representation.passes.lexing.Lexer
import representation.passes.lowering.lower
import representation.passes.name_resolving.resolveAST
import representation.passes.output.output
import representation.passes.parsing.parseFileLazy
import representation.passes.typing.typeAST
import runtime.SnuggleInstance
import util.ConsList

fun main() {

    val code = """
        let a = TestClass.getInstance()
        print(a.x)
        print(a.y)
        print(a.z)
        print(a)
    """.trimIndent()
    val lexer = Lexer("main", code)

    val file = parseFileLazy(lexer)
    val parsedAST = ParsedAST(mapOf("main" to file, "list" to parseFileLazy(Lexer("list", list))))
//    parsedAST.debugReadAllFiles() // Remove lazy wrapping
//    println(parsedAST)
    val resolvedAST = resolveAST(parsedAST, ConsList.of(
        BoolType, ObjectType, StringType, OptionType, ArrayType,
        MaybeUninitType, PrintType, IntLiteralType, *INT_TYPES, *FLOAT_TYPES,
        ReflectedBuiltinType(TestClass::class.java)
    ))
//    println(resolvedAST)
    val typedAST = typeAST(resolvedAST)
//    println(typedAST)
    val ir = lower(typedAST)
//    println(ir)
    val instance = SnuggleInstance(output(ir))
    instance.runtime.runCode()

}


val list = """
    pub class List<T> {
        mut backing: MaybeUninit<T>[]
        mut size: u32
        pub fn new() {
            super()
            this.size = 0
            this.backing = new(5)
        }
        pub fn size(): u32 { this.size }
        pub fn push(elem: T) {
            if #this == #this.backing this.grow(2 * #this)
            this.backing[#this] = new(elem)
            this.size = this.size + 1
        }
        pub fn get(index: u32): T {
            // TODO error if index >= size
            this.backing[index].get()
        }
        pub fn forEach(func: T -> ()) {
            let mut i: u32 = 0
            let size = #this
            while i < size {
                func(this[i])
                i = i + 1
            };
        }
        
        fn grow(desiredSize: u32) {
            let newBacking: MaybeUninit<T>[] = new(desiredSize)
            let mut i = 0u32
            while (i < #this) {
                newBacking[i] = this.backing[i]
                i = i + 1
            }
            this.backing = newBacking
        }
    }
""".trimIndent()