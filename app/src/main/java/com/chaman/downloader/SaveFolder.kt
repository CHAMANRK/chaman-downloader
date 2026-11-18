package com.chaman.downloader

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap

/** Video file `Video/` me, sirf-audio file `Audio/` me. */
fun subDirFor(hasVideo: Boolean): String = if (hasVideo) "Video" else "Audio"

/** Tree document id ("primary:Download/Chaman") se chhota label. */
fun treeLabel(docId: String): String {
    val path = docId.substringAfter(':', "")
    val vol = docId.substringBefore(':')
    return when {
        path.isEmpty() && vol == "primary" -> "Internal storage"
        path.isEmpty() -> "SD card"
        vol == "primary" -> path
        else -> "SD card: $path"
    }
}

/** Library me SAF file ka mime: Audio/ folder ki file hamesha audio type ka dikhe (webm audio ka mime video/webm aata hai). */
fun safLibMime(inAudioDir: Boolean, mime: String?, name: String): String {
    val m = (mime ?: "").ifBlank { extToMime(name.substringAfterLast('.', "")) }
    if (!inAudioDir || m.startsWith("audio")) return m
    return "audio/" + m.substringAfter('/', "x-unknown").ifBlank { "x-unknown" }
}

/** createFile ko woh mime do jo extension se nikalta hai, warna provider naam ke peeche dobara extension laga deta hai. */
fun safCreateMime(ext: String, fallback: String): String =
    MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.lowercase()) ?: fallback

/** Chuna hua Save folder (SAF tree). Na chuna ho to default Download/Chaman (MediaStore). */
object SaveFolder {
    private const val FILE = "chaman"
    private const val KEY = "save_tree_uri"

    fun treeUri(ctx: Context): Uri? =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY, null)?.let { Uri.parse(it) }

    fun set(ctx: Context, uri: Uri?) {
        val e = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
        if (uri == null) e.remove(KEY) else e.putString(KEY, uri.toString())
        e.apply()
    }

    fun label(ctx: Context): String {
        val t = treeUri(ctx) ?: return "Download/Chaman"
        return try {
            treeLabel(DocumentsContract.getTreeDocumentId(t))
        } catch (e: Exception) {
            "Chuna hua folder"
        }
    }

    private fun docUri(tree: Uri, docId: String): Uri = DocumentsContract.buildDocumentUriUsingTree(tree, docId)

    /** Tree ke andar `name` naam ka sub-folder dhundo, na mile to bana do. Uska document uri deta hai. */
    fun subDir(ctx: Context, tree: Uri, name: String, create: Boolean): Uri? {
        val rootId = DocumentsContract.getTreeDocumentId(tree)
        val rootUri = docUri(tree, rootId)
        val kids = DocumentsContract.buildChildDocumentsUriUsingTree(tree, rootId)
        ctx.contentResolver.query(
            kids,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE
            ),
            null, null, null
        )?.use { c ->
            while (c.moveToNext()) {
                if (c.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR && c.getString(1) == name) {
                    return docUri(tree, c.getString(0))
                }
            }
        }
        if (!create) return null
        return DocumentsContract.createDocument(ctx.contentResolver, rootUri, DocumentsContract.Document.MIME_TYPE_DIR, name)
    }

    /** Video/ aur Audio/ ki files (Library ke liye). Folder ya permission na ho to khali list. */
    fun listItems(ctx: Context): List<LibItem> {
        val tree = treeUri(ctx) ?: return emptyList()
        val out = ArrayList<LibItem>()
        for (sub in listOf("Video", "Audio")) {
            try {
                val dir = subDir(ctx, tree, sub, create = false) ?: continue
                val dirId = DocumentsContract.getDocumentId(dir)
                val kids = DocumentsContract.buildChildDocumentsUriUsingTree(tree, dirId)
                ctx.contentResolver.query(
                    kids,
                    arrayOf(
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        DocumentsContract.Document.COLUMN_SIZE,
                        DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                        DocumentsContract.Document.COLUMN_MIME_TYPE
                    ),
                    null, null, null
                )?.use { c ->
                    while (c.moveToNext()) {
                        val mime = c.getString(4)
                        if (mime == DocumentsContract.Document.MIME_TYPE_DIR) continue
                        val name = c.getString(1) ?: continue
                        val uri = docUri(tree, c.getString(0))
                        out.add(
                            LibItem(
                                id = -(uri.toString().hashCode().toLong() and 0x7fffffffL) - 1L,
                                name = name,
                                size = c.getLong(2),
                                dateSec = c.getLong(3) / 1000,
                                mime = safLibMime(sub == "Audio", mime, name),
                                uri = uri,
                                saf = true
                            )
                        )
                    }
                }
            } catch (t: Throwable) {
                // folder hata diya / permission gayi: us sub-folder ko chhod do
            }
        }
        return out
    }
}
