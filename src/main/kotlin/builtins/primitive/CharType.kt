package builtins.primitive

import java.lang.Character

import builtins.BuiltinType
import builtins.helpers.constBinary
import builtins.helpers.constUnary
import org.objectweb.asm.Opcodes
import representation.asts.typed.MethodDef
import representation.asts.typed.TypeDef
import representation.passes.typing.TypingCache
import representation.passes.typing.getBasicBuiltin
import java.math.BigInteger

// In the JVM, chars are *really* just a 16-bit unsigned integer...
object CharType: IntType(signed = false, bits = 16) {

    override val baseName: String get() = "char"
    override fun name(generics: List<TypeDef>, typeCache: TypingCache): String = baseName
    override val nameable: Boolean get() = true
    override fun runtimeName(generics: List<TypeDef>, typeCache: TypingCache): String? = null
    override fun descriptor(generics: List<TypeDef>, typeCache: TypingCache): List<String> = listOf("C")
    override fun stackSlots(generics: List<TypeDef>, typeCache: TypingCache): Int = 1
    override fun isPlural(generics: List<TypeDef>, typeCache: TypingCache): Boolean = false
    override fun isReferenceType(generics: List<TypeDef>, typeCache: TypingCache): Boolean = false
    override fun hasStaticConstructor(generics: List<TypeDef>, typeCache: TypingCache): Boolean = true

    override fun getMethods(generics: List<TypeDef>, typeCache: TypingCache): List<MethodDef> {
        val charType = getBasicBuiltin(CharType, typeCache)
        val u16Type = getBasicBuiltin(U16Type, typeCache)
        val boolType = getBasicBuiltin(BoolType, typeCache)
        return listOf(
            // Checking properties of the char, delegates to certain java methods. char -> bool
            constUnary(false, charType, "isDigit", boolType) {c: Char -> Character.isDigit(c)}
                orBytecode { it.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Character", "isDigit", "(C)Z", false) },
            constUnary(false, charType, "isWhitespace", boolType) {c: Char -> Character.isWhitespace(c)}
                    orBytecode { it.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Character", "isWhitespace", "(C)Z", false) },
            constUnary(false, charType, "isLetter", boolType) {c: Char -> Character.isLetter(c)}
                    orBytecode { it.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Character", "isLetter", "(C)Z", false) },
            constUnary(false, charType, "isUpperCase", boolType) {c: Char -> Character.isUpperCase(c)}
                    orBytecode { it.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Character", "isUpperCase", "(C)Z", false) },
            constUnary(false, charType, "isLowerCase", boolType) {c: Char -> Character.isLowerCase(c)}
                    orBytecode { it.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Character", "isLowerCase", "(C)Z", false) },

            // Converting between chars. char -> char
            constUnary(false, charType, "toUpperCase", boolType) {c: Char -> Character.toUpperCase(c)}
                    orBytecode { it.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Character", "toUpperCase", "(C)C", false) },
            constUnary(false, charType, "toLowerCase", boolType) {c: Char -> Character.toLowerCase(c)}
                    orBytecode { it.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Character", "toLowerCase", "(C)C", false) },

            // Char arithmetic
            constBinary<Char, Char, Char>(false, charType, "add", charType, charType) {a, b -> a + b.code }
                orBytecode doThenConvert { it.visitInsn(Opcodes.IADD) },
            constBinary<Char, Char, Char>(false, charType, "sub", charType, charType) {a, b -> (a - b).toChar() }
                    orBytecode doThenConvert { it.visitInsn(Opcodes.ISUB) },

            // Comparison
            constBinary<Char, Char, Boolean>(false, charType, "gt", boolType, charType) {a, b -> a > b} orBytecode intCompare(Opcodes.IF_ICMPGT),
            constBinary<Char, Char, Boolean>(false, charType, "lt", boolType, charType) {a, b -> a < b} orBytecode intCompare(Opcodes.IF_ICMPLT),
            constBinary<Char, Char, Boolean>(false, charType, "ge", boolType, charType) {a, b -> a >= b} orBytecode intCompare(Opcodes.IF_ICMPGE),
            constBinary<Char, Char, Boolean>(false, charType, "le", boolType, charType) {a, b -> a <= b} orBytecode intCompare(Opcodes.IF_ICMPLE),
            constBinary<Char, Char, Boolean>(false, charType, "eq", boolType, charType) {a, b -> a == b} orBytecode intCompare(Opcodes.IF_ICMPEQ),

            // TODO remove - just to convert char -> u16 temporarily, until casting is added
            constUnary<Char, BigInteger>(false, charType, "u16", u16Type) {c -> BigInteger.valueOf(c.code.toLong())}
                    orBytecode { /*do nothing*/ },
        )

    }

}