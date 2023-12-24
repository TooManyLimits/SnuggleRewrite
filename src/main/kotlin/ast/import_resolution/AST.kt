package ast.import_resolution

import ast.lexing.Loc
import ast.parsing.*
import builtins.BuiltinType

/**
 * Here, the raw string data of the previous AST (ParsedAST)
 * is resolved to point at the actual types that they refer to.
 *
 * For example, where before we'd have a:
 * - ParsedType.Basic("List", [ParsedType.Basic("String", [])]),
 * we would now have an
 * - ImportResolvedType.Basic(
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

data class ImportResolvedAST(
    // All files used in the program. (No longer lazy. Things that are never imported do not appear, and were never parsed.)
    val allFiles: Map<String, ImportResolvedFile>,
    //val allTypes: Set<ImportResolvedTypeDef> // All type definitions in the program.
)

data class ImportResolvedFile(val name: String, val code: ImportResolvedExpr)

sealed interface ImportResolvedTypeDef {
    val pub: Boolean
    val name: String
    data class Builtin(val builtin: BuiltinType): ImportResolvedTypeDef {
        override val name: String get() = builtin.name
        override val pub: Boolean get() = true
    }
    sealed interface SnuggleImportResolvedTypeDef: ImportResolvedTypeDef {
        val loc: Loc
        data class Class(override val loc: Loc, override val pub: Boolean, override val name: String,
                         val superType: ImportResolvedType,
                         val fields: List<ImportResolvedFieldDef>,
                         val methods: List<ImportResolvedMethodDef>): SnuggleImportResolvedTypeDef
    }

}

data class ImportResolvedFieldDef(val loc: Loc, val pub: Boolean, val static: Boolean, val name: String, val annotatedType: ImportResolvedType, val initializer: ImportResolvedExpr?)
data class ImportResolvedMethodDef(val loc: Loc, val pub: Boolean, val static: Boolean, val name: String, val params: List<ImportResolvedPattern>, val returnType: ImportResolvedType, val body: ImportResolvedExpr)

sealed interface ImportResolvedExpr {
    val loc: Loc

    data class Import(override val loc: Loc, val file: String): ImportResolvedExpr

    data class Block(override val loc: Loc, val exprs: List<ImportResolvedExpr>): ImportResolvedExpr
    data class Declaration(override val loc: Loc, val pattern: ImportResolvedPattern, val initializer: ImportResolvedExpr): ImportResolvedExpr

    data class Literal(override val loc: Loc, val value: Any): ImportResolvedExpr
    data class Variable(override val loc: Loc, val name: String): ImportResolvedExpr
    data class MethodCall(override val loc: Loc, val receiver: ImportResolvedExpr, val methodName: String, val args: List<ImportResolvedExpr>): ImportResolvedExpr
}

sealed interface ImportResolvedPattern {
    val loc: Loc

    data class BindingPattern(override val loc: Loc, val name: String, val isMut: Boolean, val typeAnnotation: ImportResolvedType?): ImportResolvedPattern
}

sealed interface ImportResolvedType {
    val loc: Loc

    //Note: Now a DIRECT REFERENCE to an ImportResolvedTypeDef, rather than a mere String. This is the *primary reason for this AST pass*.
    data class Basic(override val loc: Loc, val base: ImportResolvedTypeDef, val generics: List<ImportResolvedType>): ImportResolvedType
}