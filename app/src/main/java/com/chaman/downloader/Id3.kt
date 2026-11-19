package com.chaman.downloader

import java.io.File

/** Pure ID3v2.3 tag banane/lagane ke helpers (Android API nahi, unit test ho sakta hai). */
object Id3 {
    private fun be32(n: Int) = byteArrayOf((n ushr 24).toByte(), (n ushr 16).toByte(), (n ushr 8).toByte(), n.toByte())

    /** Tag header ka size field: 4 byte, har byte me 7 bit. */
    fun syncsafe(n: Int) = byteArrayOf(
        ((n ushr 21) and 0x7F).toByte(), ((n ushr 14) and 0x7F).toByte(),
        ((n ushr 7) and 0x7F).toByte(), (n and 0x7F).toByte()
    )

    fun readSyncsafe(b: ByteArray, off: Int): Int =
        ((b[off].toInt() and 0x7F) shl 21) or ((b[off + 1].toInt() and 0x7F) shl 14) or
            ((b[off + 2].toInt() and 0x7F) shl 7) or (b[off + 3].toInt() and 0x7F)

    private fun frame(id: String, body: ByteArray): ByteArray =
        id.toByteArray(Charsets.ISO_8859_1) + be32(body.size) + byteArrayOf(0, 0) + body

    /** Text frame: UTF-16 (BOM ke saath), Hindi/emoji naam bhi sahi. */
    private fun textFrame(id: String, text: String): ByteArray =
        frame(id, byteArrayOf(1) + byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + text.toByteArray(Charsets.UTF_16LE))

    /** Front cover: JPEG. */
    private fun coverFrame(jpeg: ByteArray): ByteArray =
        frame(
            "APIC",
            byteArrayOf(0) + "image/jpeg".toByteArray(Charsets.ISO_8859_1) + byteArrayOf(0, 3, 0) + jpeg
        )

    /** ID3v2.3 tag: title (TIT2), artist (TPE1, khaali ho to nahi), cover (APIC, null ho to nahi). */
    fun buildTag(title: String, artist: String, coverJpeg: ByteArray?): ByteArray {
        val frames = ArrayList<ByteArray>()
        if (title.isNotBlank()) frames += textFrame("TIT2", title)
        if (artist.isNotBlank()) frames += textFrame("TPE1", artist)
        if (coverJpeg != null && coverJpeg.isNotEmpty()) frames += coverFrame(coverJpeg)
        val body = frames.fold(ByteArray(0)) { a, f -> a + f }
        return "ID3".toByteArray(Charsets.ISO_8859_1) + byteArrayOf(3, 0, 0) + syncsafe(body.size) + body
    }

    /** File ke shuru me pehle se ID3v2 tag ho to uski total length (header + body), warna 0. */
    fun existingTagLength(head: ByteArray): Int {
        if (head.size < 10 || head[0] != 'I'.code.toByte() || head[1] != 'D'.code.toByte() || head[2] != '3'.code.toByte()) return 0
        return 10 + readSyncsafe(head, 6)
    }

    /** mp3 ka purana tag (FFmpeg ka) hata ke naya tag lagata hai. Temp file me likh ke replace. */
    fun replaceTag(mp3: File, tag: ByteArray) {
        val tmp = File(mp3.parentFile, mp3.name + ".tagtmp")
        try {
            mp3.inputStream().buffered().use { inp ->
                val head = ByteArray(10)
                var n = 0
                while (n < 10) {
                    val r = inp.read(head, n, 10 - n)
                    if (r < 0) break
                    n += r
                }
                val skip = if (n == 10) existingTagLength(head) else 0
                tmp.outputStream().buffered().use { out ->
                    out.write(tag)
                    if (skip == 0) out.write(head, 0, n) // purana tag nahi tha: ye bytes audio ke the
                    if (skip > 10) {
                        var left = (skip - 10).toLong()
                        while (left > 0) {
                            val k = inp.skip(left)
                            if (k > 0) left -= k
                            else if (inp.read() < 0) break
                            else left--
                        }
                    }
                    inp.copyTo(out)
                }
            }
            check(tmp.length() > tag.size.toLong()) { "tag ke baad audio nahi bacha" }
            check(mp3.delete() && tmp.renameTo(mp3)) { "mp3 replace nahi hua" }
        } finally {
            if (tmp.exists()) tmp.delete()
        }
    }
}
