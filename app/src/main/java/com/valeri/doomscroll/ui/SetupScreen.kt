package com.valeri.doomscroll.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.DisposableEffect
import com.valeri.doomscroll.ui.theme.Palette

@Composable
fun SetupScreen(modifier: Modifier = Modifier, onOpenLearnMode: () -> Unit) {
    val context = LocalContext.current
    var refreshKey by remember { mutableStateOf(0) }

    // Permissions are granted in Settings, so re-check every time we come back to the app.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshKey++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val accessibility = remember(refreshKey) { Permissions.isAccessibilityEnabled(context) }
    val overlay = remember(refreshKey) { Permissions.canDrawOverlays(context) }
    val usage = remember(refreshKey) { Permissions.hasUsageAccess(context) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Doomscroll", fontSize = 30.sp, fontWeight = FontWeight.SemiBold, color = Palette.TextPrimary)
        Text(
            "Three permissions, all granted in system settings. Nothing leaves your phone.",
            color = Palette.TextMuted,
            fontSize = 14.sp,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        PermissionCard(
            title = "Accessibility service",
            detail = "Lets the app tell a Reels feed apart from your DMs. Required.",
            granted = accessibility,
            actionLabel = "Open accessibility settings",
            onAction = { Permissions.openAccessibilitySettings(context) },
        )
        PermissionCard(
            title = "Display over other apps",
            detail = "Draws the pause screen on top of Instagram. Required.",
            granted = overlay,
            actionLabel = "Open overlay settings",
            onAction = { Permissions.openOverlaySettings(context) },
        )
        PermissionCard(
            title = "Usage access",
            detail = "Imports your existing screen-time history so the dashboard isn't empty.",
            granted = usage,
            actionLabel = "Open usage access settings",
            onAction = { Permissions.openUsageAccessSettings(context) },
        )
        PermissionCard(
            title = "Unrestricted battery",
            detail = "One UI puts background work to sleep. Set battery usage to Unrestricted.",
            granted = null,
            actionLabel = "Open battery settings",
            onAction = { Permissions.openBatterySettings(context) },
        )

        Button(
            onClick = onOpenLearnMode,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Palette.Mist,
                contentColor = Palette.Ink,
            ),
        ) {
            Text("Learn Mode", fontSize = 16.sp, modifier = Modifier.padding(vertical = 6.dp))
        }
        Text(
            "The built-in rules for Instagram and TikTok are educated guesses — those apps' " +
                "internal view ids change between releases. Learn Mode captures the real ones " +
                "from your phone.",
            color = Palette.TextMuted,
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun PermissionCard(
    title: String,
    detail: String,
    granted: Boolean?,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Palette.Surface),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(granted)
                Text(
                    title,
                    color = Palette.TextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(start = 10.dp),
                )
            }
            Text(
                detail,
                color = Palette.TextMuted,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
            TextButton(onClick = onAction, modifier = Modifier.padding(top = 4.dp)) {
                Text(if (granted == true) "Review" else actionLabel, color = Palette.Mist)
            }
        }
    }
}

@Composable
private fun StatusDot(granted: Boolean?) {
    val color = when (granted) {
        true -> Palette.Mist
        false -> Palette.Ember
        null -> Palette.TextMuted
    }
    androidx.compose.foundation.layout.Box(
        Modifier.size(10.dp).clip(CircleShape).background(color)
    )
}
