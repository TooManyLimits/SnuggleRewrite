package snuggle.toomanylimits

import snuggle.toomanylimits.runtime.InstanceBuilder

object MainObject // Here so I have something to get the classloader of... ?

fun main() {

    val code = """
        import "std/impls/iterator"
        import "std/types/Box"
        import "std/types/Inc"
        import "std/impls/String"
        
        for c in "hello".chars() print(c)
        print("hello"[2, 3] + "hello"[4, 5] + "hello"[3, 4])
        
        struct naturals {
            static fn invoke(): () -> u64? {
                let current: Box<u64> = new(0)
                fn() {
                    let next = *current;
                    *current = *current + 1
                    return new(next)
                }
            }
        }
        
        for x in naturals() // "Infinite" iterator of natural numbers
            .map::<u64>(fn(n) n*n) // Square them
            .take(20) // Take the first 20
            .filterMap::<u64>(fn(x) if x > 100 new(2 * x) else new()) // x over 100 are doubled, under 100 removed
        {
            print(x)
        }
        
        {
            import "std/impls/curry"
            let add: (i32, i32) -> i32 = fn(a, b) a + b
            let add2 = add(2)
            print(add2(3)) // 5
            print(add(3, 4)) // 7
            print(add(5)(6)) // 11
        }
        
        class X: i32 {
            
        }
        
        print(new X())
        
    """.trimIndent()

    val instance = InstanceBuilder(mutableMapOf("main" to code))
        .debugBytecode()
        .addFile("list", list)
        .addFile("box", box)
//        .reflectObject(ExtraPrinter(" :3"))
        .build()

    instance.runtime.runCode()
}

val box = """
    pub class Box<T> {
        mut v: T
        pub fn new(v: T) { super()
            this.v = v
        }
        pub fn get(): T v
        pub fn set(e: T) v = e
    }
""".trimIndent()

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
                *ind = *ind + 1
                if *ind <= #this
                    new(this[*ind - 1])
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