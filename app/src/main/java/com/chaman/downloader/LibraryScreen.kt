package com.chaman.downloader

import android.content.ActivityNotFoundException
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.util.Size
import android.provider.MediaStore
import android.widget.Toast
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.sp
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

/** File kholo (Library tap + snackbar "Open" dono yahi use karte hain). */
fun openMediaUri(ctx: Context, uri: Uri, mime: String) {
    val i = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, mime)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        ctx.startActivity(i)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(ctx, "Is file ko kholne wali app nahi mili", Toast.LENGTH_SHORT).show()
    }
}

private val thumbCache = android.util.LruCache<Long, Bitmap>(40)
private val durCache = android.util.LruCache<Long, Long>(200)

/** Duration ms me (0 = pata nahi). Cache me rakhta hai taaki scroll pe dobara na nikale. */
private fun loadDurationMs(ctx: Context, f: LibItem): Long {
    durCache.get(f.id)?.let { return it }
    val uri = ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, f.id)
    val r = MediaMetadataRetriever()
    var ms = 0L
    try {
        r.setDataSource(ctx, uri)
        ms = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
    } catch (t: Throwable) {
        ms = 0L
    } finally {
        try {
            r.release()
        } catch (t: Throwable) {
        }
    }
    durCache.put(f.id, ms)
    return ms
}

/** Pehle system thumbnail, na mile to video ka first frame. Audio files ke liye null. */
private fun loadThumb(ctx: Context, f: LibItem): Bitmap? {
    thumbCache.get(f.id)?.let { return it }
    if (f.mime.startsWith("audio")) return null
    val uri = ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, f.id)
    var bmp: Bitmap? = try {
        ctx.contentResolver.loadThumbnail(uri, Size(320, 180), null)
    } catch (t: Throwable) {
        null
    }
    if (bmp == null) {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(ctx, uri)
            bmp = r.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        } catch (t: Throwable) {
            bmp = null
        } finally {
            try {
                r.release()
            } catch (t: Throwable) {
            }
        }
    }
    if (bmp != null) thumbCache.put(f.id, bmp)
    return bmp
}

@Composable
private fun Thumb(f: LibItem) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val bmp by produceState<Bitmap?>(initialValue = thumbCache.get(f.id), f.id) {
        if (value == null) value = withContext(Dispatchers.IO) { loadThumb(ctx, f) }
    }
    val dur by produceState(initialValue = durCache.get(f.id) ?: -1L, f.id) {
        if (value < 0L) value = withContext(Dispatchers.IO) { loadDurationMs(ctx, f) }
    }
    Box(
        modifier = Modifier
            .width(104.dp)
            .height(58.dp)
            .clip(RoundedCornerShape(12.dp))
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
        } else {
            Text(
                f.name.substringAfterLast('.', "").lowercase(),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 8.dp, top = 5.dp),
                style = MaterialTheme.typography.labelLarge,
                fontSize = 11.sp,
                color = cs.onSurfaceVariant
            )
        }
        // Chhota play overlay
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(11.dp)) {
                val p = Path().apply {
                    moveTo(size.width * 0.15f, 0f)
                    lineTo(size.width, size.height / 2f)
                    lineTo(size.width * 0.15f, size.height)
                    close()
                }
                drawPath(p, Color.White)
            }
        }
        // Duration badge
        if (dur > 0L) {
            Text(
                fmtEta(dur / 1000.0),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.65f))
                    .padding(horizontal = 5.dp, vertical = 1.dp),
                fontSize = 11.sp,
                color = Color.White
            )
        }
    }
}

@Composable
private fun FilterPill(label: String, selected: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(20.dp)
    Surface(
        modifier = Modifier
            .clip(shape)
            .clickable(onClick = onClick),
        shape = shape,
        color = if (selected) cs.primary.copy(alpha = 0.16f) else cs.surface,
        border = BorderStroke(1.dp, if (selected) cs.primary.copy(alpha = 0.6f) else cs.outlineVariant)
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) cs.primary else cs.onSurfaceVariant
        )
    }
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
    val focusManager = LocalFocusManager.current
    var query by rememberSaveable { mutableStateOf("") }
    var filterName by rememberSaveable { mutableStateOf(LibFilter.ALL.name) }
    val filter = LibFilter.valueOf(filterName)
    val shown = remember(files, query, filter) {
        files.filter { libMatches(it.name, it.mime, query, filter) }
    }

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
        if (error == null && files.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("Search") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                        trailingIcon = if (query.isNotEmpty()) {
                            {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .clickable { query = "" },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("\u2715", color = cs.onSurfaceVariant, fontSize = 16.sp)
                                }
                            }
                        } else null,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = cs.primary,
                            unfocusedBorderColor = cs.outline,
                            focusedLabelColor = cs.primary,
                            cursorColor = cs.primary
                        )
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterPill("All", filter == LibFilter.ALL) { filterName = LibFilter.ALL.name }
                        FilterPill("Video", filter == LibFilter.VIDEO) { filterName = LibFilter.VIDEO.name }
                        FilterPill("Audio", filter == LibFilter.AUDIO) { filterName = LibFilter.AUDIO.name }
                    }
                }
            }
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
        if (error == null && files.isNotEmpty() && shown.isEmpty()) {
            item {
                LuxCard {
                    Text("Koi match nahi", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Search ya filter badal ke dekho.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onSurfaceVariant
                    )
                }
            }
        }
        items(shown, key = { it.id }) { f ->
            val shape = RoundedCornerShape(18.dp)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .clickable {
                        openMediaUri(
                            ctx,
                            ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, f.id),
                            f.mime
                        )
                    },
                shape = shape,
                color = cs.surface,
                border = BorderStroke(1.dp, cs.outlineVariant)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Thumb(f)
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(f.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            dateFmt.format(Date(f.dateSec * 1000)),
                            style = MaterialTheme.typography.bodyMedium,
                            color = cs.onSurfaceVariant
                        )
                        Text(
                            fmtBytes(f.size.toDouble()),
                            style = MaterialTheme.typography.labelLarge,
                            color = cs.primary
                        )
                    }
                }
            }
        }
    }
}
