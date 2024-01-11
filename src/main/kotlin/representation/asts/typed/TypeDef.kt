package representation.asts.typed

import builtins.BuiltinType
import org.objectweb.asm.MethodVisitor
import representation.passes.lexing.Loc
import representation.passes.typing.TypeDefCache
import util.Promise
import util.caching.EqualityCache
import util.caching.EqualityMemoized
import util.toGeneric

// A type definition, instantiated. No more generics left to fill.
sealed class TypeDef {

    abstract val name: String
    abstract val runtimeName: String?
    abstract val descriptor: List<String>

    abstract val stackSlots: Int
    abstract val isPlural: Boolean
    abstract val isReferenceType: Boolean

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
        if (thisUnwrapped == otherUnwrapped) return true
        for (supertype in supertypes)
            if (supertype.isSubtype(otherUnwrapped))
                return true
        return false
    }

    // Unwrap the typedef's indirections
    fun unwrap(): TypeDef =
        if (this is Indirection) promise.expect().unwrap() else this

    // An indirection which points to another TypeDef. Needed because of
    // self-references inside of types, i.e. if class A uses the type
    // A inside its definition (which is likely), the things in A need to
    // refer to A, while A is still being constructed.
    data class Indirection(val promise: Promise<TypeDef> = Promise()): TypeDef() {
        override val name: String get() = promise.expect().name
        override val runtimeName: String? get() = promise.expect().runtimeName
        override val descriptor: List<String> get() = promise.expect().descriptor
        override val stackSlots: Int get() = promise.expect().stackSlots
        override val isPlural: Boolean get() = promise.expect().isPlural
        override val isReferenceType: Boolean get() = promise.expect().isReferenceType
        override val primarySupertype: TypeDef? get() = promise.expect().primarySupertype
        override val supertypes: List<TypeDef> get() = promise.expect().supertypes
        override val fields: List<FieldDef> get() = promise.expect().fields
        override val methods: List<MethodDef> get() = promise.expect().methods
        override val generics: List<TypeDef> get() = promise.expect().generics
        override val builtin: BuiltinType? get() = promise.expect().builtin
    }

    data class Tuple(val innerTypes: List<TypeDef>): TypeDef() {
        override val name: String = toGeneric("", innerTypes).ifEmpty { "()" }
        override val runtimeName: String = "tuples/$name"
        override val descriptor: List<String> = innerTypes.flatMap { it.descriptor }
        override val stackSlots: Int = innerTypes.sumOf { it.stackSlots }
        override val isPlural: Boolean get() = true
        override val isReferenceType: Boolean get() = false
        override val primarySupertype: TypeDef? get() = null
        override val supertypes: List<TypeDef> get() = listOf()
        override val fields: List<FieldDef> = run {
            var currentIndex = 0
            innerTypes.mapIndexed { index, type ->
                FieldDef.BuiltinField(pub = true, static = false, "v$index", currentIndex, type)
                    .also { currentIndex += type.stackSlots }
            }
        }
        override val methods: List<MethodDef> get() = listOf()
        override val generics: List<TypeDef> get() = listOf()
    }

    class InstantiatedBuiltin(override val builtin: BuiltinType, override val generics: List<TypeDef>, typeCache: TypeDefCache): TypeDef() {
        override val name: String = builtin.name(generics, typeCache)
        override val runtimeName: String? = builtin.runtimeName(generics, typeCache)
        override val descriptor: List<String> = builtin.descriptor(generics, typeCache)
        override val stackSlots: Int = builtin.stackSlots(generics, typeCache)
        override val isPlural: Boolean = builtin.isPlural(generics, typeCache)
        override val isReferenceType: Boolean = builtin.isReferenceType(generics, typeCache)
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

    data class BytecodeMethodDef(override val pub: Boolean, override val static: Boolean, override val owningType: TypeDef, override val name: String, override val returnType: TypeDef, override val paramTypes: List<TypeDef>,
                                 val bytecode: (MethodVisitor) -> Unit): MethodDef
    data class ConstMethodDef(override val pub: Boolean, override val owningType: TypeDef, override val name: String, override val returnType: TypeDef, override val paramTypes: List<TypeDef>,
                              val replacer: (TypedExpr.MethodCall) -> TypedExpr): MethodDef {
        override val static: Boolean get() = false
    }
    data class StaticConstMethodDef(override val pub: Boolean, override val owningType: TypeDef, override val name: String, override val returnType: TypeDef, override val paramTypes: List<TypeDef>,
                                    val replacer: (TypedExpr.StaticMethodCall) -> TypedExpr): MethodDef {
        override val static: Boolean get() = true
    }

    // runtimeName field: often, SnuggleMethodDef will need to have a different name
    // at runtime than in the internal representation. These are the case for:
    // - constructors, whose names are changed to "<init>" to match java's requirement
    // - overloaded methods, whose names are changed to have a disambiguation number appended
    //
    // Note that body and runtime name are lazily calculated to allow for self-referencing concerns.
    data class SnuggleMethodDef(val loc: Loc, override val pub: Boolean, override val static: Boolean, override val owningType: TypeDef, override val name: String,
                                override val returnType: TypeDef,
                                override val paramTypes: List<TypeDef>,
                                val runtimeNameGetter: Lazy<String>,
                                val lazyBody: Lazy<TypedExpr>)
        : MethodDef {
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
                                      val returnTypeGetter: EqualityMemoized<List<TypeDef>, TypeDef>,
                                      val paramTypeGetter: EqualityMemoized<List<TypeDef>, List<TypeDef>>,
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
    val pluralOffset: Int? // If the owning type is plural, and this is non-static, this should be the field's offset.
    val name: String
    val type: TypeDef

    data class BuiltinField(
        override val pub: Boolean, override val static: Boolean,
        override val name: String, override val pluralOffset: Int?, override val type: TypeDef
    ): FieldDef
    data class SnuggleFieldDef(
        val loc: Loc, override val pub: Boolean, override val static: Boolean,
        override val name: String, override val pluralOffset: Int?, override val type: TypeDef
    ): FieldDef
}