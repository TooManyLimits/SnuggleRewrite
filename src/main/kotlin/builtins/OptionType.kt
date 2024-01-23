package builtins

import builtins.helpers.popType
import builtins.helpers.pushDefaultValue
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import representation.asts.typed.FieldDef
import representation.asts.typed.MethodDef
import representation.asts.typed.TypeDef
import representation.passes.typing.TypeDefCache
import representation.passes.typing.getBasicBuiltin
import representation.passes.typing.getGenericBuiltin
import representation.passes.typing.getUnit
import util.Cons
import util.ConsList
import util.appendElem

/**
 * Optional types!
 * For reference types: nullable version
 * For value types: struct of it + a bool for present/not present
 */
object OptionType: BuiltinType {
    override val numGenerics: Int get() = 1
    override val baseName: String get() = "Option"
    override fun name(generics: List<TypeDef>, typeCache: TypeDefCache): String = generics[0].name + "?"
    override val nameable: Boolean get() = true
    override fun runtimeName(generics: List<TypeDef>, typeCache: TypeDefCache): String =
        if (generics[0].isReferenceType) generics[0].runtimeName!! else "builtin_structs/option/" + generics[0].name
    override fun shouldGenerateClassAtRuntime(generics: List<TypeDef>, typeCache: TypeDefCache): Boolean = !generics[0].isReferenceType
    override fun descriptor(generics: List<TypeDef>, typeCache: TypeDefCache): List<String> =
        if (generics[0].isReferenceType) generics[0].descriptor else generics[0].descriptor + listOf("Z")
    override fun stackSlots(generics: List<TypeDef>, typeCache: TypeDefCache): Int =
        if (generics[0].isReferenceType) 1 else generics[0].stackSlots + 1
    override fun isPlural(generics: List<TypeDef>, typeCache: TypeDefCache): Boolean = !generics[0].isReferenceType
    override fun isReferenceType(generics: List<TypeDef>, typeCache: TypeDefCache): Boolean = false
    override fun hasStaticConstructor(generics: List<TypeDef>, typeCache: TypeDefCache): Boolean = true

    override fun getMethods(generics: List<TypeDef>, typeCache: TypeDefCache): List<MethodDef> {
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
                MethodDef.BytecodeMethodDef(pub = true, static = false, thisType, "bool", boolType, listOf()) {
                    //Stack is [value, bool]
                    val ifPresent = Label()
                    val done = Label()
                    it.visitJumpInsn(Opcodes.IFNE, ifPresent) //[value]
                    popType(innerType, it) //Pop inner off the stack: [].
                    it.visitInsn(Opcodes.ICONST_0) //Push false: [false]
                    it.visitJumpInsn(Opcodes.GOTO, done)
                    it.visitLabel(ifPresent) //[value]
                    popType(innerType, it) //Pop inner off the stack: [].
                    it.visitInsn(Opcodes.ICONST_1) //Push true: [true]
                    it.visitLabel(done) //[true] or [false]
                })
        }
    }

    override fun getFields(generics: List<TypeDef>, typeCache: TypeDefCache): List<FieldDef> {
        // If inside is a reference type, this has no fields
        if (generics[0].isReferenceType)
            return listOf()
        // 2 fields - the inner type, and whether the value is present
        return listOf(
            // 0 offset, it's an instance of the first generic
            FieldDef.BuiltinField(pub = false, static = false, mutable = false,"value", 0, generics[0]),
            // offset equal to the first's stack slots, it's a bool
            FieldDef.BuiltinField(pub = false, static = false, mutable = false,"isPresent", generics[0].stackSlots, getBasicBuiltin(BoolType, typeCache))
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