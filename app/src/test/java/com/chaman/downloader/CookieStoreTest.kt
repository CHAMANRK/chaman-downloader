package com.chaman.downloader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CookieStoreTest {
    private val header = "# Netscape HTTP Cookie File\n"

    @Test fun validFileWithHttpOnly() {
        val t = header +
            ".instagram.com\tTRUE\t/\tTRUE\t1893456000\tsessionid\tabc\n" +
            "#HttpOnly_.instagram.com\tTRUE\t/\tTRUE\t0\tcsrftoken\txyz\n"
        assertEquals(Pair(2, 0), validateNetscape(t))
    }

    @Test fun emptyValueIsFine() {
        val t = header + ".a.com\tTRUE\t/\tFALSE\t0\tn\t\n"
        assertEquals(Pair(1, 0), validateNetscape(t))
    }

    @Test fun missingHeaderIsInvalid() {
        assertEquals(Pair(0, 1), validateNetscape("hello"))
    }

    @Test fun badLineIsCounted() {
        val t = header + "only\tthree\tfields\n"
        assertEquals(Pair(0, 1), validateNetscape(t))
    }

    @Test fun siteForUrl() {
        assertEquals("instagram", cookieSiteForUrl("https://www.instagram.com/reel/abc/"))
        assertNull(cookieSiteForUrl("https://youtu.be/abc"))
    }
}
