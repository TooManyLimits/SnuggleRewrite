package representation.asts.typed

import builtins.BuiltinType
import org.objectweb.asm.MethodVisitor
import representation.passes.lexing.Loc
import representation.passes.typing.TypeDefCache
import util.Promise
import util.toGeneric

// A type definition, instantiated. No more generics left to fill.
sealed interface TypeDef {

    val name: String
    val runtimeName: String?
    val descriptor: List<String>

    val stackSlots: Int

    val primarySupertype: TypeDef?
    val supertypes: List<TypeDef>

    val fields: List<FieldDef>
    val methods: List<MethodDef>

    // For most types, their builtin is null. For instantiated builtins,
    // it's non-null. Including this property greatly reduces boilerplate
    // in some situations, where we need to check if a type is an instance
    // of InstantiatedBuiltin and smart-cast it before checking its builtin.
    val builtin: BuiltinType? get() = null

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
        if (this is TypeDef.Indirection) promise.expect() else this

    // An indirection which points to another TypeDef. Needed because of
    // self-references inside of types, i.e. if class A uses the type
    // A inside its definition (which is likely), the things in A need to
    // refer to A, while A is still being constructed.
    //
    // However, certain aspects of the self-referring type def need to be
    // known while the promise is still being fulfilled.
    // This includes the # of stack slots.
    data class Indirection(override val stackSlots: Int, val promise: Promise<TypeDef> = Promise()): TypeDef {
        override val name: String get() = promise.expect().name
        override val runtimeName: String? get() = promise.expect().runtimeName
        override val descriptor: List<String> get() = promise.expect().descriptor
        override val primarySupertype: TypeDef? get() = promise.expect().primarySupertype
        override val supertypes: List<TypeDef> get() = promise.expect().supertypes
        override val fields: List<FieldDef> get() = promise.expect().fields
        override val methods: List<MethodDef> get() = promise.expect().methods
        override val builtin: BuiltinType? get() = promise.expect().builtin
    }

    class InstantiatedBuiltin(override val builtin: BuiltinType, val generics: List<TypeDef>, typeCache: TypeDefCache): TypeDef {
        override val name: String = toGeneric(builtin.name, generics)
        override val runtimeName: String? = builtin.runtimeName?.let { toGeneric(it, generics) }
        override val descriptor: List<String> = builtin.descriptor
        override val stackSlots: Int = builtin.stackSlots
        override val primarySupertype: TypeDef? = builtin.getPrimarySupertype(generics, typeCache)
        override val supertypes: List<TypeDef> = builtin.getAllSupertypes(generics, typeCache)
        override val fields: List<FieldDef> = builtin.getFields(generics, typeCache)
        override val methods: List<MethodDef> = builtin.getMethods(generics, typeCache)
    }

    class ClassDef(val loc: Loc, override val name: String, val supertype: TypeDef,
                   val generics: List<TypeDef>,
                   override val fields: List<FieldDef>,
                   override val methods: List<MethodDef>
    ): TypeDef {
        override val runtimeName: String get() = name
        override val descriptor: List<String> get() = listOf("L$runtimeName;")
        override val stackSlots: Int get() = 1
        override val primarySupertype: TypeDef get() = supertype
        override val supertypes: List<TypeDef> = listOf(primarySupertype)
    }
}

// A method definition,
sealed interface MethodDef {
    val pub: Boolean //
    val static: Boolean
    val owningType: TypeDef
    val name: String
    val returnType: TypeDef
    val argTypes: List<TypeDef>

    // Override vals on the first line, important things on later lines

    data class BytecodeMethodDef(override val pub: Boolean, override val static: Boolean, override val owningType: TypeDef, override val name: String, override val returnType: TypeDef, override val argTypes: List<TypeDef>,
                                 val bytecode: (MethodVisitor) -> Unit): MethodDef
    data class ConstMethodDef(override val pub: Boolean, override val owningType: TypeDef, override val name: String, override val returnType: TypeDef, override val argTypes: List<TypeDef>,
                              val replacer: (TypedExpr.MethodCall) -> TypedExpr): MethodDef {
        override val static: Boolean get() = false
    }
    data class StaticConstMethodDef(override val pub: Boolean, override val owningType: TypeDef, override val name: String, override val returnType: TypeDef, override val argTypes: List<TypeDef>,
                              val replacer: (TypedExpr.StaticMethodCall) -> TypedExpr): MethodDef {
        override val static: Boolean get() = true
    }
    data class SnuggleMethodDef(override val pub: Boolean, override val static: Boolean, override val owningType: TypeDef, override val name: String, override val returnType: TypeDef, override val argTypes: List<TypeDef>,
                                val loc: Loc, val body: TypedExpr): MethodDef
}

sealed interface FieldDef {
    val pub: Boolean
    val static: Boolean
    val name: String
    val type: TypeDef

    data class BuiltinField(override val pub: Boolean, override val static: Boolean, override val type: TypeDef, override val name: String):
        FieldDef
}