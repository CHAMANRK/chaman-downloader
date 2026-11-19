package com.chaman.downloader

/** Speed test ke pure helpers (unit test ho sakte hain). Sample = (t sec, cumulative bytes). */
class SpeedSample(val tSec: Double, val bytes: Long)

/** Poore time ki average speed (bytes/s). */
fun avgBps(samples: List<SpeedSample>): Double {
    val last = samples.lastOrNull() ?: return 0.0
    return if (last.tSec > 0.0) last.bytes / last.tSec else 0.0
}

/** Kam se kam `windowSec` ke hisse me sabse tez speed (bytes/s). Python `_peak_bps` jaisa hi. */
fun peakBps(samples: List<SpeedSample>, windowSec: Double = 1.0): Double {
    var best = 0.0
    var j = 0
    for (i in samples.indices) {
        while (j < samples.size && samples[j].tSec - samples[i].tSec < windowSec) j++
        if (j >= samples.size) break
        val dt = samples[j].tSec - samples[i].tSec
        best = maxOf(best, (samples[j].bytes - samples[i].bytes) / dt)
    }
    return best
}

/** Ek result line: "avg 2.10 MB/s, peak 3.40 MB/s (25.3 MB in 12.0 s)". */
fun speedLine(avg: Double, peak: Double, bytes: Long, secs: Double): String =
    "avg ${fmtSpeed(avg)}, peak ${fmtSpeed(peak)} (${fmtBytes(bytes.toDouble())} in ${String.format(java.util.Locale.US, "%.1f", secs)} s)"
