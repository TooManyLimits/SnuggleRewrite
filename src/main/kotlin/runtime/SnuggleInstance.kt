package runtime;

import builtins.*
import org.objectweb.asm.ClassReader
import org.objectweb.asm.util.TraceClassVisitor
import reflection.ReflectedBuiltinType
import representation.asts.parsed.ParsedAST
import representation.passes.lexing.Lexer
import representation.passes.lowering.lower
import representation.passes.name_resolving.resolveAST
import representation.passes.output.CompiledProgram
import representation.passes.output.output
import representation.passes.parsing.parseFileLazy
import representation.passes.typing.typeAST
import representation.passes.verify_generics.verify
import util.Cons
import util.ConsList
import util.append
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
        for (otherClass in compiledProgram.otherClasses) {
            loader.defineClass(otherClass)
        }
        // Create the runtime and add it to the loader
        val runtimeClass = loader.defineClass(compiledProgram.runtimeClass)
        runtime = runtimeClass.getConstructor().newInstance() as SnuggleRuntime
    }

}

class InstanceBuilder(private val userFiles: MutableMap<String, String>) {
    private var reflectedClasses: ConsList<Class<*>> = ConsList.of()
    private var otherBuiltins: ConsList<BuiltinType> = ConsList.of()
    fun addFile(name: String, source: String): InstanceBuilder
        = this.also { userFiles[name] = source }
    fun reflect(clas: Class<*>): InstanceBuilder
        = this.also { reflectedClasses = Cons(clas, reflectedClasses) }
    fun addBuiltin(builtin: BuiltinType): InstanceBuilder
            = this.also { otherBuiltins = Cons(builtin, otherBuiltins) }
    fun build(): SnuggleInstance {
        val builtins = reflectedClasses.map { ReflectedBuiltinType(it) }
            .append(otherBuiltins)
            .append(ConsList.of(
                BoolType, *INT_TYPES, *FLOAT_TYPES, // Primitive
                IntLiteralType, // Compile time literals
                ObjectType, StringType, OptionType, ArrayType, // Essential objects
                MaybeUninitType, PrintType, // Helper objects
            ))
        return SnuggleInstance(
            output(
                lower(
                    typeAST(
                        resolveAST(
                            ParsedAST(
                                userFiles.mapValues {
                                    parseFileLazy(Lexer(it.key, it.value))
                                }
                            ),
                            builtins
                        ).also { verify(it) }
                    )
                )
            )
        )
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