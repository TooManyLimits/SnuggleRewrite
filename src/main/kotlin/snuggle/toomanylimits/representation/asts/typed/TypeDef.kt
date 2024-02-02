package snuggle.toomanylimits.representation.asts.typed

import snuggle.toomanylimits.builtins.ArrayType
import snuggle.toomanylimits.builtins.BuiltinType
import snuggle.toomanylimits.builtins.ObjectType
import snuggle.toomanylimits.builtins.OptionType
import org.objectweb.asm.Opcodes
import snuggle.toomanylimits.representation.asts.ir.Instruction
import snuggle.toomanylimits.representation.asts.resolved.ResolvedTypeDef
import snuggle.toomanylimits.representation.passes.lexing.Loc
import snuggle.toomanylimits.representation.passes.output.getMethodDescriptor
import snuggle.toomanylimits.representation.passes.output.outputInstruction
import snuggle.toomanylimits.representation.passes.typing.*
import snuggle.toomanylimits.util.ConsMap
import snuggle.toomanylimits.util.Promise
import snuggle.toomanylimits.util.toGeneric
import java.util.concurrent.atomic.AtomicInteger

// A type definition, instantiated. No more generics left to fill.
sealed class TypeDef {

    abstract val name: String // The name of this type at compile time. Used in error reporting.
    abstract val runtimeName: String? // The name of this type at runtime. Types which don't have generated classes at runtime, like i32, are null.
    abstract val descriptor: List<String> // The descriptor of this type. If plural, has multiple elements.

    abstract val stackSlots: Int // The number of stack slots this takes up. 1 for reference types and small primitives, 2 for longs/doubles, potentially more or less for plurals.
    abstract val isPlural: Boolean // Whether this type is plural, like a struct or tuple. If true, it cannot be a reference type.
    abstract val isReferenceType: Boolean // Whether this type is a reference type. If true, it cannot be plural.
    abstract val extensible: Boolean // Whether this type is extensible. If true, then this must be a reference type.

    // If true, constructors are "static fn new() -> Type". Otherwise, "fn new() -> Unit"
    abstract val hasStaticConstructor: Boolean

    abstract val primarySupertype: TypeDef? // The "primary" supertype of this type, used for inheritance.
    abstract val supertypes: List<TypeDef> // The set of all supertypes for this type, used for type checking.

    abstract val fields: List<FieldDef> // The fields of this type.
    abstract val methods: List<MethodDef> // The methods of this type.

    // The generics with which this type def was instantiated. For tuples and functions, it's the types used in their creation.
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
    val recursivePluralFields: List<Pair<String, FieldDef>> by lazy {
        if (isPlural)
            nonStaticFields.flatMap { myField ->
                if (myField.type.isPlural)
                    myField.type.recursivePluralFields.map { (myField.name + "$" + it.first) to it.second }
                else
                    listOf(myField.name to myField)
            }
        else
            throw IllegalStateException("Should never ask non-plural type for recursivePluralFields - bug in compiler, please report!")
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
        override val extensible: Boolean get() = promise.expect().extensible
        override val hasStaticConstructor: Boolean get() = promise.expect().hasStaticConstructor
        override val primarySupertype: TypeDef? get() = promise.expect().primarySupertype
        override val supertypes: List<TypeDef> get() = promise.expect().supertypes
        override val fields: List<FieldDef> get() = promise.expect().fields
        override val methods: List<MethodDef> get() = promise.expect().methods
        override val generics: List<TypeDef> get() = promise.expect().generics
        override val builtin: BuiltinType? get() = promise.expect().builtin

        override fun toString(): String {
            return "Indirect(${promise.expect().name})"
        }
    }

    class Tuple(val innerTypes: List<TypeDef>): TypeDef() {
        override val name: String = toGeneric("", innerTypes).ifEmpty { "()" }
        override val runtimeName: String = "tuples/$name"
        override val descriptor: List<String> = innerTypes.flatMap { it.descriptor }
        override val stackSlots: Int = innerTypes.sumOf { it.stackSlots }
        override val isPlural: Boolean get() = true
        override val isReferenceType: Boolean get() = false
        override val extensible: Boolean get() = false
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

    class Func(val paramTypes: List<TypeDef>, val returnType: TypeDef, typeCache: TypingCache): TypeDef() {
        val nextImplementationIndex: AtomicInteger = AtomicInteger()
        override val name: String = toGeneric("", paramTypes).ifEmpty { "()" } + "_to_" + returnType.name
        override val runtimeName: String = "lambdas/$name/base"
        override val descriptor: List<String> = listOf("L$runtimeName;")
        override val stackSlots: Int get() = 1
        override val isPlural: Boolean get() = false
        override val isReferenceType: Boolean get() = true
        override val extensible: Boolean get() = false
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
        override val extensible: Boolean get() = false
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
        fun finishCreation(typeCache: TypingCache): MethodDef {
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
            lowerer = {
                // Lowerer. Just call the constructor with an invokespecial
                sequenceOf(Instruction.MethodCall.Special(constructorPromise.expect()))
            },
            outputter = {
                // Outputter. Raw output to the class writer
                val desc = getMethodDescriptor(constructorPromise.expect())
                val writer = it.visitMethod(Opcodes.ACC_PUBLIC, "<init>", desc, null, null)
                writer.visitCode()
                writer.visitVarInsn(Opcodes.ALOAD, 0) // Load this
                writer.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false) // Call super()
                var curIndex = 1
                for (field in this.fields) {
                    if (field.type.isPlural) {
                        for ((pathToField, field2) in field.type.recursivePluralFields) {
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
            })
            constructorPromise.fulfill(generatedConstructor)
            (this.methods as MutableList<MethodDef>).add(generatedConstructor)
            return generatedConstructor
        }
    }

    class InstantiatedBuiltin(override val builtin: BuiltinType, override val generics: List<TypeDef>, override val typeHead: ResolvedTypeDef,
                              typeCache: TypingCache): TypeDef(), FromTypeHead {
        override val name: String = builtin.name(generics, typeCache)
        override val runtimeName: String? = builtin.runtimeName(generics, typeCache)
        val shouldGenerateClassAtRuntime = builtin.shouldGenerateClassAtRuntime(generics, typeCache)
        override val descriptor: List<String> = builtin.descriptor(generics, typeCache)
        override val stackSlots: Int = builtin.stackSlots(generics, typeCache)
        override val isPlural: Boolean = builtin.isPlural(generics, typeCache)
        override val isReferenceType: Boolean = builtin.isReferenceType(generics, typeCache)
        override val extensible: Boolean = builtin.extensible(generics, typeCache)
        override val hasStaticConstructor: Boolean = builtin.hasStaticConstructor(generics, typeCache)
        override val primarySupertype: TypeDef? = builtin.getPrimarySupertype(generics, typeCache)
        override val supertypes: List<TypeDef> = builtin.getAllSupertypes(generics, typeCache)
        override val fields: List<FieldDef> = builtin.getFields(generics, typeCache)
        override val methods: List<MethodDef> = builtin.getMethods(generics, typeCache)
    }

    class ClassDef(val loc: Loc, name: String, val supertype: TypeDef,
                   override val generics: List<TypeDef>,
                   override val fields: List<FieldDef>,
                   override val methods: List<MethodDef>,
                   override val typeHead: ResolvedTypeDef
    ): TypeDef(), FromTypeHead {
        override val name: String = toGeneric("snuggle/" + loc.fileName + "/" + name, generics)
        override val runtimeName: String get() = name
        override val descriptor: List<String> get() = listOf("L$runtimeName;")
        override val stackSlots: Int get() = 1
        override val isPlural: Boolean get() = false
        override val isReferenceType: Boolean get() = true
        override val extensible: Boolean get() = true
        override val hasStaticConstructor: Boolean get() = false
        override val primarySupertype: TypeDef get() = supertype
        override val supertypes: List<TypeDef> = listOf(primarySupertype)
    }

    class StructDef(val loc: Loc, name: String,
                   override val generics: List<TypeDef>,
                   override val fields: List<FieldDef>,
                   override val methods: List<MethodDef>,
                   override val typeHead: ResolvedTypeDef
    ): TypeDef(), FromTypeHead {
        override val name: String = "snuggle/" + loc.fileName + "/" + name
        override val runtimeName: String get() = name
        override val descriptor: List<String> = fields.filter { !it.static }.flatMap { it.type.descriptor }
        override val stackSlots: Int get() = fields.filter { !it.static }.sumOf { it.type.stackSlots }
        override val isPlural: Boolean get() = true
        override val isReferenceType: Boolean get() = false
        override val extensible: Boolean get() = false
        override val hasStaticConstructor: Boolean get() = true
        override val primarySupertype: TypeDef? get() = null
        override val supertypes: List<TypeDef> = listOf()
    }

    /**
     * Impl blocks only contain snuggle methods. Furthermore, their methods should all be tagged with the "staticOverride"
     * set to true.
     */
    class ImplBlockBackingDef(val loc: Loc, override val name: String, override val generics: List<TypeDef>, override val methods: List<MethodDef>): TypeDef() {
        override val fields: List<FieldDef> get() = listOf()
        override val runtimeName: String get() = name
        override val descriptor: List<String> get() = listOf()
        override val stackSlots: Int get() = 0
        override val isPlural: Boolean get() = true
        override val isReferenceType: Boolean get() = false
        override val extensible: Boolean get() = false
        override val hasStaticConstructor: Boolean get() = true
        override val primarySupertype: TypeDef? get() = null
        override val supertypes: List<TypeDef> get() = listOf()
    }

}

// For TypeDefs that came from a typehead (ResolvedTypeDef) + some generics.
// Classes, Structs, Builtins.
interface FromTypeHead {
    val typeHead: ResolvedTypeDef
}
