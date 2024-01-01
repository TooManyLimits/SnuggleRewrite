package representation.passes.parsing

import representation.asts.parsed.ParsedElement
import representation.asts.parsed.ParsedFile
import representation.passes.lexing.Lexer
import representation.passes.lexing.Loc

/**
 * Program parsing !
 */

fun parseFileLazy(lexer: Lexer): Lazy<ParsedFile> = lazy { parseFile(lexer) }

fun parseFile(lexer: Lexer): ParsedFile {
    val elems: ArrayList<ParsedElement> = ArrayList()
    while (!lexer.isDone())
        elems += parseElement(lexer, listOf()) // typeGenerics starts empty
    val loc = lexer.curLoc.merge(Loc(1, 0, 1, 0, lexer.fileName))
    return ParsedFile(lexer.fileName, ParsedElement.ParsedExpr.Block(loc, elems))
}
