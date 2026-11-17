package com.chaman.downloader

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val MossLight = lightColorScheme(
    primary = Color(0xFF4E6B4A),
    onPrimary = Color(0xFFFFFFFF),
    background = Color(0xFFF4F6F1),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1C211B)
)
private val MossDark = darkColorScheme(
    primary = Color(0xFF9DBB98),
    onPrimary = Color(0xFF14210F),
    background = Color(0xFF121612),
    surface = Color(0xFF1B211A),
    onSurface = Color(0xFFE2E8DF)
)

enum class Status { IDLE, RUNNING, PASS, FAIL }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) MossDark else MossLight) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    TestScreen()
                }
            }
        }
    }
}

@Composable
fun TestScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var pyStatus by remember { mutableStateOf(Status.IDLE) }
    var pyLog by remember { mutableStateOf("") }
    var ytStatus by remember { mutableStateOf(Status.IDLE) }
    var ytLog by remember { mutableStateOf("") }
    var infoStatus by remember { mutableStateOf(Status.IDLE) }
    var infoLog by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("https://www.youtube.com/watch?v=jNQXAC9IVRw") }

    suspend fun runPy(fn: String, vararg args: Any): String = withContext(Dispatchers.IO) {
        Python.getInstance().getModule("tests").callAttr(fn, *args).toString()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Chaman Downloader", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Text("Phase 0 / Batch A - feasibility tests", fontSize = 13.sp)

        TestCard(
            title = "Test 1: Python runtime (ARM64)",
            status = pyStatus,
            log = pyLog,
            onRun = {
                scope.launch {
                    pyStatus = Status.RUNNING; pyLog = ""
                    try {
                        pyLog = runPy("env_info"); pyStatus = Status.PASS
                    } catch (e: Throwable) {
                        pyLog = errText(e); pyStatus = Status.FAIL
                    }
                }
            }
        )

        TestCard(
            title = "Test 2: yt-dlp import + version",
            status = ytStatus,
            log = ytLog,
            onRun = {
                scope.launch {
                    ytStatus = Status.RUNNING; ytLog = ""
                    try {
                        ytLog = runPy("ytdlp_version"); ytStatus = Status.PASS
                    } catch (e: Throwable) {
                        ytLog = errText(e); ytStatus = Status.FAIL
                    }
                }
            }
        )

        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("YouTube URL") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        TestCard(
            title = "Test 3: YouTube info extract (no download)",
            status = infoStatus,
            log = infoLog,
            onRun = {
                scope.launch {
                    infoStatus = Status.RUNNING; infoLog = ""
                    try {
                        infoLog = runPy("yt_info", url.trim()); infoStatus = Status.PASS
                    } catch (e: Throwable) {
                        infoLog = errText(e); infoStatus = Status.FAIL
                    }
                }
            }
        )

        OutlinedButton(
            onClick = {
                val all = "T1 [$pyStatus]\n$pyLog\n\nT2 [$ytStatus]\n$ytLog\n\nT3 [$infoStatus]\n$infoLog"
                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("chaman-logs", all))
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Copy all logs") }
    }
}

@Composable
private fun TestCard(title: String, status: Status, log: String, onRun: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(title, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                Text(status.name, fontWeight = FontWeight.Bold)
            }
            Button(onClick = onRun, enabled = status != Status.RUNNING) { Text("Run") }
            if (log.isNotEmpty()) {
                Text(log, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            }
        }
    }
}

private fun errText(e: Throwable): String =
    if (e is PyException) "PyException: ${e.message}" else "${e::class.java.simpleName}: ${e.message}"
