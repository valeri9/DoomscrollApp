package com.valeri.doomscroll.overlay

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.valeri.doomscroll.ui.theme.DoomscrollTheme
import com.valeri.doomscroll.ui.theme.Palette
import kotlinx.coroutines.delay

private const val BREATH_CYCLE_MS = 4000

@Composable
fun InterventionScreen(spec: InterventionSpec, onComplete: (InterventionResult) -> Unit) {
    DoomscrollTheme(darkTheme = true) {
        var remaining by remember { mutableIntStateOf(spec.breathingSeconds) }
        var breathingDone by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            while (remaining > 0) {
                delay(1000)
                remaining--
                android.util.Log.d("DoomscrollOverlay", "tick remaining=$remaining")
            }
            breathingDone = true
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = if (spec.isNight) {
                            listOf(Color(0xFF14161F), Palette.Ink)
                        } else {
                            listOf(Palette.Surface, Palette.Ink)
                        }
                    )
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (!breathingDone) {
                BreathingPane(
                    remaining = remaining,
                    appLabel = appLabel(spec.packageName),
                    isNight = spec.isNight,
                    // Closing must be reachable the moment the urge to stop shows up, not only
                    // once the breathing countdown happens to finish — waiting it out is the
                    // one thing this screen must never demand of someone who already wants out.
                    onClose = {
                        onComplete(
                            InterventionResult(reasonLabel = null, reasonText = null, continuedAnyway = false)
                        )
                    },
                )
            } else {
                ChoicePane(spec = spec, onComplete = onComplete)
            }
        }
    }
}

@Composable
private fun BreathingPane(remaining: Int, appLabel: String, isNight: Boolean, onClose: () -> Unit) {
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
    val accent = if (isNight) Palette.Ember else Palette.Mist

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize().padding(32.dp),
        ) {
            Text(
                text = if (isNight) "It's late, and you're scrolling $appLabel" else "You're scrolling $appLabel",
                color = Palette.TextMuted,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
            )

            Box(
                modifier = Modifier.padding(vertical = 56.dp).size(240.dp),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(modifier = Modifier.size(240.dp).scale(scale)) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(accent.copy(alpha = 0.30f), Color.Transparent),
                        ),
                        radius = size.minDimension / 2f,
                    )
                    drawCircle(
                        color = accent.copy(alpha = 0.9f),
                        radius = size.minDimension / 2.4f,
                        style = Stroke(width = 3f),
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

        // Available from the first frame, not just once the count hits zero — the countdown
        // is a suggestion, not a lock, and the moment someone wants out is usually right here,
        // mid-count, not after it.
        OutlinedButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 28.dp, vertical = 32.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Palette.TextPrimary),
        ) {
            Text("Close the app", fontSize = 15.sp, modifier = Modifier.padding(vertical = 4.dp))
        }
    }
}

/**
 * What used to be here asked for a reason — a picker or a typed sentence — before "Continue
 * anyway" would enable. Dropped per explicit feedback: it wasn't landing as reflection, just
 * as one more thing to tap through unread on the way back in. What's left is the two outcomes
 * that actually matter: leave, or go back in — with the night-mode wait, if any, still applied
 * to the latter.
 */
@Composable
private fun ChoicePane(spec: InterventionSpec, onComplete: (InterventionResult) -> Unit) {
    var continueDelay by remember { mutableIntStateOf(spec.continueDelaySeconds) }

    LaunchedEffect(Unit) {
        while (continueDelay > 0) {
            delay(1000)
            continueDelay--
        }
    }
    val canContinue = continueDelay == 0

    fun complete(continuedAnyway: Boolean) = onComplete(
        InterventionResult(reasonLabel = null, reasonText = null, continuedAnyway = continuedAnyway)
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 32.dp),
    ) {
        Button(
            onClick = { complete(false) },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Palette.Mist,
                contentColor = Palette.Ink,
            ),
        ) {
            Text("Close the app", fontSize = 16.sp, modifier = Modifier.padding(vertical = 6.dp))
        }

        Button(
            onClick = { complete(true) },
            enabled = canContinue,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Palette.SurfaceAlt,
                contentColor = Palette.TextPrimary,
                disabledContainerColor = Palette.SurfaceAlt,
                disabledContentColor = Palette.TextMuted,
            ),
        ) {
            Text(
                if (canContinue) "Continue anyway" else "Continue anyway ($continueDelay)",
                fontSize = 16.sp,
                modifier = Modifier.padding(vertical = 6.dp),
            )
        }
    }
}

/** Cheap human label without a PackageManager round-trip on the overlay path. */
private fun appLabel(packageName: String): String = when {
    packageName.contains("instagram") -> "Instagram"
    packageName.contains("musically") || packageName.contains("trill") -> "TikTok"
    else -> packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() }
}
