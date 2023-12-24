package util

inline fun <T> Iterable<T>.allIndexed(predicate: (index: Int, T) -> Boolean): Boolean {
    var index = 0
    for (element in this) if (!predicate(index++, element)) return false
    return true
}

// Sort a mutable list using insertion sort.
// Insertion sort has the property that it can
// be used for topological sorting, unlike the
// built-in java/kotlin sorting methods.
// This means it can be used, for example, in sorting methods by specificness.
fun <T> MutableList<T>.insertionSort(comparator: (T, T) -> Int) {
    val output = ArrayList<T>(this.size)
    outer@ for (elem in this) {
        for (i in 0 until output.size) {
            val comparison = comparator(output[i], elem)
            if (comparison > 0) {
                output += elem
                continue@outer
            }
        }
        output += elem
    }
    this.clear()
    this.addAll(output)
}