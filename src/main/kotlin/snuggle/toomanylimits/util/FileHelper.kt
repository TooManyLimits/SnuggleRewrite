package snuggle.toomanylimits.util

private object Idk

fun lazyStringResource(resourcePath: String): Lazy<String> = lazy {
    val stream = Idk::class.java.getResourceAsStream(resourcePath)
        ?: throw RuntimeException("Resource at \"$resourcePath\" could not be found")
    String(stream.readAllBytes(), Charsets.UTF_8) // I have no idea what encoding it is so I'll just guess
}

fun readResourceAsString(resourcePath: String): String = lazyStringResource(resourcePath).value

fun getAllStdlibFiles(): Map<String, Lazy<String>> {
    val allFiles = readResourceAsString("/snuggle/toomanylimits/allfiles.txt")
    return allFiles.lines()
        .filter { it.isNotBlank() }
        .filter { !it.startsWith("//") }
        .map { "std/$it" }
        .associateWith { lazyStringResource("/snuggle/toomanylimits/$it.snuggle") }
}

fun <T, R> Lazy<T>.map(func: (T) -> R): Lazy<R> = lazy { func(this.value) }