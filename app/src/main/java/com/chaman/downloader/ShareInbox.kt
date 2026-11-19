package com.chaman.downloader

import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Share menu se aaya link. Activity set karti hai, MainShell uthata hai. */
object ShareInbox {
    var pending by mutableStateOf<String?>(null)
}

private val URL_RE = Regex("https?://[^\\s]+", RegexOption.IGNORE_CASE)

/** Shared text me se pehla http(s) link nikalta hai (YouTube "title + link" dono format). Na mile to null. */
fun extractFirstUrl(text: String?): String? {
    if (text.isNullOrBlank()) return null
    val m = URL_RE.find(text) ?: return null
    return m.value.trimEnd('.', ',', ')', ']', '>', '"', '\'', ';')
}

/** ACTION_SEND intent se link; ACTION_SEND na ho to null. */
fun urlFromShareIntent(intent: Intent?): String? {
    if (intent?.action != Intent.ACTION_SEND) return null
    return extractFirstUrl(intent.getStringExtra(Intent.EXTRA_TEXT))
}

const val MAX_LINK_LEN = 2000

/**
 * Link field ka input saaf karta hai. Newline hataata hai, 2000 char cap lagata hai, aur agar ek saath
 * 20+ char aa gaye (paste) aur usme link hai to sirf pehla link rakhta hai (title/extra text nahi).
 * Chhote edits (typing) ko chhuta nahi.
 */
fun normalizeLinkInput(prev: String, next: String): String {
    var v = next.replace('\n', ' ').replace('\r', ' ')
    if (v.length - prev.length > 20) {
        extractFirstUrl(v)?.let { v = it }
    }
    return v.take(MAX_LINK_LEN)
}

/** Auto-search sirf in famous sites ke https link par chalta hai (baaki links Search button se). */
private val AUTO_SEARCH_HOSTS = listOf(
    "youtube.com", "youtu.be", "instagram.com", "facebook.com", "fb.watch", "fb.com",
    "tiktok.com", "twitter.com", "x.com", "vimeo.com", "dailymotion.com", "reddit.com",
    "twitch.tv", "soundcloud.com", "pinterest.com", "pin.it", "threads.net", "snapchat.com"
)

/** https link + famous site (host exact ya subdomain) ho to true. Paste ke baad auto-search ke liye. */
fun isAutoSearchUrl(s: String): Boolean {
    val t = s.trim()
    if (t.isEmpty() || t.length > MAX_LINK_LEN || t.any { it.isWhitespace() }) return false
    if (!t.startsWith("https://", ignoreCase = true)) return false
    val host = t.substring(8)
        .substringBefore('/').substringBefore('?').substringBefore('#')
        .substringAfterLast('@').substringBefore(':').lowercase()
    if (host.isEmpty()) return false
    return AUTO_SEARCH_HOSTS.any { host == it || host.endsWith(".$it") }
}

/** Keyboard paste jaisa bada ek saath input (typing nahi). normalizeLinkInput wali hi 20 char limit. */
fun looksLikePaste(prev: String, next: String): Boolean = next.length - prev.length > 20
