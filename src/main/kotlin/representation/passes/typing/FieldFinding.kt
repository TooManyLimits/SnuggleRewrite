package representation.passes.typing

import errors.CompilationException
import representation.asts.typed.FieldDef
import representation.asts.typed.TypeDef
import representation.passes.lexing.Loc

fun findNonStaticField(receiverType: TypeDef, name: String, loc: Loc): FieldDef {
    val field = receiverType.allNonStaticFields.find { it.name == name }
        ?: throw NoSuchFieldException(receiverType, false, name, loc)
    if (field.pub || (field is FieldDef.SnuggleFieldDef && field.loc.fileName == loc.fileName))
        return field
    throw InaccessibleFieldException(receiverType, name, loc)
}
fun findStaticField(receiverType: TypeDef, name: String, loc: Loc): FieldDef {
    val field = receiverType.staticFields.find { it.name == name }
        ?: throw NoSuchFieldException(receiverType, true, name, loc)
    if (field.pub || (field is FieldDef.SnuggleFieldDef && field.loc.fileName == loc.fileName))
        return field
    throw InaccessibleFieldException(receiverType, name, loc)
}


class NoSuchFieldException(receiverType: TypeDef, static: Boolean, fieldName: String, loc: Loc)
    : CompilationException("There does not exist a ${if (static) "static" else "non-static"} field with name \"$fieldName\" in type \"${receiverType.name}\"", loc)
class InaccessibleFieldException(receiverType: TypeDef, fieldName: String, loc: Loc)
    : CompilationException("Field \"$fieldName\" in type \"${receiverType.name}\" is not accessible from here. Non-pub fields can only be accessed within the same file.", loc)