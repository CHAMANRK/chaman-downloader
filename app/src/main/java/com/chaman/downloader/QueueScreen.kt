package com.chaman.downloader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun QueueScreen(onOpenLibrary: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    var showLog by remember { mutableStateOf(false) }
    val st = DownloadState.status

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 22.dp)
            .padding(top = 20.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        PageTitle("Queue", "Abhi jo chal raha hai.")

        if (st == DlStatus.IDLE) {
            LuxCard {
                Text("Kuch nahi chal raha", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Home se link daalo, download yahan dikhega.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant
                )
            }
        } else {
            LuxCard {
                Text(
                    DownloadState.currentTitle.ifBlank { "Download" },
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                val label = when (st) {
                    DlStatus.RUNNING -> "Download ho raha hai"
                    DlStatus.PASS -> "Complete"
                    DlStatus.FAIL -> "Fail ho gaya"
                    DlStatus.CANCELLED -> "Cancel kiya"
                    else -> ""
                }
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (st == DlStatus.FAIL) cs.error else cs.primary
                )

                if (st == DlStatus.RUNNING) {
                    val barMod = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                    if (DownloadState.progress >= 0f) {
                        LinearProgressIndicator(
                            progress = { DownloadState.progress },
                            modifier = barMod,
                            color = cs.primary,
                            trackColor = cs.surfaceVariant
                        )
                        Text(
                            "${(DownloadState.progress * 100).toInt()}%",
                            style = MaterialTheme.typography.titleMedium
                        )
                    } else {
                        LinearProgressIndicator(
                            modifier = barMod,
                            color = cs.primary,
                            trackColor = cs.surfaceVariant
                        )
                    }
                    Text(
                        DownloadState.line,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = cs.onSurfaceVariant
                    )
                    QuietButton("Cancel", onClick = { DownloadService.cancelRequested = true })
                }

                if (st == DlStatus.PASS) {
                    GoldButton("Library me dekho", onClick = onOpenLibrary)
                }

                TextButton(onClick = { showLog = !showLog }) {
                    Text(if (showLog) "Log chhupao" else "Log dikhao", color = cs.primary)
                }
                if (showLog) {
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
}
