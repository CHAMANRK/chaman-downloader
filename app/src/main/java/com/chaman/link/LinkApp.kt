package com.chaman.link

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.chaman.downloader.AppDb
import com.chaman.downloader.DownloadState
import com.chaman.downloader.QStatus
import com.chaman.downloader.QueueRepo
import com.chaman.downloader.extractFirstUrl
import kotlinx.coroutines.delay
import org.json.JSONObject

/** Is app ka sab kuch yahin: naam, manifest ki baatein aur actions. Naya action = actions list me ek entry. */
object LinkApp {
    val info = LinkAppInfo(
        id = "downloader",
        naam = "Chaman Downloader",
        oneLiner = "Media download (yt-dlp)",
        keywords = listOf("video", "audio", "link", "download"),
        githubReleaseUrl = "https://github.com/CHAMANRK/chaman-downloader/releases"
    )

    val actions: List<LinkAction> = listOf(DownloadAction)
}

/**
 * download(url): link app ki Queue me judta hai (best quality, wahi serial queue). Progress queue se aata hai,
 * PASS par RESULT me file ka naam + uri. App khulta nahi.
 */
object DownloadAction : LinkAction() {
    override val name = "download"
    override val desc = "Link se video/audio download"
    override val result = "file"
    override val params = mapOf("url" to ParamSpec("string", required = true))

    private const val MAX_WAIT_MS = 6L * 60 * 60 * 1000

    override suspend fun run(ctx: Context, p: JSONObject, call: LinkCall): LinkResult {
        val url = extractFirstUrl(p.getString("url"))
            ?: return LinkResult.failed("invalid_url", "http(s) link nahi mila")
        val app = ctx.applicationContext
        val dao = AppDb.get(app).dao()

        val (id, pos) = QueueRepo.enqueueWithId(app, url, "", "", "")
        try {
            QueueRepo.kick(app)
        } catch (t: Throwable) {
            // Android ne background se service shuru karne nahi di: item line me chhodna theek nahi, hata do
            dao.remove(id)
            return LinkResult.failed("service_start_blocked", "App ki download service shuru nahi ho payi: ${t.message}")
        }

        var lastPct = 0
        var lastText = "Queue me #$pos"
        call.progress(lastPct, lastText)

        val deadline = System.currentTimeMillis() + MAX_WAIT_MS
        while (System.currentTimeMillis() < deadline) {
            delay(1000)
            val it = dao.get(id) ?: return LinkResult.failed("removed", "Queue se hata diya gaya")
            when (it.status) {
                QStatus.PASS.name -> return passResult(app, id, it.title, it.savedUri, it.savedMime)
                QStatus.FAIL.name -> return LinkResult.failed(
                    "download_failed", it.error.ifBlank { "Download fail ho gaya" }
                )
                QStatus.CANCELLED.name -> return LinkResult.failed("cancelled", "Download cancel ho gaya")
                QStatus.RUNNING.name -> {
                    val live = DownloadState.currentId == id
                    val pct = if (!live) 0 else {
                        val f = DownloadState.progress
                        if (f >= 0f) Math.round(f * 100f) else -1
                    }
                    val text = if (live) DownloadState.line.ifBlank { "Download ho raha hai" } else "Shuru ho raha hai"
                    if (pct != lastPct || text != lastText) {
                        lastPct = pct
                        lastText = text
                        call.progress(pct, text)
                    }
                }
                else -> {} // QUEUED: line me hai, number pehle bata diya
            }
        }
        return LinkResult.failed("timeout", "6 ghante me khatam nahi hua")
    }

    private fun passResult(ctx: Context, id: Long, title: String, uri: String, mime: String): LinkResult {
        val name = if (uri.isBlank()) "" else displayName(ctx, uri)
        val shown = name.ifBlank { title }
        val files = if (uri.isBlank()) emptyList() else listOf(LinkFile(shown, uri, mime))
        return LinkResult.done(
            "Download ho gaya: $shown", files,
            JSONObject().put("id", id).put("title", title)
        )
    }

    /** MediaStore ya SAF uri se file ka asli naam (na mile to khali). */
    private fun displayName(ctx: Context, uri: String): String = try {
        ctx.contentResolver.query(Uri.parse(uri), arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) ?: "" else "" } ?: ""
    } catch (t: Throwable) {
        ""
    }
}
