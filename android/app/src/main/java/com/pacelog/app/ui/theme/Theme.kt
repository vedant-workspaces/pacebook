package com.pacelog.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Pacelog palette: night-run navy, warm paper, ember accent. */
object Pal {
    val Ink = Color(0xFF0E1726)
    val InkSoft = Color(0xFF3A4558)
    val Muted = Color(0xFF6B7486)
    val Paper = Color(0xFFF6F5F1)
    val Line = Color(0xFFE5E2DA)
    val Card = Color.White
    val Ember = Color(0xFFFF5A1F)
    val EmberDark = Color(0xFFE9480F)
    val Ember50 = Color(0xFFFFF2EB)

    // Status colours, always paired with a symbol and a word.
    val Done = Color(0xFF12845A); val Done50 = Color(0xFFE6F5EE)
    val Changed = Color(0xFFA86A0B); val Changed50 = Color(0xFFFDF3E2)
    val Missed = Color(0xFFBF3A34); val Missed50 = Color(0xFFFCEBEA)
    val Planned = Color(0xFF5B6678); val Planned50 = Color(0xFFEEF0F4)

    // Chart series (validated pair for colour-vision deficiency).
    val Actual = Color(0xFFF2591D)
    val Plan = Color(0xFF4E78B5)
}

private val scheme = lightColorScheme(
    primary = Pal.Ember,
    onPrimary = Color.White,
    primaryContainer = Pal.Ember50,
    onPrimaryContainer = Pal.Ink,
    secondary = Pal.Ink,
    onSecondary = Color.White,
    background = Pal.Paper,
    onBackground = Pal.Ink,
    surface = Pal.Card,
    onSurface = Pal.Ink,
    surfaceVariant = Pal.Paper,
    onSurfaceVariant = Pal.Muted,
    surfaceContainer = Color.White,
    surfaceContainerLow = Pal.Paper,
    surfaceContainerHigh = Color.White,
    outline = Pal.Line,
    outlineVariant = Pal.Line,
    error = Pal.Missed,
)

val Numeric = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp)

@Composable
fun PacelogTheme(content: @Composable () -> Unit) {
    val base = Typography()
    MaterialTheme(
        colorScheme = scheme,
        typography = base.copy(
            headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.Black, letterSpacing = (-0.5).sp),
            headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Black),
            titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold),
        ),
        content = content,
    )
}
