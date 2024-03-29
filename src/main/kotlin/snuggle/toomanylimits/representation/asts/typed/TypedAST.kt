package snuggle.toomanylimits.representation.asts.typed

import snuggle.toomanylimits.representation.asts.resolved.ResolvedType
import snuggle.toomanylimits.representation.passes.lexing.Loc

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
class TypedAST(
    val allFiles: Map<String, TypedFile> // The files (files no longer store their names)
)

class TypedFile(val name: String, val code: TypedExpr)

// TypeDef and related things were moved to another file, TypeDef.kt!

// A couple of these Exprs have a special param "maxVariable". This refers
// to the highest index of any variable in scope at the time. This is used
// later while lowering, for storing temporaries as local variables
// (usually in relation to plural types)

sealed interface TypedExpr {
    val loc: Loc
    val type: TypeDef

    class Import(override val loc: Loc, val file: String, override val type: TypeDef): TypedExpr

    class Block(override val loc: Loc, val exprs: List<TypedExpr>, override val type: TypeDef): TypedExpr
    class Declaration(override val loc: Loc, val pattern: TypedInfalliblePattern, val initializer: TypedExpr, override val type: TypeDef): TypedExpr
    class Assignment(override val loc: Loc, val lhs: TypedExpr, val rhs: TypedExpr, val maxVariable: Int, override val type: TypeDef): TypedExpr

    class Return(override val loc: Loc, val rhs: TypedExpr, override val type: TypeDef): TypedExpr

    class If(override val loc: Loc, val cond: TypedExpr, val ifTrue: TypedExpr, val ifFalse: TypedExpr, override val type: TypeDef): TypedExpr
    class While(override val loc: Loc, val cond: TypedExpr, val body: TypedExpr, override val type: TypeDef): TypedExpr

    class Is(override val loc: Loc, val lhs: TypedExpr, val pattern: TypedFalliblePattern, override val type: TypeDef): TypedExpr
    class As(override val loc: Loc, val forced: Boolean, val lhs: TypedExpr, override val type: TypeDef): TypedExpr

    class Literal(override val loc: Loc, val value: Any, override val type: TypeDef): TypedExpr
    class Variable(override val loc: Loc, val mutable: Boolean, val name: String, val variableIndex: Int, override val type: TypeDef): TypedExpr

    class FieldAccess(override val loc: Loc, val receiver: TypedExpr, val fieldName: String, val fieldDef: FieldDef, val maxVariable: Int, override val type: TypeDef): TypedExpr
    class StaticFieldAccess(override val loc: Loc, val receiverType: TypeDef, val fieldName: String, val fieldDef: FieldDef, override val type: TypeDef): TypedExpr
    class MethodCall(override val loc: Loc, val receiver: TypedExpr, val methodName: String, val args: List<TypedExpr>, val methodDef: MethodDef, val maxVariable: Int, override val type: TypeDef): TypedExpr
    class StaticMethodCall(override val loc: Loc, val receiverType: TypeDef, val methodName: String, val args: List<TypedExpr>, val methodDef: MethodDef, val maxVariable: Int, override val type: TypeDef): TypedExpr
    class SuperMethodCall(override val loc: Loc, val thisVariableIndex: Int, val methodDef: MethodDef, val args: List<TypedExpr>, val maxVariable: Int, override val type: TypeDef): TypedExpr
    class ClassConstructorCall(override val loc: Loc, val methodDef: MethodDef, val args: List<TypedExpr>, val maxVariable: Int, override val type: TypeDef): TypedExpr
    class RawStructConstructor(override val loc: Loc, val fieldValues: List<TypedExpr>, override val type: TypeDef): TypedExpr
}

sealed interface TypedInfalliblePattern {
    val loc: Loc
    val type: TypeDef
    // After typing is over, the binding pattern _always knows its type_,
    // rather than having an _optional_ type annotation.
    class Empty(override val loc: Loc, override val type: TypeDef): TypedInfalliblePattern
    class Binding(override val loc: Loc, override val type: TypeDef, val name: String, val isMut: Boolean, val variableIndex: Int): TypedInfalliblePattern
    class Tuple(override val loc: Loc, override val type: TypeDef, val elements: List<TypedInfalliblePattern>): TypedInfalliblePattern
}

sealed interface TypedFalliblePattern {
    val loc: Loc

    class IsType(override val loc: Loc, val isMut: Boolean, val varName: String?, val variableIndex: Int, val typeToCheck: TypeDef): TypedFalliblePattern
    class LiteralPattern(override val loc: Loc, val value: Any, val literalType: TypeDef): TypedFalliblePattern
    class Tuple(override val loc: Loc, val elements: List<TypedFalliblePattern>): TypedFalliblePattern
}