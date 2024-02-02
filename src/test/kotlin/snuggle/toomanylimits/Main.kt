package snuggle.toomanylimits

import snuggle.toomanylimits.runtime.InstanceBuilder

object MainObject // Here so I have something to get the classloader of... ?

fun main() {

    val code = """
        import "std/impls/iterator"
        import "std/types/Box"
        import "std/types/Inc"
        import "std/impls/String"
        
        //for c in "hello".chars() print(c)
        print("hello"[2, 3] + "hello"[4, 5] + "hello"[3, 4])
        
        class Bleh {
            static mut x: u8
            static fn get255(): u8 {
                return 255
            }
            static fn getX(): u8 {
                return Bleh.x
            }
        }
        Bleh.x = 255
        
        print(255u8)
        print(Bleh.get255())
        print(Bleh.getX())
        print(Bleh.x)
        
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