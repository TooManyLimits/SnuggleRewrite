package util

@JvmInline
value class ConsMap<out K, out V>(val list: ConsList<Pair<K, V>>) {

    fun <V2> mapValues(func: (V) -> V2): ConsMap<K, V2> = ConsMap(this.list.map { p -> Pair(p.first, func(p.second)) })
}

fun <K, V> ConsMap<K, V>.extend(key: K, value: V): ConsMap<K, V> = ConsMap(Cons(Pair(key, value), this.list))
fun <K, V> ConsMap<K, V>.lookup(key: K): V? = this.list.firstOrNull { it.first == key } ?.second