package snuggle.toomanylimits.util

import snuggle.toomanylimits.representation.asts.typed.TypeDef

fun toGeneric(base: String, generics: List<TypeDef>, leftBracket: String = "(", rightBracket: String = ")"): String {
    return if (generics.isNotEmpty()) {
        base + leftBracket + generics.joinToString(separator = ",") { it.name } + rightBracket
    } else {
        base
    }
}

//Cursed. Horrifying if you will
//Use this whenever we send something into an actual
//JVM-adjacent method, like something from ASM. We don't
//need to mangle until then.

//ClassVisitor.visitField/visitMethod()
//MethodVisitor.visitFieldInsn/visitMethodInsn()
fun mangleSlashes(str: String): String = str.replace('/', '\\')