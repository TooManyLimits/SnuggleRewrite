package util

import java.util.IdentityHashMap

class IdentityCache<K, V> {

    private val map: IdentityHashMap<K, V> = IdentityHashMap()

    // Get the value if present, or compute it if not
    fun get(key: K, func: (K) -> V): V = map.computeIfAbsent(key, func)

    // Get a frozen Map<K, V> which cannot be edited anymore, only read
    fun freeze(): Map<K, V> = map

}