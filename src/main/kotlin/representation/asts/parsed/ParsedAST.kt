package representation.asts.parsed

import representation.asts.typed.TypeDef
import representation.passes.lexing.Loc

/**
 * The very beginning of the AST. Contains only the raw data,
 * parsed out of the tokens. The next step after this point is
 * the import resolution phase.
 */

//Files are lazily parsed
data class ParsedAST(val files: Map<String, Lazy<ParsedFile>>) {
    fun debugReadAllFiles() { // Debug function; read all the files, so they can be printed
        for (file in files) file.value.value
    }
}

//But entire files are parsed together, as one unit.
data class ParsedFile(val name: String, val block: ParsedElement.ParsedExpr.Block)

// Parsed elements
sealed interface ParsedElement {

    val loc: Loc

    sealed interface ParsedTypeDef: ParsedElement {
        val pub: Boolean
        val name: String

        data class Class(override val loc: Loc, override val pub: Boolean, override val name: String, val numGenerics: Int, val superType: ParsedType, val fields: List<ParsedFieldDef>, val methods: List<ParsedMethodDef>): ParsedTypeDef
        data class Struct(override val loc: Loc, override val pub: Boolean, override val name: String, val numGenerics: Int, val fields: List<ParsedFieldDef>, val methods: List<ParsedMethodDef>): ParsedTypeDef

    }
//
//    data class FuncDef(override val loc: Loc, val pub: Boolean, val name: String, val params: List<ParsedPattern>, val returnType: ParsedType, val body: ParsedExpr): ParsedElement
//    data class PubVar(override val loc: Loc, val mutable: Boolean, val lhs: ParsedPattern, val annotatedType: ParsedType, val initializer: ParsedExpr): ParsedElement

//    data class ImplBlock(override val loc: Loc): ParsedElement
//    data class SpecBlock(override val loc: Loc): ParsedElement

    sealed interface ParsedExpr: ParsedElement {

        data class Import(override val loc: Loc, val path: String): ParsedExpr

        data class Block(override val loc: Loc, val elements: List<ParsedElement>): ParsedExpr
        data class Declaration(override val loc: Loc, val lhs: ParsedPattern, val initializer: ParsedExpr): ParsedExpr
        // Lhs is one of: FieldAccess or Variable
        data class Assignment(override val loc: Loc, val lhs: ParsedExpr, val rhs: ParsedExpr): ParsedExpr
        data class Return(override val loc: Loc, val rhs: ParsedExpr): ParsedExpr

        data class Literal(override val loc: Loc, val value: Any): ParsedExpr
        data class Super(override val loc: Loc): ParsedExpr
        data class Variable(override val loc: Loc, val name: String): ParsedExpr
        data class Tuple(override val loc: Loc, val elements: List<ParsedExpr>): ParsedExpr
        data class Lambda(override val loc: Loc, val params: List<ParsedPattern>, val body: ParsedExpr): ParsedExpr

        data class FieldAccess(override val loc: Loc, val receiver: ParsedExpr, val fieldName: String): ParsedExpr
        data class MethodCall(override val loc: Loc, val receiver: ParsedExpr, val methodName: String, val genericArgs: List<ParsedType>, val args: List<ParsedExpr>): ParsedExpr
        data class ConstructorCall(override val loc: Loc, val type: ParsedType?, val args: List<ParsedExpr>): ParsedExpr
        data class RawStructConstructor(override val loc: Loc, val type: ParsedType?, val fieldValues: List<ParsedExpr>): ParsedExpr

        data class Parenthesized(override val loc: Loc, val inner: ParsedExpr): ParsedExpr
        //... etc
    }

}

// Helper data structures
data class ParsedFieldDef(val loc: Loc, val pub: Boolean, val static: Boolean, val mutable: Boolean, val name: String, val annotatedType: ParsedType)
data class ParsedMethodDef(val loc: Loc, val pub: Boolean, val static: Boolean, val numGenerics: Int, val name: String, val params: List<ParsedPattern>, val returnType: ParsedType, val body: ParsedElement.ParsedExpr)
sealed interface ParsedPattern {

    val loc: Loc

    data class EmptyPattern(override val loc: Loc, val typeAnnotation: ParsedType?) : ParsedPattern // _
//    data class LiteralPattern(override val loc: Loc, val value: Any): ParsedPattern //true, "hi", 5
//    data class AndPattern(override val loc: Loc, val pats: List<ParsedPattern>): ParsedPattern //pat1 & pat2 & pat3
    data class BindingPattern(override val loc: Loc, val name: String, val isMut: Boolean, val typeAnnotation: ParsedType?): ParsedPattern
    data class TuplePattern(override val loc: Loc, val elements: List<ParsedPattern>): ParsedPattern // (mut a, b: i32)

}

sealed interface ParsedType {

    val loc: Loc



    data class Basic(override val loc: Loc, val base: String, val generics: List<ParsedType>): ParsedType {
        override fun toString(): String {
            return base + if (generics.isNotEmpty()) "<" + generics.joinToString() + ">" else ""
        }
    }
    data class Tuple(override val loc: Loc, val elementTypes: List<ParsedType>): ParsedType {
        override fun toString(): String = "(${elementTypes.joinToString()})"
    }
    data class Func(override val loc: Loc, val paramTypes: List<ParsedType>, val returnType: ParsedType): ParsedType {
        override fun toString(): String = "(${paramTypes.joinToString()}) -> $returnType"
    }
    data class TypeGeneric(override val loc: Loc, val name: String, val index: Int): ParsedType {
        override fun toString(): String = "TypeGeneric($name)"
    }
    data class MethodGeneric(override val loc: Loc, val name: String, val index: Int): ParsedType {
        override fun toString(): String = "MethodGeneric($name)"
    }

}