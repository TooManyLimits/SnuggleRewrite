package util

@JvmInline
value class ConsMap<out K, out V>(val list: ConsList<Pair<K, V>>): Iterable<Pair<K, V>> {

    fun <V2> mapValues(func: (V) -> V2): ConsMap<K, V2> = ConsMap(this.list.map { p -> p.first to func(p.second) })
    fun <K2> mapKeys(func: (K) -> K2): ConsMap<K2, V> = ConsMap(this.list.map { p -> func(p.first) to p.second })
    fun filterValues(func: (V) -> Boolean): ConsMap<K, V> = ConsMap(this.list.filter { func(it.second) })
    fun filterKeys(func: (K) -> Boolean): ConsMap<K, V> = ConsMap(this.list.filter { func(it.first) })

    fun reverse(): ConsMap<K, V> = ConsMap(this.list.reverse())

    fun isEmpty() = list is Nil
    fun isNotEmpty() = list !is Nil

    companion object {
        fun <K, V> of(vararg pairs: Pair<K, V>): ConsMap<K, V> = ConsMap(ConsList.of(*pairs))
        fun <K, V> fromIterable(iterable: Iterable<Pair<K, V>>): ConsMap<K, V> = ConsMap(ConsList.fromIterable(iterable))
        fun <K, V> join(maps: Iterable<ConsMap<K, V>>): ConsMap<K, V> = of<K, V>().extendMany(maps)
    }

    override fun iterator(): Iterator<Pair<K, V>> = list.iterator()
}

// Extend this map with a new pair.
fun <K, V> ConsMap<K, V>.extend(key: K, value: V): ConsMap<K, V> = ConsMap(Cons(key to value, this.list))

// Extend this map with another map.
fun <K, V> ConsMap<K, V>.extend(other: ConsMap<K, V>): ConsMap<K, V> = ConsMap(other.list.append(this.list))

// Extend this map with each of the other maps, in order.
fun <K, V> ConsMap<K, V>.extendMany(other: Iterable<ConsMap<K, V>>): ConsMap<K, V> {
    var cur = this
    for (map in other)
        cur = cur.extend(map)
    return cur
}
fun <K, V> ConsMap<K, V>.lookup(key: K): V? = this.list.firstOrNull { it.first == key } ?.second
