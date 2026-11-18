package com.chaman.downloader

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

enum class SetupPhase { IDLE, DOWNLOADING, READY_INSTALL, NEED_PERMISSION, INSTALLING, UNINSTALLING, FAILED }

private enum class BrowserState { MISSING, WRONG_KEY, OLD, READY }

/**
 * Import Instagram cookies flow: browser app install/update (agar zarurat) -> LOGIN intent.
 * Contract: Handshake.md. Welcome page (Step P) aur Settings dono isi ko chalate hain.
 */
object CookieSetup {
    private const val BROWSER_ACTIVITY = "com.chaman.geckotest.MainActivity"
    private const val ACTION_LOGIN = "com.chaman.browser.action.LOGIN"
    private const val MIN_BROWSER_VERSION = 3L
    private const val APK_NAME = "CHAMAN-Cookie-Browser.apk"
    // Browser repo ka naam galat ho to yahan (aur Handshake.md me) badlo.
    private const val APK_URL =
        "https://github.com/CHAMANRK/CHAMAN-GeckoView-Test/releases/latest/download/CHAMAN-Cookie-Browser.apk"

    var phase by mutableStateOf(SetupPhase.IDLE)
        private set
    var progress by mutableStateOf(-1f)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    private var site = "instagram"
    private var host: Activity? = null
    private val main = Handler(Looper.getMainLooper())

    fun onResume(a: Activity) {
        host = a
        afterReturn(a)
    }

    fun onPause(a: Activity) {
        if (host === a) host = null
    }

    fun buttonLabel(hasCookies: Boolean): String = when (phase) {
        SetupPhase.IDLE, SetupPhase.FAILED -> if (hasCookies) "Dobara import" else "Import Instagram cookies"
        SetupPhase.DOWNLOADING ->
            if (progress >= 0f) "Browser download ho raha hai ${(progress * 100).toInt()}%" else "Browser download ho raha hai"
        SetupPhase.READY_INSTALL -> "Browser install karo"
        SetupPhase.NEED_PERMISSION -> "Install permission do"
        SetupPhase.INSTALLING -> "Install ho raha hai"
        SetupPhase.UNINSTALLING -> "Purana browser hata rahe hain"
    }

    fun buttonEnabled(): Boolean = phase == SetupPhase.IDLE || phase == SetupPhase.FAILED ||
        phase == SetupPhase.READY_INSTALL || phase == SetupPhase.NEED_PERMISSION

    /** Button tap: sahi phase ke hisaab se aage badhao. */
    fun onButton(site: String) {
        val a = host ?: return
        when (phase) {
            SetupPhase.IDLE, SetupPhase.FAILED -> start(a, site)
            SetupPhase.READY_INSTALL, SetupPhase.NEED_PERMISSION -> proceedInstall(a)
            else -> {}
        }
    }

    private fun browserState(ctx: Context): BrowserState {
        val pm = ctx.packageManager
        val pi = try {
            pm.getPackageInfo(CookieStore.BROWSER_PKG, 0)
        } catch (e: PackageManager.NameNotFoundException) {
            return BrowserState.MISSING
        }
        if (pm.checkSignatures(ctx.packageName, CookieStore.BROWSER_PKG) != PackageManager.SIGNATURE_MATCH) {
            return BrowserState.WRONG_KEY
        }
        return if (pi.longVersionCode < MIN_BROWSER_VERSION) BrowserState.OLD else BrowserState.READY
    }

    private fun start(a: Activity, site: String) {
        this.site = site
        error = null
        when (browserState(a)) {
            BrowserState.READY -> openLogin(a)
            BrowserState.WRONG_KEY -> {
                phase = SetupPhase.UNINSTALLING
                startSafe(a, Intent(Intent.ACTION_DELETE, Uri.parse("package:${CookieStore.BROWSER_PKG}")))
            }
            else -> download(a.applicationContext)
        }
    }

    private fun openLogin(a: Activity) {
        phase = SetupPhase.IDLE
        startSafe(
            a,
            Intent(ACTION_LOGIN)
                .setClassName(CookieStore.BROWSER_PKG, BROWSER_ACTIVITY)
                .putExtra("site", site)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private fun download(app: Context) {
        phase = SetupPhase.DOWNLOADING
        progress = -1f
        Thread {
            val err = fetchApk(app)
            main.post {
                if (err != null) {
                    error = err
                    phase = SetupPhase.FAILED
                } else {
                    phase = SetupPhase.READY_INSTALL
                    // App background me ho to Android activity start rokta hai: resume par aage badhega.
                    host?.let { proceedInstall(it) }
                }
            }
        }.start()
    }

    /** Release APK download + install se pehle check. Error text ya null. */
    private fun fetchApk(app: Context): String? {
        val dir = File(app.cacheDir, "apk").apply { mkdirs() }
        val part = File(dir, "$APK_NAME.part")
        val out = File(dir, APK_NAME)
        try {
            val conn = URL(APK_URL).openConnection() as HttpURLConnection
            conn.connectTimeout = 20_000
            conn.readTimeout = 30_000
            conn.instanceFollowRedirects = true
            if (conn.responseCode != 200) {
                conn.disconnect()
                return "Browser APK nahi mili (HTTP ${conn.responseCode}). GitHub release check karo"
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
            return verifyApk(app, out)
        } catch (e: Exception) {
            return "Download fail: ${e.message ?: e.javaClass.simpleName}"
        }
    }

    /** APK sahi app ki ho aur Downloader ki hi key se sign ho (warna handshake chalega hi nahi). */
    private fun verifyApk(app: Context, f: File): String? {
        if (f.length() < 1_000_000) return "APK chhoti ya kharab hai"
        val pm = app.packageManager
        val flags = PackageManager.GET_SIGNING_CERTIFICATES
        val apk = pm.getPackageArchiveInfo(f.absolutePath, flags) ?: return "APK padhi nahi gayi"
        if (apk.packageName != CookieStore.BROWSER_PKG) return "APK galat app ki hai"
        val mine = pm.getPackageInfo(app.packageName, flags)
        val a = apk.signingInfo?.apkContentsSigners?.toSet()
        val b = mine.signingInfo?.apkContentsSigners?.toSet()
        if (a == null || b == null || a != b) {
            return "Browser APK ki signature Downloader se match nahi hui. Browser repo me wahi keystore secrets daalo"
        }
        return null
    }

    private fun proceedInstall(a: Activity) {
        if (!a.packageManager.canRequestPackageInstalls()) {
            phase = SetupPhase.NEED_PERMISSION
            startSafe(
                a,
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${a.packageName}"))
            )
            return
        }
        val f = File(File(a.cacheDir, "apk"), APK_NAME)
        if (!f.isFile) {
            error = "APK file nahi mili, dobara try karo"
            phase = SetupPhase.FAILED
            return
        }
        val uri = FileProvider.getUriForFile(a, "${a.packageName}.files", f)
        phase = SetupPhase.INSTALLING
        startSafe(
            a,
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        )
    }

    // Settings screen / installer / uninstall screen se wapas aane par.
    private fun afterReturn(a: Activity) {
        when (phase) {
            SetupPhase.UNINSTALLING ->
                if (browserState(a) == BrowserState.MISSING) download(a.applicationContext) else phase = SetupPhase.IDLE
            SetupPhase.NEED_PERMISSION ->
                if (a.packageManager.canRequestPackageInstalls()) proceedInstall(a)
            SetupPhase.INSTALLING ->
                if (browserState(a) == BrowserState.READY) {
                    File(a.cacheDir, "apk").deleteRecursively()
                    openLogin(a)
                } else {
                    phase = SetupPhase.IDLE
                }
            SetupPhase.READY_INSTALL -> proceedInstall(a)
            else -> {}
        }
    }

    private fun startSafe(a: Activity, i: Intent) {
        try {
            a.startActivity(i)
        } catch (e: Exception) {
            error = "Open nahi ho paya: ${e.message ?: e.javaClass.simpleName}"
            phase = SetupPhase.FAILED
        }
    }
}
