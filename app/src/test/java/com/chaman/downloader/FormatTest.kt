package com.chaman.downloader

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {
    @Test fun speedKb() {
        assertEquals("500 KB/s", fmtSpeed(500 * 1024.0))
        assertEquals("1023 KB/s", fmtSpeed(1023 * 1024.0))
    }

    @Test fun speedMb() {
        assertEquals("1.00 MB/s", fmtSpeed(1024 * 1024.0))
        assertEquals("2.50 MB/s", fmtSpeed(2.5 * 1024 * 1024))
    }

    @Test fun speedUnknown() {
        assertEquals("-", fmtSpeed(0.0))
    }

    @Test fun eta() {
        assertEquals("1:15", fmtEta(75.0))
        assertEquals("1:02:05", fmtEta(3725.0))
        assertEquals("-", fmtEta(-1.0))
    }

    @Test fun bytes() {
        assertEquals("512 KB", fmtBytes(512 * 1024.0))
        assertEquals("1.5 MB", fmtBytes(1.5 * 1024 * 1024))
        assertEquals("2.00 GB", fmtBytes(2.0 * 1024 * 1024 * 1024))
    }

    @Test fun nameIllegalChars() {
        assertEquals("a b c d", safeFileName("a/b:c*d?", "x"))
        assertEquals("hello", safeFileName("..hello..", "x"))
    }

    @Test fun nameFallback() {
        assertEquals("id1", safeFileName("???", "id1"))
        assertEquals("id1", safeFileName("", "id1"))
    }

    @Test fun nameLimits() {
        assertEquals(180, safeFileName("a".repeat(300), "x").length)
        // 3-byte characters: 180 / 3 = 60
        assertEquals(60, safeFileName("ह".repeat(100), "x").length)
    }
}
