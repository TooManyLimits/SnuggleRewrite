package runtime;

import builtins.*
import org.objectweb.asm.ClassReader
import org.objectweb.asm.util.TraceClassVisitor
import reflection.ReflectedBuiltinType
import reflection.annotations.SnuggleStatic
import representation.asts.parsed.ParsedAST
import representation.passes.lexing.Lexer
import representation.passes.lowering.lower
import representation.passes.name_resolving.resolveAST
import representation.passes.output.CompiledProgram
import representation.passes.output.getRuntimeClassName
import representation.passes.output.getStaticObjectName
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
class SnuggleInstance(compiledProgram: CompiledProgram, staticInstances: ConsList<Any>, allReflectedClasses: ConsList<Class<*>>) {

    // Contains the runtime, and the class loader
    // holding the bytecodes for this runtime.
    private val loader: CustomLoader
    // Runtime is public, so users can call its methods
    val runtime: SnuggleRuntime

    init {
        // Find the deepest common child classloader. If there is none, error.
        var deepestClassLoader = ClassLoader.getSystemClassLoader()
        fun isSubLoader(a: ClassLoader, b: ClassLoader): Boolean {
            if (a == b) return true
            if (a.parent == null) return false
            return isSubLoader(a.parent, b)
        }
        for (reflectedClass in allReflectedClasses) {
            if (isSubLoader(reflectedClass.classLoader, deepestClassLoader))
                deepestClassLoader = reflectedClass.classLoader
            else
                throw IllegalStateException("Reflected classes have no common deepest classloader!")
        }
        // Create the loader, passing in the compiled class map
        loader = CustomLoader(compiledProgram.classes.toMutableMap(), deepestClassLoader)

        // Create the runtime and add it to the loader
        val runtimeClass = loader.findClass(getRuntimeClassName())!!
        val runtime = runtimeClass.getConstructor().newInstance() as SnuggleRuntime
        // Set the static instances fields on the runtime
        staticInstances.forEachIndexed { index, instance ->
            val fieldName = getStaticObjectName(index)
            runtimeClass.getDeclaredField(fieldName).set(runtime, instance)
        }
        // Set runtime
        this.runtime = runtime
    }

}

class InstanceBuilder(userFiles: Map<String, String>) {
    private val userFiles = mutableMapOf<String, String>().also { it.putAll(userFiles) } // Make mutable
    private var reflectedClasses: ConsList<Class<*>> = ConsList.of()
    private var reflectedObjects: ConsList<Any> = ConsList.of()
    private var otherBuiltins: ConsList<BuiltinType> = ConsList.of()
    fun addFile(name: String, source: String): InstanceBuilder
        = this.also { userFiles[name] = source }
    fun reflect(clas: Class<*>): InstanceBuilder
        = this.also { reflectedClasses = Cons(clas, reflectedClasses) }
    fun reflectObject(instance: Any): InstanceBuilder
        = this.also { reflectedObjects = Cons(instance, reflectedObjects) }
    fun addBuiltin(builtin: BuiltinType): InstanceBuilder
            = this.also { otherBuiltins = Cons(builtin, otherBuiltins) }
    fun build(): SnuggleInstance {
        // Create a list of static objects
        val staticObjects = reflectedObjects.mapIndexed { index, obj ->
            ReflectedBuiltinType(obj.javaClass, index)
        }

        val builtins = reflectedClasses.map { ReflectedBuiltinType(it, null) }
            .append(staticObjects) // Add the static objects to the set of builtins
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
                ), reflectedObjects
            ),
            reflectedObjects,
            reflectedObjects.map { it.javaClass }.append(reflectedClasses)
        )
    }

}


/**
 * The custom class loader which holds all the generated classes
 * for some instance.
 */
private val nextLoaderId = AtomicInteger()
private class CustomLoader(val classes: MutableMap<String, ByteArray>, deepestCommonChild: ClassLoader)
    : ClassLoader("SnuggleLoader${nextLoaderId.getAndIncrement()}", deepestCommonChild) {

    public override fun findClass(name: String): Class<*>? {
        val runtimeName = name.replace('.', '/')
        val bytes = classes.remove(runtimeName) ?: return null
        return defineClass(name, bytes, 0, bytes.size)
    }

//    fun defineClass(bytes: ByteArray): Class<*> {
//        ClassReader(bytes).accept(TraceClassVisitor(PrintWriter(System.err)), ClassReader.SKIP_DEBUG)
//        return defineClass(null, bytes, 0, bytes.size)
//    }
}