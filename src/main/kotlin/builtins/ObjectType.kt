package builtins

// Object type.
// java/lang/Object underneath.
object ObjectType: BuiltinType {

    override val name: String get() = "Object"
    override val nameable: Boolean get() = true
    override val runtimeName: String get() = "java/lang/Object"

}