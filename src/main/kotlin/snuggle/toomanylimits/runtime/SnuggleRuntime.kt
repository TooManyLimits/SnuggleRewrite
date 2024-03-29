package snuggle.toomanylimits.runtime

import snuggle.toomanylimits.errors.SnuggleException

/**
 * Represents a running "instance" of some Snuggle code.
 * This class is where one can launch the generated
 * bytecode once it's created.
 * TODO:
 * It also tracks other properties of
 * the runtime used for sandboxing, like cost and memory
 * usage
 */
interface SnuggleRuntime {

    // A single method, which just runs the code.
    @Throws(SnuggleException::class)
    fun runCode()

}