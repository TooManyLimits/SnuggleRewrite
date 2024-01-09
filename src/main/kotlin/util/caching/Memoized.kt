package util.caching

import java.util.*
import kotlin.collections.HashMap

// Memoized function backed by a cache
abstract class Memoized<K, V, M: MutableMap<K, V>>(cacheSupplier: () -> Cache<K, V, M>, private val func: (K) -> V) {

    private var cache: Cache<K, V, M> = cacheSupplier()

    operator fun invoke(arg: K): V = cache.get(arg, func)

}

class IdentityMemoized<K, V>(func: (K) -> V): Memoized<K, V, IdentityHashMap<K, V>>(::IdentityCache, func)
class EqualityMemoized<K, V>(func: (K) -> V): Memoized<K, V, HashMap<K, V>>(::EqualityCache, func)