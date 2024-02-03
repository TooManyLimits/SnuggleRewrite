package snuggle.toomanylimits.util.caching

import java.util.*

abstract class Cache<K, V, M: MutableMap<K, V>>(mapSupplier: () -> M) {

    private var map: M = mapSupplier()
    private var frozen: Boolean = false

    // Get the value if present, or compute it if not
    fun get(key: K, func: (K) -> V): V {
        if (!containsKey(key))
            put(key, func(key))
        return map[key]!!
    }

    // Get the value if present
    fun getOrThrow(key: K): V {
        if (!containsKey(key))
            throw IllegalStateException("Expected cache to contain key $key, but it did not? Bug in compiler, please report!")
        return map[key]!!
    }

    // Put the key-value pair in the map
    fun put(key: K, value: V) {
        if (frozen)
            throw IllegalStateException("Attempt to modify frozen cache")
        map.put(key, value)
    }

    fun containsKey(key: K): Boolean = map.containsKey(key)

    // Get a non-mutable Map<K, V>
    // Also prevents this Cache object from being written to anymore
    fun freeze(): Map<K, V> = map.also { frozen = true }

}

//class IdentityCache<K, V>: Cache<K, V, IdentityHashMap<K, V>>(::IdentityHashMap)
class EqualityCache<K, V>: Cache<K, V, HashMap<K, V>>(::HashMap)