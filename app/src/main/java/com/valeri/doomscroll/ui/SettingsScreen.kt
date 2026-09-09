package com.valeri.doomscroll.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.foundation.Image
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.valeri.doomscroll.ui.theme.Palette

@Composable
fun SettingsScreen(
    vm: SettingsViewModel,
    modifier: Modifier = Modifier,
    onOpenApp: (String) -> Unit,
    onAddApp: () -> Unit,
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val apps by vm.monitoredApps.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp),
    ) {
        Text(
            "Settings",
            fontSize = 28.sp,
            fontWeight = FontWeight.SemiBold,
            color = Palette.TextPrimary,
            modifier = Modifier.padding(top = 12.dp),
        )

        SectionHeader("Monitoring")
        SettingsCard {
            ToggleRow(
                title = "Interventions enabled",
                subtitle = "Master switch. Detection stops entirely when off.",
                checked = settings.enabled,
                onChange = vm::setEnabled,
            )
        }

        SectionHeader("Watched apps")
        SettingsCard {
            if (apps.isEmpty()) {
                Text(
                    "No apps yet.",
                    color = Palette.TextMuted,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(16.dp),
                )
            }
            apps.forEach { app ->
                val label = AppCatalog.labelFor(context, app.packageName)
                val icon = AppCatalog.iconFor(context, app.packageName)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenApp(app.packageName) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (icon != null) {
                        Image(icon, contentDescription = null, modifier = Modifier.size(28.dp))
                    }
                    Column(Modifier.weight(1f).padding(start = if (icon != null) 12.dp else 0.dp)) {
                        Text(label, color = Palette.TextPrimary, fontSize = 15.sp)
                        Text(
                            if (app.enabled) "Watching" else "Paused",
                            color = if (app.enabled) Palette.Mist else Palette.TextMuted,
                            fontSize = 12.sp,
                        )
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = Palette.TextMuted,
                    )
                }
            }
            TextButton(onClick = onAddApp, modifier = Modifier.padding(start = 8.dp)) {
                Text("Add an app", color = Palette.Mist)
            }
        }

        SectionHeader("Night window")
        SettingsCard {
            TimeRangeRow(
                startMinute = settings.nightStartMinute,
                endMinute = settings.nightEndMinute,
                onChange = vm::setNightWindow,
            )
            StepperRow(
                title = "Night breathing",
                subtitle = "Base length before escalation",
                value = settings.nightBreathingSeconds,
                unit = "s",
                range = 10..180,
                step = 5,
                onChange = vm::setNightBreathing,
            )
            StepperRow(
                title = "Escalation per reopen",
                subtitle = "Added for each time you come back tonight",
                value = settings.nightEscalationSeconds,
                unit = "s",
                range = 0..60,
                step = 5,
                onChange = vm::setNightEscalation,
            )
            StepperRow(
                title = "Continue delay",
                subtitle = "Continue stays greyed out this long after your reason",
                value = settings.nightContinueDelaySeconds,
                unit = "s",
                range = 0..60,
                onChange = vm::setNightContinueDelay,
            )
            StepperRow(
                title = "Minimum reason length",
                subtitle = "Characters required in the typed sentence",
                value = settings.nightMinReasonChars,
                unit = "",
                range = 0..120,
                step = 5,
                onChange = vm::setNightMinChars,
            )
        }

        SectionHeader("Daytime")
        SettingsCard {
            StepperRow(
                title = "Breathing length",
                value = settings.dayBreathingSeconds,
                unit = "s",
                range = 3..120,
                onChange = vm::setDayBreathing,
            )
        }

        SectionHeader("Trigger sensitivity")
        SettingsCard {
            StepperRow(
                title = "Cooldown",
                subtitle = "Time backgrounded before it can fire again",
                value = settings.cooldownMinutes,
                unit = "m",
                range = 1..120,
                onChange = vm::setCooldown,
            )
            StepperRow(
                title = "Scrolls before triggering",
                subtitle = "Stops one flick past the search bar counting",
                value = settings.minScrollEvents,
                unit = "",
                range = 1..30,
                onChange = vm::setMinScrolls,
            )
            StepperRow(
                title = "Minimum time in app",
                value = settings.minDwellSeconds,
                unit = "s",
                range = 0..120,
                onChange = vm::setMinDwell,
            )
        }
    }
}

@Composable
private fun TimeRangeRow(startMinute: Int, endMinute: Int, onChange: (Int, Int) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text("Night hours", color = Palette.TextPrimary, fontSize = 15.sp)
        Text(
            "${formatMinutes(startMinute)} — ${formatMinutes(endMinute)}",
            color = Palette.Ember,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 2.dp),
        )
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HalfHourStepper("Start", startMinute) { onChange(it, endMinute) }
            HalfHourStepper("End", endMinute) { onChange(startMinute, it) }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.HalfHourStepper(
    label: String,
    minute: Int,
    onChange: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.weight(1f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label, color = Palette.TextMuted, fontSize = 12.sp)
        TextButton(onClick = { onChange(((minute - 30) + 1440) % 1440) }) {
            Text("−", color = Palette.Mist, fontSize = 18.sp)
        }
        TextButton(onClick = { onChange((minute + 30) % 1440) }) {
            Text("+", color = Palette.Mist, fontSize = 18.sp)
        }
    }
}

fun formatMinutes(minuteOfDay: Int): String =
    "%02d:%02d".format(minuteOfDay / 60, minuteOfDay % 60)
