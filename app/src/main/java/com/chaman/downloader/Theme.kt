package com.chaman.downloader

import android.content.Context
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class ThemeMode { SYSTEM, DARK, AMOLED, LIGHT }

/** Chhoti settings (SharedPreferences). */
object Prefs {
    private const val FILE = "chaman"
    private const val KEY_THEME = "theme"
    private const val KEY_WELCOME = "welcome_done"

    fun theme(ctx: Context): ThemeMode {
        val v = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY_THEME, null)
        return try {
            if (v == null) ThemeMode.SYSTEM else ThemeMode.valueOf(v)
        } catch (e: IllegalArgumentException) {
            ThemeMode.SYSTEM
        }
    }

    fun welcomeDone(ctx: Context): Boolean =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY_WELCOME, false)

    fun setWelcomeDone(ctx: Context) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(KEY_WELCOME, true).apply()
    }

    fun setTheme(ctx: Context, m: ThemeMode) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(KEY_THEME, m.name).apply()
    }
}

// Dark: warm black + champagne gold. Light: ivory + bronze.
private val LuxDark = darkColorScheme(
    primary = Color(0xFFD4B26A),
    onPrimary = Color(0xFF1A1407),
    background = Color(0xFF0C0B0A),
    onBackground = Color(0xFFEDE6DA),
    surface = Color(0xFF151311),
    onSurface = Color(0xFFEDE6DA),
    surfaceVariant = Color(0xFF1E1B18),
    onSurfaceVariant = Color(0xFF9A9288),
    outline = Color(0xFF3A342C),
    outlineVariant = Color(0xFF2A2621),
    error = Color(0xFFE5826F)
)

// AMOLED: asli black background (pixels band), baaki Dark jaisa.
private val LuxAmoled = LuxDark.copy(
    background = Color(0xFF000000),
    surface = Color(0xFF080807),
    surfaceVariant = Color(0xFF121110),
    outline = Color(0xFF2E2923),
    outlineVariant = Color(0xFF1C1915)
)

private val LuxLight = lightColorScheme(
    primary = Color(0xFF8A6A25),
    onPrimary = Color(0xFFFFFBF2),
    background = Color(0xFFF6F2EA),
    onBackground = Color(0xFF1B1813),
    surface = Color(0xFFFFFDF9),
    onSurface = Color(0xFF1B1813),
    surfaceVariant = Color(0xFFEEE8DC),
    onSurfaceVariant = Color(0xFF756C5F),
    outline = Color(0xFFCFC5B1),
    outlineVariant = Color(0xFFE2DACB),
    error = Color(0xFFB3402A)
)

private val LuxTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Serif, fontWeight = FontWeight.Normal,
        fontSize = 38.sp, lineHeight = 44.sp, letterSpacing = (-0.5).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Serif, fontWeight = FontWeight.Normal,
        fontSize = 22.sp, lineHeight = 29.sp
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp
    ),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(
        fontWeight = FontWeight.Medium, fontSize = 14.sp, letterSpacing = 0.3.sp
    )
)

@Composable
fun ChamanTheme(dark: Boolean, amoled: Boolean = false, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark && amoled) LuxAmoled else if (dark) LuxDark else LuxLight,
        typography = LuxTypography,
        shapes = Shapes(
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(16.dp),
            large = RoundedCornerShape(24.dp)
        ),
        content = content
    )
}
