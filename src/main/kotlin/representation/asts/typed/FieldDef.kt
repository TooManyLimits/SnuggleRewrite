package representation.asts.typed

import representation.passes.lexing.Loc

sealed interface FieldDef {
    val pub: Boolean
    val static: Boolean
    val mutable: Boolean
    val pluralOffset: Int? // If the owning type is plural, and this is non-static, this should be the field's offset.
    val name: String
    val type: TypeDef

    data class BuiltinField(
        override val pub: Boolean, override val static: Boolean, override val mutable: Boolean,
        override val name: String, override val pluralOffset: Int?, override val type: TypeDef
    ): FieldDef
    data class SnuggleFieldDef(
        val loc: Loc, override val pub: Boolean, override val static: Boolean, override val mutable: Boolean,
        override val name: String, override val pluralOffset: Int?, override val type: TypeDef
    ): FieldDef
}