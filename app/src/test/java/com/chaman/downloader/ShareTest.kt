package com.chaman.downloader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareTest {
    @Test fun plainLink() {
        assertEquals("https://youtu.be/q7nG6rCxbK4", extractFirstUrl("https://youtu.be/q7nG6rCxbK4"))
    }

    @Test fun titlePlusLink() {
        assertEquals(
            "https://youtu.be/abc?si=xyz",
            extractFirstUrl("Mera video dekho https://youtu.be/abc?si=xyz")
        )
    }

    @Test fun trailingPunctuationTrimmed() {
        assertEquals("https://www.instagram.com/reel/AbC/", extractFirstUrl("(https://www.instagram.com/reel/AbC/)."))
    }

    @Test fun firstOfManyLinks() {
        assertEquals("https://a.com/1", extractFirstUrl("x https://a.com/1 y https://b.com/2"))
    }

    @Test fun noLink() {
        assertNull(extractFirstUrl("sirf text, link nahi"))
        assertNull(extractFirstUrl(""))
        assertNull(extractFirstUrl(null))
    }

    @Test fun pasteKeepsOnlyLink() {
        assertEquals(
            "https://youtu.be/abc?si=x",
            normalizeLinkInput("", "Dekho https://youtu.be/abc?si=x bahut mast video hai ye")
        )
    }

    @Test fun typingIsUntouched() {
        assertEquals("https://you tube", normalizeLinkInput("https://you", "https://you tube"))
    }

    @Test fun newlineAndLengthCap() {
        assertEquals("a b", normalizeLinkInput("", "a\nb"))
        assertEquals(MAX_LINK_LEN, normalizeLinkInput("", "a".repeat(5000)).length)
    }

    @Test fun autoSearchFamousHttpsOnly() {
        assertTrue(isAutoSearchUrl("https://youtu.be/q7nG6rCxbK4"))
        assertTrue(isAutoSearchUrl("https://www.youtube.com/watch?v=abc"))
        assertTrue(isAutoSearchUrl("https://m.facebook.com/watch/?v=1"))
        assertTrue(isAutoSearchUrl("https://fb.watch/abc/"))
        assertTrue(isAutoSearchUrl("https://www.instagram.com/reel/AbC/"))
        assertTrue(isAutoSearchUrl("  https://x.com/a/status/1  "))
    }

    @Test fun autoSearchRejects() {
        assertFalse(isAutoSearchUrl("http://youtu.be/abc"))
        assertFalse(isAutoSearchUrl("https://evilyoutube.com/x"))
        assertFalse(isAutoSearchUrl("https://youtube.com.evil.com/x"))
        assertFalse(isAutoSearchUrl("https://example.com/video.mp4"))
        assertFalse(isAutoSearchUrl("youtube.com/watch?v=1"))
        assertFalse(isAutoSearchUrl("https://youtu.be/a b"))
        assertFalse(isAutoSearchUrl(""))
        assertFalse(isAutoSearchUrl("https://"))
    }

    @Test fun pasteDetection() {
        assertTrue(looksLikePaste("", "https://youtu.be/abcdefghijk"))
        assertFalse(looksLikePaste("https://you", "https://yout"))
    }
}
