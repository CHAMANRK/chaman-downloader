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

    @Test fun duration() {
        assertEquals("1:15", fmtDuration(75))
        assertEquals("1:02:05", fmtDuration(3725))
    }

    @Test fun count() {
        assertEquals("950", fmtCount(950))
        assertEquals("15.3K", fmtCount(15_300))
        assertEquals("1.2M", fmtCount(1_234_567))
    }

    @Test fun dateYmd() {
        assertEquals("17 Nov 2026", fmtDateYmd("20261117"))
        assertEquals("abc", fmtDateYmd("abc"))
        assertEquals("20261317", fmtDateYmd("20261317"))
    }

    @Test fun shortErrorStripsPrefixAndKeepsFirstLine() {
        assertEquals(
            "YouTube ne is network ko rok diya.",
            shortError("PyException: RuntimeError: YouTube ne is network ko rok diya.\n\nDetails: x")
        )
        assertEquals("", shortError("   "))
        assertEquals(201, shortError("a".repeat(500)).length)
    }

    @Test fun notifProgress() {
        assertEquals(
            "Video download ho raha hai · 45% · 2.50 MB/s · 1:15 bacha",
            notifProgressText("video", 45.2, 2.5 * 1024 * 1024, 75.0)
        )
        assertEquals("Download ho raha hai", notifProgressText("single", -1.0, 0.0, -1.0))
        assertEquals("Audio download ho raha hai · 10%", notifProgressText("audio", 10.0, 0.0, -1.0))
    }

    @Test fun failTextFriendly() {
        assertEquals(
            "Internet me problem aayi. Net check karke Retry karo.",
            friendlyFailText("PyException: DownloadError: Unable to resolve host x")
        )
        assertEquals(
            "Phone me jagah kam hai. Space khali karke Retry karo.",
            friendlyFailText("IOException: No space left on device")
        )
        assertEquals(
            "YouTube ne is network ko rok diya (bot-check). Wi-Fi try karo.",
            friendlyFailText("PyException: RuntimeError: YouTube ne is network ko rok diya (bot-check). Wi-Fi try karo.\n\nDetails: x")
        )
        assertEquals(
            "Download fail ho gaya: Requested format is not available",
            friendlyFailText("PyException: RuntimeError: Requested format is not available")
        )
        assertEquals("Download fail ho gaya. Retry karo.", friendlyFailText(""))
    }

    @Test fun notifTitleBody() {
        assertEquals("Video download ho raha hai", notifProgressTitle("video"))
        assertEquals("Audio download ho raha hai", notifProgressTitle("audio"))
        assertEquals("Download ho raha hai", notifProgressTitle("single"))
        assertEquals("45% · 2.50 MB/s · 1:15 bacha", notifProgressBody(45.2, 2.5 * 1024 * 1024, 75.0))
        assertEquals("", notifProgressBody(-1.0, 0.0, -1.0))
    }

    @Test fun failBodyOnlyReason() {
        assertEquals("Retry karo.", notifFailBody(""))
        assertEquals("Requested format is not available", notifFailBody("PyException: RuntimeError: Requested format is not available"))
        assertEquals("Internet me problem aayi. Net check karke Retry karo.", notifFailBody("Unable to resolve host x"))
    }
}
