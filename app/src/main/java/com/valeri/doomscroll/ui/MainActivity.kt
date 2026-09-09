package com.valeri.doomscroll.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.valeri.doomscroll.ui.theme.DoomscrollTheme
import com.valeri.doomscroll.ui.theme.Palette
import com.valeri.doomscroll.usage.UsageSyncWorker

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Idempotent (KEEP policy), so it's safe to call on every launch.
        UsageSyncWorker.schedule(this)
        setContent {
            DoomscrollTheme(darkTheme = true) {
                Surface { RootScreen() }
            }
        }
    }
}

private enum class Tab(val label: String) { DASHBOARD("Today"), SETTINGS("Settings"), SETUP("Setup") }

/** Screens pushed on top of a tab. */
private sealed interface Overlay {
    data class AppConfig(val packageName: String) : Overlay
    data object AppPicker : Overlay
    data object Learn : Overlay
}

@Composable
private fun RootScreen() {
    var tab by remember { mutableStateOf(Tab.DASHBOARD) }
    var overlay by remember { mutableStateOf<Overlay?>(null) }

    val settingsVm: SettingsViewModel = viewModel()
    val dashboardVm: DashboardViewModel = viewModel()

    BackHandler(enabled = overlay != null) { overlay = null }

    Scaffold(
        bottomBar = {
            if (overlay == null) {
                NavigationBar(containerColor = Palette.Surface) {
                    Tab.entries.forEach { entry ->
                        NavigationBarItem(
                            selected = tab == entry,
                            onClick = { tab = entry },
                            icon = {
                                Icon(
                                    when (entry) {
                                        Tab.DASHBOARD -> Icons.Default.BarChart
                                        Tab.SETTINGS -> Icons.Default.Tune
                                        Tab.SETUP -> Icons.Default.Settings
                                    },
                                    contentDescription = entry.label,
                                )
                            },
                            label = { Text(entry.label, fontSize = 11.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Palette.Ink,
                                selectedTextColor = Palette.Mist,
                                indicatorColor = Palette.Mist,
                                unselectedIconColor = Palette.TextMuted,
                                unselectedTextColor = Palette.TextMuted,
                            ),
                        )
                    }
                }
            }
        },
    ) { inner ->
        val modifier = Modifier.padding(inner)
        when (val current = overlay) {
            is Overlay.AppConfig -> AppConfigScreen(
                vm = settingsVm,
                packageName = current.packageName,
                modifier = modifier,
                onBack = { overlay = null },
            )

            Overlay.AppPicker -> AppPickerScreen(
                modifier = modifier,
                onPick = { settingsVm.addApp(it); overlay = Overlay.AppConfig(it) },
                onBack = { overlay = null },
            )

            Overlay.Learn -> LearnModeScreen(
                vm = settingsVm,
                modifier = modifier,
                onBack = { overlay = null },
            )

            null -> when (tab) {
                Tab.DASHBOARD -> DashboardScreen(
                    vm = dashboardVm,
                    modifier = modifier,
                    onOpenSetup = { tab = Tab.SETUP },
                )

                Tab.SETTINGS -> SettingsScreen(
                    vm = settingsVm,
                    modifier = modifier,
                    onOpenApp = { overlay = Overlay.AppConfig(it) },
                    onAddApp = { overlay = Overlay.AppPicker },
                )

                Tab.SETUP -> SetupScreen(
                    modifier = modifier,
                    onOpenLearnMode = { overlay = Overlay.Learn },
                )
            }
        }
    }
}
