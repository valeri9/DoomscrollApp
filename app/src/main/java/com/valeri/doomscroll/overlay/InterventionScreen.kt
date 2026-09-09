package com.valeri.doomscroll.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.valeri.doomscroll.ui.theme.DoomscrollTheme
import com.valeri.doomscroll.ui.theme.Palette
import kotlinx.coroutines.delay

private const val BREATH_CYCLE_MS = 4000

/** Phase 1 uses a fixed list; Phase 2 makes these editable per app. */
private val DEFAULT_REASONS = listOf(
    "Bored",
    "Habit",
    "Came for something specific",
    "Got sidetracked from DMs",
    "Avoiding something",
)

@Composable
fun InterventionScreen(
    packageName: String,
    contextLabel: String,
    breathingSeconds: Int,
    onDismiss: (reason: String?) -> Unit,
) {
    DoomscrollTheme(darkTheme = true) {
        var remaining by remember { mutableIntStateOf(breathingSeconds) }
        var phase by remember { mutableStateOf(Phase.BREATHING) }

        LaunchedEffect(Unit) {
            while (remaining > 0) {
                delay(1000)
                remaining--
            }
            phase = Phase.REASON
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(Palette.Surface, Palette.Ink),
                        center = Offset.Unspecified,
                        radius = 1600f,
                    )
                ),
            contentAlignment = Alignment.Center,
        ) {
            when (phase) {
                Phase.BREATHING -> BreathingPane(remaining, appLabel(packageName))
                Phase.REASON -> ReasonPane(
                    appLabel = appLabel(packageName),
                    onPick = onDismiss,
                )
            }
        }
    }
}

private enum class Phase { BREATHING, REASON }

@Composable
private fun BreathingPane(remaining: Int, appLabel: String) {
    val transition = rememberInfiniteTransition(label = "breath")
    val scale by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = InfiniteRepeatableSpec(
            animation = tween(BREATH_CYCLE_MS, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "scale",
    )
    val inhaling = scale > 0.775f

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize().padding(32.dp),
    ) {
        Text(
            text = "You're scrolling $appLabel",
            color = Palette.TextMuted,
            fontSize = 15.sp,
        )

        Box(
            modifier = Modifier.padding(vertical = 56.dp).size(240.dp),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.size(240.dp).scale(scale)) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Palette.Mist.copy(alpha = 0.35f), Color.Transparent),
                    ),
                    radius = size.minDimension / 2f,
                )
                drawCircle(
                    color = Palette.Mist.copy(alpha = 0.9f),
                    radius = size.minDimension / 2.4f,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f),
                )
            }
            Text(
                text = "$remaining",
                color = Palette.TextPrimary,
                fontSize = 44.sp,
                fontWeight = FontWeight.Light,
            )
        }

        Text(
            text = if (inhaling) "Breathe in" else "Breathe out",
            color = Palette.TextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Light,
        )
    }
}

@Composable
private fun ReasonPane(appLabel: String, onPick: (String?) -> Unit) {
    AnimatedVisibility(visible = true, enter = fadeIn()) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp),
        ) {
            Text(
                text = "Why are you here?",
                color = Palette.TextPrimary,
                fontSize = 26.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "One tap. No wrong answer.",
                color = Palette.TextMuted,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 8.dp, bottom = 28.dp),
            )

            DEFAULT_REASONS.forEach { reason ->
                OutlinedButton(
                    onClick = { onPick(reason) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Palette.TextPrimary),
                ) {
                    Text(reason, fontSize = 16.sp, modifier = Modifier.padding(vertical = 6.dp))
                }
            }

            Button(
                onClick = { onPick(null) },
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Palette.Mist,
                    contentColor = Palette.Ink,
                ),
            ) {
                Text("Continue anyway", fontSize = 16.sp, modifier = Modifier.padding(vertical = 6.dp))
            }
        }
    }
}

/** Cheap human label without a PackageManager round-trip on the overlay path. */
private fun appLabel(packageName: String): String = when {
    packageName.contains("instagram") -> "Instagram"
    packageName.contains("musically") || packageName.contains("trill") -> "TikTok"
    else -> packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() }
}
