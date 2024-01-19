package reflection.annotations

@Target(AnnotationTarget.CLASS, AnnotationTarget.FIELD, AnnotationTarget.FUNCTION, AnnotationTarget.CONSTRUCTOR)
@Retention(AnnotationRetention.RUNTIME)
annotation class SnuggleRename(val newName: String)

@Target(AnnotationTarget.TYPE)
@Retention(AnnotationRetention.RUNTIME)
annotation class Unsigned(val bits: Int = -1)