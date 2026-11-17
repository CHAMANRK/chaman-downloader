package com.chaman.downloader

/** File extension se MIME type (MediaStore me save karne ke liye). */
fun extToMime(ext: String): String = when (ext.lowercase()) {
    "mp4", "m4v" -> "video/mp4"
    "webm" -> "video/webm"
    "mkv" -> "video/x-matroska"
    "m4a" -> "audio/mp4"
    "mp3" -> "audio/mpeg"
    "opus", "ogg" -> "audio/ogg"
    "jpg", "jpeg" -> "image/jpeg"
    "png" -> "image/png"
    else -> "application/octet-stream"
}
