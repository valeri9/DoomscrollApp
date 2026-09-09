package com.valeri.doomscroll.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.valeri.doomscroll.classifier.MatchType
import com.valeri.doomscroll.classifier.RuleKind
import com.valeri.doomscroll.learn.CapturedNode
import com.valeri.doomscroll.learn.LearnMode
import com.valeri.doomscroll.ui.theme.Palette
import kotlinx.coroutines.delay

private const val CAPTURE_WINDOW_MS = 15_000L

/**
 * Captures the real view ids from whatever is on screen, so the shipped guesses for
 * Instagram and TikTok can be replaced with ids that actually exist on this device.
 */
@Composable
fun LearnModeScreen(vm: SettingsViewModel, modifier: Modifier = Modifier, onBack: () -> Unit) {
    val capture by LearnMode.lastCapture.collectAsStateWithLifecycle()
    var countdown by remember { mutableIntStateOf(0) }
    var added by remember { mutableStateOf(emptySet<String>()) }

    LaunchedEffect(countdown) {
        if (countdown > 0) {
            delay(1000)
            countdown--
            if (countdown == 0) LearnMode.arm(CAPTURE_WINDOW_MS)
        }
    }

    Column(modifier = modifier.fillMaxSize().padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("Back", color = Palette.Mist) }
        }
        Text("Learn Mode", fontSize = 26.sp, fontWeight = FontWeight.SemiBold, color = Palette.TextPrimary)
        Text(
            "Start a capture, switch to the app, and land on the screen you want it to " +
                "recognise. The next screen change within 15 seconds gets recorded.",
            color = Palette.TextMuted,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 6.dp, bottom = 14.dp),
        )

        Button(
            onClick = { countdown = 5 },
            enabled = countdown == 0,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Palette.Mist,
                contentColor = Palette.Ink,
            ),
        ) {
            Text(
                if (countdown > 0) "Switch apps now — $countdown" else "Capture in 5 seconds",
                fontSize = 16.sp,
                modifier = Modifier.padding(vertical = 6.dp),
            )
        }

        val result = capture
        if (result == null) {
            Text(
                "No capture yet.",
                color = Palette.TextMuted,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 20.dp),
            )
        } else {
            Text(
                "${result.packageName} — ${result.nodes.size} ids",
                color = Palette.TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 20.dp),
            )
            Text(
                result.windowClassName.ifBlank { "(no window class)" },
                color = Palette.TextMuted,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(bottom = 10.dp),
            )
            Text(
                "Tap Doomscroll or Legit to turn an id into a rule for this app.",
                color = Palette.TextMuted,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(result.nodes) { node ->
                    NodeRow(
                        node = node,
                        onAdd = { kind ->
                            // Teaching it about an app implies watching that app.
                            vm.addApp(result.packageName)
                            vm.addRule(result.packageName, kind, MatchType.VIEW_ID, node.shortId)
                            added = added + node.shortId
                        },
                        alreadyAdded = node.shortId in added,
                    )
                }
            }
        }
    }
}

@Composable
private fun NodeRow(node: CapturedNode, onAdd: (RuleKind) -> Unit, alreadyAdded: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Palette.Surface),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                node.shortId,
                color = if (node.scrollable) Palette.Mist else Palette.TextPrimary,
                fontSize = 14.sp,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                buildString {
                    append(node.className.substringAfterLast('.'))
                    if (node.scrollable) append("  · scrollable")
                    if (node.editable) append("  · text input")
                    append("  · depth ${node.depth}")
                },
                color = Palette.TextMuted,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 3.dp),
            )
            if (alreadyAdded) {
                Text(
                    "Added",
                    color = Palette.Mist,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { onAdd(RuleKind.DOOMSCROLL) }) {
                        Text("Doomscroll", color = Palette.Ember, fontSize = 13.sp)
                    }
                    TextButton(onClick = { onAdd(RuleKind.LEGIT) }) {
                        Text("Legit", color = Palette.Mist, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}
