package representation.asts.parsed

import representation.passes.lexing.Loc

/**
 * The very beginning of the AST. Contains only the raw data,
 * parsed out of the tokens. The next step after this point is
 * the import resolution phase.
 */

//Files are lazily parsed
class ParsedAST(val files: Map<String, Lazy<ParsedFile>>) {
    fun debugReadAllFiles() { // Debug function; read all the files, so they can be printed
        for (file in files) file.value.value
    }
}

//But entire files are parsed together, as one unit.
class ParsedFile(val name: String, val block: ParsedElement.ParsedExpr.Block)

// Parsed elements
sealed interface ParsedElement {

    val loc: Loc

    sealed interface ParsedTypeDef: ParsedElement {
        val pub: Boolean
        val name: String

        class Class(override val loc: Loc, override val pub: Boolean, override val name: String, val numGenerics: Int, val superType: ParsedType, val fields: List<ParsedFieldDef>, val methods: List<ParsedMethodDef>): ParsedTypeDef
        class Struct(override val loc: Loc, override val pub: Boolean, override val name: String, val numGenerics: Int, val fields: List<ParsedFieldDef>, val methods: List<ParsedMethodDef>): ParsedTypeDef

    }
//
//    class FuncDef(override val loc: Loc, val pub: Boolean, val name: String, val params: List<ParsedPattern>, val returnType: ParsedType, val body: ParsedExpr): ParsedElement
//    class PubVar(override val loc: Loc, val mutable: Boolean, val lhs: ParsedPattern, val annotatedType: ParsedType, val initializer: ParsedExpr): ParsedElement

    class ParsedImplBlock(override val loc: Loc, val pub: Boolean, val numGenerics: Int, val implType: ParsedType, val methods: List<ParsedMethodDef>): ParsedElement
//    class SpecBlock(override val loc: Loc): ParsedElement

    sealed interface ParsedExpr: ParsedElement {

        class Import(override val loc: Loc, val path: String): ParsedExpr

        class Block(override val loc: Loc, val pub: Boolean, val elements: List<ParsedElement>): ParsedExpr
        class Declaration(override val loc: Loc, val lhs: ParsedInfalliblePattern, val initializer: ParsedExpr): ParsedExpr
        // Lhs is one of: FieldAccess or Variable
        class Assignment(override val loc: Loc, val lhs: ParsedExpr, val rhs: ParsedExpr): ParsedExpr
        class Return(override val loc: Loc, val rhs: ParsedExpr): ParsedExpr

        class If(override val loc: Loc, val cond: ParsedExpr, val ifTrue: ParsedExpr, val ifFalse: ParsedExpr?): ParsedExpr
        class While(override val loc: Loc, val cond: ParsedExpr, val body: ParsedExpr): ParsedExpr
        class For(override val loc: Loc, val pattern: ParsedInfalliblePattern, val iterable: ParsedExpr, val body: ParsedExpr): ParsedExpr

        class Is(override val loc: Loc, val lhs: ParsedExpr, val pattern: ParsedFalliblePattern): ParsedExpr

        class Literal(override val loc: Loc, val value: Any): ParsedExpr
        class Super(override val loc: Loc): ParsedExpr
        class Variable(override val loc: Loc, val name: String): ParsedExpr
        class Tuple(override val loc: Loc, val elements: List<ParsedExpr>): ParsedExpr
        class Lambda(override val loc: Loc, val params: List<ParsedInfalliblePattern>, val body: ParsedExpr): ParsedExpr

        class FieldAccess(override val loc: Loc, val receiver: ParsedExpr, val fieldName: String): ParsedExpr
        class MethodCall(override val loc: Loc, val receiver: ParsedExpr, val methodName: String, val genericArgs: List<ParsedType>, val args: List<ParsedExpr>): ParsedExpr
        class ConstructorCall(override val loc: Loc, val type: ParsedType?, val args: List<ParsedExpr>): ParsedExpr
        class RawStructConstructor(override val loc: Loc, val type: ParsedType?, val fieldValues: List<ParsedExpr>): ParsedExpr

        class Parenthesized(override val loc: Loc, val inner: ParsedExpr): ParsedExpr
        //... etc
    }

}

// Helper data structures
class ParsedFieldDef(val loc: Loc, val pub: Boolean, val static: Boolean, val mutable: Boolean, val name: String, val annotatedType: ParsedType)
class ParsedMethodDef(val loc: Loc, val pub: Boolean, val static: Boolean, val numGenerics: Int, val name: String, val params: List<ParsedInfalliblePattern>, val returnType: ParsedType, val body: ParsedElement.ParsedExpr)

// Infallible patterns, used in `let`
sealed interface ParsedInfalliblePattern {

    val loc: Loc

    class Empty(override val loc: Loc, val typeAnnotation: ParsedType?) : ParsedInfalliblePattern // _
//    class LiteralPattern(override val loc: Loc, val value: Any): ParsedPattern //true, "hi", 5
//    class AndPattern(override val loc: Loc, val pats: List<ParsedPattern>): ParsedPattern //pat1 & pat2 & pat3
    class Binding(override val loc: Loc, val name: String, val isMut: Boolean, val typeAnnotation: ParsedType?): ParsedInfalliblePattern
    class Tuple(override val loc: Loc, val elements: List<ParsedInfalliblePattern>): ParsedInfalliblePattern // (mut a, b: i32)

}

// Fallible patterns, used in `is`
sealed interface ParsedFalliblePattern {
    val loc: Loc

    class IsType(override val loc: Loc, val isMut: Boolean, val varName: String?, val type: ParsedType): ParsedFalliblePattern
    class LiteralPattern(override val loc: Loc, val value: Any): ParsedFalliblePattern
    class Tuple(override val loc: Loc, val elements: List<ParsedFalliblePattern>): ParsedFalliblePattern
}


sealed interface ParsedType {

    val loc: Loc

    class Basic(override val loc: Loc, val base: String, val generics: List<ParsedType>): ParsedType {
        override fun toString(): String {
            return base + if (generics.isNotEmpty()) "<" + generics.joinToString() + ">" else ""
        }
    }
    class Tuple(override val loc: Loc, val elementTypes: List<ParsedType>): ParsedType {
        override fun toString(): String = "(${elementTypes.joinToString()})"
    }
    class Func(override val loc: Loc, val paramTypes: List<ParsedType>, val returnType: ParsedType): ParsedType {
        override fun toString(): String = "(${paramTypes.joinToString()}) -> $returnType"
    }
    class TypeGeneric(override val loc: Loc, val name: String, val index: Int): ParsedType {
        override fun toString(): String = "TypeGeneric($name)"
    }
    class MethodGeneric(override val loc: Loc, val name: String, val index: Int): ParsedType {
        override fun toString(): String = "MethodGeneric($name)"
    }

}