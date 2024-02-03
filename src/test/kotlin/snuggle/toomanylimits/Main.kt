package snuggle.toomanylimits

import snuggle.toomanylimits.runtime.InstanceBuilder

object MainObject // Here so I have something to get the classloader of... ?

fun main() {

    val code = """
        import "std/impls/iterator"
        import "std/types/Box"
        import "std/types/Inc"
        import "std/types/List"
        import "std/impls/String"
        
        //for c in "hello".chars() print(c)
        print("hello"[2, 3].add::<String>("hello"[4, 5]).add::<String>("hello"[3, 4]))
        
        let x: List<i32> = new()
        x.push(10)
        x.push(20)
        
        print(x[0])
        print(x[1])
        print(x[2])
        
    """.trimIndent()

    val instance = InstanceBuilder(mutableMapOf("main" to code))
        .debugBytecode()
        .build()

    instance.runtime.runCode()
}