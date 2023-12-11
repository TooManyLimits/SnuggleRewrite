package ast.parsing

import ast.lexing.Lexer

/**
 * Element parsing !
 */

fun parseElement(lexer: Lexer): ParsedElement {
    return when {
        else -> parseExpr(lexer)
    }
}
