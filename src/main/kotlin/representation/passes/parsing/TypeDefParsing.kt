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
        parseType(lexer, typeGenerics, listOf(), extraInfo = "for superclass")
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
                if (typeType == TypeType.ENUM) throw ParsingException("Cannot have constructors for enum type", loc)
                if (typeType == TypeType.CLASS && isStatic) throw ParsingException("Constructors in classes cannot be static", loc)
                if (typeType == TypeType.STRUCT && !isStatic) throw ParsingException("Constructors in structs must be static", loc)
                if (lexer.check(TokenType.DOUBLE_COLON)) throw ParsingException("Cannot have generic arguments to constructor", loc)
                val params = parseParams(lexer, typeGenerics, listOf())
                val returnType = if (lexer.consume(TokenType.COLON)) parseType(lexer, typeGenerics, listOf()) else ParsedType.Tuple(Loc.NEVER, listOf())
                if (typeType == TypeType.CLASS && (returnType !is ParsedType.Tuple || returnType.elementTypes.isNotEmpty()))
                    throw ParsingException("Class constructor must return unit, but found $returnType", returnType.loc)
                val body = parseExpr(lexer, typeGenerics, listOf())
                val numGenerics = 0 // Always 0, constructor cannot be generic
                methods += ParsedMethodDef(loc, isPub, isStatic, numGenerics, "new", params, returnType, body)
            } else {
                val nameTok = lexer.expect(TokenType.IDENTIFIER, "for function name")
                val methodGenerics = parseGenerics(lexer)
                val params = parseParams(lexer, typeGenerics, methodGenerics)
                val returnType = if (lexer.consume(TokenType.COLON)) parseType(lexer, typeGenerics, methodGenerics) else ParsedType.Tuple(Loc.NEVER, listOf())
                val body = parseExpr(lexer, typeGenerics, methodGenerics)
                methods += ParsedMethodDef(nameTok.loc, isPub, isStatic, methodGenerics.size, nameTok.string(), params, returnType, body)
            }
        } else {
            // Otherwise, we must have field(s).
            val fieldName = lexer.expect(TokenType.IDENTIFIER, "to begin field name, or a function definition")
            lexer.expect(TokenType.COLON, "for mandatory field type annotation")
            val typeAnnotation = parseType(lexer, typeGenerics, listOf())
            fields += ParsedFieldDef(fieldName.loc, isPub, isStatic, fieldName.string(), typeAnnotation)
        }
    }
    return fields to methods
}

