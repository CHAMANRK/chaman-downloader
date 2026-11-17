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

    fun convert(cacheDir: File): String {
        val out = File(cacheDir, "probe_tone.m4a")
        out.delete()
        val cmd = "-y -f lavfi -i sine=frequency=440:duration=2 -c:a aac -b:a 128k \"${out.absolutePath}\""
        val (ok, logs) = exec(cmd)
        check(ok) { "convert fail:\n" + logs.takeLast(800) }
        check(out.exists() && out.length() > 0) { "output file nahi bani ya khaali hai" }
        return "OK: ${out.name}, ${out.length()} bytes"
    }
}
