package com.chaman.downloader

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream

const val COOKIE_MAX_BYTES = 512 * 1024

/** Link ka cookie site: abhi sirf Instagram. Baaki links ke liye null (cookies nahi lagti). */
fun cookieSiteForUrl(url: String): String? =
    if (url.lowercase().contains("instagram.com")) "instagram" else null

/** Netscape cookies.txt check. Returns (valid cookie lines, bad lines). Header Netscape marker hona chahiye. */
fun validateNetscape(text: String): Pair<Int, Int> {
    val lines = text.split("\n").map { it.trimEnd('\r') }.filter { it.isNotBlank() }
    if (lines.isEmpty() || !lines[0].startsWith("# Netscape HTTP Cookie File")) {
        return Pair(0, 1)
    }
    var good = 0
    var bad = 0
    for (line in lines.drop(1)) {
        if (line.startsWith("#") && !line.startsWith("#HttpOnly_")) continue
        val f = line.split("\t")
        val ok = f.size == 7 &&
            f[0].isNotEmpty() &&
            (f[1] == "TRUE" || f[1] == "FALSE") &&
            f[2].startsWith("/") &&
            (f[3] == "TRUE" || f[3] == "FALSE") &&
            f[4].toLongOrNull() != null &&
            f[5].isNotEmpty()
        if (ok) good++ else bad++
    }
    return Pair(good, bad)
}

class CookieImportResult(val ok: Boolean, val message: String)

/** Browser app se aayi cookies ka UI refresh signal: tick badhe to MainShell Settings tab kholta hai. */
object CookieInbox {
    var tick by mutableStateOf(0)
}

/** Import ki hui cookies: filesDir/cookies/<site>.txt (app private). Values kabhi UI ya log me nahi. */
object CookieStore {
    const val ACTION_IMPORT = "com.chaman.downloader.action.IMPORT_COOKIES"
    const val BROWSER_PKG = "com.chaman.browser.geckotest"
    private const val BROWSER_AUTHORITY = "$BROWSER_PKG.cookies"
    private val SITES = listOf("instagram")

    /** Import ya clear ke baad badhta hai, Settings isse count refresh karta hai. */
    var version by mutableStateOf(0)

    fun dir(ctx: Context): File = File(ctx.filesDir, "cookies").apply { mkdirs() }

    fun file(ctx: Context, site: String): File = File(dir(ctx), "$site.txt")

    fun has(ctx: Context, site: String): Boolean = file(ctx, site).isFile

    fun count(ctx: Context, site: String): Int {
        val f = file(ctx, site)
        if (!f.isFile) return 0
        return try {
            validateNetscape(f.readText()).first
        } catch (e: Exception) {
            0
        }
    }

    fun clear(ctx: Context, site: String) {
        file(ctx, site).delete()
        File(File(dir(ctx), "run"), "$site.txt").delete()
        version++
    }

    private fun fail(msg: String) = CookieImportResult(false, msg)

    private fun readCapped(input: InputStream): String? {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        var total = 0
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            total += n
            if (total > COOKIE_MAX_BYTES) return null
            out.write(buf, 0, n)
        }
        return out.toString("UTF-8")
    }

    private fun readUri(ctx: Context, uri: Uri): String? =
        try {
            ctx.contentResolver.openInputStream(uri)?.use { readCapped(it) }
        } catch (e: Exception) {
            null
        }

    /** Handshake IMPORT_COOKIES: sab check pass ho tabhi save. Rules: Handshake.md. */
    fun importFrom(ctx: Context, uri: Uri, site: String): CookieImportResult {
        if (site !in SITES) return fail("Ye site abhi supported nahi hai")
        if (uri.scheme != "content" || uri.authority != BROWSER_AUTHORITY) {
            return fail("Cookies ka source galat hai")
        }
        val pm = ctx.packageManager
        val owner = try {
            pm.resolveContentProvider(BROWSER_AUTHORITY, 0)?.packageName
        } catch (e: Exception) {
            null
        }
        if (owner != BROWSER_PKG) return fail("Cookie Browser app nahi mili")
        if (pm.checkSignatures(ctx.packageName, BROWSER_PKG) != PackageManager.SIGNATURE_MATCH) {
            return fail("Browser app ki signature match nahi hui (alag key se bani hai)")
        }
        val text = readUri(ctx, uri) ?: return fail("Cookies file padh nahi payi (ya bahut badi hai)")
        val (good, bad) = validateNetscape(text)
        if (bad > 0) return fail("cookies.txt kharab hai ($bad bad lines)")
        if (good == 0) return fail("Cookies khali hain. Browser me pehle login karo")

        val dest = file(ctx, site)
        val tmp = File(dest.parentFile, "$site.txt.tmp")
        return try {
            tmp.writeText(text)
            if (!tmp.renameTo(dest)) {
                dest.delete()
                tmp.renameTo(dest)
            }
            version++
            CookieImportResult(true, "Instagram: $good cookies save ho gayi")
        } catch (e: Exception) {
            fail("Save fail: ${e.message ?: e.javaClass.simpleName}")
        }
    }
}
