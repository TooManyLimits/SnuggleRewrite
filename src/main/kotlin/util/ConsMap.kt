package util

@JvmInline
value class ConsMap<out K, out V>(val list: ConsList<Pair<K, V>>) {

    fun <V2> mapValues(func: (V) -> V2): ConsMap<K, V2> = ConsMap(this.list.map { p -> p.first to func(p.second) })

    fun isEmpty() = list is Nil
    fun isNotEmpty() = list !is Nil
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