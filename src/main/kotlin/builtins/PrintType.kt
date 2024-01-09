package builtins

import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import representation.asts.typed.MethodDef
import representation.asts.typed.TypeDef
import representation.passes.typing.TypeDefCache
import representation.passes.typing.getBasicBuiltin
import representation.passes.typing.getUnit
import java.io.PrintStream
import java.io.PrintWriter

// Temporary builtin to allow printing
object PrintType: BuiltinType {
    override val name: String get() = "print"
    override val nameable: Boolean get() = true
    override val runtimeName: String? get() = null
    override val descriptor: List<String> get() = listOf()
    override fun stackSlots(generics: List<TypeDef>, typeCache: TypeDefCache): Int = 0
    override fun isPlural(generics: List<TypeDef>, typeCache: TypeDefCache): Boolean = true
    override fun isReferenceType(generics: List<TypeDef>, typeCache: TypeDefCache): Boolean = false

    override fun getMethods(generics: List<TypeDef>, typeCache: TypeDefCache): List<MethodDef> {
        val thisType = getBasicBuiltin(this, typeCache)
        val boolType = getBasicBuiltin(BoolType, typeCache)
        val unitType = getUnit(typeCache)
        val i32Type = getBasicBuiltin(I32Type, typeCache)
        val objectType = getBasicBuiltin(ObjectType, typeCache)
        return listOf(
            // print(bool)
            MethodDef.BytecodeMethodDef(pub = true, static = true, 0, thisType, "invoke", unitType, listOf(boolType)) {
                // [arg]
                it.visitFieldInsn(Opcodes.GETSTATIC, Type.getInternalName(System::class.java), "out", Type.getDescriptor(PrintStream::class.java))
                // [arg, System.out]
                it.visitInsn(Opcodes.SWAP)
                // [System.out, arg]
                it.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(PrintStream::class.java), "println", "(Z)V", false)
                // []
            },
            // print(i32)
            MethodDef.BytecodeMethodDef(pub = true, static = true, 0, thisType, "invoke", unitType, listOf(i32Type)) {
                // [arg]
                it.visitFieldInsn(Opcodes.GETSTATIC, Type.getInternalName(System::class.java), "out", Type.getDescriptor(PrintStream::class.java))
                // [arg, System.out]
                it.visitInsn(Opcodes.SWAP)
                // [System.out, arg]
                it.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(PrintStream::class.java), "println", "(I)V", false)
                // []
            },
            // print(Object)
            MethodDef.BytecodeMethodDef(pub = true, static = true, 0, thisType, "invoke", unitType, listOf(objectType)) {
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