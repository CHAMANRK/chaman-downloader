package com.chaman.downloader

import java.util.Locale

/** Pure helper functions (unit test me check hote hain). */

/** bytes/sec -> "500 KB/s" ya (1024 KB/s se upar) "2.50 MB/s". */
fun fmtSpeed(bps: Double): String {
    if (bps <= 0.0) return "-"
    val kb = bps / 1024.0
    return if (kb >= 1024.0) String.format(Locale.US, "%.2f MB/s", kb / 1024.0)
    else String.format(Locale.US, "%.0f KB/s", kb)
}

/** bytes -> KB / MB / GB (1024 base). */
fun fmtBytes(b: Double): String {
    val kb = b / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format(Locale.US, "%.2f GB", gb)
        mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
        else -> String.format(Locale.US, "%.0f KB", kb)
    }
}

/** seconds -> "m:ss" ya "h:mm:ss"; negative = unknown. */
fun fmtEta(sec: Double): String {
    if (sec < 0.0) return "-"
    val t = sec.toLong()
    val h = t / 3600
    val m = (t % 3600) / 60
    val s = t % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%d:%02d", m, s)
}

/** Queue ki progress line: "video 12.3 MB / 45.6 MB   2.40 MB/s   ETA 0:15". */
fun formatProgressLine(kind: String, done: Double, total: Double, speed: Double, eta: Double): String {
    val tot = if (total > 0.0) fmtBytes(total) else "?"
    return "$kind ${fmtBytes(done)} / $tot   ${fmtSpeed(speed)}   ETA ${fmtEta(eta)}"
}

/**
 * Video title -> safe file name (extension ke bina).
 * Android ke illegal chars hatata hai, spaces collapse, UTF-8 bytes me limit
 * (Hindi jaise 3-byte characters ke liye bhi 255-byte filename limit safe rahe).
 */
fun safeFileName(title: String, fallback: String, maxBytes: Int = 180): String {
    val illegal = "/\\:*?\"<>|"
    val sb = StringBuilder()
    for (ch in title) {
        if (ch.code < 32 || illegal.indexOf(ch) >= 0) sb.append(' ') else sb.append(ch)
    }
    val cleaned = sb.toString().replace(Regex("\\s+"), " ").trim().trim('.', ' ')
    val out = StringBuilder()
    var bytes = 0
    var i = 0
    while (i < cleaned.length) {
        val cp = cleaned.codePointAt(i)
        val b = when {
            cp < 0x80 -> 1
            cp < 0x800 -> 2
            cp < 0x10000 -> 3
            else -> 4
        }
        if (bytes + b > maxBytes) break
        out.appendCodePoint(cp)
        bytes += b
        i += Character.charCount(cp)
    }
    val s = out.toString().trim().trim('.', ' ')
    return if (s.isEmpty()) fallback else s
}

/** seconds -> "m:ss" / "h:mm:ss" (video duration). */
fun fmtDuration(sec: Long): String = fmtEta(sec.toDouble())

/** 1234567 -> "1.2M", 15300 -> "15.3K", 950 -> "950". */
fun fmtCount(n: Long): String = when {
    n >= 1_000_000_000L -> String.format(Locale.US, "%.1fB", n / 1_000_000_000.0)
    n >= 1_000_000L -> String.format(Locale.US, "%.1fM", n / 1_000_000.0)
    n >= 1_000L -> String.format(Locale.US, "%.1fK", n / 1_000.0)
    else -> n.toString()
}

private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

/** "20261117" -> "17 Nov 2026"; format na samjhe to input wapas. */
fun fmtDateYmd(s: String): String {
    if (s.length != 8 || !s.all { it.isDigit() }) return s
    val m = s.substring(4, 6).toInt()
    if (m !in 1..12) return s
    return "${s.substring(6, 8).toInt()} ${MONTHS[m - 1]} ${s.substring(0, 4)}"
}

/** Error ka pehla line, "PyException: RuntimeError: " jaise prefix hata ke (Queue card me dikhane ke liye). */
fun shortError(msg: String): String {
    val first = msg.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: return ""
    val clean = first.removePrefix("PyException:").trim().removePrefix("RuntimeError:").trim()
    return if (clean.length > 200) clean.take(200) + "…" else clean
}

/** v0.4.20 diagnostic: error text me "cookies: ..." line ho to wahi (Queue card me error ke neeche dikhane ke liye), warna "". */
fun cookiesLine(msg: String): String =
    msg.lineSequence().map { it.trim() }.firstOrNull { it.startsWith("cookies:") } ?: ""

/** Queue card ka error text: chhota error + (Instagram pe) cookies diagnostic line. */
fun queueErrorText(msg: String): String {
    val base = shortError(msg)
    val c = cookiesLine(msg)
    return if (c.isEmpty() || base == c) base else base + "\n" + c
}

/** Notification ki progress line: "Video download ho raha hai · 45% · 12.00 MB/s · 2:10 bacha". */
fun notifProgressText(kind: String, pct: Double, speed: Double, eta: Double): String {
    val what = when (kind) {
        "video" -> "Video download ho raha hai"
        "audio" -> "Audio download ho raha hai"
        else -> "Download ho raha hai"
    }
    val parts = ArrayList<String>()
    parts += what
    if (pct >= 0.0) parts += "${pct.toInt()}%"
    if (speed > 0.0) parts += fmtSpeed(speed)
    if (eta >= 0.0) parts += "${fmtEta(eta)} bacha"
    return parts.joinToString(" · ")
}

/** Notification title (halat ke hisaab se) aur body (progress). */
fun notifProgressTitle(kind: String): String = when (kind) {
    "video" -> "Video download ho raha hai"
    "audio" -> "Audio download ho raha hai"
    else -> "Download ho raha hai"
}

fun notifProgressBody(pct: Double, speed: Double, eta: Double): String {
    val parts = ArrayList<String>()
    if (pct >= 0.0) parts += "${pct.toInt()}%"
    if (speed > 0.0) parts += fmtSpeed(speed)
    if (eta >= 0.0) parts += "${fmtEta(eta)} bacha"
    return parts.joinToString(" · ")
}

/** Fail notification ki body: title me "Download fail ho gaya" pehle se hai, isliye sirf kaaran. */
fun notifFailBody(raw: String): String {
    val t = friendlyFailText(raw)
    return when {
        t.startsWith("Download fail ho gaya: ") -> t.removePrefix("Download fail ho gaya: ")
        t == "Download fail ho gaya. Retry karo." -> "Retry karo."
        else -> t
    }
}

/** Error (poora text) -> notification ke liye chhota Hinglish kaaran. */
fun friendlyFailText(raw: String): String {
    val l = raw.lowercase(Locale.US)
    fun has(vararg k: String) = k.any { l.contains(it) }
    return when {
        has("bot-check") -> shortError(raw) // Python ka message pehle se Hinglish hai
        has("no space left", "enospc") -> "Phone me jagah kam hai. Space khali karke Retry karo."
        has("unable to resolve host", "unknownhost", "no address associated", "timed out",
            "timeout", "network is unreachable", "connection reset", "connection refused") ->
            "Internet me problem aayi. Net check karke Retry karo."
        has("429", "too many requests") -> "Site ne abhi rok diya (bahut requests). Thodi der baad Retry karo."
        has("login required", "log in", "sign in") -> "Is link ko login chahiye ya site ne rok diya. Baad me try karo."
        has("private video", "video unavailable", "this video is not available", "has been removed") ->
            "Ye video available nahi hai (private ya hata diya gaya)."
        else -> {
            val s = shortError(raw)
            if (s.isEmpty()) "Download fail ho gaya. Retry karo."
            else "Download fail ho gaya: " + (if (s.length > 100) s.take(100) + "…" else s)
        }
    }
}

/** MP3 option ka format marker: "mp3|<yt-dlp audio format id>". Python download() ye prefix pehchanta hai. */
const val MP3_PREFIX = "mp3|"

fun mp3Fmt(sourceFmt: String): String = MP3_PREFIX + sourceFmt

fun isMp3Fmt(fmt: String): Boolean = fmt.startsWith(MP3_PREFIX)

