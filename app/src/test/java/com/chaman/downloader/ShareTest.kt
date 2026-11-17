package com.chaman.downloader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
