package ast.passes.parsing

import ast.ParsedElement
import ast.lexing.Lexer

/**
 * Element parsing !
 */

fun parseElement(lexer: Lexer): ParsedElement {
    return when {
        else -> parseExpr(lexer)
    }
}
