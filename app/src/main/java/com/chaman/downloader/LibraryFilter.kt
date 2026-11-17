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
