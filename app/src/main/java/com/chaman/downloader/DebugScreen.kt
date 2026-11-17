package com.chaman.downloader

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chaquo.python.PyException
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Status { IDLE, RUNNING, PASS, FAIL }

class TestState(val label: String) {
    var status by mutableStateOf(Status.IDLE)
    var log by mutableStateOf("")
}

private fun CoroutineScope.runTest(t: TestState, block: () -> String) {
    launch {
        t.status = Status.RUNNING
        t.log = ""
        try {
            t.log = withContext(Dispatchers.IO) { block() }
            t.status = Status.PASS
        } catch (e: Throwable) {
            t.log = errText(e)
            t.status = Status.FAIL
        }
    }
}

private fun py(fn: String, vararg args: Any): String =
    Python.getInstance().getModule("tests").callAttr(fn, *args).toString()

@Composable
fun TestScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    val t1 = remember { TestState("T1 Python") }
    val t2 = remember { TestState("T2 yt-dlp") }
    val t3 = remember { TestState("T3 YouTube") }
    val t4 = remember { TestState("T4 Instagram") }
    val t5 = remember { TestState("T5a FFmpeg version") }
    val t6 = remember { TestState("T5b FFmpeg convert") }
    val t7 = remember { TestState("T5c MP3 encoder") }

    var ytUrl by remember { mutableStateOf("https://www.youtube.com/watch?v=jNQXAC9IVRw") }
    var igUrl by remember { mutableStateOf("https://www.instagram.com/reel/DfzzfBLiVrH/") }
    var dlUrl by remember { mutableStateOf("https://youtu.be/q7nG6rCxbK4") }

    val pm = remember { ctx.getSystemService(Context.POWER_SERVICE) as PowerManager }
    var ignoringBattery by remember { mutableStateOf(pm.isIgnoringBatteryOptimizations(ctx.packageName)) }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Chaman Downloader", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Text("Phase 0 / Batch C - feasibility tests", fontSize = 13.sp)

        TestCard("Test 1: Python runtime (ARM64)", t1) { scope.runTest(t1) { py("env_info") } }
        TestCard("Test 2: yt-dlp import + version", t2) { scope.runTest(t2) { py("ytdlp_version") } }

        OutlinedTextField(
            value = ytUrl, onValueChange = { ytUrl = it },
            label = { Text("YouTube URL") },
            modifier = Modifier.fillMaxWidth(), singleLine = true
        )
        TestCard("Test 3: YouTube info extract", t3) { scope.runTest(t3) { py("yt_info", ytUrl.trim()) } }

        OutlinedTextField(
            value = igUrl, onValueChange = { igUrl = it },
            label = { Text("Instagram public reel URL") },
            modifier = Modifier.fillMaxWidth(), singleLine = true
        )
        TestCard("Test 4: Instagram public info (no login)", t4) { scope.runTest(t4) { py("yt_info", igUrl.trim()) } }

        TestCard("Test 5a: FFmpeg native -version", t5) { scope.runTest(t5) { FfmpegProbe.version() } }
        TestCard("Test 5b: FFmpeg tone to m4a convert", t6) {
            scope.runTest(t6) { FfmpegProbe.convert(ctx.cacheDir) }
        }
        TestCard("Test 5c: libmp3lame, tone to mp3 convert", t7) {
            scope.runTest(t7) { FfmpegProbe.convertMp3(ctx.cacheDir) }
        }

        // ---- Batch C: background download stability ----
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Battery optimization", fontWeight = FontWeight.Medium)
                Text(
                    if (ignoringBattery) "Exempt hai (background me kill hone ka chance kam)"
                    else "Exempt nahi hai - Oppo/ColorOS download kill kar sakta hai",
                    fontSize = 13.sp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            ctx.startActivity(
                                Intent(
                                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                    Uri.parse("package:${ctx.packageName}")
                                )
                            )
                        },
                        enabled = !ignoringBattery
                    ) { Text("Exempt karo") }
                    OutlinedButton(onClick = {
                        ignoringBattery = pm.isIgnoringBatteryOptimizations(ctx.packageName)
                    }) { Text("Re-check") }
                }
            }
        }

        OutlinedTextField(
            value = dlUrl, onValueChange = { dlUrl = it },
            label = { Text("Lamba video URL (background test)") },
            modifier = Modifier.fillMaxWidth(), singleLine = true
        )
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        "Test 6: Background download (foreground service)",
                        fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f)
                    )
                    Text(DownloadState.status.name, fontWeight = FontWeight.Bold)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            ctx.startForegroundService(
                                Intent(ctx, DownloadService::class.java)
                                    .putExtra(DownloadService.EXTRA_URL, dlUrl.trim())
                            )
                        },
                        enabled = DownloadState.status != DlStatus.RUNNING
                    ) { Text("Start") }
                    OutlinedButton(
                        onClick = { DownloadService.cancelRequested = true },
                        enabled = DownloadState.status == DlStatus.RUNNING
                    ) { Text("Cancel") }
                }
                if (DownloadState.status == DlStatus.RUNNING) {
                    if (DownloadState.progress >= 0f) {
                        LinearProgressIndicator(
                            progress = { DownloadState.progress },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    Text(DownloadState.line, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                }
                if (DownloadState.log.isNotEmpty()) {
                    Text(DownloadState.log, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                }
            }
        }

        OutlinedButton(
            onClick = {
                val all = listOf(t1, t2, t3, t4, t5, t6)
                    .joinToString("\n\n") { "${it.label} [${it.status}]\n${it.log}" } +
                    "\n\nT6 Background download [${DownloadState.status}]\nbattery_exempt=$ignoringBattery\n${DownloadState.log}"
                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("chaman-logs", all))
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Copy all logs") }
    }
}

@Composable
private fun TestCard(title: String, state: TestState, onRun: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(title, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                Text(state.status.name, fontWeight = FontWeight.Bold)
            }
            Button(onClick = onRun, enabled = state.status != Status.RUNNING) { Text("Run") }
            if (state.log.isNotEmpty()) {
                Text(state.log, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            }
        }
    }
}

fun errText(e: Throwable): String =
    if (e is PyException) "PyException: ${e.message}" else "${e::class.java.simpleName}: ${e.message}"
