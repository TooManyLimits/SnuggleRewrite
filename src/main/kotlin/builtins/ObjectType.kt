package builtins

// Object type.
// java/lang/Object underneath.
object ObjectType: BuiltinType {

    override val name: String get() = "Object"
    override val nameable: Boolean get() = true
    override val runtimeName: String get() = "java/lang/Object"
    override val descriptor: List<String> = listOf("L$runtimeName;")
    override val stackSlots: Int get() = 1
}