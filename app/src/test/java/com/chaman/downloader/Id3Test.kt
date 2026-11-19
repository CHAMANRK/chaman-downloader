package com.chaman.downloader

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class Id3Test {
    private fun ascii(b: ByteArray, off: Int, len: Int) = String(b, off, len, Charsets.ISO_8859_1)

    @Test fun syncsafeRoundTrip() {
        for (n in listOf(0, 1, 127, 128, 300, 16383, 16384, 1_000_000, 268_435_455)) {
            assertEquals(n, Id3.readSyncsafe(Id3.syncsafe(n), 0))
        }
        assertTrue(Id3.syncsafe(268_435_455).all { it.toInt() in 0..0x7F })
    }

    @Test fun headerAndSize() {
        val t = Id3.buildTag("Naam", "Artist", byteArrayOf(1, 2, 3))
        assertEquals("ID3", ascii(t, 0, 3))
        assertEquals(3, t[3].toInt())
        assertEquals(0, t[4].toInt())
        assertEquals(0, t[5].toInt())
        assertEquals(t.size - 10, Id3.readSyncsafe(t, 6))
    }

    @Test fun titleFrameLayout() {
        val t = Id3.buildTag("Ab", "", null)
        assertEquals("TIT2", ascii(t, 10, 4))
        // body = encoding(1) + BOM(2) + 2 chars * 2 byte
        val size = ((t[14].toInt() and 0xFF) shl 24) or ((t[15].toInt() and 0xFF) shl 16) or
            ((t[16].toInt() and 0xFF) shl 8) or (t[17].toInt() and 0xFF)
        assertEquals(1 + 2 + 4, size)
        assertEquals(10 + 10 + size, t.size) // header + frame header + body (artist/cover nahi)
    }

    @Test fun coverFrameEndsWithJpeg() {
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 9, 9, 0xFF.toByte(), 0xD9.toByte())
        val t = Id3.buildTag("T", "A", jpeg)
        assertTrue(String(t, Charsets.ISO_8859_1).contains("APIC"))
        assertTrue(String(t, Charsets.ISO_8859_1).contains("TPE1"))
        assertArrayEquals(jpeg, t.copyOfRange(t.size - jpeg.size, t.size))
    }

    @Test fun existingTagLength() {
        val t = Id3.buildTag("x", "", null)
        assertEquals(t.size, Id3.existingTagLength(t + byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)))
        assertEquals(0, Id3.existingTagLength(byteArrayOf(0xFF.toByte(), 0xFB.toByte(), 1, 2, 3, 4, 5, 6, 7, 8, 9)))
        assertEquals(0, Id3.existingTagLength(byteArrayOf(1, 2)))
    }

    @Test fun replaceTagSwapsOldTag() {
        val audio = ByteArray(3000) { (it % 251).toByte() }
        val f = File.createTempFile("id3t", ".mp3")
        try {
            f.writeBytes(Id3.buildTag("old", "", null) + audio)
            val tag = Id3.buildTag("new", "who", byteArrayOf(5, 6, 7))
            Id3.replaceTag(f, tag)
            assertArrayEquals(tag + audio, f.readBytes())
        } finally {
            f.delete()
        }
    }

    @Test fun replaceTagWhenNoOldTag() {
        val audio = ByteArray(500) { (it % 200 + 1).toByte() }
        audio[0] = 0xFF.toByte()
        audio[1] = 0xFB.toByte()
        val f = File.createTempFile("id3t", ".mp3")
        try {
            f.writeBytes(audio)
            val tag = Id3.buildTag("new", "", null)
            Id3.replaceTag(f, tag)
            assertArrayEquals(tag + audio, f.readBytes())
        } finally {
            f.delete()
        }
    }
}
