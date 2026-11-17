package com.chaman.downloader

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/** Welcome pages ka text. Credit me "Gaya" nahi likhna (user ka rule). */
val WELCOME_PAGES: List<String> = listOf(
    "Welcome to Chaman Downloader",
    "Download all your videos & audios from here.",
    "Made by Najeef\nRequested by Gulam Jilani"
)

/** Typing sound: code se synth (audio file nahi). Low "thock" = chhota thump + halka click. */
class TypeSound {
    private var track: AudioTrack? = null

    init {
        try {
            val rate = 22050
            val n = (rate * 0.055).toInt()
            val pcm = ShortArray(n)
            val rnd = Random(7)
            for (i in 0 until n) {
                val t = i / rate.toDouble()
                val thump = sin(2.0 * PI * 150.0 * t) * exp(-t * 70.0)
                val body = sin(2.0 * PI * 310.0 * t) * exp(-t * 110.0) * 0.45
                val click = (rnd.nextDouble() * 2.0 - 1.0) * exp(-t * 420.0) * 0.5
                val v = (thump + body + click).coerceIn(-1.0, 1.0)
                pcm[i] = (v * 0.9 * Short.MAX_VALUE).toInt().toShort()
            }
            val t = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(rate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(n * 2)
                .build()
            t.write(pcm, 0, n)
            track = t
        } catch (e: Throwable) {
            track = null
        }
    }

    fun tick() {
        val t = track ?: return
        try {
            t.stop()
            t.reloadStaticData()
            t.play()
        } catch (e: Throwable) {
            // sound na chale to bhi screen chalti rahe
        }
    }

    fun release() {
        try {
            track?.release()
        } catch (e: Throwable) {
        }
        track = null
    }
}

/** Halka vibration (VIBRATE permission normal hai). */
class Buzz(ctx: Context) {
    @Suppress("DEPRECATION")
    private val vib: Vibrator? = ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

    private fun shot(ms: Long, amp: Int) {
        val v = vib ?: return
        if (!v.hasVibrator()) return
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                v.vibrate(VibrationEffect.createOneShot(ms, amp))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(ms)
            }
        } catch (e: Throwable) {
        }
    }

    fun key() = shot(14, 110)
    fun page() = shot(50, 220)
}

@Composable
fun WelcomeScreen(onDone: () -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val sound = remember { TypeSound() }
    val buzz = remember { Buzz(ctx) }
    DisposableEffect(Unit) { onDispose { sound.release() } }

    var page by remember { mutableStateOf(0) }
    var typedLen by remember { mutableStateOf(0) }
    var done by remember { mutableStateOf(false) }
    var skip by remember { mutableStateOf(false) }

    val last = page == WELCOME_PAGES.lastIndex

    BackHandler { if (page > 0 && done) { page -= 1 } }

    LaunchedEffect(page) {
        val text = WELCOME_PAGES[page]
        typedLen = 0
        done = false
        skip = false
        delay(350)
        for (i in text.indices) {
            if (skip) break
            typedLen = i + 1
            val c = text[i]
            if (c != ' ' && c != '\n') {
                buzz.key()
                sound.tick()
            }
            delay(if (c == '\n') 420L else 62L)
        }
        typedLen = text.length
        buzz.page()
        done = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(cs.background)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { if (!done) skip = true }
    ) {
        Crossfade(targetState = page, label = "welcomePage", modifier = Modifier.fillMaxSize()) { p ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(horizontal = 30.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                val full = WELCOME_PAGES[p]
                val shown = if (p == page) full.take(typedLen) else full
                Text(
                    shown,
                    style = MaterialTheme.typography.headlineLarge,
                    color = cs.onBackground
                )
            }
        }

        // 3 dots
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
                .padding(start = 30.dp, bottom = 52.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            WELCOME_PAGES.indices.forEach { i ->
                val w by animateDpAsState(if (i == page) 22.dp else 7.dp, label = "dot")
                Box(
                    modifier = Modifier
                        .height(7.dp)
                        .width(w)
                        .clip(CircleShape)
                        .background(if (i == page) cs.primary else cs.outline)
                )
            }
        }

        // Floating Next / tick
        AnimatedVisibility(
            visible = done,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 26.dp, bottom = 36.dp),
            enter = fadeIn() + scaleIn(initialScale = 0.6f),
            exit = fadeOut() + scaleOut()
        ) {
            Box(
                modifier = Modifier
                    .size(62.dp)
                    .clip(CircleShape)
                    .background(cs.primary)
                    .clickable {
                        buzz.page()
                        if (last) onDone() else page += 1
                    },
                contentAlignment = Alignment.Center
            ) {
                WelcomeGlyph(tick = last, tint = cs.onPrimary, modifier = Modifier.size(26.dp))
            }
        }
    }
}

/** Next arrow ya tick (Canvas SVG-style icon, emoji nahi). */
@Composable
private fun WelcomeGlyph(tick: Boolean, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val st = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val p = Path()
        if (tick) {
            p.moveTo(w * 0.18f, h * 0.54f)
            p.lineTo(w * 0.42f, h * 0.76f)
            p.lineTo(w * 0.84f, h * 0.26f)
        } else {
            p.moveTo(w * 0.14f, h * 0.5f)
            p.lineTo(w * 0.84f, h * 0.5f)
            p.moveTo(w * 0.52f, h * 0.18f)
            p.lineTo(w * 0.86f, h * 0.5f)
            p.lineTo(w * 0.52f, h * 0.82f)
        }
        drawPath(p, tint, style = st)
    }
}
