package representation.passes.parsing

import errors.ParsingException
import representation.asts.parsed.ParsedElement
import representation.asts.parsed.ParsedFieldDef
import representation.asts.parsed.ParsedMethodDef
import representation.asts.parsed.ParsedType
import representation.passes.lexing.Lexer
import representation.passes.lexing.Loc
import representation.passes.lexing.TokenType

// The "class" token was just consumed
fun parseClass(lexer: Lexer, isPub: Boolean): ParsedElement.ParsedTypeDef.Class {
    val classLoc = lexer.last().loc
    val className = lexer.expect(TokenType.IDENTIFIER, extraInfo = "after \"class\"").string()
    val typeGenerics = parseGenerics(lexer)
    val supertype = if (lexer.consume(TokenType.COLON))
        parseType(lexer, typeGenerics, extraInfo = "for superclass")
    else
        ParsedType.Basic(Loc.NEVER, "Object", listOf()) // Default superclass to Object if none is given
    lexer.expect(TokenType.LEFT_CURLY, "to begin class definition")
    val members = parseMembers(lexer, typeGenerics, TypeType.CLASS)
    return ParsedElement.ParsedTypeDef.Class(classLoc, isPub, className, typeGenerics.size, supertype, members.first, members.second)
}

fun parseStruct(lexer: Lexer, isPub: Boolean): ParsedElement.ParsedTypeDef.Struct {
    val structLoc = lexer.last().loc
    val structName = lexer.expect(TokenType.IDENTIFIER, extraInfo = "after \"struct\"").string()
    val typeGenerics = parseGenerics(lexer)
    lexer.expect(TokenType.LEFT_CURLY, "to begin class definition")
    val members = parseMembers(lexer, typeGenerics, TypeType.STRUCT)
    return ParsedElement.ParsedTypeDef.Struct(structLoc, isPub, structName, typeGenerics.size, members.first, members.second)
}

private fun parseGenerics(lexer: Lexer): List<String> {
    if (lexer.consume(TokenType.LESS))
        return commaSeparated(lexer, TokenType.GREATER) { it.expect(TokenType.IDENTIFIER, "for generic").string() }
    return listOf()
}

enum class TypeType {
    CLASS, STRUCT, ENUM
}

// Parses members. The { was just consumed. Continues until finding a }.
private fun parseMembers(lexer: Lexer, typeGenerics: List<String>, typeType: TypeType): Pair<List<ParsedFieldDef>, List<ParsedMethodDef>> {
    val fields = ArrayList<ParsedFieldDef>()
    val methods = ArrayList<ParsedMethodDef>()
    while (!lexer.consume(TokenType.RIGHT_CURLY)) {
        // Collect modifiers, pub and static
        val isPub = lexer.consume(TokenType.PUB)
        val isStatic = lexer.consume(TokenType.STATIC)

        if (lexer.consume(TokenType.FN)) {
            // Special treatment/requirements for constructors vs regular methods
            if (lexer.consume(TokenType.NEW)) {
                val loc = lexer.last().loc
                if (typeType == TypeType.ENUM) throw ParsingException("Cannot have constructors for enum type", lexer.last().loc)
                if (typeType == TypeType.CLASS && isStatic) throw ParsingException("Constructors in classes cannot be static", lexer.last().loc)
                if (typeType == TypeType.STRUCT && !isStatic) throw ParsingException("Constructors in structs must be static", lexer.last().loc)
                val params = parseParams(lexer, typeGenerics)
                val returnType = if (lexer.consume(TokenType.COLON)) parseType(lexer, typeGenerics) else ParsedType.Tuple(Loc.NEVER, listOf())
                if (typeType == TypeType.CLASS && (returnType !is ParsedType.Tuple || returnType.elementTypes.isNotEmpty()))
                    throw ParsingException("Class constructor must return unit, but found $returnType", returnType.loc)
                val body = parseExpr(lexer, typeGenerics)
                val numGenerics = 0 // TODO
                methods += ParsedMethodDef(loc, isPub, isStatic, numGenerics, "new", params, returnType, body)
            } else {
                val nameTok = lexer.expect(TokenType.IDENTIFIER, "for function name")
                val params = parseParams(lexer, typeGenerics)
                val returnType = if (lexer.consume(TokenType.COLON)) parseType(lexer, typeGenerics) else ParsedType.Tuple(Loc.NEVER, listOf())
                val body = parseExpr(lexer, typeGenerics)
                val numGenerics = 0 // TODO
                methods += ParsedMethodDef(nameTok.loc, isPub, isStatic, numGenerics, nameTok.string(), params, returnType, body)
            }
        } else {
            // Otherwise, we must have field(s).
            val fieldName = lexer.expect(TokenType.IDENTIFIER, "to begin field name, or a function definition")
            lexer.expect(TokenType.COLON, "for mandatory field type annotation")
            val typeAnnotation = parseType(lexer, typeGenerics)
            fields += ParsedFieldDef(fieldName.loc, isPub, isStatic, fieldName.string(), typeAnnotation)
        }
    }
    return fields to methods
}

