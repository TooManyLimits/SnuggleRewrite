package util

/**
 * Note: PLEASE PLEASE PLEASE do not create a
 * LateInit with a lambda that closes over
 * mutable variables. This ends up leading to
 * awful, terrible code, and is the reason this
 * project had to be rewritten in the first place.
 */
class LateInit<I, T>(val func: (I) -> T) {
    var cachedRes: T? = null

    // Return the cached value if it exists, otherwise
    // compute it from the given input.
    fun get(input: I): T = cachedRes ?: (func(input).also { cachedRes = it })
}

