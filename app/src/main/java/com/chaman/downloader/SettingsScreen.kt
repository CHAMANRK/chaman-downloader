package com.chaman.downloader

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

@Composable
fun SettingsScreen(
    mode: ThemeMode,
    onMode: (ThemeMode) -> Unit,
    onOpenDebug: () -> Unit,
    onOpenWelcome: () -> Unit
) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val pm = remember { ctx.getSystemService(Context.POWER_SERVICE) as PowerManager }
    var exempt by remember { mutableStateOf(pm.isIgnoringBatteryOptimizations(ctx.packageName)) }

    // Settings screen se wapas aane par battery status refresh
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) {
                exempt = pm.isIgnoringBatteryOptimizations(ctx.packageName)
            }
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    var saveLabel by remember { mutableStateOf(SaveFolder.label(ctx)) }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try {
                ctx.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                SaveFolder.set(ctx, uri)
                saveLabel = SaveFolder.label(ctx)
            } catch (e: Exception) {
                Toast.makeText(ctx, "Folder permission nahi mili: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    val version = remember {
        try {
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "-"
        } catch (e: Exception) {
            "-"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 22.dp)
            .padding(top = 20.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        PageTitle("Settings")

        LuxCard {
            Text("Theme", style = MaterialTheme.typography.titleMedium)
            listOf(
                listOf(ThemeMode.SYSTEM to "System", ThemeMode.DARK to "Dark"),
                listOf(ThemeMode.AMOLED to "AMOLED", ThemeMode.LIGHT to "Light")
            ).forEach { rowItems ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowItems.forEach { (m, label) ->
                        val sel = m == mode
                        val shape = RoundedCornerShape(22.dp)
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .clip(shape)
                                .clickable { onMode(m) },
                            shape = shape,
                            color = if (sel) cs.primary.copy(alpha = 0.16f) else cs.surface,
                            border = BorderStroke(1.dp, if (sel) cs.primary.copy(alpha = 0.6f) else cs.outline)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    label,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (sel) cs.primary else cs.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        if (!exempt) {
            LuxCard {
                Text("Background download", style = MaterialTheme.typography.titleMedium)
                GoldButton("Exempt karo", onClick = {
                    ctx.startActivity(
                        Intent(
                            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:${ctx.packageName}")
                        )
                    )
                })
            }
        }

        LuxCard {
            Text("Save folder", style = MaterialTheme.typography.titleMedium)
            Text(
                "$saveLabel  (Video/ aur Audio/ andar khud bante hain)",
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant
            )
            GoldButton("Folder chuno", onClick = { pickFolder.launch(null) })
            if (SaveFolder.treeUri(ctx) != null) {
                QuietButton("Default (Download/Chaman) pe wapas", onClick = {
                    SaveFolder.treeUri(ctx)?.let { old ->
                        try {
                            ctx.contentResolver.releasePersistableUriPermission(
                                old, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                            )
                        } catch (e: Exception) {
                        }
                    }
                    SaveFolder.set(ctx, null)
                    saveLabel = SaveFolder.label(ctx)
                })
            }
        }

        val cookieVersion = CookieStore.version
        val igCount = remember(cookieVersion) { CookieStore.count(ctx, "instagram") }
        LuxCard {
            Text("Instagram cookies", style = MaterialTheme.typography.titleMedium)
            if (igCount > 0) {
                Text(
                    "$igCount cookies imported",
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant
                )
            }
            GoldButton(
                CookieSetup.buttonLabel(igCount > 0),
                onClick = { CookieSetup.onButton("instagram") },
                enabled = CookieSetup.buttonEnabled()
            )
            if (igCount > 0 && CookieSetup.phase == SetupPhase.IDLE) {
                QuietButton("Clear", onClick = { CookieStore.clear(ctx, "instagram") })
            }
            CookieSetup.error?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = cs.error)
            }
        }

        QuietButton("Engine tests (Debug)", onClick = onOpenDebug)

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("Version $version", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            Text(
                "Made by Najeef",
                style = MaterialTheme.typography.bodyMedium,
                color = cs.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onOpenWelcome() }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            )
        }
    }
}
