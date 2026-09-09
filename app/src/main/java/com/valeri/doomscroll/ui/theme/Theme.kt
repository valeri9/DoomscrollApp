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
