package com.chaman.downloader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeedStatsTest {
    private fun s(t: Double, b: Long) = SpeedSample(t, b)

    @Test fun avgOfSteadyStream() {
        val l = listOf(s(0.0, 0), s(1.0, 1000), s(2.0, 2000), s(4.0, 4000))
        assertEquals(1000.0, avgBps(l), 0.001)
    }

    @Test fun avgEmptyOrZeroTime() {
        assertEquals(0.0, avgBps(emptyList()), 0.0)
        assertEquals(0.0, avgBps(listOf(s(0.0, 0))), 0.0)
    }

    @Test fun peakFindsFastWindow() {
        // pehle 2 sec dheere (100 B/s), phir 2 sec tez (1000 B/s)
        val l = listOf(s(0.0, 0), s(1.0, 100), s(2.0, 200), s(3.0, 1200), s(4.0, 2200))
        assertEquals(1000.0, peakBps(l), 0.001)
    }

    @Test fun peakNeedsFullWindow() {
        // 1 sec se chhota data: koi window nahi
        val l = listOf(s(0.0, 0), s(0.4, 400), s(0.8, 800))
        assertEquals(0.0, peakBps(l), 0.0)
    }

    @Test fun lineFormat() {
        val line = speedLine(2.0 * 1024 * 1024, 3.0 * 1024 * 1024, 25L * 1024 * 1024, 12.0)
        assertTrue(line.contains("avg 2.00 MB/s"))
        assertTrue(line.contains("peak 3.00 MB/s"))
        assertTrue(line.contains("25.0 MB in 12.0 s"))
    }
}
