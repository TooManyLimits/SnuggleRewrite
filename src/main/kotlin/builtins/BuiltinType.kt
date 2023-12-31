package builtins

import representation.asts.typed.FieldDef
import representation.asts.typed.MethodDef
import representation.asts.typed.TypeDef
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

    // The java descriptor of this type. For instance, booleans
    // have "Z". Integers have "I". Longs are "J". Strings are
    // "Ljava/lang/String;".
    val descriptor: List<String>

    // The number of slots on the stack this takes up.
    // For most basic java types, it's 1. For longs and doubles,
    // it's 2. For plural types, the value can be anything >= 0.
    val stackSlots: Int

    // Get the fields of this type, given the generics.
    fun getFields(generics: List<TypeDef>, typeCache: TypeDefCache): List<FieldDef> = listOf() //listOf() for now

    // Get the methods of this type, given the generics.
    fun getMethods(generics: List<TypeDef>, typeCache: TypeDefCache): List<MethodDef> = listOf() //listOf() for now

    // Get the primary supertype of this type (used for inheritance purposes)
    // Default is null.
    fun getPrimarySupertype(generics: List<TypeDef>, typeCache: TypeDefCache): TypeDef? = null

    // Get all supertypes of this type (used for type-checking purposes)
    // Default is just the primary supertype.
    fun getAllSupertypes(generics: List<TypeDef>, typeCache: TypeDefCache): List<TypeDef> =
        getPrimarySupertype(generics, typeCache)?.let { listOf(it) } ?: listOf()

}