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
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.DisposableEffect
import androidx.compose.material3.TextButton
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.IntentSenderRequest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import android.os.Build
import android.content.IntentSender
import android.content.ContentValues
import android.app.RecoverableSecurityException
import android.app.Activity
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

/** Selection mode chalu hai ya nahi (MainShell ka Back isse dekhta hai). */
object LibrarySelection {
    var active by androidx.compose.runtime.mutableStateOf(false)
}

private class NeedsGrant(val sender: IntentSender, val retry: Boolean)

private fun mediaUri(id: Long): Uri = ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id)

/** File apni app ki na ho (reinstall ke baad) to system permission popup chahiye. */
private fun grantFor(ctx: Context, e: SecurityException, uris: List<Uri>, delete: Boolean): NeedsGrant {
    if (Build.VERSION.SDK_INT >= 30) {
        val pi = if (delete) MediaStore.createDeleteRequest(ctx.contentResolver, uris)
        else MediaStore.createWriteRequest(ctx.contentResolver, uris)
        return NeedsGrant(pi.intentSender, retry = !delete)
    }
    if (e is RecoverableSecurityException) {
        return NeedsGrant(e.userAction.actionIntent.intentSender, retry = true)
    }
    throw e
}

private fun deleteItems(ctx: Context, ids: List<Long>): NeedsGrant? {
    val denied = ArrayList<Uri>()
    var first: SecurityException? = null
    for (id in ids) {
        val u = mediaUri(id)
        try {
            ctx.contentResolver.delete(u, null, null)
        } catch (e: SecurityException) {
            denied.add(u)
            if (first == null) first = e
        }
    }
    val err = first ?: return null
    return grantFor(ctx, err, denied, true)
}

private fun renameItem(ctx: Context, id: Long, newName: String): NeedsGrant? {
    val u = mediaUri(id)
    val v = ContentValues().apply { put(MediaStore.Downloads.DISPLAY_NAME, newName) }
    try {
        val n = ctx.contentResolver.update(u, v, null, null)
        if (n < 1) throw IllegalStateException("Rename nahi hua")
        return null
    } catch (e: SecurityException) {
        return grantFor(ctx, e, listOf(u), false)
    }
}

private fun shareItems(ctx: Context, items: List<LibItem>) {
    if (items.isEmpty()) return
    val uris = ArrayList(items.map { mediaUri(it.id) })
    val i = if (items.size == 1) {
        Intent(Intent.ACTION_SEND).setType(items[0].mime).putExtra(Intent.EXTRA_STREAM, uris[0])
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE)
            .setType(commonMime(items.map { it.mime }))
            .putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
    }
    i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        ctx.startActivity(Intent.createChooser(i, "Share"))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(ctx, "Share karne wali app nahi mili", Toast.LENGTH_SHORT).show()
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
private fun SelectMark(on: Boolean) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(if (on) cs.primary else Color.Transparent)
            .border(1.5.dp, if (on) cs.primary else cs.outline, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (on) {
            val tick = cs.onPrimary
            Canvas(modifier = Modifier.size(12.dp)) {
                val w = size.width
                val h = size.height
                val p = Path().apply {
                    moveTo(w * 0.08f, h * 0.55f)
                    lineTo(w * 0.4f, h * 0.88f)
                    lineTo(w * 0.94f, h * 0.14f)
                }
                drawPath(p, tick, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}

@Composable
private fun ItemMenu(onRename: () -> Unit, onDelete: () -> Unit, onShare: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    var open by remember { mutableStateOf(false) }
    Box {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .clickable { open = true },
            contentAlignment = Alignment.Center
        ) {
            val dot = cs.onSurfaceVariant
            Canvas(modifier = Modifier.size(width = 4.dp, height = 18.dp)) {
                val r = 2.dp.toPx()
                for (k in 0..2) {
                    drawCircle(dot, r, Offset(size.width / 2f, size.height * (k + 0.5f) / 3f))
                }
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Rename") }, onClick = { open = false; onRename() })
            DropdownMenuItem(text = { Text("Delete", color = cs.error) }, onClick = { open = false; onDelete() })
            DropdownMenuItem(text = { Text("Share") }, onClick = { open = false; onShare() })
        }
    }
}

@Composable
private fun RenameDialog(item: LibItem, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val parts = remember(item.id) { splitFileName(item.name) }
    var text by remember(item.id) { mutableStateOf(parts.first) }
    val newName = renamedFileName(item.name, text)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = cs.surface,
        title = { Text("Rename") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                suffix = { Text(parts.second, color = cs.onSurfaceVariant) },
                shape = MaterialTheme.shapes.medium,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = cs.primary,
                    unfocusedBorderColor = cs.outline,
                    cursorColor = cs.primary
                )
            )
        },
        confirmButton = {
            TextButton(
                enabled = newName != null && newName != item.name,
                onClick = { if (newName != null) onConfirm(newName) }
            ) { Text("Rename", color = cs.primary) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = cs.onSurfaceVariant) } }
    )
}

@Composable
private fun DeleteDialog(items: List<LibItem>, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = cs.surface,
        title = { Text("Delete?") },
        text = {
            Text(
                if (items.size == 1) "\"${items[0].name}\" delete ho jayegi. Wapas nahi aayegi."
                else "${items.size} files delete ho jayengi. Wapas nahi aayengi.",
                color = cs.onSurfaceVariant
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Delete", color = cs.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = cs.onSurfaceVariant) } }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(active: Boolean) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    var files by remember { mutableStateOf<List<LibItem>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var reloadTick by remember { mutableStateOf(0) }
    val st = DownloadState.status
    val dateFmt = remember { SimpleDateFormat("d MMM yyyy", Locale.getDefault()) }
    val focusManager = LocalFocusManager.current
    var query by rememberSaveable { mutableStateOf("") }
    var filterName by rememberSaveable { mutableStateOf(LibFilter.ALL.name) }
    val filter = LibFilter.valueOf(filterName)
    val shown = remember(files, query, filter) {
        files.filter { libMatches(it.name, it.mime, query, filter) }
    }

    // Selection: sirf dikhne wali (filtered) files par kaam karta hai
    var selected by remember { mutableStateOf<Set<Long>>(emptySet()) }
    val selItems = shown.filter { it.id in selected }
    val selecting = selItems.isNotEmpty()
    var renameTarget by remember { mutableStateOf<LibItem?>(null) }
    var deleteTargets by remember { mutableStateOf<List<LibItem>>(emptyList()) }
    var retry by remember { mutableStateOf<(() -> Unit)?>(null) }

    LaunchedEffect(selecting, active) { LibrarySelection.active = selecting && active }
    DisposableEffect(Unit) { onDispose { LibrarySelection.active = false } }
    LaunchedEffect(active) { if (!active) selected = emptySet() }
    BackHandler(enabled = selecting && active) { selected = emptySet() }

    fun toggle(id: Long) {
        selected = if (id in selected) selected - id else selected + id
    }

    val grantLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        val rt = retry
        retry = null
        if (r.resultCode == Activity.RESULT_OK) {
            if (rt != null) rt() else {
                reloadTick++
                selected = emptySet()
            }
        }
    }

    fun launchGrant(g: NeedsGrant, then: () -> Unit) {
        retry = if (g.retry) then else null
        grantLauncher.launch(IntentSenderRequest.Builder(g.sender).build())
    }

    fun finished(msg: String) {
        Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
        reloadTick++
        selected = emptySet()
    }

    fun doDelete(ids: List<Long>) {
        scope.launch {
            try {
                val g = withContext(Dispatchers.IO) { deleteItems(ctx, ids) }
                if (g == null) finished("Delete ho gaya") else launchGrant(g) { doDelete(ids) }
            } catch (t: Throwable) {
                Toast.makeText(ctx, errText(t), Toast.LENGTH_LONG).show()
            }
        }
    }

    fun doRename(item: LibItem, newName: String) {
        scope.launch {
            try {
                val g = withContext(Dispatchers.IO) { renameItem(ctx, item.id, newName) }
                if (g == null) finished("Rename ho gaya") else launchGrant(g) { doRename(item, newName) }
            } catch (t: Throwable) {
                Toast.makeText(ctx, errText(t), Toast.LENGTH_LONG).show()
            }
        }
    }

    LaunchedEffect(active, st, reloadTick) {
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

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(
                start = 22.dp, end = 22.dp, top = 20.dp,
                bottom = if (selecting) 190.dp else 120.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                PageTitle("Library")
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
                item { EmptyState(iconIndex = 2, title = "Library khali") }
            }
            if (error == null && files.isNotEmpty() && shown.isEmpty()) {
                item { EmptyState(iconIndex = 2, title = "Koi match nahi") }
            }
            items(shown, key = { it.id }) { f ->
                val isSel = f.id in selected
                val shape = RoundedCornerShape(18.dp)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .combinedClickable(
                            onClick = {
                                if (selecting) toggle(f.id) else openMediaUri(ctx, mediaUri(f.id), f.mime)
                            },
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                toggle(f.id)
                            }
                        ),
                    shape = shape,
                    color = if (isSel) lerp(cs.surface, cs.primary, 0.10f) else cs.surface,
                    border = BorderStroke(
                        if (isSel) 1.5.dp else 1.dp,
                        if (isSel) cs.primary else cs.outlineVariant
                    )
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
                        if (selecting) {
                            SelectMark(isSel)
                        } else {
                            ItemMenu(
                                onRename = { renameTarget = f },
                                onDelete = { deleteTargets = listOf(f) },
                                onShare = { shareItems(ctx, listOf(f)) }
                            )
                        }
                    }
                }
            }
        }

        if (selecting) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(start = 20.dp, end = 20.dp, bottom = 88.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(26.dp),
                color = cs.surfaceVariant,
                border = BorderStroke(1.dp, cs.outline),
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .clickable { selected = emptySet() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("\u2715", color = cs.onSurfaceVariant, fontSize = 16.sp)
                    }
                    Text(
                        "${selItems.size}",
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 6.dp),
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (selItems.size == 1) {
                        TextButton(onClick = { renameTarget = selItems[0] }) { Text("Rename", color = cs.primary) }
                    }
                    TextButton(onClick = { deleteTargets = selItems }) { Text("Delete", color = cs.error) }
                    TextButton(onClick = { shareItems(ctx, selItems) }) { Text("Share", color = cs.primary) }
                }
            }
        }

        renameTarget?.let { t ->
            RenameDialog(
                item = t,
                onDismiss = { renameTarget = null },
                onConfirm = { n ->
                    renameTarget = null
                    doRename(t, n)
                }
            )
        }
        if (deleteTargets.isNotEmpty()) {
            DeleteDialog(
                items = deleteTargets,
                onDismiss = { deleteTargets = emptyList() },
                onConfirm = {
                    val ids = deleteTargets.map { it.id }
                    deleteTargets = emptyList()
                    doDelete(ids)
                }
            )
        }
    }
}
