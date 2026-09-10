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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.input.ImeAction
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
                BreathingPane(remaining, appLabel(spec.packageName), spec.isNight)
            } else {
                ReasonPane(spec = spec, onComplete = onComplete)
            }
        }
    }
}

@Composable
private fun BreathingPane(remaining: Int, appLabel: String, isNight: Boolean) {
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
}

@Composable
private fun ReasonPane(spec: InterventionSpec, onComplete: (InterventionResult) -> Unit) {
    var pickedLabel by remember { mutableStateOf<String?>(null) }
    var typed by remember { mutableStateOf("") }
    var continueDelay by remember { mutableIntStateOf(0) }

    val typedLongEnough = typed.trim().length >= spec.minReasonChars
    // If there is nothing to pick — every reason for this app was deleted in Settings, or
    // some future path leaves the list empty — treat that as already satisfied rather than
    // as a block on continuing; it must never gate an action there's no way to complete.
    val hasReason = if (spec.requireTypedReason) typedLongEnough else {
        pickedLabel != null || spec.reasons.isEmpty()
    }

    // The delay is friction on CONTINUING, specifically — closing the app is the outcome
    // this whole screen exists to make easy, so it is never gated on a reason or a wait.
    // Starts counting the moment a reason becomes valid, not behind a separate submit step.
    LaunchedEffect(hasReason) {
        if (!hasReason) return@LaunchedEffect
        continueDelay = spec.continueDelaySeconds
        while (continueDelay > 0) {
            delay(1000)
            continueDelay--
        }
    }
    val canContinue = hasReason && continueDelay == 0

    fun complete(continuedAnyway: Boolean) = onComplete(
        InterventionResult(
            reasonLabel = pickedLabel,
            reasonText = typed.trim().takeIf { it.isNotEmpty() },
            continuedAnyway = continuedAnyway,
        )
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 28.dp, vertical = 32.dp),
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

        Text(
            text = "or, if you're staying — why are you here?",
            color = Palette.TextMuted,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 28.dp, bottom = 16.dp),
        )

        if (spec.requireTypedReason) {
            OutlinedTextField(
                value = typed,
                onValueChange = { typed = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                placeholder = { Text("What made you open it?", color = Palette.TextMuted) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Palette.TextPrimary,
                    unfocusedTextColor = Palette.TextPrimary,
                    disabledTextColor = Palette.TextMuted,
                    focusedBorderColor = Palette.Ember,
                    unfocusedBorderColor = Palette.TextMuted,
                    cursorColor = Palette.Ember,
                ),
            )
            if (typed.isNotEmpty() && !typedLongEnough) {
                Text(
                    "${spec.minReasonChars - typed.trim().length} more characters",
                    color = Palette.TextMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 6.dp).fillMaxWidth(),
                    textAlign = TextAlign.End,
                )
            }
        } else {
            spec.reasons.forEach { reason ->
                val selected = pickedLabel == reason
                OutlinedButton(
                    onClick = { pickedLabel = reason },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = if (selected) Palette.Ink else Palette.TextPrimary,
                        containerColor = if (selected) Palette.Mist else Color.Transparent,
                    ),
                ) {
                    Text(reason, fontSize = 16.sp, modifier = Modifier.padding(vertical = 6.dp))
                }
            }
        }

        Button(
            onClick = { complete(true) },
            enabled = canContinue,
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Palette.SurfaceAlt,
                contentColor = Palette.TextPrimary,
                disabledContainerColor = Palette.SurfaceAlt,
                disabledContentColor = Palette.TextMuted,
            ),
        ) {
            Text(
                when {
                    canContinue -> "Continue anyway"
                    hasReason -> "Continue anyway ($continueDelay)"
                    else -> "Continue anyway"
                },
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
