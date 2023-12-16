package util

import ast.typing.TypeDef

fun toGeneric(base: String, generics: List<TypeDef>, leftBracket: String = "(", rightBracket: String = ")"): String {
    return if (generics.isNotEmpty()) {
        base + leftBracket + generics.joinToString(separator = ",") { it.name } + rightBracket
    } else {
        base
    }
}