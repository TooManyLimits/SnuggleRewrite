import runtime.InstanceBuilder

fun main() {

    val code = """
        pub class Box<T> {
            mut value: T
            pub fn new(v: T) {
                super()
                this.value = v
            }
            pub fn get(): T this.value
            pub fn set(v: T) this.value = v
        }
        
        pub impl<T> () -> T? {
            fn iter(): () -> T? this
            fn map<R>(func: T -> R): () -> R? {
                fn() {
                    let inner: T? = this()
                    if inner
                        new(func(inner.get()))
                    else
                        new()
                }
            }
            fn indexed(): () -> (u32, T)? {
                let counter: Box<u32> = new(0)
                fn() {
                    counter[] = counter[] + 1
                    let inner: T? = this()
                    if inner 
                        new((counter[] - 1, inner[])) 
                    else 
                        new()
                }
            }
        }
        
        struct range {
            static fn invoke(n: i32): () -> i32? {
                let i = new Box<i32>(0)
                fn() {
                    i[] = i[] + 1
                    if (i[] > n) new() else new(i[] - 1)
                }
            }
        }
                
        for x in range(10).map::<i32>(fn(x) x*x)
            print(x)
        
    """.trimIndent()

    val instance = InstanceBuilder(mutableMapOf("main" to code))
        .debugBytecode()
        .addFile("list", list)
//        .reflectObject(ExtraPrinter(" :3"))
        .build()

    instance.runtime.runCode()
}


val list = """
    import "main"
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
        
        pub fn iter(): () -> T? {
            let ind: Box<u32> = new(0)
            fn() {
                ind[] = ind[] + 1
                if ind[] <= #this
                    new(this[ind[] - 1])
                else new()
            }
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