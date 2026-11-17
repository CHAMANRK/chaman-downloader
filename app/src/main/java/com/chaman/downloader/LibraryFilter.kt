package com.chaman.downloader

enum class LibFilter { ALL, VIDEO, AUDIO }

/** Library search + filter: naam me query (case-insensitive) aur type (video/audio) match. */
fun libMatches(name: String, mime: String, query: String, filter: LibFilter): Boolean {
    val okType = when (filter) {
        LibFilter.ALL -> true
        LibFilter.VIDEO -> mime.startsWith("video")
        LibFilter.AUDIO -> mime.startsWith("audio")
    }
    if (!okType) return false
    val q = query.trim()
    return q.isEmpty() || name.contains(q, ignoreCase = true)
}

// Share ke liye mime: sab same -> wahi; sirf type same -> type + wildcard; warna poora wildcard.
fun commonMime(mimes: List<String>): String {
    if (mimes.isEmpty()) return "*/*"
    if (mimes.all { it == mimes[0] }) return mimes[0]
    val types = mimes.map { it.substringBefore('/') }
    return if (types.all { it == types[0] }) types[0] + "/*" else "*/*"
}

/** "Video.mp4" -> ("Video", ".mp4"); extension na ho to ("Video", ""). */
fun splitFileName(name: String): Pair<String, String> {
    val dot = name.lastIndexOf('.')
    return if (dot > 0) Pair(name.substring(0, dot), name.substring(dot)) else Pair(name, "")
}

/** Rename: user ka input -> naya poora file naam (purani extension wahi rehti hai). Khali/invalid ho to null. */
fun renamedFileName(oldName: String, input: String): String? {
    val ext = splitFileName(oldName).second
    var t = input.trim()
    if (ext.isNotEmpty() && t.endsWith(ext, ignoreCase = true)) t = t.dropLast(ext.length)
    val base = safeFileName(t, "")
    return if (base.isEmpty()) null else base + ext
}
