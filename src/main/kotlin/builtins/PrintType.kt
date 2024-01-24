package builtins

import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import representation.asts.typed.MethodDef
import representation.asts.typed.TypeDef
import representation.passes.typing.TypingCache
import representation.passes.typing.getBasicBuiltin
import representation.passes.typing.getUnit
import java.io.PrintStream

// Temporary builtin to allow printing
object PrintType: BuiltinType {
    override val baseName: String get() = "print"
    override fun name(generics: List<TypeDef>, typeCache: TypingCache): String = baseName
    override val nameable: Boolean get() = true
    override fun runtimeName(generics: List<TypeDef>, typeCache: TypingCache): String? = null
    override fun descriptor(generics: List<TypeDef>, typeCache: TypingCache): List<String> = listOf()
    override fun stackSlots(generics: List<TypeDef>, typeCache: TypingCache): Int = 0
    override fun isPlural(generics: List<TypeDef>, typeCache: TypingCache): Boolean = true
    override fun isReferenceType(generics: List<TypeDef>, typeCache: TypingCache): Boolean = false
    override fun hasStaticConstructor(generics: List<TypeDef>, typeCache: TypingCache): Boolean = true

    override fun getMethods(generics: List<TypeDef>, typeCache: TypingCache): List<MethodDef> {
        val thisType = getBasicBuiltin(this, typeCache)
        val boolType = getBasicBuiltin(BoolType, typeCache)
        val unitType = getUnit(typeCache)
        val i32Type = getBasicBuiltin(I32Type, typeCache)
        val u32Type = getBasicBuiltin(U32Type, typeCache)
        val objectType = getBasicBuiltin(ObjectType, typeCache)
        return listOf(
            // print(bool)
            MethodDef.BytecodeMethodDef(pub = true, static = true, thisType, "invoke", unitType, listOf(boolType)) {
                // [arg]
                it.visitFieldInsn(Opcodes.GETSTATIC, Type.getInternalName(System::class.java), "out", Type.getDescriptor(PrintStream::class.java))
                // [arg, System.out]
                it.visitInsn(Opcodes.SWAP)
                // [System.out, arg]
                it.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(PrintStream::class.java), "println", "(Z)V", false)
                // []
            },
            // print(i32)
            MethodDef.BytecodeMethodDef(pub = true, static = true, thisType, "invoke", unitType, listOf(i32Type)) {
                // [arg]
                it.visitFieldInsn(Opcodes.GETSTATIC, Type.getInternalName(System::class.java), "out", Type.getDescriptor(PrintStream::class.java))
                // [arg, System.out]
                it.visitInsn(Opcodes.SWAP)
                // [System.out, arg]
                it.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(PrintStream::class.java), "println", "(I)V", false)
                // []
            },
            // print(u32)
            MethodDef.BytecodeMethodDef(pub = true, static = true, thisType, "invoke", unitType, listOf(u32Type)) {
                // [arg as int]
                it.visitInsn(Opcodes.I2L) // [arg sign-extended]
                it.visitLdcInsn(0xFFFFFFFFL) // [arg sign-extended, 0xFFFFFFFF]
                it.visitInsn(Opcodes.LAND) // [arg as long, unsigned]
                it.visitFieldInsn(Opcodes.GETSTATIC, Type.getInternalName(System::class.java), "out", Type.getDescriptor(PrintStream::class.java))
                // [arg, System.out]
                it.visitInsn(Opcodes.DUP_X2) // [System.out, arg, System.out]
                it.visitInsn(Opcodes.POP) // [System.out, arg]
                it.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(PrintStream::class.java), "println", "(J)V", false)
                // []
            },
            // print(Object)
            MethodDef.BytecodeMethodDef(pub = true, static = true, thisType, "invoke", unitType, listOf(objectType)) {
                // [arg]
                it.visitFieldInsn(Opcodes.GETSTATIC, Type.getInternalName(System::class.java), "out", Type.getDescriptor(PrintStream::class.java))
                // [arg, System.out]
                it.visitInsn(Opcodes.SWAP)
                // [System.out, arg]
                it.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Object", "toString", "()Ljava/lang/String;", false)
                // [System.out, arg.toString()]
                it.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(PrintStream::class.java), "println", "(Ljava/lang/String;)V", false)
                // []
            },
        )
    }
}