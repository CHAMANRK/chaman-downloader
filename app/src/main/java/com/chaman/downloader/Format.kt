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
