package com.chaman.downloader

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }
        // Python ko cookie folder batao (Instagram links pe yt-dlp cookiefile ke liye)
        try {
            val py = Python.getInstance().getModule("tests")
            py.callAttr("set_cookie_dir", CookieStore.dir(this).absolutePath)
            py.callAttr("set_instagram_cookies_enabled", Flags.INSTAGRAM_COOKIES)
        } catch (e: Exception) {
        }
        handleShare(intent)
        if (savedInstanceState == null) handleCookies(intent)
        setContent { ChamanApp(this) }
    }

    override fun onResume() {
        super.onResume()
        if (Flags.INSTAGRAM_COOKIES) CookieSetup.onResume(this)
    }

    override fun onPause() {
        if (Flags.INSTAGRAM_COOKIES) CookieSetup.onPause(this)
        super.onPause()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShare(intent)
        handleCookies(intent)
    }

    // Cookie Browser se aaya IMPORT_COOKIES intent (Handshake.md). Sab check CookieStore.importFrom me.
    private fun handleCookies(i: Intent?) {
        if (!Flags.INSTAGRAM_COOKIES) return
        if (i?.action != CookieStore.ACTION_IMPORT) return
        val uri = i.data ?: return
        val r = CookieStore.importFrom(this, uri, i.getStringExtra("site") ?: "")
        Toast.makeText(this, r.message, Toast.LENGTH_LONG).show()
        if (r.ok) CookieInbox.tick++
    }

    private fun handleShare(i: Intent?) {
        if (i?.action != Intent.ACTION_SEND) return
        val u = urlFromShareIntent(i)
        if (u != null) {
            ShareInbox.pending = u
        } else {
            Toast.makeText(this, "Share me koi link nahi mila", Toast.LENGTH_SHORT).show()
        }
    }
}

private val TABS = listOf("Home", "Queue", "Library", "Settings")

@Composable
fun ChamanApp(activity: ComponentActivity) {
    val ctx = LocalContext.current
    var mode by remember { mutableStateOf(Prefs.theme(ctx)) }
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.AMOLED -> true
        ThemeMode.LIGHT -> false
    }

    DisposableEffect(dark) {
        val t = android.graphics.Color.TRANSPARENT
        val style = if (dark) SystemBarStyle.dark(t) else SystemBarStyle.light(t, t)
        activity.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        onDispose { }
    }

    ChamanTheme(dark, amoled = mode == ThemeMode.AMOLED) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            var showDebug by rememberSaveable { mutableStateOf(false) }
            var showWelcome by rememberSaveable { mutableStateOf(!Prefs.welcomeDone(ctx)) }
            if (showWelcome) {
                WelcomeScreen(onDone = {
                    Prefs.setWelcomeDone(ctx)
                    showWelcome = false
                })
            } else if (showDebug) {
                BackHandler { showDebug = false }
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                ) {
                    Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        QuietButton("Wapas", onClick = { showDebug = false }, modifier = Modifier.width(120.dp))
                    }
                    TestScreen()
                }
            } else {
                MainShell(
                    mode = mode,
                    onMode = {
                        mode = it
                        Prefs.setTheme(ctx, it)
                    },
                    onOpenDebug = { showDebug = true },
                    onOpenWelcome = { showWelcome = true }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MainShell(mode: ThemeMode, onMode: (ThemeMode) -> Unit, onOpenDebug: () -> Unit, onOpenWelcome: () -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { TABS.size })

    // Download complete: haptic + chhota "Saved" snackbar (Open action)
    val snackbar = remember { SnackbarHostState() }
    val haptic = LocalHapticFeedback.current
    val dlStatus = DownloadState.status
    var prevStatus by remember { mutableStateOf(dlStatus) }
    LaunchedEffect(dlStatus) {
        val was = prevStatus
        prevStatus = dlStatus
        if (was == DlStatus.RUNNING && dlStatus == DlStatus.PASS) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            val canOpen = DownloadState.savedUri != null
            val r = snackbar.showSnackbar(
                message = "Saved",
                actionLabel = if (canOpen) "Open" else null,
                duration = SnackbarDuration.Long
            )
            if (r == SnackbarResult.ActionPerformed) {
                val u = DownloadState.savedUri
                if (u != null) openMediaUri(ctx, android.net.Uri.parse(u), DownloadState.savedMime)
            }
        }
    }

    // Queue tab se hatne par sirf Completed card hatao (RUNNING / FAIL / CANCELLED rehte hain)
    var prevPage by remember { mutableStateOf(pagerState.currentPage) }
    LaunchedEffect(pagerState.currentPage) {
        val p = pagerState.currentPage
        if (prevPage == 1 && p != 1 && DownloadState.status == DlStatus.PASS) {
            DownloadState.status = DlStatus.IDLE
        }
        prevPage = p
    }

    var url by rememberSaveable { mutableStateOf("") }
    var info by remember { mutableStateOf<InfoState>(InfoState.None) }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    BackHandler(enabled = pagerState.currentPage != 0 && !LibrarySelection.active) {
        scope.launch { pagerState.animateScrollToPage(0) }
    }

    fun goTo(i: Int) {
        scope.launch { pagerState.animateScrollToPage(i) }
    }

    // Cookies import hui: Settings tab kholo (count wahin dikhta hai)
    LaunchedEffect(CookieInbox.tick) {
        if (CookieInbox.tick > 0) goTo(3)
    }

    // Share se link aaya: Home kholo, URL bharo, auto-search (download khud nahi hota)
    LaunchedEffect(ShareInbox.pending) {
        val p = ShareInbox.pending ?: return@LaunchedEffect
        ShareInbox.pending = null
        url = p
        goTo(0)
        // scope.launch: pending null hone se ye effect restart hota hai, search cancel na ho
        scope.launch { runSearch(p) { info = it } }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            val offset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction).absoluteValue
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = 1f - offset.coerceIn(0f, 1f) * 0.6f }
            ) {
                when (page) {
                    0 -> HomeScreen(
                        url = url,
                        onUrl = { url = it },
                        info = info,
                        onInfo = { info = it },
                        onDownloadStarted = { goTo(1) }
                    )
                    1 -> QueueScreen(onOpenLibrary = { goTo(2) })
                    2 -> LibraryScreen(active = pagerState.currentPage == 2)
                    else -> SettingsScreen(mode = mode, onMode = onMode, onOpenDebug = onOpenDebug, onOpenWelcome = onOpenWelcome)
                }
            }
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 88.dp),
            snackbar = { data ->
                Snackbar(
                    snackbarData = data,
                    shape = RoundedCornerShape(16.dp),
                    containerColor = cs.surfaceVariant,
                    contentColor = cs.onSurface,
                    actionColor = cs.primary
                )
            }
        )

        FloatingNav(
            pagerState = pagerState,
            onSelect = { goTo(it) },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 12.dp)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FloatingNav(
    pagerState: PagerState,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    // Yahin padhte hain taaki progress tick pe sirf nav recompose ho, poora shell nahi
    val queueBusy = DownloadState.status == DlStatus.RUNNING
    val queueProgress = DownloadState.progress
    Surface(
        modifier = modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .height(64.dp),
        shape = RoundedCornerShape(32.dp),
        color = cs.surface,
        border = BorderStroke(1.dp, cs.outline.copy(alpha = 0.6f)),
        shadowElevation = 12.dp
    ) {
        BoxWithConstraints(modifier = Modifier.padding(6.dp)) {
            val tabW = maxWidth / TABS.size
            val pos = pagerState.currentPage + pagerState.currentPageOffsetFraction
            val pill = RoundedCornerShape(26.dp)

            // Sliding indicator: pager ke saath-saath chalta hai (swipe me bhi)
            Box(
                modifier = Modifier
                    .offset(x = tabW * pos)
                    .width(tabW)
                    .fillMaxHeight()
                    .clip(pill)
                    .background(cs.primary.copy(alpha = 0.16f))
                    .border(1.dp, cs.primary.copy(alpha = 0.45f), pill)
            )

            Row(modifier = Modifier.fillMaxSize()) {
                TABS.forEachIndexed { i, label ->
                    val closeness = (1f - (pos - i).absoluteValue).coerceIn(0f, 1f)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(pill)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onSelect(i) },
                        contentAlignment = Alignment.Center
                    ) {
                        val tint = lerp(cs.onSurfaceVariant, cs.primary, closeness)
                        Column(
                            modifier = Modifier.animateContentSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(1.dp)
                        ) {
                            if (i == 1 && queueBusy) {
                                // Download chalte waqt: icon andar, ring bahar
                                Box(modifier = Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .border(2.dp, cs.outline.copy(alpha = 0.5f), CircleShape)
                                    )
                                    if (queueProgress >= 0f) {
                                        CircularProgressIndicator(
                                            progress = { queueProgress },
                                            modifier = Modifier.size(28.dp),
                                            color = cs.primary,
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(28.dp),
                                            color = cs.primary,
                                            strokeWidth = 2.dp
                                        )
                                    }
                                    NavIcon(i, tint, Modifier.size(15.dp))
                                }
                            } else {
                                Box(modifier = Modifier.size(22.dp)) {
                                    NavIcon(i, tint, Modifier.fillMaxSize())
                                }
                            }
                            if (closeness > 0.5f) {
                                Text(
                                    label,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = tint
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
