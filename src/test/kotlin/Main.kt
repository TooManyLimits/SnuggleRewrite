import runtime.InstanceBuilder

fun main() {

    val code = """
        import "list"
        class Event<Input>: List<Input -> ()> {
            pub fn new() super()
            // Currently requires "this." for closed over variables...
            // Since those closed-over variables are *fields* of the closure object
            pub fn invoke(input: Input)
                this.forEach(fn(elem) elem(this.input)) 
        }
        let x = new Event<i32>()
        x.push(fn(arg) print(arg))
        x.push(fn(arg) print(arg * 2))
        x.push(fn(arg) print(arg * arg))
        x(10)
        x(9)
    """.trimIndent()

    val instance = InstanceBuilder(mutableMapOf("main" to code))
        .addFile("list", list)
        .reflectObject(EvilPrinter(", lol!!"))
        .build()

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