package snuggle.toomanylimits.util

class Promise<T>(private var element: T? = null) {
    fun expect(): T {
        return element ?: throw IllegalStateException("Expected promise to be fulfilled, but wasn't")
    }
    fun fulfill(v: T) {
        if (element == null) element = v
        else throw IllegalStateException("Attempt to fulfill promise multiple times")
    }

    override fun toString(): String =
        element?.toString() ?: "<Promise not yet fulfilled>"
}