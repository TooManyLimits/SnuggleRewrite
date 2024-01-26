package representation.asts.typed

import representation.passes.lexing.Loc

/**
 * Previous AST stage was the ResolvedAST.
 *
 * The AST is now typed and instantiated.
 *
 * "Instantiated" means that generics have been replaced with
 * concrete, known types.
 * "Typed" means that every expression knows what type it is.
 *
 * You may ask, "Why are these steps merged into one, instead
 * of being done separately?" The reason is that these two
 * processes, instantiation and typing, can call upon each
 * other recursively. While type-checking something, you may
 * need to instantiate something else, then type check the thing
 * that was just instantiated.
 *
 * I wish it were possible to split these tasks up into multiple
 * pieces, but unfortunately it isn't as far as I can tell. I only
 * hope that the code being (mostly) immutable this time around will
 * lessen the sense of dread that one feels when looking at it.
 */

// The full typed AST of a program
data class TypedAST(
    val allFiles: Map<String, TypedFile> // The files (files no longer store their names)
)

data class TypedFile(val name: String, val code: TypedExpr)

// TypeDef and related things were moved to another file, TypeDef.kt!

// A couple of these Exprs have a special param "maxVariable". This refers
// to the highest index of any variable in scope at the time. This is used
// later while lowering, for storing temporaries as local variables
// (usually in relation to plural types)

sealed interface TypedExpr {
    val loc: Loc
    val type: TypeDef

    data class Import(override val loc: Loc, val file: String, override val type: TypeDef): TypedExpr

    data class Block(override val loc: Loc, val exprs: List<TypedExpr>, override val type: TypeDef): TypedExpr
    data class Declaration(override val loc: Loc, val pattern: TypedPattern, val initializer: TypedExpr, override val type: TypeDef): TypedExpr
    data class Assignment(override val loc: Loc, val lhs: TypedExpr, val rhs: TypedExpr, val maxVariable: Int, override val type: TypeDef): TypedExpr

    data class Return(override val loc: Loc, val rhs: TypedExpr, override val type: TypeDef): TypedExpr

    data class If(override val loc: Loc, val cond: TypedExpr, val ifTrue: TypedExpr, val ifFalse: TypedExpr, override val type: TypeDef): TypedExpr
    data class While(override val loc: Loc, val cond: TypedExpr, val body: TypedExpr, override val type: TypeDef): TypedExpr

    data class Literal(override val loc: Loc, val value: Any, override val type: TypeDef): TypedExpr
    data class Variable(override val loc: Loc, val mutable: Boolean, val name: String, val variableIndex: Int, override val type: TypeDef): TypedExpr

    data class FieldAccess(override val loc: Loc, val receiver: TypedExpr, val fieldName: String, val fieldDef: FieldDef, val maxVariable: Int, override val type: TypeDef): TypedExpr
    data class StaticFieldAccess(override val loc: Loc, val receiverType: TypeDef, val fieldName: String, val fieldDef: FieldDef, override val type: TypeDef): TypedExpr
    data class MethodCall(override val loc: Loc, val receiver: TypedExpr, val methodName: String, val args: List<TypedExpr>, val methodDef: MethodDef, val maxVariable: Int, override val type: TypeDef): TypedExpr
    data class StaticMethodCall(override val loc: Loc, val receiverType: TypeDef, val methodName: String, val args: List<TypedExpr>, val methodDef: MethodDef, val maxVariable: Int, override val type: TypeDef): TypedExpr
    data class SuperMethodCall(override val loc: Loc, val thisVariableIndex: Int, val methodDef: MethodDef, val args: List<TypedExpr>, val maxVariable: Int, override val type: TypeDef): TypedExpr
    data class ClassConstructorCall(override val loc: Loc, val methodDef: MethodDef, val args: List<TypedExpr>, val maxVariable: Int, override val type: TypeDef): TypedExpr
    data class RawStructConstructor(override val loc: Loc, val fieldValues: List<TypedExpr>, override val type: TypeDef): TypedExpr
}

sealed interface TypedPattern {
    val loc: Loc
    val type: TypeDef
    // After typing is over, the binding pattern _always knows its type_,
    // rather than having an _optional_ type annotation.
    data class EmptyPattern(override val loc: Loc, override val type: TypeDef): TypedPattern
    data class BindingPattern(override val loc: Loc, override val type: TypeDef, val name: String, val isMut: Boolean, val variableIndex: Int): TypedPattern
    data class TuplePattern(override val loc: Loc, override val type: TypeDef, val elements: List<TypedPattern>): TypedPattern
}