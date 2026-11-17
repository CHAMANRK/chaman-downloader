package com.chaman.downloader

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.os.IBinder
import android.os.PowerManager
import android.provider.MediaStore
import com.chaquo.python.PyException
import com.chaquo.python.Python
import org.json.JSONObject
import java.io.File

/** Python side is object ko call karta hai (progress + cancel check). */
class ProgressCb(private val onP: (Double, String) -> Unit) {
    fun onStats(pct: Double, kind: String, done: Double, total: Double, speed: Double, eta: Double) {
        onP(pct, formatProgressLine(kind, done, total, speed, eta))
    }

    fun isCancelled(): Boolean = DownloadService.cancelRequested
}

class DownloadService : Service() {

    companion object {
        const val EXTRA_URL = "url"
        const val EXTRA_FORMAT = "format" // yt-dlp format string; khaali = best
        const val ACTION_CANCEL = "com.chaman.downloader.CANCEL"
        private const val CHANNEL = "downloads"
        private const val NOTIF_ID = 1
        private const val DONE_ID = 2

        @Volatile
        var cancelRequested = false
    }

    private var worker: Thread? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            cancelRequested = true
            if (worker?.isAlive != true) stopSelf()
            return START_NOT_STICKY
        }

        ensureChannel()
        startForeground(
            NOTIF_ID,
            buildNotif("Starting...", -1),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )

        val url = intent?.getStringExtra(EXTRA_URL)
        val fmt = intent?.getStringExtra(EXTRA_FORMAT) ?: ""
        if (url.isNullOrBlank()) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        if (worker?.isAlive == true) return START_NOT_STICKY

        val pm = getSystemService(PowerManager::class.java)
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "chaman:download").apply {
            acquire(3 * 60 * 60 * 1000L)
        }

        cancelRequested = false
        DownloadState.reset()
        val nm = getSystemService(NotificationManager::class.java)

        var lastNotif = 0L
        var lastHeartbeat = System.currentTimeMillis()
        var nextMark = 10
        val cb = ProgressCb { pct, text ->
            DownloadState.progress = if (pct >= 0) (pct / 100.0).toFloat() else -1f
            DownloadState.line = text
            val now = System.currentTimeMillis()
            if (now - lastNotif > 1000) {
                lastNotif = now
                nm.notify(NOTIF_ID, buildNotif(text, pct.toInt()))
            }
            while (pct >= nextMark && nextMark <= 100) {
                DownloadState.logLine("$nextMark%  $text")
                nextMark += 10
                lastHeartbeat = now
            }
            if (now - lastHeartbeat > 30_000) {
                lastHeartbeat = now
                DownloadState.logLine("alive  $text")
            }
        }

        worker = Thread {
            val outDir = File(filesDir, "dl").apply { mkdirs() }
            try {
                val json = JSONObject(
                    Python.getInstance().getModule("tests")
                        .callAttr("download", url, outDir.absolutePath, cb, fmt).toString()
                )
                DownloadState.logLine("formats: ${json.getString("desc")}")
                val parts = json.getJSONArray("files")
                val src: File
                if (parts.length() == 2) {
                    val v = File(parts.getString(0))
                    val a = File(parts.getString(1))
                    DownloadState.logLine("download done: video ${v.length()} B, audio ${a.length()} B. FFmpeg merge...")
                    DownloadState.line = "Merging video + audio..."
                    src = File(outDir, json.getString("id") + "." + json.getString("merge_ext"))
                    FfmpegProbe.merge(v, a, src)
                    v.delete()
                    a.delete()
                } else {
                    src = File(parts.getString(0))
                }
                val displayName = safeFileName(json.optString("title", ""), json.getString("id")) +
                    "." + src.extension
                DownloadState.logLine("ready: $displayName (${src.length()} bytes), MediaStore me save")
                saveToDownloads(src, displayName, !json.optBoolean("has_video", true))
                val secs = (System.currentTimeMillis() - DownloadState.startedAt) / 1000
                DownloadState.logLine("PASS in ${secs}s -> Download/Chaman/$displayName")
                DownloadState.progress = 1f
                DownloadState.status = DlStatus.PASS
                notifyDone("Download complete: $displayName")
            } catch (t: Throwable) {
                outDir.listFiles()?.forEach { it.delete() }
                if (cancelRequested) {
                    DownloadState.logLine("CANCELLED by user")
                    DownloadState.status = DlStatus.CANCELLED
                    notifyDone("Download cancelled")
                } else {
                    val msg = if (t is PyException) "PyException: ${t.message}"
                    else "${t::class.java.simpleName}: ${t.message}"
                    DownloadState.logLine("FAIL  $msg")
                    DownloadState.error = shortError(msg)
                    DownloadState.status = DlStatus.FAIL
                    notifyDone("Download failed")
                }
            } finally {
                wakeLock?.let { if (it.isHeld) it.release() }
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }.also { it.start() }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        wakeLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }

    private fun saveToDownloads(src: File, displayName: String, audioOnly: Boolean) {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, displayName)
            put(MediaStore.Downloads.MIME_TYPE, extToMime(src.extension, audioOnly))
            put(MediaStore.Downloads.RELATIVE_PATH, "Download/Chaman")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val resolver = contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("MediaStore insert fail")
        resolver.openOutputStream(uri)!!.use { out ->
            src.inputStream().use { it.copyTo(out) }
        }
        values.clear()
        values.put(MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        src.delete()
    }

    private fun ensureChannel() {
        val ch = NotificationChannel(CHANNEL, "Downloads", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    private fun buildNotif(text: String, pct: Int): Notification {
        val cancelPi = PendingIntent.getService(
            this, 0,
            Intent(this, DownloadService::class.java).setAction(ACTION_CANCEL),
            PendingIntent.FLAG_IMMUTABLE
        )
        val action = Notification.Action.Builder(
            Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel),
            "Cancel",
            cancelPi
        ).build()
        val b = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Chaman Downloader")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(action)
        if (pct in 0..100) b.setProgress(100, pct, false) else b.setProgress(0, 0, true)
        return b.build()
    }

    private fun notifyDone(text: String) {
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Chaman Downloader")
            .setContentText(text)
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java).notify(DONE_ID, n)
    }
}
