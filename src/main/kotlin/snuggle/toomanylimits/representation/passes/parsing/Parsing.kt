package snuggle.toomanylimits.representation.passes.parsing

import snuggle.toomanylimits.representation.asts.parsed.ParsedElement
import snuggle.toomanylimits.representation.asts.parsed.ParsedFile
import snuggle.toomanylimits.representation.passes.lexing.Lexer
import snuggle.toomanylimits.representation.passes.lexing.Loc

/**
 * Program parsing !
 */

fun parseFileLazy(lexer: Lexer): Lazy<ParsedFile> = lazy { parseFile(lexer) }

fun parseFile(lexer: Lexer): ParsedFile {
    val elems: ArrayList<ParsedElement> = ArrayList()
    while (!lexer.isDone())
        elems += parseElement(lexer, listOf(), listOf()) // Generics start empty
    val loc = lexer.curLoc.merge(Loc(1, 0, 1, 0, lexer.fileName))
    return ParsedFile(lexer.fileName, ParsedElement.ParsedExpr.Block(loc, true, elems))
}
