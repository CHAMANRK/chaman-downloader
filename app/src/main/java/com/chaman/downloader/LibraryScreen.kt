package com.chaman.downloader

import android.content.ActivityNotFoundException
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LibItem(val id: Long, val name: String, val size: Long, val dateSec: Long, val mime: String)

private fun queryLibrary(ctx: Context): List<LibItem> {
    val out = ArrayList<LibItem>()
    val proj = arrayOf(
        MediaStore.Downloads._ID,
        MediaStore.Downloads.DISPLAY_NAME,
        MediaStore.Downloads.SIZE,
        MediaStore.Downloads.DATE_ADDED,
        MediaStore.Downloads.MIME_TYPE
    )
    ctx.contentResolver.query(
        MediaStore.Downloads.EXTERNAL_CONTENT_URI,
        proj,
        "${MediaStore.Downloads.RELATIVE_PATH} LIKE ?",
        arrayOf("Download/Chaman%"),
        "${MediaStore.Downloads.DATE_ADDED} DESC"
    )?.use { c ->
        while (c.moveToNext()) {
            out.add(
                LibItem(
                    id = c.getLong(0),
                    name = c.getString(1) ?: "(no name)",
                    size = c.getLong(2),
                    dateSec = c.getLong(3),
                    mime = c.getString(4) ?: "*/*"
                )
            )
        }
    }
    return out
}

private fun fmtSize(b: Long): String = when {
    b >= 1_000_000_000L -> "%.2f GB".format(b / 1_000_000_000.0)
    b >= 1_000_000L -> "%.1f MB".format(b / 1_000_000.0)
    else -> "%d KB".format(b / 1000)
}

@Composable
fun LibraryScreen(active: Boolean) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    var files by remember { mutableStateOf<List<LibItem>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val st = DownloadState.status
    val dateFmt = remember { SimpleDateFormat("d MMM yyyy", Locale.getDefault()) }

    LaunchedEffect(active, st) {
        if (active) {
            try {
                files = withContext(Dispatchers.IO) { queryLibrary(ctx) }
                error = null
            } catch (t: Throwable) {
                error = errText(t)
            }
            loaded = true
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 20.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            PageTitle("Library", "Download/Chaman me jo files hain.")
        }
        if (error != null) {
            item {
                LuxCard {
                    Text("Library nahi khuli", style = MaterialTheme.typography.titleMedium, color = cs.error)
                    Text(error ?: "", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                }
            }
        } else if (loaded && files.isEmpty()) {
            item {
                LuxCard {
                    Text("Abhi koi file nahi", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Download complete hote hi file yahan aa jayegi.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onSurfaceVariant
                    )
                }
            }
        }
        items(files, key = { it.id }) { f ->
            val shape = RoundedCornerShape(18.dp)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .clickable {
                        val uri = ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, f.id)
                        val i = Intent(Intent.ACTION_VIEW)
                            .setDataAndType(uri, f.mime)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        try {
                            ctx.startActivity(i)
                        } catch (e: ActivityNotFoundException) {
                            Toast.makeText(ctx, "Is file ko kholne wali app nahi mili", Toast.LENGTH_SHORT).show()
                        }
                    },
                shape = shape,
                color = cs.surface,
                border = BorderStroke(1.dp, cs.outlineVariant)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(f.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            dateFmt.format(Date(f.dateSec * 1000)),
                            style = MaterialTheme.typography.bodyMedium,
                            color = cs.onSurfaceVariant
                        )
                    }
                    Text(
                        fmtSize(f.size),
                        style = MaterialTheme.typography.labelLarge,
                        color = cs.primary,
                        modifier = Modifier.padding(start = 12.dp)
                    )
                }
            }
        }
    }
}
