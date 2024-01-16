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

    // The base name of the type, without generic details.
    // For example, take Option<T>. Its name() is "T?", but its
    // baseName is "Option".
    val baseName: String

    // The name of this type, as it appears in error messages,
    // and how it is written in Snuggle code.
    fun name(generics: List<TypeDef>, typeCache: TypeDefCache): String

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
    fun runtimeName(generics: List<TypeDef>, typeCache: TypeDefCache): String?

    // Whether this type should generate a corresponding class at runtime. The class
    // will have name given by runtimeName. Used by a few types, like Option<T> where
    // T is not a reference type, but for most cases it's just false.
    fun shouldGenerateClassAtRuntime(generics: List<TypeDef>, typeCache: TypeDefCache): Boolean = false

    // The java descriptor of this type. For instance, booleans
    // have "Z". Integers have "I". Longs are "J". Strings are
    // "Ljava/lang/String;".
    fun descriptor(generics: List<TypeDef>, typeCache: TypeDefCache): List<String>

    // The number of generics this type has. 0 by default.
    val numGenerics: Int get() = 0

    // The number of slots on the stack this takes up.
    // For most basic java types, it's 1. For longs and doubles,
    // it's 2. For plural types, the value can be anything >= 0.
    fun stackSlots(generics: List<TypeDef>, typeCache: TypeDefCache): Int

    // If a type is plural, that means it consists of multiple
    // JVM values, stored together on the stack. For example,
    // structs are plural types. When translating to the jvm,
    // special care needs to be given for plural types.
    fun isPlural(generics: List<TypeDef>, typeCache: TypeDefCache): Boolean

    // Whether the type can be considered a reference type.
    // I don't have a perfect definition of this, unfortunately, but one
    // way of looking at it would be to say "field accesses on this type
    // should work the same way as they would for a normal class". There
    // may be some variance in how this is interpreted.
    fun isReferenceType(generics: List<TypeDef>, typeCache: TypeDefCache): Boolean

    // Whether the type uses a static constructor style. If true, constructors
    // should be of the form "static fn new() -> Type", returning an instance,
    // if false they should be "fn new() -> Unit", initializing an existing instance.
    fun hasStaticConstructor(generics: List<TypeDef>, typeCache: TypeDefCache): Boolean

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