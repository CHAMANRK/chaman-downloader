package com.chaman.downloader

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Pure helpers (unit test: UpdateTest.kt). */
object UpdateLogic {
    /** "v0.4.24" ya "0.4.24-parked" -> [0, 4, 24]. Number se shuru na ho to null. */
    fun parseVersion(s: String?): List<Int>? {
        if (s == null) return null
        val t = s.trim().removePrefix("v").removePrefix("V")
        val m = Regex("^\\d+(\\.\\d+)*").find(t) ?: return null
        return m.value.split(".").map { it.toIntOrNull() ?: return null }
    }

    /** remote tag local versionName se bada ho to true. Parse na ho to false (popup nahi). */
    fun isNewer(remote: String?, local: String?): Boolean {
        val r = parseVersion(remote) ?: return false
        val l = parseVersion(local) ?: return false
        val n = maxOf(r.size, l.size)
        for (i in 0 until n) {
            val a = r.getOrElse(i) { 0 }
            val b = l.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    /** (naam, url) list me se wanted naam ki url. */
    fun pickApkUrl(assets: List<Pair<String, String>>, wanted: String): String? =
        assets.firstOrNull { it.first == wanted }?.second?.takeIf { it.startsWith("https://") }

    fun shouldCheck(now: Long, lastOk: Long, gapMs: Long): Boolean = lastOk <= 0L || now - lastOk >= gapMs || now < lastOk
}

enum class UpdPhase { IDLE, AVAILABLE, DOWNLOADING, READY_INSTALL, NEED_PERMISSION, INSTALLING, FAILED }

/**
 * Auto update: GitHub Releases ka latest (sirf normal release, pre-release nahi) check karta hai.
 * Release me asset ka naam APK_ASSET hona chahiye (HANDOFF.md me release tarika).
 * Koi token nahi, repo public.
 */
object Updater {
    private const val RELEASE_API = "https://api.github.com/repos/CHAMANRK/chaman-downloader/releases/latest"
    private const val APK_ASSET = "Chaman-Downloader.apk"
    private const val APK_FILE = "chaman-update.apk"
    private const val CHECK_GAP_MS = 6L * 60 * 60 * 1000
    private const val PREF = "chaman"
    private const val KEY_LAST_OK = "upd_last_ok"

    var phase by mutableStateOf(UpdPhase.IDLE)
        private set
    var tag by mutableStateOf("")
        private set
    var progress by mutableStateOf(-1f)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    private var apkUrl = ""
    private var checking = false
    private var dismissedTag = ""
    private var pendingInstall = false
    private var host: Activity? = null
    private val main = Handler(Looper.getMainLooper())

    fun onResume(a: Activity) {
        host = a
        afterReturn(a)
    }

    fun onPause(a: Activity) {
        if (host === a) host = null
    }

    private fun localVersion(ctx: Context): String =
        try {
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: ""
        } catch (e: Exception) {
            ""
        }

    /** App start par ek baar. Net/GitHub fail ho to chupchap kuch nahi. */
    fun check(ctx: Context) {
        if (checking || phase != UpdPhase.IDLE) return
        val app = ctx.applicationContext
        val prefs = app.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (!UpdateLogic.shouldCheck(now, prefs.getLong(KEY_LAST_OK, 0L), CHECK_GAP_MS)) return
        checking = true
        Thread {
            try {
                val conn = URL(RELEASE_API).openConnection() as HttpURLConnection
                conn.connectTimeout = 10_000
                conn.readTimeout = 10_000
                conn.setRequestProperty("Accept", "application/vnd.github+json")
                conn.setRequestProperty("User-Agent", "ChamanDownloader")
                if (conn.responseCode != 200) {
                    conn.disconnect()
                    return@Thread
                }
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
                val j = JSONObject(body)
                if (j.optBoolean("draft") || j.optBoolean("prerelease")) return@Thread
                val t = j.optString("tag_name")
                val arr = j.optJSONArray("assets")
                val assets = ArrayList<Pair<String, String>>()
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        assets.add(o.optString("name") to o.optString("browser_download_url"))
                    }
                }
                val url = UpdateLogic.pickApkUrl(assets, APK_ASSET)
                if (UpdateLogic.isNewer(t, localVersion(app)) && url != null) {
                    if (t != dismissedTag) {
                        main.post {
                            if (phase == UpdPhase.IDLE) {
                                tag = t
                                apkUrl = url
                                error = null
                                phase = UpdPhase.AVAILABLE
                            }
                        }
                    }
                } else {
                    prefs.edit().putLong(KEY_LAST_OK, now).apply()
                }
            } catch (e: Exception) {
                // chupchap: update check zaroori nahi
            } finally {
                checking = false
            }
        }.start()
    }

    /** Baad me / Band karo. Is version ka popup is process me dobara nahi. */
    fun close() {
        dismissedTag = tag
        pendingInstall = false
        phase = UpdPhase.IDLE
        error = null
        File(File(host?.cacheDir ?: return, "apk"), APK_FILE).delete()
    }

    fun startDownload(ctx: Context) {
        if (phase != UpdPhase.AVAILABLE && phase != UpdPhase.FAILED) return
        val app = ctx.applicationContext
        phase = UpdPhase.DOWNLOADING
        progress = -1f
        error = null
        Thread {
            val err = fetchApk(app)
            main.post {
                if (err != null) {
                    error = err
                    phase = UpdPhase.FAILED
                } else {
                    phase = UpdPhase.READY_INSTALL
                    val h = host
                    if (h != null) proceedInstall(h) else pendingInstall = true
                }
            }
        }.start()
    }

    fun install() {
        val h = host ?: return
        if (phase == UpdPhase.READY_INSTALL || phase == UpdPhase.NEED_PERMISSION) proceedInstall(h)
    }

    private fun fetchApk(app: Context): String? {
        val dir = File(app.cacheDir, "apk").apply { mkdirs() }
        val part = File(dir, "$APK_FILE.part")
        val out = File(dir, APK_FILE)
        try {
            val conn = URL(apkUrl).openConnection() as HttpURLConnection
            conn.connectTimeout = 20_000
            conn.readTimeout = 30_000
            conn.instanceFollowRedirects = true
            if (conn.responseCode != 200) {
                val code = conn.responseCode
                conn.disconnect()
                return "APK nahi mili (HTTP $code)"
            }
            val total = conn.contentLengthLong
            conn.inputStream.use { input ->
                part.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    var last = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        done += n
                        val now = System.currentTimeMillis()
                        if (now - last > 150) {
                            last = now
                            progress = if (total > 0) done.toFloat() / total else -1f
                        }
                    }
                }
            }
            conn.disconnect()
            if (out.exists()) out.delete()
            if (!part.renameTo(out)) return "APK save nahi hui"
            val bad = verifyApk(app, out)
            if (bad != null) out.delete()
            return bad
        } catch (e: Exception) {
            return "Download fail: ${e.message ?: e.javaClass.simpleName}"
        }
    }

    /** Wahi app, wahi signing key, aur version abhi se bada. Warna install nahi. */
    private fun verifyApk(app: Context, f: File): String? {
        if (f.length() < 1_000_000) return "APK chhoti ya kharab hai"
        val pm = app.packageManager
        val flags = PackageManager.GET_SIGNING_CERTIFICATES
        val apk = pm.getPackageArchiveInfo(f.absolutePath, flags) ?: return "APK padhi nahi gayi"
        if (apk.packageName != app.packageName) return "APK galat app ki hai"
        val mine = pm.getPackageInfo(app.packageName, flags)
        if (apk.longVersionCode <= mine.longVersionCode) return "APK purani hai"
        val a = apk.signingInfo?.apkContentsSigners?.toSet()
        val b = mine.signingInfo?.apkContentsSigners?.toSet()
        if (a == null || b == null || a != b) return "APK ki signature match nahi hui"
        return null
    }

    private fun proceedInstall(a: Activity) {
        if (!a.packageManager.canRequestPackageInstalls()) {
            phase = UpdPhase.NEED_PERMISSION
            startSafe(a, Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${a.packageName}")))
            return
        }
        val f = File(File(a.cacheDir, "apk"), APK_FILE)
        if (!f.isFile) {
            error = "APK file nahi mili, dobara try karo"
            phase = UpdPhase.FAILED
            return
        }
        val uri = FileProvider.getUriForFile(a, "${a.packageName}.files", f)
        phase = UpdPhase.INSTALLING
        startSafe(
            a,
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        )
    }

    // Permission screen / installer se wapas aane par. Install ho gaya to process hi naya ho jata hai.
    private fun afterReturn(a: Activity) {
        when (phase) {
            UpdPhase.NEED_PERMISSION -> if (a.packageManager.canRequestPackageInstalls()) proceedInstall(a)
            UpdPhase.INSTALLING -> phase = UpdPhase.READY_INSTALL
            UpdPhase.READY_INSTALL -> if (pendingInstall) {
                pendingInstall = false
                proceedInstall(a)
            }
            else -> {}
        }
    }

    private fun startSafe(a: Activity, i: Intent) {
        try {
            a.startActivity(i)
        } catch (e: Exception) {
            error = "Open nahi ho paya: ${e.message ?: e.javaClass.simpleName}"
            phase = UpdPhase.FAILED
        }
    }
}

/** Naya version popup. Download chalte waqt (Queue RUNNING) AVAILABLE popup nahi aata. */
@Composable
fun UpdateDialog() {
    val ph = Updater.phase
    if (ph == UpdPhase.IDLE) return
    if (ph == UpdPhase.AVAILABLE && DownloadState.status == DlStatus.RUNNING) return
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val busy = ph == UpdPhase.DOWNLOADING || ph == UpdPhase.INSTALLING
    val title = when (ph) {
        UpdPhase.AVAILABLE -> "Naya version ${Updater.tag}"
        UpdPhase.DOWNLOADING -> "Update download ho raha hai"
        UpdPhase.INSTALLING -> "Install ho raha hai"
        UpdPhase.FAILED -> "Update fail ho gaya"
        else -> "Update install karo"
    }
    AlertDialog(
        onDismissRequest = { if (!busy) Updater.close() },
        containerColor = cs.surface,
        title = { Text(title) },
        text = {
            if (ph == UpdPhase.DOWNLOADING) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val barMod = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                    if (Updater.progress >= 0f) {
                        LinearProgressIndicator(
                            progress = { Updater.progress },
                            modifier = barMod,
                            color = cs.primary,
                            trackColor = cs.surfaceVariant
                        )
                        Text("${(Updater.progress * 100).toInt()}%", color = cs.onSurfaceVariant)
                    } else {
                        LinearProgressIndicator(modifier = barMod, color = cs.primary, trackColor = cs.surfaceVariant)
                    }
                }
            } else if (ph == UpdPhase.FAILED) {
                Text(Updater.error ?: "", color = cs.error)
            }
        },
        confirmButton = {
            when (ph) {
                UpdPhase.AVAILABLE -> TextButton(onClick = { Updater.startDownload(ctx) }) { Text("Update", color = cs.primary) }
                UpdPhase.FAILED -> TextButton(onClick = { Updater.startDownload(ctx) }) { Text("Dobara", color = cs.primary) }
                UpdPhase.READY_INSTALL -> TextButton(onClick = { Updater.install() }) { Text("Install karo", color = cs.primary) }
                UpdPhase.NEED_PERMISSION -> TextButton(onClick = { Updater.install() }) { Text("Permission do", color = cs.primary) }
                else -> {}
            }
        },
        dismissButton = {
            if (!busy) {
                TextButton(onClick = { Updater.close() }) {
                    Text(if (ph == UpdPhase.AVAILABLE) "Baad me" else "Band karo", color = cs.onSurfaceVariant)
                }
            }
        }
    )
}
