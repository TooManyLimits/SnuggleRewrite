package util


sealed interface ConsList<out T> : Iterable<T> {

    fun <R> map(func: (elem: T) -> R): ConsList<R> {
        return when (this) {
            is Nil<T> -> nil()
            is Cons<T> -> Cons(func(elem), rest.map(func))
        }
    }

    fun <R> mapIndexed(index: Int = 0, func: (index: Int, elem: T) -> R): ConsList<R> {
        return when (this) {
            is Nil<T> -> nil()
            is Cons<T> -> Cons(func(index, elem), rest.mapIndexed(index + 1, func))
        }
    }

    fun <R> flatMap(func: (elem: T) -> ConsList<R>): ConsList<R> {
        return when (this) {
            is Nil<T> -> nil()
            is Cons<T> -> func(elem).append(rest.flatMap(func))
        }
    }

    fun filter(func: (T) -> Boolean): ConsList<T> {
        return when (this) {
            is Nil<T> -> nil()
            is Cons<T> -> if (func(elem)) Cons(elem, rest.filter(func)) else rest.filter(func)
        }
    }

    fun <K> associate(getKey: (T) -> K): ConsMap<K, T> {
        return ConsMap(map { e -> Pair(getKey(e), e) })
    }

    fun reverse(): ConsList<T> {
        var cur = this
        var res: ConsList<T> = nil()
        while (cur is Cons<T>) {
            res = Cons(cur.elem, res)
            cur = cur.rest
        }
        return res
    }

    companion object {
        fun <T> nil(): Nil<T> {
            return Nil.INSTANCE as Nil<T>
        }
        fun <T> of(vararg elems: T): ConsList<T> {
            return elems.foldRight(nil(), ::Cons)
        }
    }
}

fun <T> ConsList<T>.append(other: ConsList<T>): ConsList<T> {
    return when (this) {
        is Nil<T> -> other
        is Cons<T> -> Cons(elem, rest.append(other))
    }
}


class Nil<T> private constructor() : ConsList<T> {
    init {
        if (INSTANCE != null) //no it isn't, kotlin! im doing cursed things you cannot comprehend
            throw IllegalStateException("Cannot instantiate Nil; instead use static method nil()")
    }
    companion object {
        val INSTANCE: Nil<Any> = Nil()
    }
    override fun equals(other: Any?): Boolean {
        return this === other
    }
    override fun hashCode(): Int {
        return System.identityHashCode(this)
    }
    override fun toString(): String {
        return "[]"
    }

    override fun iterator() = object : Iterator<Nothing> {
        override fun hasNext() = false
        override fun next() = throw NoSuchElementException()
    }
}

data class Cons<T>(val elem: T, val rest: ConsList<T>): ConsList<T> {
    override fun equals(other: Any?): Boolean {
        return super.equals(other)
    }
    override fun hashCode(): Int {
        return 31 * elem.hashCode() + rest.hashCode()
    }
    override fun toString(): String {
        return "$elem::$rest"
    }
    override fun iterator() = iterator {
        yield(elem)
        yieldAll(rest)
    }
}