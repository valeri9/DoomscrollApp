package com.valeri.doomscroll.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.ui.graphics.Color
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
private val DoomscrollRest = ChartPalette.Teal.copy(alpha = 0.35f)

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
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionCheck++
                // importIfNeeded() below only runs once per process, so without this "today" is
                // only as fresh as the last 30-minute background sync.
                vm.refresh()
            }
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
            StatCard(Modifier.padding(top = 12.dp)) {
                Text("Usage access not granted", color = Palette.TextPrimary, fontSize = 15.sp)
                Text(
                    "Time in each app comes from the OS. Without this permission only doomscroll " +
                        "time and interventions can be shown.",
                    color = Palette.TextMuted,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
                TextButton(onClick = onOpenSetup) { Text("Grant it", color = Palette.Mist) }
            }
        }

        HeroTile(state, importing)

        SectionHeader("Last 7 days vs the 7 before")
        WeekComparison(state)

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
        ) {
            Text(
                "Everything below covers the last",
                color = Palette.TextMuted,
                fontSize = 12.sp,
                modifier = Modifier.weight(1f),
            )
            RangeChips(state.range, vm::setRange)
        }

        SectionHeader("Daily")
        DailyCard(state)

        SectionHeader("When you get caught")
        HourCard(state)

        SectionHeader("After the breath")
        AfterBreathCard(state)

        SectionHeader("Per app")
        StatCard {
            if (state.apps.isEmpty()) EmptyChart("Nothing recorded in this range yet.")
            val maxMs = state.apps.maxOfOrNull { maxOf(it.appMs, it.doomscrollMs) }?.coerceAtLeast(1L) ?: 1L
            state.apps.forEach { AppRow(it, maxMs) }
        }
    }
}

@Composable
private fun HeroTile(state: DashboardState, importing: Boolean) {
    StatCard(Modifier.padding(top = 12.dp)) {
        Text(
            if (importing) "Importing history…" else "Doomscrolling today",
            color = Palette.TextMuted,
            fontSize = 13.sp,
        )
        Text(
            formatDuration(state.doomscrollTodayMs),
            color = Palette.TextPrimary,
            fontSize = 40.sp,
            fontWeight = FontWeight.Light,
            modifier = Modifier.padding(top = 2.dp),
        )
        Text(
            "of ${formatDuration(state.appTodayMs)} in watched apps · ${formatDuration(state.phoneTodayMs)} whole phone",
            color = Palette.TextMuted,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 4.dp),
        )
        if (state.caughtToday > 0) {
            Text(
                "Caught ${times(state.caughtToday)} today",
                color = Palette.TextPrimary,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        if (state.trackingSince == null || state.trackingSince == state.today) {
            Text(
                "Counting started when this update was installed today, so doomscrolling earlier " +
                    "today and on previous days isn't included. Tomorrow is the first full day.",
                color = Palette.TextMuted,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

@Composable
private fun WeekComparison(state: DashboardState) {
    StatCard {
        ComparisonRow(
            label = "Doomscrolling",
            current = formatDuration(state.weekDoomscroll.current),
            previous = state.weekDoomscroll.previous?.let(::formatDuration),
            change = state.weekDoomscroll.previous?.let { changeText(state.weekDoomscroll.current, it) },
            missing = "not tracked yet",
        )
        ComparisonRow(
            label = "In watched apps",
            current = formatDuration(state.weekAppTime.current),
            previous = state.weekAppTime.previous?.let(::formatDuration),
            change = state.weekAppTime.previous?.let { changeText(state.weekAppTime.current, it) },
        )
        ComparisonRow(
            label = "Times caught",
            current = state.weekCaught.current.toString(),
            previous = state.weekCaught.previous?.toString(),
            change = state.weekCaught.previous?.let { changeText(state.weekCaught.current.toLong(), it.toLong()) },
        )
        val rateNow = state.weekCloseRate.current
        val rateBefore = state.weekCloseRate.previous
        ComparisonRow(
            label = "Closed the app",
            current = rateNow?.let(::percent) ?: "—",
            previous = rateBefore?.let(::percent),
            change = if (rateNow != null && rateBefore != null) pointsText(rateNow, rateBefore) else null,
        )
        Text(
            "Full days only — today joins the comparison once it's over.",
            color = Palette.TextMuted,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

@Composable
private fun ComparisonRow(
    label: String,
    current: String,
    previous: String?,
    change: String?,
    missing: String = "no data",
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = Palette.TextPrimary, fontSize = 14.sp)
            Text(
                previous?.let { "was $it" } ?: missing,
                color = Palette.TextMuted,
                fontSize = 12.sp,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(current, color = Palette.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Light)
            if (change != null) Text(change, color = Palette.TextMuted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun DailyCard(state: DashboardState) {
    StatCard {
        DailyBarChart(
            data = state.days.map {
                BarDatum(
                    label = it.date.format(DAY_LABEL),
                    value = it.appMs.toFloat(),
                    part = it.doomscrollMs.toFloat(),
                )
            },
            stacked = true,
            readout = { d ->
                "${d.label} · ${formatDuration(d.part.toLong())} doomscrolling of ${formatDuration(d.value.toLong())}"
            },
        )
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LegendLabel(ChartPalette.Teal, "Doomscrolling")
            LegendLabel(DoomscrollRest, "Rest of app time")
        }
    }
}

@Composable
private fun HourCard(state: DashboardState) {
    StatCard {
        val total = state.caughtByHour.sum()
        if (total == 0) {
            EmptyChart("No interventions in this range yet.")
            return@StatCard
        }
        val peak = state.caughtByHour.max()
        DailyBarChart(
            data = state.caughtByHour.mapIndexed { hour, count ->
                BarDatum(label = hourLabel(hour), value = count.toFloat(), emphasised = count == peak)
            },
            readout = { d ->
                val hour = d.label.substringBefore(':').toInt()
                "${hourLabel(hour)}–${hourLabel((hour + 1) % 24)} · ${times(d.value.toInt())}"
            },
            height = 110.dp,
        )
        val peakHour = state.caughtByHour.indexOf(peak)
        Text(
            "Most often ${hourLabel(peakHour)}–${hourLabel((peakHour + 1) % 24)} " +
                "(${times(peak)} of $total)",
            color = Palette.TextPrimary,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

@Composable
private fun AfterBreathCard(state: DashboardState) {
    StatCard {
        val rate = state.closeRate
        if (rate == null) {
            EmptyChart("No finished interventions in this range yet.")
            return@StatCard
        }
        Text(
            "${percent(rate)} closed the app",
            color = Palette.TextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Light,
        )
        SplitBar(
            leftFraction = rate,
            leftColor = ChartPalette.Teal,
            rightColor = ChartPalette.Amber,
            modifier = Modifier.padding(top = 10.dp),
        )
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            LegendLabel(ChartPalette.Teal, "Closed ${state.closed}")
            Box(Modifier.weight(1f))
            LegendLabel(ChartPalette.Amber, "Continued ${state.continued}")
        }
        if (state.hops > 0) {
            Text(
                "${times(state.hops).replaceFirstChar { it.uppercase() }} you reopened or switched apps " +
                    "right after closing, and got stopped again.",
                color = Palette.TextMuted,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 12.dp),
            )
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
private fun AppRow(app: AppStat, maxMs: Long) {
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
            Text(formatDuration(app.appMs), color = Palette.TextPrimary, fontSize = 14.sp)
        }
        DoomscrollShareBar(app, maxMs, Modifier.padding(top = 6.dp))
        val details = buildList {
            add("${formatDuration(app.doomscrollMs)} doomscrolling")
            if (app.caught > 0) add("caught ${times(app.caught)}")
            app.closeRate?.let { add("closed ${percent(it)}") }
        }
        Text(
            details.joinToString(" · "),
            color = Palette.TextMuted,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** App time as a track, with its doomscroll part solid — the same encoding as the daily chart. */
@Composable
private fun DoomscrollShareBar(app: AppStat, maxMs: Long, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth()) {
        HorizontalBar(fraction = maxOf(app.appMs, app.doomscrollMs).toFloat() / maxMs, color = DoomscrollRest)
        if (app.doomscrollMs > 0) {
            HorizontalBar(fraction = app.doomscrollMs.toFloat() / maxMs, color = ChartPalette.Teal, track = false)
        }
    }
}

@Composable
private fun StatCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Palette.Surface),
    ) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

@Composable
private fun LegendLabel(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.foundation.Canvas(Modifier.size(8.dp)) { drawCircle(color) }
        Text(text, color = Palette.TextMuted, fontSize = 12.sp, modifier = Modifier.padding(start = 6.dp))
    }
}

private fun hourLabel(hour: Int) = "%02d:00".format(hour)

private fun times(n: Int) = if (n == 1) "once" else "$n times"

private fun percent(fraction: Float) = "${(fraction * 100).roundToInt()}%"

/** "30% less" / "12% more" / "about the same" — words, so the direction never rests on colour. */
private fun changeText(current: Long, previous: Long): String {
    if (previous == 0L) return if (current == 0L) "same" else "up from nothing"
    val pct = ((current - previous) * 100f / previous).roundToInt()
    return when {
        abs(pct) < 3 -> "about the same"
        pct < 0 -> "${abs(pct)}% less"
        else -> "$pct% more"
    }
}

private fun pointsText(current: Float, previous: Float): String {
    val pts = ((current - previous) * 100).roundToInt()
    return when {
        abs(pts) < 2 -> "about the same"
        pts > 0 -> "$pts pts more often"
        else -> "${abs(pts)} pts less often"
    }
}

fun formatDuration(ms: Long): String {
    val totalMinutes = ms / 60_000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        totalMinutes > 0 -> "${minutes}m"
        // A few seconds of doomscrolling flooring to "0m" reads as "nothing was measured".
        ms >= 1_000 -> "${ms / 1_000}s"
        else -> "0m"
    }
}
