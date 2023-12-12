package builtins

// Bool type.
object BoolType: BuiltinType {

    override val name: String get() = "bool"
    override val nameable: Boolean get() = true
    override val runtimeName: String? get() = null

}