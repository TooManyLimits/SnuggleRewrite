package util

import representation.asts.typed.TypeDef

fun toGeneric(base: String, generics: List<TypeDef>, leftBracket: String = "(", rightBracket: String = ")"): String {
    return if (generics.isNotEmpty()) {
        base + leftBracket + generics.joinToString(separator = ",") { it.name } + rightBracket
    } else {
        base
    }
}

fun mangleSlashes(str: String): String = str.replace('/', '\\')