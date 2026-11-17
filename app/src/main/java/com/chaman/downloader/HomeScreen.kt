package com.chaman.downloader

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chaquo.python.Python
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Ek download option (quality). fmt = yt-dlp format string, khaali = best. */
class QualityOpt(val label: String, val detail: String, val size: Long, val fmt: String)

class SearchResult(
    val title: String,
    val uploader: String,
    val duration: String,
    val date: String,
    val views: String,
    val likes: String,
    val site: String,
    val description: String,
    val thumb: String,
    val videos: List<QualityOpt>,
    val audios: List<QualityOpt>
)

sealed interface InfoState {
    object None : InfoState
    object Loading : InfoState
    class Ok(val data: SearchResult) : InfoState
    class Err(val msg: String) : InfoState
}

private fun parseOpts(arr: JSONArray): List<QualityOpt> {
    val out = ArrayList<QualityOpt>()
    for (i in 0 until arr.length()) {
        val o = arr.getJSONObject(i)
        out.add(QualityOpt(o.getString("label"), o.getString("detail"), o.optLong("size", 0L), o.getString("fmt")))
    }
    return out
}

private fun parseSearch(raw: String): SearchResult {
    val j = JSONObject(raw)
    fun str(k: String): String = if (j.isNull(k)) "" else j.getString(k)
    return SearchResult(
        title = str("title").ifBlank { "(no title)" },
        uploader = str("uploader"),
        duration = if (j.isNull("duration_sec")) "" else fmtDuration(j.getDouble("duration_sec").toLong()),
        date = fmtDateYmd(str("upload_date")),
        views = if (j.isNull("view_count")) "" else fmtCount(j.getLong("view_count")),
        likes = if (j.isNull("like_count")) "" else fmtCount(j.getLong("like_count")),
        site = str("site"),
        description = str("description"),
        thumb = str("thumbnail"),
        videos = parseOpts(j.getJSONArray("videos")),
        audios = parseOpts(j.getJSONArray("audios"))
    )
}

private val webThumbCache = LruCache<String, Bitmap>(8)

private fun fetchBitmap(u: String): Bitmap? {
    webThumbCache.get(u)?.let { return it }
    return try {
        val c = URL(u).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 10_000
        val b = c.inputStream.use { BitmapFactory.decodeStream(it) }
        if (b != null) webThumbCache.put(u, b)
        b
    } catch (t: Throwable) {
        null
    }
}

/** Search chalata hai aur state onInfo se bhejta hai (Home ka button + Share dono use karte hain). */
suspend fun runSearch(u: String, onInfo: (InfoState) -> Unit) {
    onInfo(InfoState.Loading)
    try {
        val raw = withContext(Dispatchers.IO) {
            Python.getInstance().getModule("tests").callAttr("search", u).toString()
        }
        onInfo(InfoState.Ok(parseSearch(raw)))
    } catch (t: Throwable) {
        onInfo(InfoState.Err(errText(t)))
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun HomeScreen(
    url: String,
    onUrl: (String) -> Unit,
    info: InfoState,
    onInfo: (InfoState) -> Unit,
    onDownloadStarted: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    val busy = DownloadState.status == DlStatus.RUNNING
    val hasUrl = url.isNotBlank()
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    fun closeKeyboard() {
        focus.clearFocus()
        keyboard?.hide()
    }

    fun start(title: String, fmt: String) {
        DownloadState.currentTitle = title
        ctx.startForegroundService(
            Intent(ctx, DownloadService::class.java)
                .putExtra(DownloadService.EXTRA_URL, url.trim())
                .putExtra(DownloadService.EXTRA_FORMAT, fmt)
        )
        onDownloadStarted()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 22.dp)
            .padding(top = 20.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        PageTitle("Chaman", "Link daalo. Baaki Chaman sambhalega.")

        LuxCard {
            OutlinedTextField(
                value = url,
                onValueChange = {
                    onUrl(it)
                    if (info !is InfoState.None) onInfo(InfoState.None)
                },
                label = { Text("Link") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = cs.primary,
                    unfocusedBorderColor = cs.outline,
                    focusedLabelColor = cs.primary,
                    cursorColor = cs.primary
                )
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuietButton("Paste", onClick = {
                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val t = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
                    if (!t.isNullOrBlank()) {
                        onUrl(t.trim())
                        onInfo(InfoState.None)
                    }
                    closeKeyboard()
                }, modifier = Modifier.weight(1f), height = 54.dp)
                GoldButton("Search", onClick = {
                    closeKeyboard()
                    val u = url.trim()
                    scope.launch { runSearch(u, onInfo) }
                }, modifier = Modifier.weight(1f), enabled = hasUrl && info !is InfoState.Loading)
            }
        }

        when (info) {
            is InfoState.Loading -> LuxCard {
                Text("Details nikal raha hu...", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = cs.primary,
                    trackColor = cs.surfaceVariant
                )
            }
            is InfoState.Err -> LuxCard {
                Text("Search nahi ho paya", style = MaterialTheme.typography.titleMedium, color = cs.error)
                Text(info.msg, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, maxLines = 8, overflow = TextOverflow.Ellipsis)
                QuietButton("Seedha best quality me download", onClick = { start(url.trim(), "") }, enabled = !busy)
            }
            is InfoState.Ok -> ResultView(info.data, busy, ::start)
            else -> {}
        }

        if (busy) {
            Text(
                "Ek download chal raha hai. Queue me dekho.",
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ResultView(d: SearchResult, busy: Boolean, start: (String, String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    var showDesc by remember { mutableStateOf(false) }

    // Thumbnail + details
    LuxCard {
        if (d.thumb.isNotBlank()) {
            val bmp by produceState<Bitmap?>(initialValue = null, d.thumb) {
                value = withContext(Dispatchers.IO) { fetchBitmap(d.thumb) }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(cs.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                val b = bmp
                if (b != null) {
                    Image(
                        bitmap = b.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
        Text(d.title, style = MaterialTheme.typography.headlineSmall, maxLines = 4, overflow = TextOverflow.Ellipsis)
        InfoRow("Channel", d.uploader)
        InfoRow("Duration", d.duration)
        InfoRow("Uploaded", d.date)
        InfoRow("Views", d.views)
        InfoRow("Likes", d.likes)
        InfoRow("Site", d.site)
        if (d.description.isNotBlank()) {
            Text(
                d.description,
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant,
                maxLines = if (showDesc) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis
            )
            TextButton(onClick = { showDesc = !showDesc }) {
                Text(if (showDesc) "Kam dikhao" else "Poora dikhao", color = cs.primary)
            }
        }
    }

    // Video qualities
    LuxCard {
        Text("Video", style = MaterialTheme.typography.titleMedium)
        val best = QualityOpt("Best available", "Auto: sabse achhi quality", 0L, "")
        (listOf(best) + d.videos).forEach { o ->
            QualityRow(o, !busy) { start("${d.title} (${o.label})", o.fmt) }
        }
    }

    // Audio only
    if (d.audios.isNotEmpty()) {
        LuxCard {
            Text("Sirf audio", style = MaterialTheme.typography.titleMedium)
            d.audios.forEach { o ->
                QualityRow(o, !busy) { start("${d.title} (${o.label})", o.fmt) }
            }
        }
    }
}

@Composable
private fun QualityRow(o: QualityOpt, enabled: Boolean, onDownload: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val sizeText = if (o.size > 0) "~" + fmtBytes(o.size.toDouble()) else ""
    val sub = listOf(o.detail, sizeText).filter { it.isNotBlank() }.joinToString(", ")
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(o.label, style = MaterialTheme.typography.titleMedium)
            Text(sub, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
        }
        GoldButton("Download", onClick = onDownload, modifier = Modifier.width(118.dp), enabled = enabled, height = 42.dp)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    if (value.isBlank()) return
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 16.dp))
    }
}
