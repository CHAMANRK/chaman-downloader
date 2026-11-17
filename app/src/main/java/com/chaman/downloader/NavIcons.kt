package com.chaman.downloader

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/** Nav ke 4 icon (Canvas se; koi extra icon library nahi). index = tab index: Home, Queue, Library, Settings. */
@Composable
fun NavIcon(index: Int, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val sw = 1.8.dp.toPx()
        val st = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (index) {
            0 -> { // Home
                val roof = Path().apply {
                    moveTo(w * 0.12f, h * 0.48f)
                    lineTo(w * 0.5f, h * 0.14f)
                    lineTo(w * 0.88f, h * 0.48f)
                }
                val body = Path().apply {
                    moveTo(w * 0.24f, h * 0.42f)
                    lineTo(w * 0.24f, h * 0.84f)
                    lineTo(w * 0.76f, h * 0.84f)
                    lineTo(w * 0.76f, h * 0.42f)
                }
                drawPath(roof, tint, style = st)
                drawPath(body, tint, style = st)
            }
            1 -> { // Queue (download arrow + tray)
                val arrow = Path().apply {
                    moveTo(w * 0.5f, h * 0.12f)
                    lineTo(w * 0.5f, h * 0.6f)
                    moveTo(w * 0.3f, h * 0.42f)
                    lineTo(w * 0.5f, h * 0.62f)
                    lineTo(w * 0.7f, h * 0.42f)
                }
                val tray = Path().apply {
                    moveTo(w * 0.16f, h * 0.7f)
                    lineTo(w * 0.16f, h * 0.86f)
                    lineTo(w * 0.84f, h * 0.86f)
                    lineTo(w * 0.84f, h * 0.7f)
                }
                drawPath(arrow, tint, style = st)
                drawPath(tray, tint, style = st)
            }
            2 -> { // Library (2x2 grid)
                val cell = Size(w * 0.34f, h * 0.34f)
                val r = CornerRadius(w * 0.08f, w * 0.08f)
                val xs = listOf(w * 0.12f, w * 0.54f)
                val ys = listOf(h * 0.12f, h * 0.54f)
                for (x in xs) for (y in ys) {
                    drawRoundRect(tint, topLeft = Offset(x, y), size = cell, cornerRadius = r, style = st)
                }
            }
            else -> { // Settings (3 sliders)
                val ys = listOf(h * 0.24f, h * 0.5f, h * 0.76f)
                val knobs = listOf(w * 0.68f, w * 0.34f, w * 0.6f)
                val kr = w * 0.09f
                for (i in 0..2) {
                    drawLine(tint, Offset(w * 0.12f, ys[i]), Offset(w * 0.88f, ys[i]), strokeWidth = sw, cap = StrokeCap.Round)
                    drawCircle(tint, radius = kr + sw / 2f, center = Offset(knobs[i], ys[i]))
                }
            }
        }
    }
}
