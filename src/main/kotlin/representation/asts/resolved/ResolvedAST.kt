package representation.asts.resolved

import representation.passes.lexing.Loc
import builtins.BuiltinType
import util.Promise

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
    val builtinMap: Map<BuiltinType, ResolvedTypeDef>
)

data class ResolvedFile(val name: String, val code: ResolvedExpr)

sealed interface ResolvedTypeDef {
    val pub: Boolean
    val name: String
    val numGenerics: Int

    // Indirection type, required for self-referencing data.
    // See TypeDef.Indirection
    data class Indirection(val promise: Promise<ResolvedTypeDef> = Promise()): ResolvedTypeDef {
        override val pub: Boolean get() = promise.expect().pub
        override val name: String get() = promise.expect().name
        override val numGenerics: Int get() = promise.expect().numGenerics
    }

    data class Builtin(val builtin: BuiltinType): ResolvedTypeDef {
        override val name: String get() = builtin.baseName
        override val pub: Boolean get() = true
        override val numGenerics: Int get() = builtin.numGenerics
    }
    data class Class(val loc: Loc, override val pub: Boolean, override val name: String,
                     override val numGenerics: Int,
                     val superType: ResolvedType,
                     val fields: List<ResolvedFieldDef>,
                     val methods: List<ResolvedMethodDef>): ResolvedTypeDef

    data class Struct(val loc: Loc, override val pub: Boolean, override val name: String,
                     override val numGenerics: Int,
                     val fields: List<ResolvedFieldDef>,
                     val methods: List<ResolvedMethodDef>): ResolvedTypeDef

}

data class ResolvedFieldDef(val loc: Loc, val pub: Boolean, val static: Boolean, val mutable: Boolean, val name: String, val annotatedType: ResolvedType)
data class ResolvedMethodDef(val loc: Loc, val pub: Boolean, val static: Boolean, val numGenerics: Int, val name: String, val params: List<ResolvedPattern>, val returnType: ResolvedType, val body: ResolvedExpr)

sealed interface ResolvedExpr {
    val loc: Loc

    data class Import(override val loc: Loc, val file: String): ResolvedExpr

    data class Block(override val loc: Loc, val exprs: List<ResolvedExpr>): ResolvedExpr
    data class Declaration(override val loc: Loc, val pattern: ResolvedPattern, val initializer: ResolvedExpr): ResolvedExpr
    // Lhs is one of: FieldAccess, StaticFieldAccess, Variable
    data class Assignment(override val loc: Loc, val lhs: ResolvedExpr, val rhs: ResolvedExpr): ResolvedExpr

    data class Return(override val loc: Loc, val rhs: ResolvedExpr): ResolvedExpr

    data class Literal(override val loc: Loc, val value: Any): ResolvedExpr
    data class Variable(override val loc: Loc, val name: String): ResolvedExpr
    data class Tuple(override val loc: Loc, val elements: List<ResolvedExpr>): ResolvedExpr

    data class FieldAccess(override val loc: Loc, val receiver: ResolvedExpr, val fieldName: String): ResolvedExpr
    data class StaticFieldAccess(override val loc: Loc, val receiverType: ResolvedType, val fieldName: String): ResolvedExpr
    data class MethodCall(override val loc: Loc, val receiver: ResolvedExpr, val methodName: String, val genericArgs: List<ResolvedType>, val args: List<ResolvedExpr>): ResolvedExpr
    data class StaticMethodCall(override val loc: Loc, val receiverType: ResolvedType, val methodName: String, val genericArgs: List<ResolvedType>, val args: List<ResolvedExpr>): ResolvedExpr
    data class SuperMethodCall(override val loc: Loc, val methodName: String, val genericArgs: List<ResolvedType>, val args: List<ResolvedExpr>): ResolvedExpr
    data class ConstructorCall(override val loc: Loc, val type: ResolvedType?, val args: List<ResolvedExpr>): ResolvedExpr
    data class RawStructConstructor(override val loc: Loc, val type: ResolvedType?, val fieldValues: List<ResolvedExpr>): ResolvedExpr


}

sealed interface ResolvedPattern {
    val loc: Loc

    data class BindingPattern(override val loc: Loc, val name: String, val isMut: Boolean, val typeAnnotation: ResolvedType?): ResolvedPattern
}

sealed interface ResolvedType {
    val loc: Loc

    //Note: Now a DIRECT REFERENCE to a ResolvedTypeDef, rather than a mere String. This is the *primary reason for this AST pass*.
    data class Basic(override val loc: Loc, val base: ResolvedTypeDef, val generics: List<ResolvedType>): ResolvedType
    data class Tuple(override val loc: Loc, val elements: List<ResolvedType>): ResolvedType
    data class TypeGeneric(override val loc: Loc, val name: String, val index: Int): ResolvedType
    data class MethodGeneric(override val loc: Loc, val name: String, val index: Int): ResolvedType
}