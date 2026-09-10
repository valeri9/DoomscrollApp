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
    var submitted by remember { mutableStateOf(false) }
    var continueDelay by remember { mutableIntStateOf(0) }

    // The extra wait only starts once a reason is actually given.
    LaunchedEffect(submitted) {
        if (!submitted) return@LaunchedEffect
        continueDelay = spec.continueDelaySeconds
        while (continueDelay > 0) {
            delay(1000)
            continueDelay--
        }
    }

    val typedLongEnough = typed.trim().length >= spec.minReasonChars
    // If there is nothing to pick — every reason for this app was deleted in Settings, or
    // some future path leaves the list empty — the overlay must never become unclosable.
    // BACK is deliberately swallowed here, so an unmet gate with nothing to satisfy it would
    // trap the user until the 5-minute safety timeout. Treat "nothing to choose from" as
    // already satisfied rather than as blocked.
    val canSubmit = if (spec.requireTypedReason) typedLongEnough else {
        pickedLabel != null || spec.reasons.isEmpty()
    }
    val canContinue = submitted && continueDelay == 0

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 28.dp, vertical = 32.dp),
    ) {
        Text(
            text = "Why are you here?",
            color = Palette.TextPrimary,
            fontSize = 26.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = if (spec.requireTypedReason) {
                "Write it out — at least ${spec.minReasonChars} characters."
            } else {
                "One tap. No wrong answer."
            },
            color = Palette.TextMuted,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
        )

        if (spec.requireTypedReason) {
            OutlinedTextField(
                value = typed,
                onValueChange = { if (!submitted) typed = it },
                enabled = !submitted,
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
                    onClick = { if (!submitted) pickedLabel = reason },
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

        if (!submitted) {
            Button(
                onClick = { submitted = true },
                enabled = canSubmit,
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Palette.Mist,
                    contentColor = Palette.Ink,
                    disabledContainerColor = Palette.SurfaceAlt,
                    disabledContentColor = Palette.TextMuted,
                ),
            ) {
                Text("Done", fontSize = 16.sp, modifier = Modifier.padding(vertical = 6.dp))
            }
        } else {
            Button(
                onClick = {
                    onComplete(
                        InterventionResult(
                            reasonLabel = pickedLabel,
                            reasonText = typed.trim().takeIf { it.isNotEmpty() },
                            continuedAnyway = true,
                        )
                    )
                },
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
                    if (canContinue) "Continue anyway" else "Continue anyway ($continueDelay)",
                    fontSize = 16.sp,
                    modifier = Modifier.padding(vertical = 6.dp),
                )
            }
            Button(
                onClick = {
                    onComplete(
                        InterventionResult(
                            reasonLabel = pickedLabel,
                            reasonText = typed.trim().takeIf { it.isNotEmpty() },
                            continuedAnyway = false,
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Palette.Mist,
                    contentColor = Palette.Ink,
                ),
            ) {
                Text("Close the app", fontSize = 16.sp, modifier = Modifier.padding(vertical = 6.dp))
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
