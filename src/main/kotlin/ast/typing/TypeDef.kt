package ast.typing

import builtins.BuiltinType
import util.Promise
import util.toGeneric

// A type definition, instantiated. No more generics left to fill.
sealed interface TypeDef {

    val name: String
    val runtimeName: String?

    val fields: List<FieldDef>
    val methods: List<MethodDef>

    fun isSubtype(other: TypeDef, cache: TypeDefCache): Boolean {
        //TODO
        return true
    }

    class InstantiatedBuiltin(builtin: BuiltinType, private val generics: List<TypeDef>, typeCache: TypeDefCache): TypeDef {
        override val name: String = toGeneric(builtin.name, generics)
        override val runtimeName: String? = builtin.runtimeName?.let { toGeneric(it, generics) }
        override val fields: List<FieldDef> = builtin.getFields(generics, typeCache)
        override val methods: List<MethodDef> = builtin.getMethods(generics, typeCache)
    }

    // An indirection which points to another TypeDef. Needed because of
    // self-references inside of types, i.e. if class A uses the type
    // A inside its definition (which is likely), the things in A need to
    // refer to A, while A is still being constructed.
    data class Indirection(val promise: Promise<TypeDef> = Promise()): TypeDef {
        override val name: String get() = promise.expect().name
        override val runtimeName: String? get() = promise.expect().runtimeName
        override val fields: List<FieldDef> get() = promise.expect().fields
        override val methods: List<MethodDef> get() = promise.expect().methods
    }
}

// A method definition,
sealed interface MethodDef {
    val pub: Boolean //
    val static: Boolean
    val name: String
    val returnType: TypeDef
    val argTypes: List<TypeDef>

    // Override vals on the first line, important things on the second line

    data class BytecodeMethodDef(override val pub: Boolean, override val static: Boolean, override val name: String, override val returnType: TypeDef, override val argTypes: List<TypeDef>,
                                 val bytecode: (Array<Byte>) -> Unit): MethodDef
    data class ConstMethodDef(override val pub: Boolean, override val static: Boolean, override val name: String, override val returnType: TypeDef, override val argTypes: List<TypeDef>,
                              val func: (TypedExpr.MethodCall) -> TypedExpr): MethodDef
}

sealed interface FieldDef {
    val pub: Boolean
    val static: Boolean
    val name: String
    val type: TypeDef

    data class BuiltinField(override val pub: Boolean, override val static: Boolean, override val type: TypeDef, override val name: String): FieldDef
}