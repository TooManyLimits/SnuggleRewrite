package builtins

import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import representation.asts.typed.MethodDef
import representation.asts.typed.TypeDef
import representation.passes.typing.TypeDefCache
import representation.passes.typing.getBasicBuiltin
import java.io.PrintStream
import java.io.PrintWriter

// Temporary builtin to allow printing
object PrintType: BuiltinType {
    override val name: String get() = "print"
    override val nameable: Boolean get() = true
    override val runtimeName: String? get() = null
    override val descriptor: List<String> get() = listOf()
    override val stackSlots: Int get() = 1

    override fun getMethods(generics: List<TypeDef>, typeCache: TypeDefCache): List<MethodDef> {
        val thisType = getBasicBuiltin(this, typeCache)
        val boolType = getBasicBuiltin(BoolType, typeCache)
        val i32Type = getBasicBuiltin(I32Type, typeCache)
        return listOf(
            // Just have it output bool for now, since we don't have a unit/tuple type yet :P
            // print(bool)
            MethodDef.BytecodeMethodDef(pub = true, static = true, thisType, "invoke", boolType, listOf(boolType)) {
                // [arg]
                it.visitFieldInsn(Opcodes.GETSTATIC, Type.getInternalName(System::class.java), "out", Type.getDescriptor(PrintStream::class.java))
                // [arg, System.out]
                it.visitInsn(Opcodes.SWAP)
                // [System.out, arg]
                it.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(PrintStream::class.java), "println", "(Z)V", false)
                // []
                it.visitInsn(Opcodes.ICONST_0) // push a bool on the stack, since again we dont have a tuple yet :P
            },
            // print(i32)
            MethodDef.BytecodeMethodDef(pub = true, static = true, thisType, "invoke", boolType, listOf(i32Type)) {
                // [arg]
                it.visitFieldInsn(Opcodes.GETSTATIC, Type.getInternalName(System::class.java), "out", Type.getDescriptor(PrintStream::class.java))
                // [arg, System.out]
                it.visitInsn(Opcodes.SWAP)
                // [System.out, arg]
                it.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(PrintStream::class.java), "println", "(I)V", false)
                // []
                it.visitInsn(Opcodes.ICONST_0) // push a bool on the stack, since again we dont have a tuple yet :P
            },
        )
    }
}