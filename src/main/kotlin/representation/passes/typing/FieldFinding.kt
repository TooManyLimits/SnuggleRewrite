package representation.passes.typing

import errors.CompilationException
import representation.asts.typed.FieldDef
import representation.asts.typed.TypeDef
import representation.passes.lexing.Loc

fun findField(receiverType: TypeDef, static: Boolean, name: String, loc: Loc): FieldDef {
    // Find fields with name
    val fields = receiverType.fields.filter { it.name == name }
    // No fields -> throw NoSuchFieldException
    if (fields.isEmpty()) throw NoSuchFieldException(receiverType, name, loc)
    // One field, but its static/nonstatic doesn't match
    if (fields.size == 1 && fields[0].static != static) throw FieldStaticMismatchException(receiverType, name, static, loc)
    // More than one field with correct static-ness (bug in compiler)
    val fieldsCorrectStatic = fields.filter { it.static == static }
    if (fieldsCorrectStatic.size > 1) throw IllegalStateException("Multiple fields of same name \"$name\" and static-ness on same type \"${receiverType.name}\"? Bug in compiler, please report")
    // Check accessibility
    val bestField = fieldsCorrectStatic[0]
    if (bestField.pub || (bestField is FieldDef.SnuggleFieldDef && bestField.loc.fileName == loc.fileName))
        return bestField
    else
        throw InaccessibleFieldException(receiverType, name, loc)
}


class NoSuchFieldException(receiverType: TypeDef, fieldName: String, loc: Loc)
    : CompilationException("There does not exist a field with name \"$fieldName\" in type \"${receiverType.name}\"", loc)
class InaccessibleFieldException(receiverType: TypeDef, fieldName: String, loc: Loc)
    : CompilationException("Field \"$fieldName\" in type \"${receiverType.name}\" is not accessible from here. Non-pub fields can only be accessed within the same file.", loc)
class FieldStaticMismatchException(receiverType: TypeDef, fieldName: String, accessWasStatic: Boolean, loc: Loc)
    : CompilationException("Field \"$fieldName\" in type \"${receiverType.name}\" is " +
        "${if (accessWasStatic) "not" else ""} static, but tried to access " +
        if (accessWasStatic) "statically" else "as instance field", loc)