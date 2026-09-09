package com.valeri.doomscroll.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.valeri.doomscroll.ui.theme.ChartPalette
import com.valeri.doomscroll.ui.theme.Palette
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt

private val DAY_LABEL = DateTimeFormatter.ofPattern("d MMM")

@Composable
fun DashboardScreen(
    vm: DashboardViewModel,
    modifier: Modifier = Modifier,
    onOpenSetup: () -> Unit,
) {
    val context = LocalContext.current
    val state by vm.state.collectAsStateWithLifecycle()
    val importing by vm.importing.collectAsStateWithLifecycle()
    var permissionCheck by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) permissionCheck++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val hasUsageAccess = remember(permissionCheck) { Permissions.hasUsageAccess(context) }

    LaunchedEffect(hasUsageAccess) {
        if (hasUsageAccess) vm.importIfNeeded()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp),
    ) {
        Text(
            "Today",
            fontSize = 28.sp,
            fontWeight = FontWeight.SemiBold,
            color = Palette.TextPrimary,
            modifier = Modifier.padding(top = 12.dp),
        )

        if (!hasUsageAccess) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                colors = CardDefaults.cardColors(containerColor = Palette.Surface),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Usage access not granted", color = Palette.TextPrimary, fontSize = 15.sp)
                    Text(
                        "Screen-time history comes from the OS. Without this permission the " +
                            "dashboard can only show interventions.",
                        color = Palette.TextMuted,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    TextButton(onClick = onOpenSetup) { Text("Grant it", color = Palette.Mist) }
                }
            }
        }

        HeroTile(state, importing)

        SectionHeader("Screen time")
        RangeChips(state.range, vm::setRange)
        Card(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            colors = CardDefaults.cardColors(containerColor = Palette.Surface),
        ) {
            Column(Modifier.padding(16.dp)) {
                DailyBarChart(
                    data = state.days.map {
                        BarDatum(
                            label = it.date.format(DAY_LABEL),
                            value = it.totalMs.toFloat(),
                            emphasised = it.date == state.days.lastOrNull()?.date,
                        )
                    },
                    valueFormatter = { formatDuration(it.toLong()) },
                )
                Text(
                    "Total phone screen time per day",
                    color = Palette.TextMuted,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        SectionHeader("Per app")
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Palette.Surface),
        ) {
            Column(Modifier.padding(16.dp)) {
                if (state.apps.isEmpty()) {
                    EmptyChart("Nothing recorded in this range yet.")
                }
                val maxMs = state.apps.maxOfOrNull { it.totalMs }?.coerceAtLeast(1L) ?: 1L
                state.apps.take(8).forEach { app ->
                    AppRow(app, maxMs)
                }
            }
        }

        SectionHeader("Doomscrolling")
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Palette.Surface),
        ) {
            Column(Modifier.padding(16.dp)) {
                Row {
                    StatBlock("Interventions", state.totalInterventions.toString(), Modifier.weight(1f))
                    StatBlock(
                        "Per day",
                        if (state.range > 0) {
                            "%.1f".format(state.totalInterventions.toFloat() / state.range)
                        } else "0",
                        Modifier.weight(1f),
                    )
                }

                if (state.totalInterventions > 0) {
                    val nightFraction = state.nightInterventions.toFloat() / state.totalInterventions
                    Text(
                        "Night vs day",
                        color = Palette.TextMuted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 16.dp, bottom = 6.dp),
                    )
                    SplitBar(nightFraction)
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                        LegendLabel(ChartPalette.Amber, "Night ${state.nightInterventions}")
                        Box(Modifier.weight(1f))
                        LegendLabel(
                            ChartPalette.Teal,
                            "Day ${state.totalInterventions - state.nightInterventions}",
                        )
                    }
                }

                if (state.reasons.isNotEmpty()) {
                    Text(
                        "Most common reasons",
                        color = Palette.TextMuted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 18.dp, bottom = 6.dp),
                    )
                    val topCount = state.reasons.first().count.coerceAtLeast(1)
                    state.reasons.take(5).forEach { reason ->
                        Column(Modifier.padding(vertical = 5.dp)) {
                            Row {
                                Text(
                                    reason.label,
                                    color = Palette.TextPrimary,
                                    fontSize = 13.sp,
                                    modifier = Modifier.weight(1f),
                                )
                                Text("${reason.count}", color = Palette.TextMuted, fontSize = 13.sp)
                            }
                            HorizontalBar(
                                fraction = reason.count.toFloat() / topCount,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                } else {
                    EmptyChart("No interventions logged yet.")
                }
            }
        }
    }
}

@Composable
private fun HeroTile(state: DashboardState, importing: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        colors = CardDefaults.cardColors(containerColor = Palette.Surface),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(
                if (importing) "Importing history…" else "Screen time today",
                color = Palette.TextMuted,
                fontSize = 13.sp,
            )
            Text(
                formatDuration(state.todayMs),
                color = Palette.TextPrimary,
                fontSize = 40.sp,
                fontWeight = FontWeight.Light,
                modifier = Modifier.padding(top = 2.dp),
            )
            if (state.averageMs > 0) {
                val pct = (state.deltaFraction * 100).roundToInt()
                Text(
                    text = when {
                        pct > 0 -> "$pct% above your ${state.range}-day average of ${formatDuration(state.averageMs)}"
                        pct < 0 -> "${abs(pct)}% below your ${state.range}-day average of ${formatDuration(state.averageMs)}"
                        else -> "In line with your ${state.range}-day average"
                    },
                    color = if (pct > 0) ChartPalette.Amber else Palette.TextMuted,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun RangeChips(range: Int, onChange: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(7, 30).forEach { days ->
            FilterChip(
                selected = range == days,
                onClick = { onChange(days) },
                label = { Text("$days days", fontSize = 13.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = Palette.SurfaceAlt,
                    labelColor = Palette.TextMuted,
                    selectedContainerColor = Palette.Mist,
                    selectedLabelColor = Palette.Ink,
                ),
            )
        }
    }
}

@Composable
private fun AppRow(app: AppBreakdown, maxMs: Long) {
    val context = LocalContext.current
    val icon = AppCatalog.iconFor(context, app.packageName)
    Column(Modifier.padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Image(icon, contentDescription = null, modifier = Modifier.size(22.dp))
            }
            Text(
                AppCatalog.labelFor(context, app.packageName),
                color = Palette.TextPrimary,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f).padding(start = if (icon != null) 10.dp else 0.dp),
            )
            Text(formatDuration(app.totalMs), color = Palette.TextPrimary, fontSize = 14.sp)
        }
        HorizontalBar(
            fraction = app.totalMs.toFloat() / maxMs,
            modifier = Modifier.padding(top = 6.dp),
        )
        if (app.interventions > 0) {
            Text(
                "${app.interventions} doomscroll ${if (app.interventions == 1) "session" else "sessions"} caught",
                color = ChartPalette.Amber,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun StatBlock(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(value, color = Palette.TextPrimary, fontSize = 26.sp, fontWeight = FontWeight.Light)
        Text(label, color = Palette.TextMuted, fontSize = 12.sp)
    }
}

@Composable
private fun LegendLabel(color: androidx.compose.ui.graphics.Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.foundation.Canvas(Modifier.size(8.dp)) { drawCircle(color) }
        Text(text, color = Palette.TextMuted, fontSize = 12.sp, modifier = Modifier.padding(start = 6.dp))
    }
}

fun formatDuration(ms: Long): String {
    val totalMinutes = ms / 60_000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        totalMinutes > 0 -> "${minutes}m"
        else -> "0m"
    }
}
