package builtins

import representation.passes.typing.FieldDef
import representation.passes.typing.MethodDef
import representation.passes.typing.TypeDef
import representation.passes.typing.TypeDefCache

/**
 * A type that is defined through Java code, not through
 * Snuggle code. These are injected to the AST while
 * moving from the ParsedAST to the ResolvedAST
 * phase.
 */
interface BuiltinType {

    // The name of this type, as it appears in error messages,
    // and how it is written in Snuggle code.
    val name: String

    // Whether the type is nameable. Can it be referred to by
    // name? This is almost always the case, except for elusive
    // types such as IntLiteral and FloatLiteral, which only
    // exist at compile time.
    val nameable: Boolean

    // The name of this type when it exists at runtime. This
    // generally will have some kind of path prepended to it,
    // or be different altogether. For example, on the String
    // BuiltinType, it will have:
    // name = "String",
    // runtimeName = "java/lang/String".
    // Some types, like bool or f32, have no runtimeName, since
    // there is no class associated with them. This is why it's
    // optional.
    val runtimeName: String?

    // Get the fields of this type, given the generics.
    fun getFields(generics: List<TypeDef>, typeCache: TypeDefCache): List<FieldDef> = listOf() //listOf() for now

    // Get the methods of this type, given the generics.
    fun getMethods(generics: List<TypeDef>, typeCache: TypeDefCache): List<MethodDef> = listOf() //listOf() for now

}