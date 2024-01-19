package reflection

import builtins.BuiltinType
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import reflection.annotations.SnuggleAcknowledgeGenerics
import reflection.annotations.SnuggleAllow
import reflection.annotations.SnuggleDeny
import reflection.annotations.SnuggleRename
import representation.asts.typed.FieldDef
import representation.asts.typed.MethodDef
import representation.asts.typed.TypeDef
import representation.passes.typing.TypeDefCache
import representation.passes.typing.getReflectedBuiltin
import java.lang.reflect.AnnotatedElement
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import kotlin.reflect.KClass

/**
 * A BuiltinType generated by reflecting a java/kotlin Class
 */
class ReflectedBuiltinType(val reflectedClass: Class<*>): BuiltinType {

    init {
        if (reflectedClass.typeParameters.isNotEmpty())
            if (!reflectedClass.isAnnotationPresent(SnuggleAcknowledgeGenerics::class.java))
                throw IllegalArgumentException("Generic jvm classes not supported for reflection - to override this, add @SnuggleAcknowledgeGenerics. Generic types will be erased to Object.")
    }

    // Generic classes not supported
    override val numGenerics: Int get() = 0

    override val baseName: String = reflectedClass.annotationOrElse(SnuggleRename::class, reflectedClass.simpleName) { it.newName }

    override fun name(generics: List<TypeDef>, typeCache: TypeDefCache): String = baseName
    override val nameable: Boolean = true //TODO configurable with annotation
    override fun runtimeName(generics: List<TypeDef>, typeCache: TypeDefCache): String =
        Type.getInternalName(reflectedClass)
    override fun descriptor(generics: List<TypeDef>, typeCache: TypeDefCache): List<String> =
        listOf("L" + this.runtimeName(generics, typeCache) + ";")
    override fun stackSlots(generics: List<TypeDef>, typeCache: TypeDefCache): Int = 1
    override fun isPlural(generics: List<TypeDef>, typeCache: TypeDefCache): Boolean = false
    override fun isReferenceType(generics: List<TypeDef>, typeCache: TypeDefCache): Boolean = true
    override fun hasStaticConstructor(generics: List<TypeDef>, typeCache: TypeDefCache): Boolean = false //TODO Configurable
    override fun getPrimarySupertype(generics: List<TypeDef>, typeCache: TypeDefCache): TypeDef
        = fetchType(reflectedClass.annotatedSuperclass, typeCache)

    override fun getFields(generics: List<TypeDef>, typeCache: TypeDefCache): List<FieldDef> {
        return reflectedClass.declaredFields.mapNotNull {
            // If this is denied, or neither this nor the class is allowed, don't emit a field.
            if (it.isAnnotationPresent(SnuggleDeny::class.java) ||
                !it.isAnnotationPresent(SnuggleAllow::class.java) &&
                !reflectedClass.isAnnotationPresent(SnuggleAllow::class.java)
            ) null
            // Otherwise, do emit one.
            else FieldDef.BuiltinField(
                Modifier.isPublic(it.modifiers),
                Modifier.isStatic(it.modifiers),
                !Modifier.isFinal(it.modifiers),
                it.annotationOrElse(SnuggleRename::class, it.name) { it.newName },
                null,
                fetchType(it.annotatedType, typeCache)
            )
        }
    }

    override fun getMethods(generics: List<TypeDef>, typeCache: TypeDefCache): List<MethodDef> {
        val thisType = getReflectedBuiltin(reflectedClass, typeCache)
        return reflectedClass.declaredMethods.mapNotNull {
            reflectMethod(it, thisType, typeCache)
        } + reflectedClass.declaredConstructors.mapNotNull {
            null // TODO Constructors
        }
    }

    private fun reflectMethod(method: Method, owningType: TypeDef, typeCache: TypeDefCache): MethodDef? {
        // If this is denied, or neither this nor the class is allowed, don't emit the method.
        if (method.isAnnotationPresent(SnuggleDeny::class.java) ||
            !method.isAnnotationPresent(SnuggleAllow::class.java) &&
            !reflectedClass.isAnnotationPresent(SnuggleAllow::class.java)
        )
            return null
        val isStatic = Modifier.isStatic(method.modifiers)
        val name = method.annotationOrElse(SnuggleRename::class, method.name) { it.newName }
        return MethodDef.BytecodeMethodDef(
            pub = true, isStatic, owningType, name,
            returnType = fetchType(method.annotatedReturnType, typeCache),
            paramTypes = method.annotatedParameterTypes.map { fetchType(it, typeCache) }
        ) {
            val opcode = if (isStatic) Opcodes.INVOKESTATIC else Opcodes.INVOKEVIRTUAL
            val owner = owningType.runtimeName
            val descriptor = Type.getMethodDescriptor(method)
            it.visitMethodInsn(opcode, owner, name, descriptor, false)
        }
    }

}

// Helper for classes and methods and w/e else

private fun <C: Annotation, T> AnnotatedElement.annotationOrElse(annotationClass: KClass<C>, defaultValue: T, getter: (C) -> T): T {
    if (this.isAnnotationPresent(annotationClass.java))
        return getter(this.getAnnotation(annotationClass.java))
    return defaultValue
}