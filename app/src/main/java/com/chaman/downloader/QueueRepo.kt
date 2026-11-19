package com.chaman.downloader

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Queue ke kaam (DB + service ko jagana). Sab kuch background thread par chalta hai. */
object QueueRepo {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** "Quality badlo" dabane par (id, url): usi link ka naya download banne par purana item hat jata hai. */
    @Volatile
    var replace: Pair<Long, String>? = null

    /** Blocking (IO thread se bulao). Naya item QUEUED me daalta hai, line me uska number (#N) deta hai. */
    fun enqueue(ctx: Context, url: String, fmt: String, title: String, thumb: String): Int {
        val d = AppDb.get(ctx).dao()
        d.insert(
            DlItem(
                url = url, fmt = fmt, title = title, thumb = thumb,
                status = QStatus.QUEUED.name, progress = 0f, error = "",
                savedUri = "", savedMime = "", addedAt = System.currentTimeMillis()
            )
        )
        val r = replace
        replace = null
        if (r != null && r.second == url) d.remove(r.first)
        return d.activeCount()
    }

    /** Service ko bolo ki line chalaye (chal rahi ho to bas jaag jaye). */
    fun kick(ctx: Context) {
        ctx.startForegroundService(
            Intent(ctx, DownloadService::class.java).setAction(DownloadService.ACTION_PROCESS)
        )
    }

    /** Item line me daalo + service jagao; kaam hone par main thread par onQueued(N). */
    fun enqueueAndStart(
        ctx: Context, url: String, fmt: String, title: String, thumb: String,
        onQueued: (Int) -> Unit = {}
    ) {
        val app = ctx.applicationContext
        scope.launch {
            val n = try {
                enqueue(app, url, fmt, title, thumb)
            } catch (t: Throwable) {
                -1
            }
            if (n > 0) {
                kick(app)
                withContext(Dispatchers.Main) { onQueued(n) }
            }
        }
    }

    /** App khulne par: pichli baar ke QUEUED items khud shuru (DB khulte hi purana RUNNING FAIL \"Interrupted\" ho jata hai). */
    fun resumeQueued(ctx: Context) {
        val app = ctx.applicationContext
        scope.launch {
            try {
                if (AppDb.get(app).dao().queuedCount() > 0) kick(app)
            } catch (t: Throwable) {
            }
        }
    }

    /** QUEUED item seedha CANCELLED; chal raha ho to service ko cancel bolo. */
    fun cancel(ctx: Context, id: Long) {
        val app = ctx.applicationContext
        scope.launch {
            if (AppDb.get(app).dao().cancelQueued(id) == 0 &&
                DownloadState.currentId == id && DownloadState.status == DlStatus.RUNNING
            ) {
                DownloadService.cancelRequested = true
            }
        }
    }

    fun retry(ctx: Context, id: Long) {
        val app = ctx.applicationContext
        scope.launch {
            if (AppDb.get(app).dao().requeue(id, System.currentTimeMillis()) > 0) kick(app)
        }
    }

    fun remove(ctx: Context, id: Long) {
        val app = ctx.applicationContext
        scope.launch { AppDb.get(app).dao().remove(id) }
    }

    fun clearCompleted(ctx: Context) {
        val app = ctx.applicationContext
        scope.launch { AppDb.get(app).dao().clearCompleted() }
    }
}
