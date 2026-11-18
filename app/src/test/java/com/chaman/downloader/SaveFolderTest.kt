package com.chaman.downloader

import org.junit.Assert.assertEquals
import org.junit.Test

class SaveFolderTest {
    @Test
    fun subDirByType() {
        assertEquals("Video", subDirFor(true))
        assertEquals("Audio", subDirFor(false))
    }

    @Test
    fun treeLabels() {
        assertEquals("Download/Chaman", treeLabel("primary:Download/Chaman"))
        assertEquals("Internal storage", treeLabel("primary:"))
        assertEquals("SD card: Movies", treeLabel("1A2B-3C4D:Movies"))
        assertEquals("SD card", treeLabel("1A2B-3C4D:"))
    }

    @Test
    fun audioDirMimeAlwaysAudio() {
        assertEquals("audio/webm", safLibMime(true, "video/webm", "a.webm"))
        assertEquals("audio/mp4", safLibMime(true, "audio/mp4", "a.m4a"))
        assertEquals("video/mp4", safLibMime(false, "video/mp4", "v.mp4"))
        assertEquals("video/mp4", safLibMime(false, null, "v.mp4"))
    }
}
