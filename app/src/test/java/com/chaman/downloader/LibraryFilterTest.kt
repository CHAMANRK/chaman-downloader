package com.chaman.downloader

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryFilterTest {
    @Test fun allEmptyQuery() {
        assertTrue(libMatches("Song.m4a", "audio/mp4", "", LibFilter.ALL))
        assertTrue(libMatches("Clip.mp4", "video/mp4", "   ", LibFilter.ALL))
    }

    @Test fun queryIgnoresCase() {
        assertTrue(libMatches("Mera Video.mp4", "video/mp4", "mera", LibFilter.ALL))
        assertTrue(libMatches("Mera Video.mp4", "video/mp4", " VIDEO ", LibFilter.ALL))
        assertFalse(libMatches("Mera Video.mp4", "video/mp4", "gana", LibFilter.ALL))
    }

    @Test fun typeFilter() {
        assertTrue(libMatches("a.mp4", "video/mp4", "", LibFilter.VIDEO))
        assertFalse(libMatches("a.m4a", "audio/mp4", "", LibFilter.VIDEO))
        assertTrue(libMatches("a.m4a", "audio/mp4", "", LibFilter.AUDIO))
        assertFalse(libMatches("a.mp4", "video/mp4", "", LibFilter.AUDIO))
    }

    @Test fun typeAndQuery() {
        assertTrue(libMatches("Song one.m4a", "audio/mp4", "one", LibFilter.AUDIO))
        assertFalse(libMatches("Song one.m4a", "audio/mp4", "two", LibFilter.AUDIO))
    }
}
