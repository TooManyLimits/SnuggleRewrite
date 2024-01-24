package reflection

import builtins.*
import reflection.annotations.Unsigned
import representation.asts.typed.TypeDef
import representation.passes.typing.*
import java.lang.reflect.AnnotatedArrayType
import java.lang.reflect.AnnotatedType

/**
 * Used to fetch a type given a JVM AnnotatedType, and convert
 * to a TypeDef.
 */

fun fetchType(type: AnnotatedType, typeCache: TypingCache): TypeDef {

    if (type is AnnotatedArrayType) {
        // If array, recurse
        val inner = fetchType(type.annotatedGenericComponentType, typeCache)
        return getGenericBuiltin(ArrayType, listOf(inner), typeCache)
    }

    val c = type.type as Class<*>

    return when (c.name) {
        // Primitives
        "boolean" -> getBasicBuiltin(BoolType, typeCache)
        "byte" -> {
            if (type.isAnnotationPresent(Unsigned::class.java))
                throw IllegalStateException("Failed to reflect class: Do not use @Unsigned byte, instead use @Unsigned(8) int!")
            getBasicBuiltin(I8Type, typeCache)
        }
        "short" -> {
            if (type.isAnnotationPresent(Unsigned::class.java))
                throw IllegalStateException("Failed to reflect class: Do not use @Unsigned short, instead use @Unsigned(16) int!")
            getBasicBuiltin(I8Type, typeCache)
        }
        "int" -> {
            val u = type.getAnnotation(Unsigned::class.java)
            when {
                u == null -> getBasicBuiltin(I32Type, typeCache)
                u.value == 8 -> getBasicBuiltin(U8Type, typeCache)
                u.value == 16 -> getBasicBuiltin(U16Type, typeCache)
                u.value == -1 || u.value == 32 -> getBasicBuiltin(U32Type, typeCache)
                else -> throw IllegalStateException("Failed to reflect class: for \"@Unsigned(n) int\", n should be -1 (default), 8, 16, or 32!")
            }
        }
        "long" -> {
            val u = type.getAnnotation(Unsigned::class.java)
            when {
                u == null -> getBasicBuiltin(I64Type, typeCache)
                u.value == -1 || u.value == 64 -> getBasicBuiltin(U64Type, typeCache)
                else -> throw IllegalStateException("Failed to reflect class: for \"@Unsigned(n) long\", n should be -1 (default), or 64!")
            }
        }
        "float" -> getBasicBuiltin(F32Type, typeCache)
        "double" -> getBasicBuiltin(F64Type, typeCache)
        "char" -> TODO()
        "void" -> getUnit(typeCache)
        // Builtin classes
        "java.lang.String" -> getBasicBuiltin(StringType, typeCache)
        "java.lang.Object" -> getBasicBuiltin(ObjectType, typeCache)
        // Default, assume another reflected class
        else -> getReflectedBuiltin(c, typeCache)
    }

}