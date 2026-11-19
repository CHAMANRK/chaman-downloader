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
import android.provider.DocumentsContract
import android.provider.MediaStore
import com.chaquo.python.PyException
import com.chaquo.python.Python
import org.json.JSONObject
import java.io.File

/** Python side is object ko call karta hai (progress + cancel check). */
class ProgressCb(private val onP: (Double, String, String, String) -> Unit) {
    fun onStats(pct: Double, kind: String, done: Double, total: Double, speed: Double, eta: Double) {
        onP(
            pct,
            formatProgressLine(kind, done, total, speed, eta),
            notifProgressTitle(kind),
            notifProgressBody(pct, speed, eta)
        )
    }

    fun isCancelled(): Boolean = DownloadService.cancelRequested
}

class DownloadService : Service() {

    companion object {
        const val ACTION_CANCEL = "com.chaman.downloader.CANCEL"
        const val ACTION_PROCESS = "com.chaman.downloader.PROCESS" // queue chalao (DB me QUEUED items uthao)
        const val EXTRA_RETRY_ID = "retry_id" // notification Retry: is item ko dobara line me daalo
        private const val CHANNEL = "downloads"
        private const val NOTIF_ID = 1
        private const val DONE_BASE = 1000 // har item ka apna Done/Fail notification: DONE_BASE + id
        private const val RC_APP = 6

        @Volatile
        var cancelRequested = false

        private fun doneId(id: Long): Int = DONE_BASE + (id % 100000).toInt()
        private fun rc(id: Long, k: Int): Int = 100 + (id % 100000).toInt() * 4 + k
    }

    private val lock = Any()
    private var workerRunning = false
    private var wake = false
    private var lastStartId = 0
    private val retryIds = java.util.concurrent.ConcurrentLinkedQueue<Long>()
    private var wakeLock: PowerManager.WakeLock? = null

    @Volatile
    private var curNotif: Notification? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            cancelRequested = true
            synchronized(lock) {
                lastStartId = startId
                if (!workerRunning) stopSelf(startId)
            }
            return START_NOT_STICKY
        }

        ensureChannel()
        val rid = intent?.getLongExtra(EXTRA_RETRY_ID, -1L) ?: -1L
        if (rid > 0) retryIds.add(rid)

        synchronized(lock) {
            lastStartId = startId
            // startForegroundService ke baad 5 sec me startForeground zaroori hai (worker chal raha ho tab bhi)
            startForeground(
                NOTIF_ID,
                curNotif ?: buildNotif("Link check ho raha hai", "", -1),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
            if (workerRunning) {
                wake = true
            } else {
                workerRunning = true
                wake = false
                val pm = getSystemService(PowerManager::class.java)
                val wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "chaman:download").apply {
                    setReferenceCounted(false)
                    acquire(3 * 60 * 60 * 1000L)
                }
                wakeLock = wl
                Thread { workerMain(wl) }.start()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        wakeLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }

    /** Foreground notification badlo (aur yaad rakho, taaki dobara startForeground me wahi jaye). */
    private fun fg(head: String, text: String, pct: Int) {
        val n = buildNotif(head, text, pct)
        curNotif = n
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, n)
    }

    /** Serial queue: ek-ek karke QUEUED item chalata hai, khatam hone par service band. */
    private fun workerMain(wl: PowerManager.WakeLock) {
        val dao = AppDb.get(this).dao()
        try {
            while (true) {
                while (true) {
                    val r = retryIds.poll() ?: break
                    dao.requeue(r, System.currentTimeMillis())
                }
                cancelRequested = false
                val item = dao.nextQueued()
                if (item == null) {
                    var exit = false
                    synchronized(lock) {
                        if (wake || retryIds.isNotEmpty()) {
                            wake = false
                        } else {
                            workerRunning = false
                            curNotif = null
                            stopForeground(STOP_FOREGROUND_REMOVE)
                            stopSelf(lastStartId)
                            exit = true
                        }
                    }
                    if (exit) return
                    continue
                }
                if (dao.claim(item.id) == 0) continue // beech me cancel/hata diya gaya
                runItem(item, dao, wl)
            }
        } catch (t: Throwable) {
            DownloadState.logLine("Queue worker error: ${t::class.java.simpleName}: ${t.message}")
            synchronized(lock) {
                workerRunning = false
                curNotif = null
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf(lastStartId)
            }
        } finally {
            if (wl.isHeld) wl.release() // sirf apna wake lock (naya worker apna alag banata hai)
        }
    }

    /** Ek item ka poora download (pehle ka logic, bas ab DB row ke saath). */
    private fun runItem(item: DlItem, dao: DlDao, wl: PowerManager.WakeLock) {
        val url = item.url
        val fmt = item.fmt
        val title = item.title.ifBlank { item.url }
        wl.acquire(3 * 60 * 60 * 1000L) // har item pe timeout dobara 3 ghante

        DownloadState.reset()
        DownloadState.currentId = item.id
        DownloadState.sourceUrl = url
        DownloadState.sourceFmt = fmt
        DownloadState.currentTitle = title
        val nm = getSystemService(NotificationManager::class.java)
        nm.cancel(doneId(item.id)) // is item ka purana Done/Fail notification hatao
        fg("Link check ho raha hai", "", -1)

        var lastNotif = 0L
        var lastHeartbeat = System.currentTimeMillis()
        var nextMark = 10
        val cb = ProgressCb { pct, text, ntitle, nbody ->
            DownloadState.progress = if (pct >= 0) (pct / 100.0).toFloat() else -1f
            DownloadState.line = text
            val now = System.currentTimeMillis()
            if (now - lastNotif > 1000) {
                lastNotif = now
                fg(ntitle, nbody.ifBlank { DownloadState.currentTitle }, pct.toInt())
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

        cookieSiteForUrl(url)?.takeIf { Flags.INSTAGRAM_COOKIES }?.let { site ->
            if (CookieStore.has(this, site)) DownloadState.logLine("cookies: $site import ki hui lagayi")
        }

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
                fg("Video aur audio jod rahe hain", DownloadState.currentTitle, -1)
                src = File(outDir, json.getString("id") + "." + json.getString("merge_ext"))
                FfmpegProbe.merge(v, a, src)
                v.delete()
                a.delete()
            } else if (json.optBoolean("to_mp3", false)) {
                val a = File(parts.getString(0))
                DownloadState.logLine("download done: audio ${a.length()} B. mp3 convert...")
                DownloadState.line = "MP3 bana rahe hain..."
                fg("MP3 bana rahe hain", DownloadState.currentTitle, -1)
                src = File(outDir, json.getString("id") + ".mp3")
                FfmpegProbe.toMp3(a, src)
                a.delete()
                fun js(k: String) = if (json.isNull(k)) "" else json.optString(k, "")
                DownloadState.logLine(Cover.tagMp3(src, js("title"), js("uploader"), js("thumbnail")))
            } else {
                src = File(parts.getString(0))
            }
            val vidTitle = json.optString("title", "")
            if (vidTitle.isNotBlank() && vidTitle != "null" && (item.title.isBlank() || item.title == item.url)) {
                dao.setTitle(item.id, vidTitle) // Debug / "seedha download" me naam khali tha
            }
            val displayName = safeFileName(vidTitle, json.getString("id")) + "." + src.extension
            DownloadState.logLine("ready: $displayName (${src.length()} bytes), MediaStore me save")
            fg("File save ho rahi hai", displayName, -1)
            val dest = saveFile(src, displayName, json.optBoolean("has_video", true))
            val secs = (System.currentTimeMillis() - DownloadState.startedAt) / 1000
            DownloadState.logLine("PASS in ${secs}s -> $dest/$displayName")
            DownloadState.progress = 1f
            val savedUri = DownloadState.savedUri
            val savedMime = DownloadState.savedMime
            dao.finish(item.id, QStatus.PASS.name, 1f, "", savedUri ?: "", savedMime)
            DownloadState.status = DlStatus.PASS
            // UI ke liye PASS ki khabar (agla item turant reset karega, isliye alag fields)
            DownloadState.passUrl = url
            DownloadState.passUri = savedUri
            DownloadState.passMime = savedMime
            DownloadState.passCount += 1
            notifyPass(item.id, displayName)
        } catch (t: Throwable) {
            outDir.listFiles()?.forEach { it.delete() }
            if (cancelRequested) {
                DownloadState.logLine("CANCELLED by user")
                runCatching { dao.finish(item.id, QStatus.CANCELLED.name, 0f, "", "", "") }
                DownloadState.status = DlStatus.CANCELLED
                notifyStopped(item.id, "Download rok diya", DownloadState.currentTitle)
            } else {
                val msg = if (t is PyException) "PyException: ${t.message}"
                else "${t::class.java.simpleName}: ${t.message}"
                DownloadState.logLine("FAIL  $msg")
                val err = queueErrorText(msg)
                DownloadState.error = err
                runCatching { dao.finish(item.id, QStatus.FAIL.name, 0f, err, "", "") }
                DownloadState.status = DlStatus.FAIL
                notifyStopped(item.id, "Download fail ho gaya", notifFailBody(msg))
            }
        }
    }

    /** Chune Save folder me (Video/ ya Audio/); na chuna ho ya na mile to default Download/Chaman/<Video|Audio>. Dest label deta hai. */
    private fun saveFile(src: File, displayName: String, hasVideo: Boolean): String {
        val sub = subDirFor(hasVideo)
        val tree = SaveFolder.treeUri(this)
        if (tree != null) {
            try {
                saveToTree(tree, sub, src, displayName, !hasVideo)
                return SaveFolder.label(this) + "/" + sub
            } catch (t: Throwable) {
                DownloadState.logLine("Chuna folder nahi chala (${t::class.java.simpleName}: ${t.message}), default Download/Chaman me save")
            }
        }
        saveToDownloads(src, displayName, !hasVideo, sub)
        return "Download/Chaman/$sub"
    }

    private fun saveToTree(tree: Uri, sub: String, src: File, displayName: String, audioOnly: Boolean) {
        val dir = SaveFolder.subDir(this, tree, sub, create = true)
            ?: throw IllegalStateException("$sub folder nahi bana")
        val mime = safCreateMime(src.extension, extToMime(src.extension, audioOnly))
        val uri = DocumentsContract.createDocument(contentResolver, dir, mime, displayName)
            ?: throw IllegalStateException("File nahi bani")
        try {
            contentResolver.openOutputStream(uri, "w")!!.use { out ->
                src.inputStream().use { it.copyTo(out) }
            }
        } catch (t: Throwable) {
            try {
                DocumentsContract.deleteDocument(contentResolver, uri)
            } catch (e: Throwable) {
            }
            throw t
        }
        src.delete()
        DownloadState.savedMime = extToMime(src.extension, audioOnly)
        DownloadState.savedUri = uri.toString()
    }

    private fun saveToDownloads(src: File, displayName: String, audioOnly: Boolean, sub: String) {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, displayName)
            put(MediaStore.Downloads.MIME_TYPE, extToMime(src.extension, audioOnly))
            put(MediaStore.Downloads.RELATIVE_PATH, "Download/Chaman/$sub")
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

    private fun buildNotif(head: String, text: String, pct: Int): Notification {
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
            .setContentTitle(head)
            .setContentText(text.ifBlank { null })
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

    private fun postResult(id: Long, head: String, text: String, actions: List<Notification.Action>) {
        val b = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(head)
            .setContentText(text.ifBlank { null })
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(appPi())
            .setAutoCancel(true)
        actions.forEach { b.addAction(it) }
        getSystemService(NotificationManager::class.java).notify(doneId(id), b.build())
    }

    /** Download ho gaya: [Open] [Share]. */
    private fun notifyPass(id: Long, displayName: String) {
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
                PendingIntent.getActivity(this, rc(id, 1), view, fl))
            acts += action(android.R.drawable.ic_menu_share, "Share",
                PendingIntent.getActivity(this, rc(id, 2), chooser, fl))
        }
        postResult(id, "Download ho gaya", displayName, acts)
    }

    /** Fail ya cancel: [Retry] (isi item ko dobara line ke aakhir me daalta hai, wahi link + quality). */
    private fun notifyStopped(id: Long, head: String, text: String) {
        val i = Intent(this, DownloadService::class.java)
            .setAction(ACTION_PROCESS)
            .putExtra(EXTRA_RETRY_ID, id)
        val pi = PendingIntent.getForegroundService(
            this, rc(id, 0), i,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        postResult(id, head, text, listOf(action(android.R.drawable.ic_menu_rotate, "Retry", pi)))
    }
}
