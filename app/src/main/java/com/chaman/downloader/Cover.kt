package com.chaman.downloader

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** MP3 ke liye cover (thumbnail) + tags lagana. Kuch bhi fail ho to download fail nahi hota (sirf log). */
object Cover {
    private const val MAX_SIDE = 600

    /** Thumbnail url -> JPEG bytes (max 600px). Fail par null. */
    fun fetchJpeg(url: String): ByteArray? = try {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 10_000
        val raw = c.inputStream.use { it.readBytes() }
        val bmp = BitmapFactory.decodeByteArray(raw, 0, raw.size)
        if (bmp == null) null else {
            val scale = MAX_SIDE.toFloat() / maxOf(bmp.width, bmp.height)
            val out = if (scale < 1f)
                Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt().coerceAtLeast(1), (bmp.height * scale).toInt().coerceAtLeast(1), true)
            else bmp
            val bos = ByteArrayOutputStream()
            out.compress(Bitmap.CompressFormat.JPEG, 88, bos)
            bos.toByteArray()
        }
    } catch (t: Throwable) {
        null
    }

    /** Tags lagao; log line wapas (DownloadState log me jati hai). Exception bahar nahi aata. */
    fun tagMp3(mp3: File, title: String, artist: String, thumbUrl: String): String = try {
        val jpeg = if (thumbUrl.isBlank()) null else fetchJpeg(thumbUrl)
        Id3.replaceTag(mp3, Id3.buildTag(title, artist, jpeg))
        "mp3 tags lage: title" + (if (artist.isNotBlank()) ", artist" else "") + (if (jpeg != null) ", cover" else ", cover nahi (thumbnail nahi mila)")
    } catch (t: Throwable) {
        "mp3 tags nahi lage (${t::class.java.simpleName}: ${t.message}), bina tag ke save"
    }
}
