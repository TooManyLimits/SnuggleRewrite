package ast.passes.parsing

import ast.lexing.Loc
import util.ConsList
import util.ConsMap

//Files are lazily parsed
data class ParsedAST(val files: ConsMap<String, Lazy<ParsedFile>>)

//But entire files are parsed together, as one unit.
data class ParsedFile(val name: String, val elements: ConsList<ParsedElement>)

// Parsed elements
sealed interface ParsedElement {

    val loc: Loc

//    sealed interface ParsedTypeDef: ParsedElement {
//        val pub: Boolean
//        val name: String
//
//        data class Class(override val loc: Loc, override val pub: Boolean, override val name: String, val superType: ParsedType, val fields: ConsList<ParsedFieldDef>, val methods: ConsList<ParsedMethodDef>): ParsedTypeDef
//        data class Struct(override val loc: Loc, override val pub: Boolean, override val name: String, val fields: ConsList<ParsedFieldDef>, val methods: ConsList<ParsedMethodDef>): ParsedTypeDef
//    }
//
//    data class FuncDef(override val loc: Loc, val pub: Boolean, val name: String, val params: ConsList<ParsedParam>, val returnType: ParsedType, val body: ParsedExpr): ParsedElement
//    data class PubVar(override val loc: Loc, val mutable: Boolean, val lhs: ParsedPattern, val annotatedType: ParsedType, val initializer: ParsedExpr): ParsedElement

    sealed interface ParsedExpr: ParsedElement {

//        data class Import(override val loc: Loc, val path: String): ParsedExpr

        data class Declaration(override val loc: Loc, val lhs: ParsedPattern, val initializer: ParsedExpr): ParsedExpr

        data class Literal(override val loc: Loc, val value: Any): ParsedExpr
        data class Variable(override val loc: Loc, val name: String): ParsedExpr
        data class MethodCall(override val loc: Loc, val receiver: ParsedExpr, val methodName: String, val args: ConsList<ParsedExpr>): ParsedExpr

        //... etc
    }

}

//Other data structures

//data class ParsedFieldDef(val pub: Boolean, val static: Boolean, val name: String, val annotatedType: ParsedType, val initializer: ParsedElement.ParsedExpr?)
//data class ParsedMethodDef(val pub: Boolean, val static: Boolean, val name: String, val params: ConsList<ParsedParam>, val returnType: ParsedType, val body: ParsedElement.ParsedExpr)
//data class ParsedParam(val name: String, val type: ParsedType)

sealed interface ParsedPattern {

    val loc: Loc

//    object Empty : ParsedPattern // _
//    data class Literal(override val loc: Loc, val value: Any): ParsedPattern //true, "hi", 5
//    data class And(override val loc: Loc, val pats: ConsList<ParsedPattern>): ParsedPattern //pat1 & pat2 & pat3
    data class Binding(override val loc: Loc, val name: String, val isMut: Boolean, val typeAnnotation: ParsedType?): ParsedPattern
//    data class Tuple(override val loc: Loc, val elements: ConsList<ParsedPattern>): ParsedPattern // (mut a, b: i32)

}

sealed interface ParsedType {

    val loc: Loc

    data class Basic(override val loc: Loc, val base: String, val generics: ConsList<ParsedType>): ParsedType
//    data class Tuple(override val loc: Loc, val elementTypes: ConsList<ParsedType>): ParsedType
//    data class Func(override val loc: Loc, val paramTypes: ConsList<ParsedType>, val returnType: ParsedType): ParsedType
    data class TypeGeneric(override val loc: Loc, val name: String, val index: Int): ParsedType
    data class MethodGeneric(override val loc: Loc, val name: String, val index: Int): ParsedType

}