package util

import java.util.*
import kotlin.collections.HashMap

abstract class Cache<K, V, M: MutableMap<K, V>>(mapSupplier: () -> M) {

    private var map: M? = mapSupplier()

    // Get the value if present, or compute it if not
    fun get(key: K, func: (K) -> V): V = map?.computeIfAbsent(key, func) ?: throw IllegalStateException("Attempt to use frozen cache")

    // Put the key-value pair in the map
    fun put(key: K, value: V) {
        map?.put(key, value) ?: throw IllegalStateException("Attempt to use frozen cache")
    }

    // Get a frozen Map<K, V> which cannot be edited anymore, only read
    // Also prevents this Cache object from being accessed anymore
    fun freeze(): Map<K, V> = map?.also { map = null } ?: throw IllegalStateException("Attempt to freeze already frozen cache")

}

class IdentityCache<K, V>: Cache<K, V, IdentityHashMap<K, V>>(::IdentityHashMap)
class EqualityCache<K, V>: Cache<K, V, HashMap<K, V>>(::HashMap)