package representation.asts.resolved

import builtins.BuiltinType
import representation.passes.lexing.Loc
import util.ConsList
import util.Promise
import java.util.*

/**
 * Here, the raw string data of the previous AST (ParsedAST)
 * is resolved to point at the actual types that they refer to.
 *
 * For example, where before we'd have a:
 * - ParsedType.Basic("List", [ParsedType.Basic("String", [])]),
 * we would now have a
 * - ResolvedType.Basic(
 *      <direct reference to the List type def>, [
 *          ParsedType.Basic(<direct reference to the String type def>, [])
 *      ].
 *
 * Everything related to importing and namespacing is done here - after this
 * point, the only purpose of "import" expressions is to potentially run the
 * top-level code in the mentioned file.
 *
 * /// NOTABLE CHANGES ///
 * // Overall structure
 * - Files are no longer lazy. Anything which is never imported anywhere is removed
 *   entirely.
 * - Files no longer store their types, functions, etc. Instead, that information is
 *   aggregated into a single list in the AST for the program, and expressions point
 *   directly to the required data. This is because the purpose of the import resolution
 *   phase is to abstract away the complexity of "things being defined in different files".
 * - Built-in types are introduced to the mix. Since built-in types are not defined by Snuggle
 *   code themselves, they are not part of the Parsed AST. However, they are referred to by name,
 *   so the import-resolved AST needs to know about their existence.
 *
 * // Expressions
 *   TODO:
 * - The MethodCall expression now contains data describing what extensions are in scope at
 *   the site of the method call. This way, when it's time to later resolve overloads, we have the
 *   necessary information to determine "what extensions are in scope" at our fingertips. (Not having
 *   this was a mistake that was made last time.)
 *
 */

data class ResolvedAST(
    // All files used in the program. (No longer lazy. Things that are never imported do not appear, and were never parsed.)
    val allFiles: Map<String, ResolvedFile>,
    // The builtin map. Builtins are mapped to their corresponding ResolvedTypeDefs.
    val builtinMap: IdentityHashMap<BuiltinType, ResolvedTypeDef.Builtin>
)

data class ResolvedFile(val name: String, val code: ResolvedExpr)

sealed class ResolvedTypeDef {
    abstract val pub: Boolean
    abstract val name: String
    abstract val numGenerics: Int

    // Unwrap the typedef's indirections
    fun unwrap(): ResolvedTypeDef =
        if (this is Indirection) promise.expect().unwrap() else this
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other is ResolvedTypeDef) return this.unwrap() === other.unwrap()
        return false
    }

    override fun hashCode(): Int {
        return System.identityHashCode(this.unwrap())
    }

    // Indirection type, required for self-referencing data.
    // See TypeDef.Indirection
    class Indirection(val promise: Promise<ResolvedTypeDef> = Promise()): ResolvedTypeDef() {
        override val pub: Boolean get() = promise.expect().pub
        override val name: String get() = promise.expect().name
        override val numGenerics: Int get() = promise.expect().numGenerics
    }

    class Builtin(val builtin: BuiltinType): ResolvedTypeDef() {
        override val name: String get() = builtin.baseName
        override val pub: Boolean get() = true
        override val numGenerics: Int get() = builtin.numGenerics
    }
    class Class(val loc: Loc, override val pub: Boolean, override val name: String,
                     override val numGenerics: Int,
                     val superType: ResolvedType,
                     val fields: List<ResolvedFieldDef>,
                     val methods: List<ResolvedMethodDef>): ResolvedTypeDef()

    class Struct(val loc: Loc, override val pub: Boolean, override val name: String,
                     override val numGenerics: Int,
                     val fields: List<ResolvedFieldDef>,
                     val methods: List<ResolvedMethodDef>): ResolvedTypeDef()

}

class ResolvedFieldDef(val loc: Loc, val pub: Boolean, val static: Boolean, val mutable: Boolean, val name: String, val annotatedType: ResolvedType)
class ResolvedMethodDef(val loc: Loc, val pub: Boolean, val static: Boolean, val numGenerics: Int, val name: String, val params: List<ResolvedInfalliblePattern>, val returnType: ResolvedType, val body: ResolvedExpr)

sealed class ResolvedImplBlock {

    fun unwrap(): Base = if (this is Indirect) promise.expect().unwrap() else this as Base
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other is ResolvedImplBlock) return this.unwrap() === other.unwrap()
        return false
    }
    override fun hashCode(): Int {
        return System.identityHashCode(this.unwrap())
    }

    class Indirect(val promise: Promise<ResolvedImplBlock> = Promise()): ResolvedImplBlock()
    class Base(val loc: Loc, val pub: Boolean, val numGenerics: Int, val implType: ResolvedType, val methods: List<ResolvedMethodDef>): ResolvedImplBlock()
}
sealed interface ResolvedExpr {
    val loc: Loc

    class Import(override val loc: Loc, val file: String): ResolvedExpr

    class Block(override val loc: Loc, val exprs: List<ResolvedExpr>): ResolvedExpr
    class Declaration(override val loc: Loc, val pattern: ResolvedInfalliblePattern, val initializer: ResolvedExpr): ResolvedExpr
    // Lhs is one of: FieldAccess, StaticFieldAccess, Variable
    class Assignment(override val loc: Loc, val lhs: ResolvedExpr, val rhs: ResolvedExpr): ResolvedExpr

    class Return(override val loc: Loc, val rhs: ResolvedExpr): ResolvedExpr

    class If(override val loc: Loc, val cond: ResolvedExpr, val ifTrue: ResolvedExpr, val ifFalse: ResolvedExpr?): ResolvedExpr
    class While(override val loc: Loc, val cond: ResolvedExpr, val body: ResolvedExpr): ResolvedExpr
    class For(override val loc: Loc, val pattern: ResolvedInfalliblePattern, val iterable: ResolvedExpr, val body: ResolvedExpr): ResolvedExpr

    class Is(override val loc: Loc, val negated: Boolean, val lhs: ResolvedExpr, val pattern: ResolvedFalliblePattern): ResolvedExpr

    class Literal(override val loc: Loc, val value: Any): ResolvedExpr
    class Variable(override val loc: Loc, val name: String): ResolvedExpr
    class Tuple(override val loc: Loc, val elements: List<ResolvedExpr>): ResolvedExpr
    class Lambda(override val loc: Loc, val params: List<ResolvedInfalliblePattern>, val body: ResolvedExpr): ResolvedExpr

    class FieldAccess(override val loc: Loc, val receiver: ResolvedExpr, val fieldName: String): ResolvedExpr
    class StaticFieldAccess(override val loc: Loc, val receiverType: ResolvedType, val fieldName: String): ResolvedExpr

    // Method calls carry with them a list of ImplBlock, which were in scope when the method call was made.
    // Methods from these ImplBlock will be added to the pool of potentially callable methods.
    class MethodCall(override val loc: Loc, val receiver: ResolvedExpr, val methodName: String, val genericArgs: List<ResolvedType>, val args: List<ResolvedExpr>, val implBlocks: ConsList<ResolvedImplBlock>): ResolvedExpr
    class StaticMethodCall(override val loc: Loc, val receiverType: ResolvedType, val methodName: String, val genericArgs: List<ResolvedType>, val args: List<ResolvedExpr>, val implBlocks: ConsList<ResolvedImplBlock>): ResolvedExpr
    class SuperMethodCall(override val loc: Loc, val methodName: String, val genericArgs: List<ResolvedType>, val args: List<ResolvedExpr>, val implBlocks: ConsList<ResolvedImplBlock>): ResolvedExpr
    class ConstructorCall(override val loc: Loc, val type: ResolvedType?, val args: List<ResolvedExpr>, val implBlocks: ConsList<ResolvedImplBlock>): ResolvedExpr
    class RawStructConstructor(override val loc: Loc, val type: ResolvedType?, val fieldValues: List<ResolvedExpr>): ResolvedExpr


}

sealed interface ResolvedInfalliblePattern {
    val loc: Loc

    class Empty(override val loc: Loc, val typeAnnotation: ResolvedType?): ResolvedInfalliblePattern
    class Binding(override val loc: Loc, val name: String, val isMut: Boolean, val typeAnnotation: ResolvedType?): ResolvedInfalliblePattern
    class Tuple(override val loc: Loc, val elements: List<ResolvedInfalliblePattern>): ResolvedInfalliblePattern
}

sealed interface ResolvedFalliblePattern {
    val loc: Loc

    class IsType(override val loc: Loc, val isMut: Boolean, val varName: String?, val type: ResolvedType): ResolvedFalliblePattern
    class LiteralPattern(override val loc: Loc, val value: Any): ResolvedFalliblePattern
    class Tuple(override val loc: Loc, val elements: List<ResolvedFalliblePattern>): ResolvedFalliblePattern
}


sealed interface ResolvedType {
    val loc: Loc

    //Note: Now a DIRECT REFERENCE to a ResolvedTypeDef, rather than a mere String. This is the *primary reason for this AST pass*.
    class Basic(override val loc: Loc, val base: ResolvedTypeDef, val generics: List<ResolvedType>): ResolvedType
    class Tuple(override val loc: Loc, val elements: List<ResolvedType>): ResolvedType
    class Func(override val loc: Loc, val paramTypes: List<ResolvedType>, val returnType: ResolvedType): ResolvedType
    class TypeGeneric(override val loc: Loc, val name: String, val index: Int): ResolvedType
    class MethodGeneric(override val loc: Loc, val name: String, val index: Int): ResolvedType
}