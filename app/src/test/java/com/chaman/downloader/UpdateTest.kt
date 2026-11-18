package com.chaman.downloader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateTest {
    @Test fun parseTagAndName() {
        assertEquals(listOf(0, 4, 24), UpdateLogic.parseVersion("v0.4.24"))
        assertEquals(listOf(0, 4, 24), UpdateLogic.parseVersion("0.4.24-parked"))
        assertEquals(listOf(1), UpdateLogic.parseVersion("V1"))
        assertNull(UpdateLogic.parseVersion("junk"))
        assertNull(UpdateLogic.parseVersion(null))
    }

    @Test fun newerOnlyWhenBigger() {
        assertTrue(UpdateLogic.isNewer("v0.4.25", "0.4.24-parked"))
        assertFalse(UpdateLogic.isNewer("v0.4.24", "0.4.24-parked"))
        assertFalse(UpdateLogic.isNewer("v0.4.9", "0.4.24"))
        assertTrue(UpdateLogic.isNewer("v0.5.0", "0.4.99"))
        assertTrue(UpdateLogic.isNewer("v1.0", "0.9.9"))
        assertTrue(UpdateLogic.isNewer("v0.4.24.1", "0.4.24"))
        assertFalse(UpdateLogic.isNewer("v0.4.24.0", "0.4.24"))
        assertFalse(UpdateLogic.isNewer("junk", "0.4.24"))
        assertFalse(UpdateLogic.isNewer("v0.4.25", null))
    }

    @Test fun pickApk() {
        val a = listOf(
            "BUILD_INFO.txt" to "https://x/BUILD_INFO.txt",
            "Chaman-Downloader.apk" to "https://x/Chaman-Downloader.apk"
        )
        assertEquals("https://x/Chaman-Downloader.apk", UpdateLogic.pickApkUrl(a, "Chaman-Downloader.apk"))
        assertNull(UpdateLogic.pickApkUrl(a, "other.apk"))
        assertNull(UpdateLogic.pickApkUrl(listOf("Chaman-Downloader.apk" to "http://x/a.apk"), "Chaman-Downloader.apk"))
    }

    @Test fun throttle() {
        val gap = 6L * 60 * 60 * 1000
        assertTrue(UpdateLogic.shouldCheck(1000L, 0L, gap))
        assertFalse(UpdateLogic.shouldCheck(gap, 1000L, gap))
        assertTrue(UpdateLogic.shouldCheck(gap + 1000L, 1000L, gap))
        assertTrue(UpdateLogic.shouldCheck(500L, 1000L, gap))
    }
}
