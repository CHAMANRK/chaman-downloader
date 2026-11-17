package com.chaman.downloader

import org.junit.Assert.assertEquals
import org.junit.Test

class MimeTest {
    @Test
    fun knownExtensions() {
        assertEquals("video/mp4", extToMime("mp4"))
        assertEquals("video/mp4", extToMime("MP4"))
        assertEquals("audio/mp4", extToMime("m4a"))
        assertEquals("audio/mpeg", extToMime("mp3"))
        assertEquals("video/webm", extToMime("webm"))
    }

    @Test
    fun unknownExtensionFallsBack() {
        assertEquals("application/octet-stream", extToMime("xyz"))
        assertEquals("application/octet-stream", extToMime(""))
    }
}
