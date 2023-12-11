package ast.parsing

import ast.lexing.Lexer
import util.Cons
import util.ConsList
import util.ConsList.Companion.nil

/**
 * Program parsing !
 */

fun parseFileLazy(lexer: Lexer): Lazy<ParsedFile> = lazy { parseFile(lexer) }

fun parseFile(lexer: Lexer): ParsedFile {
    var elems: ConsList<ParsedElement> = nil()
    while (!lexer.isDone())
        elems = Cons(parseElement(lexer), elems)
    return ParsedFile(lexer.fileName, elems.reverse())
}
