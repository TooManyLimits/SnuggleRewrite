package runtime;

import org.objectweb.asm.ClassReader
import org.objectweb.asm.util.TraceClassVisitor
import representation.passes.output.CompiledProgram
import java.io.PrintWriter
import java.util.concurrent.atomic.AtomicInteger


/**
 * A wrapper over a SnuggleRuntime, with helpers.
 * A class implementing SnuggleRuntime is generated
 * by the compiler, alongside several other class
 * files, and the ClassLoader holding them is here.
 */
class SnuggleInstance(compiledProgram: CompiledProgram) {

    // Contains the runtime, and the class loader
    // holding the bytecodes for this runtime.
    private val loader: CustomLoader = CustomLoader()
    // Runtime is public, so users can call its methods
    val runtime: SnuggleRuntime

    init {
        // Add the other classes to the class loader
        compiledProgram.otherClasses.forEach { loader.defineClass(it) }
        // Create the runtime and add it to the loader
        val runtimeClass = loader.defineClass(compiledProgram.runtimeClass)
        runtime = runtimeClass.getConstructor().newInstance() as SnuggleRuntime
    }

}


/**
 * The custom class loader which holds all the generated classes
 * for some instance.
 */
private val nextLoaderId = AtomicInteger()
private class CustomLoader: ClassLoader("SnuggleLoader${nextLoaderId.getAndIncrement()}", getSystemClassLoader()) {
    fun defineClass(bytes: ByteArray): Class<*> {
        ClassReader(bytes).accept(TraceClassVisitor(PrintWriter(System.err)), ClassReader.SKIP_DEBUG)
        return defineClass(null, bytes, 0, bytes.size)
    }
}