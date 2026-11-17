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

class LibraryActionsTest {
    @Test fun commonMimeRules() {
        org.junit.Assert.assertEquals("video/mp4", commonMime(listOf("video/mp4", "video/mp4")))
        org.junit.Assert.assertEquals("video/*", commonMime(listOf("video/mp4", "video/webm")))
        org.junit.Assert.assertEquals("*/*", commonMime(listOf("video/mp4", "audio/mp4")))
        org.junit.Assert.assertEquals("*/*", commonMime(emptyList()))
    }

    @Test fun splitName() {
        org.junit.Assert.assertEquals(Pair("Video", ".mp4"), splitFileName("Video.mp4"))
        org.junit.Assert.assertEquals(Pair("a.b", ".m4a"), splitFileName("a.b.m4a"))
        org.junit.Assert.assertEquals(Pair("noext", ""), splitFileName("noext"))
    }

    @Test fun renameKeepsExtension() {
        org.junit.Assert.assertEquals("Naya.mp4", renamedFileName("Purana.mp4", "Naya"))
        org.junit.Assert.assertEquals("Naya.mp4", renamedFileName("Purana.mp4", " Naya.MP4 "))
        org.junit.Assert.assertEquals("a b.mp4", renamedFileName("x.mp4", "a/b"))
    }

    @Test fun renameRejectsEmpty() {
        org.junit.Assert.assertNull(renamedFileName("x.mp4", "   "))
        org.junit.Assert.assertNull(renamedFileName("x.mp4", "///"))
    }
}
