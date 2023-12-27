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
    val stackSlots: Int

    val fields: List<FieldDef>
    val methods: List<MethodDef>

    fun isSubtype(other: TypeDef, cache: TypeDefCache): Boolean {
        //TODO
        return true
    }

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
        override val fields: List<FieldDef> get() = promise.expect().fields
        override val methods: List<MethodDef> get() = promise.expect().methods
    }

    class InstantiatedBuiltin(builtin: BuiltinType, val generics: List<TypeDef>, typeCache: TypeDefCache): TypeDef {
        override val name: String = toGeneric(builtin.name, generics)
        override val runtimeName: String? = builtin.runtimeName?.let { toGeneric(it, generics) }
        override val stackSlots: Int = builtin.stackSlots
        override val fields: List<FieldDef> = builtin.getFields(generics, typeCache)
        override val methods: List<MethodDef> = builtin.getMethods(generics, typeCache)
    }

    class ClassDef(val loc: Loc, override val name: String, val supertype: TypeDef,
                   val generics: List<TypeDef>,
                   override val fields: List<FieldDef>,
                   override val methods: List<MethodDef>
    ): TypeDef {
        override val runtimeName: String get() = name
        override val stackSlots: Int get() = 1
    }



}

// A method definition,
sealed interface MethodDef {
    val pub: Boolean //
    val static: Boolean
    val name: String
    val returnType: TypeDef
    val argTypes: List<TypeDef>

    // Override vals on the first line, important things on later lines

    data class BytecodeMethodDef(override val pub: Boolean, override val static: Boolean, override val name: String, override val returnType: TypeDef, override val argTypes: List<TypeDef>,
                                 val bytecode: (MethodVisitor) -> Unit): MethodDef
    data class ConstMethodDef(override val pub: Boolean, override val static: Boolean, override val name: String, override val returnType: TypeDef, override val argTypes: List<TypeDef>,
                              val bytecode: (TypedExpr.MethodCall) -> TypedExpr): MethodDef
    data class SnuggleMethodDef(override val pub: Boolean, override val static: Boolean, override val name: String, override val returnType: TypeDef, override val argTypes: List<TypeDef>,
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