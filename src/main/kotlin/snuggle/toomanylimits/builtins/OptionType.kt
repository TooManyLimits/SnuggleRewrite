package snuggle.toomanylimits.builtins

import snuggle.toomanylimits.builtins.helpers.pushDefaultValue
import snuggle.toomanylimits.builtins.primitive.BoolType
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import snuggle.toomanylimits.representation.asts.typed.FieldDef
import snuggle.toomanylimits.representation.asts.typed.MethodDef
import snuggle.toomanylimits.representation.asts.typed.TypeDef
import snuggle.toomanylimits.representation.passes.typing.TypingCache
import snuggle.toomanylimits.representation.passes.typing.getBasicBuiltin
import snuggle.toomanylimits.representation.passes.typing.getGenericBuiltin
import snuggle.toomanylimits.representation.passes.typing.getUnit
import snuggle.toomanylimits.util.Cons
import snuggle.toomanylimits.util.ConsList
import snuggle.toomanylimits.util.appendElem

/**
 * Optional types!
 * For reference types: nullable version
 * For value types: struct of it + a bool for present/not present
 */
object OptionType: BuiltinType {
    override val numGenerics: Int get() = 1
    override val baseName: String get() = "Option"
    override fun name(generics: List<TypeDef>, typeCache: TypingCache): String = generics[0].name + "?"
    override val nameable: Boolean get() = true
    override fun runtimeName(generics: List<TypeDef>, typeCache: TypingCache): String =
        if (generics[0].isReferenceType) generics[0].runtimeName!! else "builtin_structs/option/" + generics[0].name
    override fun shouldGenerateClassAtRuntime(generics: List<TypeDef>, typeCache: TypingCache): Boolean = !generics[0].isReferenceType
    override fun descriptor(generics: List<TypeDef>, typeCache: TypingCache): List<String> =
        if (generics[0].isReferenceType) generics[0].descriptor else generics[0].descriptor + listOf("Z")
    override fun stackSlots(generics: List<TypeDef>, typeCache: TypingCache): Int =
        if (generics[0].isReferenceType) 1 else generics[0].stackSlots + 1
    override fun isPlural(generics: List<TypeDef>, typeCache: TypingCache): Boolean = !generics[0].isReferenceType
    override fun isReferenceType(generics: List<TypeDef>, typeCache: TypingCache): Boolean = false
    override fun extensible(generics: List<TypeDef>, typeCache: TypingCache): Boolean = false
    override fun hasStaticConstructor(generics: List<TypeDef>, typeCache: TypingCache): Boolean = true

    override fun getMethods(generics: List<TypeDef>, typeCache: TypingCache): List<MethodDef> {
        val thisType = getGenericBuiltin(OptionType, generics, typeCache)
        val innerType = generics[0]
        val boolType = getBasicBuiltin(BoolType, typeCache)
        val unitType = getUnit(typeCache)
        // Change method implementations depending on inner type
        return if (innerType.isReferenceType) {
            // Working with a nullable reference type!
            listOf(
                MethodDef.BytecodeMethodDef(pub = true, static = false, thisType, "get", innerType, listOf()) {
                    val afterError = Label()
                    it.visitInsn(Opcodes.DUP)
                    it.visitJumpInsn(Opcodes.IFNONNULL, afterError)
                    errorInGet(it)
                    it.visitLabel(afterError)
                },
                MethodDef.BytecodeMethodDef(pub = true, static = true, thisType, "new", thisType, listOf()) {
                    it.visitInsn(Opcodes.ACONST_NULL)
                },
                MethodDef.BytecodeMethodDef(pub = true, static = true, thisType, "new", thisType, listOf(innerType)) {},
                MethodDef.BytecodeMethodDef(pub = true, static = false, thisType, "bool", boolType, listOf()) {
                    val ifPresent = Label()
                    val done = Label()
                    it.visitJumpInsn(Opcodes.IFNONNULL, ifPresent)
                    it.visitInsn(Opcodes.ICONST_0)
                    it.visitJumpInsn(Opcodes.GOTO, done)
                    it.visitLabel(ifPresent)
                    it.visitInsn(Opcodes.ICONST_1)
                    it.visitLabel(done)
                })
        } else {
            // Working with a value type + a bool!
            listOf(
                MethodDef.BytecodeMethodDef(pub = true, static = false, thisType, "get", innerType, listOf(), null, { writer, _, _ ->
                    val afterError = Label()
                    writer.visitJumpInsn(Opcodes.IFNE, afterError)
                    errorInGet(writer)
                    writer.visitLabel(afterError)
                }) {
                    // Prepend existing desired fields with "value", and add an extra "ifPresent" desire.
                    it.map<ConsList<FieldDef>> {
                        Cons(thisType.fields.find { it.name == "value" }!!, it)
                    }.appendElem(ConsList.of(thisType.fields.find { it.name == "isPresent" }!!))
                },
                MethodDef.BytecodeMethodDef(pub = true, static = true, thisType, "new", thisType, listOf()) {
                    pushDefaultValue(innerType, it)
                    it.visitInsn(Opcodes.ICONST_0)
                },
                MethodDef.BytecodeMethodDef(pub = true, static = true, thisType, "new", thisType, listOf(innerType)) {
                    it.visitInsn(Opcodes.ICONST_1)
                },
                MethodDef.BytecodeMethodDef(pub = true, static = false, thisType, "bool", boolType, listOf(), null, { writer, _, _ ->
                    //Stack is just the bool, we don't need to do anything
                }) {
                    if (it != ConsList.of(ConsList.nil<FieldDef>())) throw IllegalStateException()
                    ConsList.of(ConsList.of(thisType.fields.find { it.name == "isPresent" }!!))
                }
            )
        }
    }

    override fun getFields(generics: List<TypeDef>, typeCache: TypingCache): List<FieldDef> {
        // If inside is a reference type, this has no fields
        if (generics[0].isReferenceType)
            return listOf()
        // 2 fields - the inner type, and whether the value is present
        return listOf(
            // 0 offset, it's an instance of the first generic
            FieldDef.BuiltinField(pub = false, static = false, mutable = false,"value", 0, generics[0]),
            // offset equal to the first's stack slots, it's a bool
            FieldDef.BuiltinField(pub = false, static = false, mutable = false,"isPresent", generics[0].stackSlots, getBasicBuiltin(
                BoolType, typeCache))
        )
    }

}

// When you try to get() on an empty option
private fun errorInGet(writer: MethodVisitor) {
    // TODO: Better error
    val runtimeException = Type.getInternalName(RuntimeException::class.java)
    writer.visitTypeInsn(Opcodes.NEW, runtimeException)
    writer.visitInsn(Opcodes.DUP)
    writer.visitLdcInsn("Attempt to use get() on empty Option")
    writer.visitMethodInsn(Opcodes.INVOKESPECIAL, runtimeException, "<init>", "(Ljava/lang/String;)V", false)
    writer.visitInsn(Opcodes.ATHROW)
}