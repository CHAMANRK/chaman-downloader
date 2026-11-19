package com.chaman.downloader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun QueueScreen(onOpenLibrary: () -> Unit) {
    val ctx = LocalContext.current
    val dao = remember { AppDb.get(ctx).dao() }
    val flow = remember(dao) { dao.observeAll() }
    val items by flow.collectAsState(initial = emptyList())
    val sorted = remember(items) { sortForDisplay(items) }
    var logOpenId by remember { mutableStateOf(-1L) }
    val hasDone = items.any { QStatus.parse(it.status) == QStatus.PASS }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 22.dp)
            .padding(top = 20.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        PageTitle("Queue")

        if (items.isEmpty()) {
            EmptyState(iconIndex = 1, title = "Queue khali")
        } else {
            if (hasDone) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { QueueRepo.clearCompleted(ctx) }) {
                        Text("Completed hatao", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            sorted.forEach { item ->
                QueueItemCard(
                    item = item,
                    position = queuePosition(items, item.id),
                    logOpen = logOpenId == item.id,
                    onToggleLog = { logOpenId = if (logOpenId == item.id) -1L else item.id },
                    onOpenLibrary = onOpenLibrary
                )
            }
        }
    }
}

@Composable
private fun QueueItemCard(
    item: DlItem,
    position: Int,
    logOpen: Boolean,
    onToggleLog: () -> Unit,
    onOpenLibrary: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val st = QStatus.parse(item.status)
    val isCurrent = DownloadState.currentId == item.id

    LuxCard {
        Text(
            item.title.ifBlank { item.url },
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            queueStatusLabel(st, position),
            style = MaterialTheme.typography.labelLarge,
            color = if (st == QStatus.FAIL) cs.error else cs.primary
        )

        if (st == QStatus.FAIL && item.error.isNotBlank()) {
            Text(item.error, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
        }

        if (st == QStatus.RUNNING) {
            val progress = if (isCurrent) DownloadState.progress else item.progress
            val barMod = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
            if (progress >= 0f) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = barMod,
                    color = cs.primary,
                    trackColor = cs.surfaceVariant
                )
                Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.titleMedium)
            } else {
                LinearProgressIndicator(modifier = barMod, color = cs.primary, trackColor = cs.surfaceVariant)
            }
            if (isCurrent) {
                Text(
                    DownloadState.line,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = cs.onSurfaceVariant
                )
            }
        }

        when (st) {
            QStatus.RUNNING -> QuietButton("Cancel", onClick = { QueueRepo.cancel(ctx, item.id) })
            QStatus.QUEUED -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuietButton("Cancel", onClick = { QueueRepo.cancel(ctx, item.id) }, modifier = Modifier.weight(1f), height = 54.dp)
                QuietButton("Quality badlo", onClick = { changeQuality(item) }, modifier = Modifier.weight(1f), height = 54.dp)
            }
            QStatus.FAIL, QStatus.CANCELLED -> {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GoldButton("Retry", onClick = { QueueRepo.retry(ctx, item.id) }, modifier = Modifier.weight(1f))
                    QuietButton("Quality badlo", onClick = { changeQuality(item) }, modifier = Modifier.weight(1f), height = 54.dp)
                }
                TextButton(onClick = { QueueRepo.remove(ctx, item.id) }) {
                    Text("Hatao", color = cs.onSurfaceVariant)
                }
            }
            QStatus.PASS -> {
                GoldButton("Library me dekho", onClick = onOpenLibrary)
                TextButton(onClick = { QueueRepo.remove(ctx, item.id) }) {
                    Text("Hatao", color = cs.onSurfaceVariant)
                }
            }
        }

        // Log sirf us item ka jo abhi chal raha ya aakhri chala (memory me rehta hai)
        if (isCurrent) {
            TextButton(onClick = onToggleLog) {
                Text(if (logOpen) "Log chhupao" else "Log dikhao", color = cs.primary)
            }
            if (logOpen) {
                Text(
                    DownloadState.log,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = cs.onSurfaceVariant
                )
            }
        }
    }
}

/** Link Home me bharo + search; nayi quality ka download banne par ye purana item hat jata hai. */
private fun changeQuality(item: DlItem) {
    QueueRepo.replace = item.id to item.url
    ShareInbox.pending = item.url
}
