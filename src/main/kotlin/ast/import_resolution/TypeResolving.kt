package ast.import_resolution

import ast.parsing.ParsedAST
import builtins.BuiltinType
import util.ConsList
import util.ConsList.Companion.nil
import util.ConsMap

/**
 * Resolve a ParsedAST into an ImportResolvedAST.
 * Details about the difference between the two can be found in their
 * respective files.
 *
 * - Requires builtin type information. This information is injected into the AST.
 */
fun resolveAST(ast: ParsedAST, builtinTypes: ConsList<BuiltinType>): ImportResolvedAST {

    // Get the starting map of type definitions.
    val startingTypeDefs: ConsMap<String, ImportResolvedTypeDef> = builtinTypes
        .filter(BuiltinType::nameable)
        .map(ImportResolvedTypeDef::Builtin)
        .associate { t -> t.builtin.name }




    throw RuntimeException()
}
