package util.caching

import java.util.*

/**
 * Functionality of these types is rather similar to the Cache, but with a key difference.
 *
 * Caches allow you to check if a value was already calculated, and not calculate the value.
 * This also allows that, but it ALSO allows you to check if the value is CURRENTLY being
 * calculated.
 *
 * As a tradeoff, this class cannot provide you with the finished value when you query it.
 * The finished values are only intended to be read out at the very end, once everything is
 * done.
 */

abstract class IncrementalCalculator<K, V, M: MutableMap<K, V?>>(mapSupplier: () -> M) {
    private var map: M? = mapSupplier()

    fun <K2: K> compute(key: K2, func: (K2) -> V?) {
        val map = map ?: throw IllegalStateException("Attempt to use frozen calculator")
        if (!map.containsKey(key)) {
            map.put(key, null)
            map.put(key, func(key))
        }
    }

    fun freeze(): M
        = (map ?: throw IllegalStateException("Attempt to freeze calculator twice")).also { map = null }

}

//class IdentityIncrementalCalculator<K, V>: IncrementalCalculator<K, V, IdentityHashMap<K, V?>>(::IdentityHashMap)
class EqualityIncrementalCalculator<K, V>: IncrementalCalculator<K, V, HashMap<K, V?>>(::HashMap)