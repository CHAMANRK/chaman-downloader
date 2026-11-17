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
