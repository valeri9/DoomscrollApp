package com.valeri.doomscroll.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object Palette {
    val Ink = Color(0xFF070B14)
    val Surface = Color(0xFF111827)
    val SurfaceAlt = Color(0xFF1B2436)
    val Mist = Color(0xFF7FD1C1)
    val MistDeep = Color(0xFF3E9C8B)
    val Ember = Color(0xFFE8A87C)
    val TextPrimary = Color(0xFFEAF0F6)
    val TextMuted = Color(0xFF97A3B6)
}

private val DarkScheme = darkColorScheme(
    primary = Palette.Mist,
    onPrimary = Palette.Ink,
    secondary = Palette.Ember,
    background = Palette.Ink,
    onBackground = Palette.TextPrimary,
    surface = Palette.Surface,
    onSurface = Palette.TextPrimary,
    surfaceVariant = Palette.SurfaceAlt,
    onSurfaceVariant = Palette.TextMuted,
)

private val LightScheme = lightColorScheme(
    primary = Palette.MistDeep,
    secondary = Palette.Ember,
)

@Composable
fun DoomscrollTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        content = content,
    )
}

/**
 * Chart marks are a separate palette from the UI accents.
 *
 * The UI accents (Mist, Ember) sit around OKLCH L 0.79 with chroma under 0.10 — fine as
 * chrome on a dark ground, but as data marks they fall outside the dark-mode lightness band
 * and read as washed-out gray. These two are the validated substitutes: L within 0.48–0.67,
 * chroma above 0.10, worst-case CVD separation ΔE 12.5 (protan) against each other and
 * >= 3:1 contrast against the Ink surface.
 */
object ChartPalette {
    /** Series 1 — screen time. Also the single-series colour for the daily trend. */
    val Teal = Color(0xFF12A88C)
    /** Series 2 — night-time share. */
    val Amber = Color(0xFFD97828)
    /** Recessive grid and axis lines. */
    val Grid = Color(0xFF232B3B)
    /** Bars for days with no data, and the unfilled remainder of a track. */
    val Track = Color(0xFF1A2231)
}
