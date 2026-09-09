package com.valeri.doomscroll.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.valeri.doomscroll.ui.theme.Palette

@Composable
fun AppPickerScreen(
    modifier: Modifier = Modifier,
    onPick: (String) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    // Enumerating and rasterising every launcher icon is slow; keep it off the main thread.
    val apps by produceState(initialValue = emptyList<InstalledApp>(), context) {
        value = withContext(Dispatchers.IO) { AppCatalog.installedApps(context) }
    }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        TextButton(onClick = onBack, modifier = Modifier.padding(top = 4.dp)) {
            Text("Back", color = Palette.Mist)
        }
        Text("Add an app", fontSize = 26.sp, fontWeight = FontWeight.SemiBold, color = Palette.TextPrimary)
        Text(
            "Adding an app only starts watching it. Without a rule saying which of its screens " +
                "count as a feed, nothing will fire — add rules with Learn Mode.",
            color = Palette.TextMuted,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 6.dp, bottom = 12.dp),
        )

        if (apps.isEmpty()) {
            Text("Loading apps…", color = Palette.TextMuted, fontSize = 14.sp)
        }

        LazyColumn {
            items(apps, key = { it.packageName }) { app ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(app.packageName) }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    app.icon?.let {
                        Image(it, contentDescription = null, modifier = Modifier.size(34.dp))
                    }
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(app.label, color = Palette.TextPrimary, fontSize = 15.sp)
                        Text(
                            app.packageName,
                            color = Palette.TextMuted,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
        }
    }
}
