package com.chaman.downloader

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class DlStatus { IDLE, RUNNING, PASS, FAIL, CANCELLED }

/** Service aur UI ke beech shared state (Phase 0 ke liye simple singleton). */
object DownloadState {
    var status by mutableStateOf(DlStatus.IDLE)
    var progress by mutableStateOf(0f) // 0..1, -1 = indeterminate
    var line by mutableStateOf("")
    var log by mutableStateOf("")
    var currentTitle by mutableStateOf("") // UI set karta hai (info title ya URL)
    @Volatile var startedAt: Long = 0L

    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.US)

    fun reset() {
        status = DlStatus.RUNNING
        progress = 0f
        line = ""
        log = ""
        startedAt = System.currentTimeMillis()
        logLine("start")
    }

    fun logLine(s: String) {
        val t = synchronized(fmt) { fmt.format(Date()) }
        log += "$t  $s\n"
    }
}
