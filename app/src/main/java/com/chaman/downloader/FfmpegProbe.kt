package com.chaman.downloader

import java.io.File

/**
 * Phase 0 FFmpeg probe. Library ka API reflection se call hota hai, taaki fork ka
 * package/API alag nikle to build na toote, balki device pe saaf FAIL message aaye.
 */
object FfmpegProbe {
    private const val CLS = "com.arthenica.ffmpegkit.FFmpegKit"

    private fun exec(cmd: String): Pair<Boolean, String> {
        val cls = try {
            Class.forName(CLS)
        } catch (e: ClassNotFoundException) {
            throw IllegalStateException("$CLS nahi mila - fork ka package alag ho sakta hai")
        }
        val session = cls.getMethod("execute", String::class.java).invoke(null, cmd)!!
        val rc = session.javaClass.getMethod("getReturnCode").invoke(session)!!
        val ok = rc.javaClass.getMethod("isValueSuccess").invoke(rc) as Boolean
        val logs = session.javaClass.getMethod("getAllLogsAsString").invoke(session) as? String ?: ""
        return ok to logs
    }

    fun version(): String {
        val (ok, logs) = exec("-version")
        check(ok) { "ffmpeg -version fail:\n" + logs.takeLast(600) }
        return logs.lineSequence().firstOrNull { it.startsWith("ffmpeg version") } ?: logs.take(300)
    }

    /** Video + audio ko bina re-encode ke ek file me jodna (-c copy). */
    fun merge(video: File, audio: File, out: File) {
        out.delete()
        val flags = if (out.extension.equals("mp4", ignoreCase = true)) " -movflags +faststart" else ""
        val cmd = "-y -i \"${video.absolutePath}\" -i \"${audio.absolutePath}\" -c copy$flags \"${out.absolutePath}\""
        val (ok, logs) = exec(cmd)
        check(ok) { "merge fail:\n" + logs.takeLast(800) }
        check(out.exists() && out.length() > 0) { "merge ke baad file nahi bani ya khaali hai" }
    }

    fun convert(cacheDir: File): String {
        val out = File(cacheDir, "probe_tone.m4a")
        out.delete()
        val cmd = "-y -f lavfi -i sine=frequency=440:duration=2 -c:a aac -b:a 128k \"${out.absolutePath}\""
        val (ok, logs) = exec(cmd)
        check(ok) { "convert fail:\n" + logs.takeLast(800) }
        check(out.exists() && out.length() > 0) { "output file nahi bani ya khaali hai" }
        return "OK: ${out.name}, ${out.length()} bytes"
    }

    @Volatile
    private var mp3Cache: Boolean? = null

    /** Is FFmpeg build me libmp3lame encoder hai ya nahi (ek baar check, cache). */
    fun hasMp3Encoder(): Boolean {
        mp3Cache?.let { return it }
        val r = try {
            val (ok, logs) = exec("-hide_banner -encoders")
            ok && logs.contains("libmp3lame")
        } catch (t: Throwable) {
            false
        }
        mp3Cache = r
        return r
    }

    /** Audio file ko mp3 (192 kbps, CBR) me badalna; video stream hataya jata hai. */
    fun toMp3(src: File, out: File) {
        out.delete()
        val cmd = "-y -i \"${src.absolutePath}\" -vn -c:a libmp3lame -b:a 192k -id3v2_version 3 \"${out.absolutePath}\""
        val (ok, logs) = exec(cmd)
        check(ok) { "mp3 convert fail:\n" + logs.takeLast(800) }
        check(out.exists() && out.length() > 0) { "mp3 file nahi bani ya khaali hai" }
    }

    /** Debug test: 2 sec ki tone ko mp3 me convert. */
    fun convertMp3(cacheDir: File): String {
        val enc = hasMp3Encoder()
        check(enc) { "libmp3lame encoder is FFmpeg build me NAHI hai (mp3 possible nahi)" }
        val tmp = File(cacheDir, "probe_tone_src.m4a")
        tmp.delete()
        val (ok1, l1) = exec("-y -f lavfi -i sine=frequency=440:duration=2 -c:a aac -b:a 128k \"${tmp.absolutePath}\"")
        check(ok1) { "tone bananay me fail:\n" + l1.takeLast(500) }
        val out = File(cacheDir, "probe_tone.mp3")
        toMp3(tmp, out)
        tmp.delete()
        return "OK: libmp3lame hai, ${out.name}, ${out.length()} bytes"
    }
}
