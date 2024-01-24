package representation.asts.typed

import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import representation.asts.ir.Instruction
import representation.passes.lexing.Loc
import util.ConsList
import util.caching.EqualityCache
import util.caching.EqualityMemoized

// A method definition,
sealed interface MethodDef {
    val pub: Boolean //
    val static: Boolean
    val owningType: TypeDef
    val name: String
    val runtimeName: String get() = name // For most, the runtime name is the same as the name.
    val returnType: TypeDef
    val paramTypes: List<TypeDef>

    // Override vals on the first line, important things on later lines
    class BytecodeMethodDef(override val pub: Boolean, override val static: Boolean, override val owningType: TypeDef, override val name: String, override val returnType: TypeDef, override val paramTypes: List<TypeDef>,
                            // Bytecode which runs early, before the receiver or the args have been pushed to the stack yet.
                            val preBytecode: ((writer: MethodVisitor, maxVariable: Int, desiredFields: ConsList<ConsList<FieldDef>>) -> Unit)?,
                            val bytecode: (writer: MethodVisitor, maxVariable: Int, desiredFields: ConsList<ConsList<FieldDef>>) -> Unit,
                            val desiredReceiverFields: ((ConsList<ConsList<FieldDef>>) -> ConsList<ConsList<FieldDef>>)?):
        MethodDef {
        constructor(pub: Boolean, static: Boolean, owningType: TypeDef, name: String, returnType: TypeDef, paramTypes: List<TypeDef>, bytecode: (MethodVisitor) -> Unit)
            : this(pub, static, owningType, name, returnType, paramTypes,
                null,
                { writer, _, _ -> bytecode(writer); },
                null)
    }
    class ConstMethodDef(override val pub: Boolean, override val owningType: TypeDef, override val name: String, override val returnType: TypeDef, override val paramTypes: List<TypeDef>,
                         val replacer: (TypedExpr.MethodCall) -> TypedExpr
    ): MethodDef {
        override val static: Boolean get() = false
    }
    class StaticConstMethodDef(override val pub: Boolean, override val owningType: TypeDef, override val name: String, override val returnType: TypeDef, override val paramTypes: List<TypeDef>,
                               val replacer: (TypedExpr.StaticMethodCall) -> TypedExpr
    ): MethodDef {
        override val static: Boolean get() = true
    }
    // Method def without an implementation, used in things like TypeDef.Func
    class InterfaceMethodDef(override val pub: Boolean, override val static: Boolean, override val owningType: TypeDef, override val name: String, override val returnType: TypeDef, override val paramTypes: List<TypeDef>):
        MethodDef
    // A MethodDef which has custom behavior for lowering itself and outputting itself to the ClassWriter
    class CustomMethodDef(override val pub: Boolean, override val static: Boolean, override val owningType: TypeDef, override val name: String, override val runtimeName: String, override val returnType: TypeDef, override val paramTypes: List<TypeDef>,
                          val lowerer: () -> Sequence<Instruction>,
                          val outputter: (ClassWriter) -> Unit): MethodDef
    // runtimeName field: often, SnuggleMethodDef will need to have a different name
    // at runtime than in the internal representation. These are the case for:
    // - constructors, whose names are changed to "<init>" to match java's requirement
    // - overloaded methods, whose names are changed to have a disambiguation number appended
    //
    // Note that some of these fields are lazily calculated to allow for self-referencing concerns.
    //
    // staticOverrideReceiverType field: Used for impl blocks. If non-null, then the following will occur:
    // - The method will be treated as static by the backend.
    //   It will emit as a static method, and calls to it will use INVOKESTATIC,
    //   even if the method's `static` field is not true.
    // - The type in the staticOverrideReceiverType will be used as the first parameter of
    //   the generated static method. Set it to what "this" would be in the method def.
    class SnuggleMethodDef(val loc: Loc, override val pub: Boolean, override val static: Boolean, override val owningType: TypeDef, override val name: String,
                           val staticOverrideReceiverType: TypeDef?,
                           val returnTypeGetter: Lazy<TypeDef>,
                           val paramTypesGetter: Lazy<List<TypeDef>>,
                           val runtimeNameGetter: Lazy<String>,
                           val lazyBody: Lazy<TypedExpr>)
        : MethodDef {
            override val returnType by returnTypeGetter
            override val paramTypes by paramTypesGetter
            override val runtimeName by runtimeNameGetter
        }

    abstract class GenericMethodDef<T: MethodDef>(val numGenerics: Int): MethodDef {
        // The specializations created from this generic method def
        val specializations: EqualityCache<List<TypeDef>, T> = EqualityCache()
        fun getSpecialization(generics: List<TypeDef>) = specializations.get(generics) { specialize(it) }
        // Abstract: Specialize this GenericMethodDef into a (non-generic!) MethodDef by replacing the generics.
        protected abstract fun specialize(generics: List<TypeDef>): T
        // Unspecialized generic method defs cannot know certain properties
        override val paramTypes: List<TypeDef> get() = throw IllegalStateException("Should not be asking generic method def for its param types - it doesn't know them")
        override val returnType: TypeDef get() = throw IllegalStateException("Should not be asking generic method def for its return type - it doesn't know it")
        override val runtimeName: String get() = throw IllegalStateException("Should not be asking generic method def for its runtime name - it doesn't know it")

        // A generic method defined in Snuggle code
        class GenericSnuggleMethodDef(val loc: Loc, override val pub: Boolean, override val static: Boolean, numGenerics: Int,
                                      override val owningType: TypeDef, override val name: String,
                                      val staticOverrideReceiverType: TypeDef?, // Serves same purpose as above - if the specializations of this should have the static override flag on.
                                      val returnTypeGetter: EqualityMemoized<List<TypeDef>, Lazy<TypeDef>>,
                                      val paramTypeGetter: EqualityMemoized<List<TypeDef>, Lazy<List<TypeDef>>>,
                                      val runtimeNameGetter: EqualityMemoized<List<TypeDef>, Lazy<String>>,
                                      val lazyBodyGetter: EqualityMemoized<List<TypeDef>, Lazy<TypedExpr>>
        ) : GenericMethodDef<SnuggleMethodDef>(numGenerics) {
            override fun specialize(generics: List<TypeDef>): SnuggleMethodDef = SnuggleMethodDef(
                loc, pub, static, owningType, name, staticOverrideReceiverType, returnTypeGetter(generics),
                paramTypeGetter(generics), runtimeNameGetter(generics), lazyBodyGetter(generics)
            )
        }

    }
}