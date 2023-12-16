package util

fun <T> union(sets: Iterable<Set<T>>): Set<T> {
    var curSet = setOf<T>()
    for (set in sets) {
        if (set.isNotEmpty())
            curSet = if (curSet.isEmpty()) set else curSet + set
    }
    return curSet
}

fun <T> union(vararg sets: Set<T>): Set<T> {
    var curSet = setOf<T>()
    for (set in sets) {
        if (set.isNotEmpty())
            curSet = if (curSet.isEmpty()) set else curSet + set
    }
    return curSet
}