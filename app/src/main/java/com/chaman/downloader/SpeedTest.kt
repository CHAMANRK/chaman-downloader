package com.chaman.downloader

import com.chaquo.python.Python
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

/** Step T: Debug Test 7. Network baseline + yt-dlp ki alag settings ki speed. Koi file save nahi hoti. */
object SpeedTest {
    const val DEFAULT_BASE_URL = "https://speed.cloudflare.com/__down?bytes=100000000"
    const val BASE_SECS = 10.0
    const val YT_SECS = 12

    class Variant(val label: String, val frags: Int, val chunk: Long)

    val VARIANTS = listOf(
        Variant("normal", 1, 0L),
        Variant("fragments 4", 4, 0L),
        Variant("fragments 8", 8, 0L),
        Variant("chunk 2MB", 1, 2L * 1024 * 1024)
    )

    private val cancelled = AtomicBoolean(false)

    fun cancel() {
        cancelled.set(true)
        try {
            Python.getInstance().getModule("tests").callAttr("speed_cancel", true)
        } catch (t: Throwable) {
        }
    }

    /** Seedha HTTP download BASE_SECS tak; yt-dlp ke bina phone ki asli speed. */
    fun baseline(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000
        c.readTimeout = 15_000
        c.setRequestProperty("User-Agent", "Mozilla/5.0")
        val t00 = System.nanoTime()
        val code = c.responseCode
        check(code in 200..299) { "HTTP $code" }
        val samples = ArrayList<SpeedSample>()
        var total = 0L
        var t0 = -1L
        var ttfbMs = 0L
        c.inputStream.use { inp ->
            val buf = ByteArray(64 * 1024)
            while (!cancelled.get()) {
                val n = inp.read(buf)
                if (n < 0) break
                val now = System.nanoTime()
                if (t0 < 0) {
                    t0 = now
                    ttfbMs = (now - t00) / 1_000_000
                }
                total += n
                val t = (now - t0) / 1e9
                samples.add(SpeedSample(t, total))
                if (t >= BASE_SECS) break
            }
        }
        c.disconnect()
        check(samples.isNotEmpty()) { "koi data nahi aaya" }
        val secs = samples.last().tSec
        return speedLine(avgBps(samples), peakBps(samples), total, secs) + ", pehla byte ${ttfbMs} ms"
    }

    /** Pura test. onLog ko har step ke baad poora text milta hai. */
    fun runAll(cacheDir: File, baseUrl: String, ytUrl: String, onLog: (String) -> Unit) {
        cancelled.set(false)
        val sb = StringBuilder()
        fun add(s: String) {
            sb.appendLine(s)
            onLog(sb.toString())
        }
        add("Network baseline (${BASE_SECS.toInt()} s): $baseUrl")
        val bl = try {
            baseline(baseUrl)
        } catch (t: Throwable) {
            "FAIL " + errText(t)
        }
        add("  $bl")

        val dir = File(cacheDir, "speedtest").apply {
            deleteRecursively()
            mkdirs()
        }
        try {
            add("")
            add("yt-dlp: $ytUrl")
            val py = Python.getInstance().getModule("tests")
            for (v in VARIANTS) {
                if (cancelled.get()) {
                    add("Cancel kiya")
                    break
                }
                add("${v.label} (${YT_SECS} s)...")
                val line = try {
                    val j = JSONObject(
                        py.callAttr("speed_probe", ytUrl, dir.absolutePath, YT_SECS, v.frags, v.chunk).toString()
                    )
                    val cl = j.optString("client", "default")
                    speedLine(j.getDouble("avg_bps"), j.getDouble("peak_bps"), j.getLong("bytes"), j.getDouble("secs")) +
                        ", shuru hone me ${String.format(java.util.Locale.US, "%.1f", j.getDouble("start_secs"))} s [client=$cl]" +
                        (if (j.optBoolean("cancelled")) " (cancel)" else "")
                } catch (t: Throwable) {
                    "FAIL " + errText(t)
                }
                add("  $line")
                dir.listFiles()?.forEach { it.delete() }
            }
        } finally {
            dir.deleteRecursively()
        }
        add("")
        add("Khatam. Network baseline ke paas yt-dlp aaye to dikkat YouTube/yt-dlp me nahi, network me hai.")
    }
}
