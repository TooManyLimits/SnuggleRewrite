package util

typealias ConsMap<K, V> = ConsList<Pair<K, V>>

fun <K, V> ConsMap<K, V>.extend(key: K, value: V): ConsMap<K, V> = Cons(Pair(key, value), this)

fun <K, V> ConsMap<K, V>.lookup(key: K): V? = this.firstOrNull { it.first == key } ?.second