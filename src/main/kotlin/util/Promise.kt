package util

class Promise<T>(private var element: T? = null) {
//    fun get(): T? = element
    fun expect(): T {
        return element ?: throw IllegalStateException("Expected promise to be filled, but wasn't")
    }
    fun fill(v: T) {
        if (element == null) element = v
        else throw IllegalStateException("Attempt to fill promise multiple times")
    }
}