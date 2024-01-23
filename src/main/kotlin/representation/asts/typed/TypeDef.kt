package representation.asts.typed

import builtins.ArrayType
import builtins.BuiltinType
import builtins.ObjectType
import builtins.OptionType
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import representation.asts.ir.Instruction
import representation.passes.lexing.Loc
import representation.passes.output.getMethodDescriptor
import representation.passes.output.outputInstruction
import representation.passes.typing.*
import util.ConsList
import util.ConsMap
import util.Promise
import util.caching.EqualityCache
import util.caching.EqualityMemoized
import util.toGeneric
import java.util.concurrent.atomic.AtomicInteger

// A type definition, instantiated. No more generics left to fill.
sealed class TypeDef {

    abstract val name: String
    abstract val runtimeName: String?
    abstract val descriptor: List<String>

    abstract val stackSlots: Int
    abstract val isPlural: Boolean
    abstract val isReferenceType: Boolean

    // If true, constructors are "static fn new() -> Type". Otherwise, "fn new() -> Unit"
    abstract val hasStaticConstructor: Boolean

    abstract val primarySupertype: TypeDef?
    abstract val supertypes: List<TypeDef>

    abstract val fields: List<FieldDef>
    abstract val methods: List<MethodDef>

    // The generics from which this type def was made
    abstract val generics: List<TypeDef>

    // Helpers caching common values.
    val nonStaticFields: List<FieldDef> by lazy { fields.filter { !it.static } }
    val staticFields: List<FieldDef> by lazy { fields.filter { it.static } }
    val nonStaticMethods: List<MethodDef> by lazy { methods.filter { !it.static } }
    val staticMethods: List<MethodDef> by lazy { methods.filter { it.static } }
    // "all", including inherited ones.
    val allNonStaticFields: List<FieldDef> by lazy {
        val supers = primarySupertype?.allNonStaticFields?.filter { superField -> nonStaticFields.none { it.name == superField.name } } ?: listOf()
        nonStaticFields + supers
    }
    // Generic methods cannot be overridden, this is just because it seemed like it would be hard to implement lmao
    val allNonStaticMethods: List<MethodDef> by lazy {
        val supers = primarySupertype?.allNonStaticMethods?.filter { superMethod ->
            superMethod is MethodDef.GenericMethodDef<*> || // Generics are inherited but cannot be overridden
            nonStaticMethods.none {
                it.name == superMethod.name &&
                it.paramTypes == superMethod.paramTypes &&
                it.returnType == superMethod.returnType
            }
        } ?: listOf()
        nonStaticMethods + supers
    }

    // Helper for obtaining a recursing tree of non-static fields,
    // as well as full names for each. This is useful when lowering
    // plural types.
    val recursiveNonStaticFields: List<Pair<String, FieldDef>> by lazy {
        if (isPlural)
            nonStaticFields.flatMap { myField ->
                if (myField.type.isPlural)
                    myField.type.recursiveNonStaticFields.map { (myField.name + "$" + it.first) to it.second }
                else
                    listOf(myField.name to myField)
            }
        else
            throw IllegalStateException("Should never ask non-plural type for recursiveNonStaticFields - bug in compiler, please report!")
    }

    // Helper to check whether this is an optional reference type, like "String?".
    // Useful because this property is checked on several occasions, because optional
    // reference types are very hooked into the working of the jvm (they are represented
    // as nullable). Similar case for arrays of 0-sized objects, they are represented as an
    // int on the jvm.
    val isOptionalReferenceType: Boolean get() = this.builtin == OptionType && this.generics[0].isReferenceType
    val isArrayOfZeroSizes: Boolean get() = this.builtin == ArrayType && this.generics[0].stackSlots == 0

    // For most types, their builtin is null. For instantiated builtins,
    // it's non-null. Including this property greatly reduces boilerplate
    // in some situations, where we need to check if a type is an instance
    // of InstantiatedBuiltin and smart-cast it before checking its builtin.
    open val builtin: BuiltinType? get() = null

    // The actual type of the type def. Exists for Indirections
    // to override, since the actual type of the class is one of the
    // things that may need to be known recursively.
    open val actualType: Class<out TypeDef> get() = javaClass

    fun isSubtype(other: TypeDef): Boolean {
        val thisUnwrapped = this.unwrap()
        val otherUnwrapped = other.unwrap()
        if (thisUnwrapped.equals(otherUnwrapped)) return true
        for (supertype in supertypes)
            if (supertype.isSubtype(otherUnwrapped))
                return true
        return false
    }

    // Unwrap the typedef's indirections
    fun unwrap(): TypeDef =
        if (this is Indirection) promise.expect().unwrap() else this

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other is TypeDef) return this.unwrap() === other.unwrap()
        return false
    }

    override fun hashCode(): Int =
        if (this is Indirection) this.unwrap().hashCode() else System.identityHashCode(this)

    // An indirection which points to another TypeDef. Needed because of
    // self-references inside of types, i.e. if class A uses the type
    // A inside its definition (which is likely), the things in A need to
    // refer to A, while A is still being constructed.
    class Indirection(val promise: Promise<TypeDef> = Promise()): TypeDef() {
        override val name: String get() = promise.expect().name
        override val runtimeName: String? get() = promise.expect().runtimeName
        override val descriptor: List<String> get() = promise.expect().descriptor
        override val stackSlots: Int get() = promise.expect().stackSlots
        override val isPlural: Boolean get() = promise.expect().isPlural
        override val isReferenceType: Boolean get() = promise.expect().isReferenceType
        override val hasStaticConstructor: Boolean get() = promise.expect().hasStaticConstructor
        override val primarySupertype: TypeDef? get() = promise.expect().primarySupertype
        override val supertypes: List<TypeDef> get() = promise.expect().supertypes
        override val fields: List<FieldDef> get() = promise.expect().fields
        override val methods: List<MethodDef> get() = promise.expect().methods
        override val generics: List<TypeDef> get() = promise.expect().generics
        override val builtin: BuiltinType? get() = promise.expect().builtin
    }

    class Tuple(val innerTypes: List<TypeDef>): TypeDef() {
        override val name: String = toGeneric("", innerTypes).ifEmpty { "()" }
        override val runtimeName: String = "tuples/$name"
        override val descriptor: List<String> = innerTypes.flatMap { it.descriptor }
        override val stackSlots: Int = innerTypes.sumOf { it.stackSlots }
        override val isPlural: Boolean get() = true
        override val isReferenceType: Boolean get() = false
        override val hasStaticConstructor: Boolean get() = true
        override val primarySupertype: TypeDef? get() = null
        override val supertypes: List<TypeDef> get() = listOf()
        override val fields: List<FieldDef> = run {
            var currentIndex = 0
            innerTypes.mapIndexed { index, type ->
                FieldDef.BuiltinField(pub = true, static = false, mutable = false, "v$index", currentIndex, type)
                    .also { currentIndex += type.stackSlots }
            }
        }
        override val methods: List<MethodDef> get() = listOf()
        override val generics: List<TypeDef> get() = innerTypes
    }

    class Func(val paramTypes: List<TypeDef>, val returnType: TypeDef, typeCache: TypeDefCache): TypeDef() {
        val nextImplementationIndex: AtomicInteger = AtomicInteger()
        override val name: String = toGeneric("", paramTypes).ifEmpty { "()" } + "_to_" + returnType.name
        override val runtimeName: String = "lambdas/$name/base"
        override val descriptor: List<String> = listOf("L$runtimeName;")
        override val stackSlots: Int get() = 1
        override val isPlural: Boolean get() = false
        override val isReferenceType: Boolean get() = true
        override val hasStaticConstructor: Boolean get() = false
        override val primarySupertype: TypeDef = getBasicBuiltin(ObjectType, typeCache)
        override val supertypes: List<TypeDef> get() = listOf(primarySupertype)
        override val fields: List<FieldDef> get() = listOf()
        override val methods: List<MethodDef> = listOf(
            MethodDef.InterfaceMethodDef(pub = true, static = false, this, "invoke", returnType, paramTypes)
        )
        override val generics: List<TypeDef> = paramTypes + returnType
    }

    // An implementation of a TypeDef.Func.
    // Creation of this type def is split into two phases. In the first phase,
    // it's provided with the scope of the lambda's creation, and it makes
    // a field for each local variable in scope. Then, later, once the
    // "implementation" method def has been type-checked, unused fields
    // are removed, leaving only the ones which are closed over. This
    // also allows the constructor method to be generated.
    class FuncImplementation(functionToImplement: Func, scope: ConsMap<String, VariableBinding>, implementation: MethodDef.SnuggleMethodDef): TypeDef() {
        override val name: String = "impl_" + functionToImplement.nextImplementationIndex.getAndIncrement()
        override val runtimeName: String = "lambdas/" + functionToImplement.name + "/" + name
        override val descriptor: List<String> = listOf("L$runtimeName;")
        override val stackSlots: Int = 1
        override val isPlural: Boolean = false
        override val isReferenceType: Boolean = true
        override val hasStaticConstructor: Boolean = false
        override val primarySupertype: TypeDef = functionToImplement
        override val supertypes: List<TypeDef> = listOf(primarySupertype)
        override val generics: List<TypeDef> = functionToImplement.generics
        // Important: the fields and methods are MUTABLE list, so some can be removed/added later, in the
        // second phase of creation.
        override val fields: List<FieldDef> = scope.flattened().mapTo(mutableListOf()) { (name, binding) ->
            FieldDef.BuiltinField(pub = true, static = false, mutable = false, name, null, binding.type) }
        override val methods: List<MethodDef> = mutableListOf(implementation)
        // Finishes the creation of the type, and returns the generated constructor
        fun finishCreation(typeCache: TypeDefCache): MethodDef {
            // Finish the creation of this type def. This involves removing any unnecessary fields as well as
            // adding the constructor method.
            // Fetch the lazy body:
            val body = (methods[0] as MethodDef.SnuggleMethodDef).lazyBody.value
            // Find the fields used on the "this" in the lazy body
            val thisFieldUsages = findThisFieldAccesses(body)
            // Remove all fields from this type that aren't used in the body
            (this.fields as MutableList<FieldDef>).retainAll(thisFieldUsages)
            // Create the constructor, as a custom method def
            val unitType = getUnit(typeCache)
            val constructorPromise: Promise<MethodDef> = Promise() // Self-reference constructor
            val generatedConstructor = MethodDef.CustomMethodDef(pub = false, static = false, owningType = this,
                "new", "<init>", unitType, this.fields.map { it.type },
            {
                // Lowerer. Just call the constructor with an invokespecial
                sequenceOf(Instruction.MethodCall.Special(constructorPromise.expect()))
            }) {
                // Outputter. Raw output to the class writer
                val desc = getMethodDescriptor(constructorPromise.expect())
                val writer = it.visitMethod(Opcodes.ACC_PUBLIC, "<init>", desc, null, null)
                writer.visitCode()
                writer.visitVarInsn(Opcodes.ALOAD, 0) // Load this
                writer.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false) // Call super()
                var curIndex = 1
                for (field in this.fields) {
                    if (field.type.isPlural) {
                        for ((pathToField, field2) in field.type.recursiveNonStaticFields) {
                            // Load this, load param we're storing, store in ref type, inc index
                            outputInstruction(Instruction.LoadLocal(0, this), writer)
                            outputInstruction(Instruction.LoadLocal(curIndex, field2.type), writer)
                            outputInstruction(Instruction.PutReferenceTypeField(this, field2.type, pathToField), writer)
                            curIndex += field2.type.stackSlots
                        }
                    } else {
                        // Load this, load the param we're storing, store in ref type, inc index
                        outputInstruction(Instruction.LoadLocal(0, this), writer)
                        outputInstruction(Instruction.LoadLocal(curIndex, field.type), writer)
                        outputInstruction(Instruction.PutReferenceTypeField(this, field.type, field.name), writer)
                        curIndex += field.type.stackSlots
                    }
                }
                writer.visitInsn(Opcodes.RETURN)
                writer.visitMaxs(0, 0)
                writer.visitEnd()
            }
            constructorPromise.fulfill(generatedConstructor)
            (this.methods as MutableList<MethodDef>).add(generatedConstructor)
            return generatedConstructor
        }
    }

    class InstantiatedBuiltin(override val builtin: BuiltinType, override val generics: List<TypeDef>, typeCache: TypeDefCache): TypeDef() {
        override val name: String = builtin.name(generics, typeCache)
        override val runtimeName: String? = builtin.runtimeName(generics, typeCache)
        val shouldGenerateClassAtRuntime = builtin.shouldGenerateClassAtRuntime(generics, typeCache)
        override val descriptor: List<String> = builtin.descriptor(generics, typeCache)
        override val stackSlots: Int = builtin.stackSlots(generics, typeCache)
        override val isPlural: Boolean = builtin.isPlural(generics, typeCache)
        override val isReferenceType: Boolean = builtin.isReferenceType(generics, typeCache)
        override val hasStaticConstructor: Boolean = builtin.hasStaticConstructor(generics, typeCache)
        override val primarySupertype: TypeDef? = builtin.getPrimarySupertype(generics, typeCache)
        override val supertypes: List<TypeDef> = builtin.getAllSupertypes(generics, typeCache)
        override val fields: List<FieldDef> = builtin.getFields(generics, typeCache)
        override val methods: List<MethodDef> = builtin.getMethods(generics, typeCache)
    }

    class ClassDef(val loc: Loc, override val name: String, val supertype: TypeDef,
                   override val generics: List<TypeDef>,
                   override val fields: List<FieldDef>,
                   override val methods: List<MethodDef>
    ): TypeDef() {
        override val runtimeName: String get() = name
        override val descriptor: List<String> get() = listOf("L$runtimeName;")
        override val stackSlots: Int get() = 1
        override val isPlural: Boolean get() = false
        override val isReferenceType: Boolean get() = true
        override val hasStaticConstructor: Boolean get() = false
        override val primarySupertype: TypeDef get() = supertype
        override val supertypes: List<TypeDef> = listOf(primarySupertype)
    }

    class StructDef(val loc: Loc, override val name: String,
                   override val generics: List<TypeDef>,
                   override val fields: List<FieldDef>,
                   override val methods: List<MethodDef>
    ): TypeDef() {
        override val runtimeName: String get() = name
        override val descriptor: List<String> = fields.filter { !it.static }.flatMap { it.type.descriptor }
        override val stackSlots: Int get() = fields.filter { !it.static }.sumOf { it.type.stackSlots }
        override val isPlural: Boolean get() = true
        override val isReferenceType: Boolean get() = false
        override val hasStaticConstructor: Boolean get() = true
        override val primarySupertype: TypeDef? get() = null
        override val supertypes: List<TypeDef> = listOf()
    }
}

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
                            val desiredReceiverFields: ((ConsList<ConsList<FieldDef>>) -> ConsList<ConsList<FieldDef>>)?): MethodDef {
        constructor(pub: Boolean, static: Boolean, owningType: TypeDef, name: String, returnType: TypeDef, paramTypes: List<TypeDef>, bytecode: (MethodVisitor) -> Unit)
            : this(pub, static, owningType, name, returnType, paramTypes,
                null,
                { writer, _, _ -> bytecode(writer); },
                null)
    }
    data class ConstMethodDef(override val pub: Boolean, override val owningType: TypeDef, override val name: String, override val returnType: TypeDef, override val paramTypes: List<TypeDef>,
                              val replacer: (TypedExpr.MethodCall) -> TypedExpr): MethodDef {
        override val static: Boolean get() = false
    }
    data class StaticConstMethodDef(override val pub: Boolean, override val owningType: TypeDef, override val name: String, override val returnType: TypeDef, override val paramTypes: List<TypeDef>,
                                    val replacer: (TypedExpr.StaticMethodCall) -> TypedExpr): MethodDef {
        override val static: Boolean get() = true
    }
    // Method def without an implementation, used in things like TypeDef.Func
    data class InterfaceMethodDef(override val pub: Boolean, override val static: Boolean, override val owningType: TypeDef, override val name: String, override val returnType: TypeDef, override val paramTypes: List<TypeDef>): MethodDef
    // A MethodDef which has custom behavior for lowering itself and outputting itself to the ClassWriter
    data class CustomMethodDef(override val pub: Boolean, override val static: Boolean, override val owningType: TypeDef, override val name: String, override val runtimeName: String, override val returnType: TypeDef, override val paramTypes: List<TypeDef>,
                               val lowerer: () -> Sequence<Instruction>,
                               val outputter: (ClassWriter) -> Unit): MethodDef
    // runtimeName field: often, SnuggleMethodDef will need to have a different name
    // at runtime than in the internal representation. These are the case for:
    // - constructors, whose names are changed to "<init>" to match java's requirement
    // - overloaded methods, whose names are changed to have a disambiguation number appended
    //
    // Note that some of these fields are lazily calculated to allow for self-referencing concerns.
    data class SnuggleMethodDef(val loc: Loc, override val pub: Boolean, override val static: Boolean, override val owningType: TypeDef, override val name: String,
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
                                      val returnTypeGetter: EqualityMemoized<List<TypeDef>, Lazy<TypeDef>>,
                                      val paramTypeGetter: EqualityMemoized<List<TypeDef>, Lazy<List<TypeDef>>>,
                                      val runtimeNameGetter: EqualityMemoized<List<TypeDef>, Lazy<String>>,
                                      val lazyBodyGetter: EqualityMemoized<List<TypeDef>, Lazy<TypedExpr>>
        )
        : GenericMethodDef<SnuggleMethodDef>(numGenerics) {
            override fun specialize(generics: List<TypeDef>): SnuggleMethodDef = SnuggleMethodDef(
                loc, pub, static, owningType, name, returnTypeGetter(generics),
                paramTypeGetter(generics), runtimeNameGetter(generics), lazyBodyGetter(generics)
            )
        }

    }
}

sealed interface FieldDef {
    val pub: Boolean
    val static: Boolean
    val mutable: Boolean
    val pluralOffset: Int? // If the owning type is plural, and this is non-static, this should be the field's offset.
    val name: String
    val type: TypeDef

    data class BuiltinField(
        override val pub: Boolean, override val static: Boolean, override val mutable: Boolean,
        override val name: String, override val pluralOffset: Int?, override val type: TypeDef
    ): FieldDef
    data class SnuggleFieldDef(
        val loc: Loc, override val pub: Boolean, override val static: Boolean, override val mutable: Boolean,
        override val name: String, override val pluralOffset: Int?, override val type: TypeDef
    ): FieldDef
}