package com.chaman.downloader

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipData
import android.content.ContentValues
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.IBinder
import android.os.PowerManager
import android.provider.MediaStore
import com.chaquo.python.PyException
import com.chaquo.python.Python
import org.json.JSONObject
import java.io.File

/** Python side is object ko call karta hai (progress + cancel check). */
class ProgressCb(private val onP: (Double, String, String) -> Unit) {
    fun onStats(pct: Double, kind: String, done: Double, total: Double, speed: Double, eta: Double) {
        onP(
            pct,
            formatProgressLine(kind, done, total, speed, eta),
            notifProgressText(kind, pct, speed, eta)
        )
    }

    fun isCancelled(): Boolean = DownloadService.cancelRequested
}

class DownloadService : Service() {

    companion object {
        const val EXTRA_URL = "url"
        const val EXTRA_FORMAT = "format" // yt-dlp format string; khaali = best
        const val EXTRA_TITLE = "title" // Retry ke liye (Queue me naam dikhane ko)
        const val ACTION_CANCEL = "com.chaman.downloader.CANCEL"
        private const val CHANNEL = "downloads"
        private const val NOTIF_ID = 1
        private const val DONE_ID = 2
        private const val RC_RETRY = 3
        private const val RC_OPEN = 4
        private const val RC_SHARE = 5
        private const val RC_APP = 6

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
            buildNotif("Link check ho raha hai...", -1),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )

        val url = intent?.getStringExtra(EXTRA_URL)
        val fmt = intent?.getStringExtra(EXTRA_FORMAT) ?: ""
        val title = intent?.getStringExtra(EXTRA_TITLE) ?: ""
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
        if (title.isNotBlank()) DownloadState.currentTitle = title
        val nm = getSystemService(NotificationManager::class.java)
        nm.cancel(DONE_ID) // purana Done/Fail/Cancel notification hatao

        var lastNotif = 0L
        var lastHeartbeat = System.currentTimeMillis()
        var nextMark = 10
        val cb = ProgressCb { pct, text, ntext ->
            DownloadState.progress = if (pct >= 0) (pct / 100.0).toFloat() else -1f
            DownloadState.line = text
            val now = System.currentTimeMillis()
            if (now - lastNotif > 1000) {
                lastNotif = now
                nm.notify(NOTIF_ID, buildNotif(ntext, pct.toInt()))
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
                    nm.notify(NOTIF_ID, buildNotif("Video aur audio jod rahe hain...", -1))
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
                nm.notify(NOTIF_ID, buildNotif("File save ho rahi hai...", -1))
                saveToDownloads(src, displayName, !json.optBoolean("has_video", true))
                val secs = (System.currentTimeMillis() - DownloadState.startedAt) / 1000
                DownloadState.logLine("PASS in ${secs}s -> Download/Chaman/$displayName")
                DownloadState.progress = 1f
                DownloadState.status = DlStatus.PASS
                notifyPass(displayName)
            } catch (t: Throwable) {
                outDir.listFiles()?.forEach { it.delete() }
                if (cancelRequested) {
                    DownloadState.logLine("CANCELLED by user")
                    DownloadState.status = DlStatus.CANCELLED
                    notifyStopped("Download rok diya", url, fmt, title)
                } else {
                    val msg = if (t is PyException) "PyException: ${t.message}"
                    else "${t::class.java.simpleName}: ${t.message}"
                    DownloadState.logLine("FAIL  $msg")
                    DownloadState.error = shortError(msg)
                    DownloadState.status = DlStatus.FAIL
                    notifyStopped(friendlyFailText(msg), url, fmt, title)
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
        DownloadState.savedMime = extToMime(src.extension, audioOnly)
        DownloadState.savedUri = uri.toString()
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

    private fun appPi(): PendingIntent = PendingIntent.getActivity(
        this, RC_APP,
        Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun action(icon: Int, label: String, pi: PendingIntent): Notification.Action =
        Notification.Action.Builder(Icon.createWithResource(this, icon), label, pi).build()

    private fun postResult(text: String, actions: List<Notification.Action>) {
        val b = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Chaman Downloader")
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(appPi())
            .setAutoCancel(true)
        actions.forEach { b.addAction(it) }
        getSystemService(NotificationManager::class.java).notify(DONE_ID, b.build())
    }

    /** Download ho gaya: [Open] [Share]. */
    private fun notifyPass(displayName: String) {
        val uri = DownloadState.savedUri?.let { Uri.parse(it) }
        val acts = ArrayList<Notification.Action>()
        if (uri != null) {
            val mime = DownloadState.savedMime
            val view = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, mime)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newRawUri("", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(send, "Share")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val fl = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            acts += action(android.R.drawable.ic_menu_view, "Open",
                PendingIntent.getActivity(this, RC_OPEN, view, fl))
            acts += action(android.R.drawable.ic_menu_share, "Share",
                PendingIntent.getActivity(this, RC_SHARE, chooser, fl))
        }
        postResult("Download ho gaya: $displayName", acts)
    }

    /** Fail ya cancel: [Retry] (wahi link, wahi quality). */
    private fun notifyStopped(text: String, url: String, fmt: String, title: String) {
        val i = Intent(this, DownloadService::class.java)
            .putExtra(EXTRA_URL, url)
            .putExtra(EXTRA_FORMAT, fmt)
            .putExtra(EXTRA_TITLE, title)
        val pi = PendingIntent.getForegroundService(
            this, RC_RETRY, i,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        postResult(text, listOf(action(android.R.drawable.ic_menu_rotate, "Retry", pi)))
    }
}
