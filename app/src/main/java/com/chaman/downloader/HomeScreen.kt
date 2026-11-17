package com.chaman.downloader

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chaquo.python.Python
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

sealed interface InfoState {
    object None : InfoState
    object Loading : InfoState
    class Ok(val title: String, val uploader: String, val duration: String, val quality: String) : InfoState
    class Err(val msg: String) : InfoState
}

private fun fmtDuration(sec: Long): String {
    val h = sec / 3600
    val m = (sec % 3600) / 60
    val s = sec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

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
                }, modifier = Modifier.weight(1f))
                QuietButton("Info dekho", onClick = {
                    val u = url.trim()
                    scope.launch {
                        onInfo(InfoState.Loading)
                        try {
                            val raw = withContext(Dispatchers.IO) {
                                Python.getInstance().getModule("tests").callAttr("yt_info", u).toString()
                            }
                            val j = JSONObject(raw)
                            val title = if (j.isNull("title")) "-" else j.getString("title")
                            val up = if (j.isNull("uploader")) "-" else j.getString("uploader")
                            val dur = if (j.isNull("duration_sec")) "-" else fmtDuration(j.getDouble("duration_sec").toLong())
                            val h = j.optInt("max_height", 0)
                            onInfo(InfoState.Ok(title, up, dur, if (h > 0) "${h}p" else "-"))
                        } catch (t: Throwable) {
                            onInfo(InfoState.Err(errText(t)))
                        }
                    }
                }, modifier = Modifier.weight(1f), enabled = hasUrl && info !is InfoState.Loading)
            }
        }

        when (info) {
            is InfoState.Loading -> LuxCard {
                Text("Info nikal raha hu...", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = cs.primary,
                    trackColor = cs.surfaceVariant
                )
            }
            is InfoState.Ok -> LuxCard {
                Text(info.title, style = MaterialTheme.typography.headlineSmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                InfoRow("Channel", info.uploader)
                InfoRow("Duration", info.duration)
                InfoRow("Max quality", info.quality)
            }
            is InfoState.Err -> LuxCard {
                Text("Info nahi mili", style = MaterialTheme.typography.titleMedium, color = cs.error)
                Text(info.msg, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, maxLines = 6, overflow = TextOverflow.Ellipsis)
            }
            else -> {}
        }

        GoldButton("Download", onClick = {
            val u = url.trim()
            DownloadState.currentTitle = (info as? InfoState.Ok)?.title ?: u
            ctx.startForegroundService(
                Intent(ctx, DownloadService::class.java).putExtra(DownloadService.EXTRA_URL, u)
            )
            onDownloadStarted()
        }, enabled = hasUrl && !busy)

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
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 16.dp))
    }
}
