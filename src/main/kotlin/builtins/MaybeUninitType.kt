package builtins

import builtins.helpers.pushDefaultValue
import representation.asts.typed.FieldDef
import representation.asts.typed.MethodDef
import representation.asts.typed.TypeDef
import representation.passes.typing.TypeDefCache
import representation.passes.typing.getGenericBuiltin
import util.toGeneric

/**
 * Works as a wrapper around nullability, in a similar way to Option.
 * However, it does no runtime tracking, and is literally just an unsafe
 * wrapper around its inner generic. Inspired by Rust MaybeUninit. I say
 * this is "unsafe", but the worst that can happen is a NullPointerException
 * if you use a reference type that's uninitialized.
 */
object MaybeUninitType: BuiltinType {
    override val numGenerics: Int get() = 1
    override val baseName: String get() = "MaybeUninit"
    override fun name(generics: List<TypeDef>, typeCache: TypeDefCache): String
        = toGeneric(baseName, generics, "<", ">")
    override val nameable: Boolean get() = true
    override fun runtimeName(generics: List<TypeDef>, typeCache: TypeDefCache): String? = generics[0].runtimeName
    override fun descriptor(generics: List<TypeDef>, typeCache: TypeDefCache): List<String> = generics[0].descriptor
    override fun stackSlots(generics: List<TypeDef>, typeCache: TypeDefCache): Int = generics[0].stackSlots
    override fun isPlural(generics: List<TypeDef>, typeCache: TypeDefCache): Boolean = true
    override fun isReferenceType(generics: List<TypeDef>, typeCache: TypeDefCache): Boolean = false
    override fun hasStaticConstructor(generics: List<TypeDef>, typeCache: TypeDefCache): Boolean = true

    override fun getFields(generics: List<TypeDef>, typeCache: TypeDefCache): List<FieldDef> {
        // MaybeUninit<ReferenceType> -> field is Option<ReferenceType>
        // MaybeUninit<PluralType> -> fields are MaybeUninit<Each member of the plural type>
        // MaybeUninit<OtherType> -> field is OtherType
        val innerType = generics[0]
        return when {
            innerType.isReferenceType -> {
                // Wrap in Option
                val fieldType = getGenericBuiltin(OptionType, listOf(innerType), typeCache)
                listOf(FieldDef.BuiltinField(false, false, false, "value", 0, fieldType))
            }
            innerType.isPlural -> innerType.nonStaticFields.map {
                // Wrap each inner field in MaybeUninit
                val fieldType = getGenericBuiltin(MaybeUninitType, listOf(it.type), typeCache)
                FieldDef.BuiltinField(pub = false, static = false, mutable = false,
                    name = it.name, pluralOffset = it.pluralOffset, type = fieldType
                )
            }
            // Don't wrap, just leave as is
            else -> listOf(FieldDef.BuiltinField(false, false, false, "value", 0, innerType))
        }
    }

    // Methods are essentially all no-ops, except for creating a new empty MaybeUninit, which
    // just pushes default value
    override fun getMethods(generics: List<TypeDef>, typeCache: TypeDefCache): List<MethodDef> {
        val thisType = getGenericBuiltin(MaybeUninitType, generics, typeCache)
        val inner = generics[0]
        return listOf(
            // Default constructor new() -> push a default value
            MethodDef.BytecodeMethodDef(pub = true, static = true, thisType, "new", thisType, listOf()) {
                pushDefaultValue(inner, it)
            },
            // Wrapping constructor new(inner). No-op
            MethodDef.BytecodeMethodDef(pub = true, static = true, thisType, "new", thisType, listOf(inner)) {},
            // Getter, does nothing, basically an std::mem::transmute.
            MethodDef.BytecodeMethodDef(pub = true, static = false, thisType, "get", inner, listOf()) {}
        )
    }
}