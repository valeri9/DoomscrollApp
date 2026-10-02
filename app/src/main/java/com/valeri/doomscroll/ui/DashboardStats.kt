package com.valeri.doomscroll.ui

import com.valeri.doomscroll.data.db.DailyAppUsageEntity
import com.valeri.doomscroll.data.db.DoomscrollTimeEntity
import com.valeri.doomscroll.data.db.InterventionEntity
import com.valeri.doomscroll.data.repo.DoomscrollRepository
import java.time.LocalDate

/** A figure for the last 7 full days next to the 7 before. previous is null when there's no data to compare against. */
data class Comparison<T>(val current: T, val previous: T?)

data class DayStat(val date: LocalDate, val appMs: Long, val doomscrollMs: Long)

data class AppStat(
    val packageName: String,
    val appMs: Long,
    val doomscrollMs: Long,
    val caught: Int,
    val closed: Int,
    val continued: Int,
) {
    val closeRate: Float? get() = rate(closed, continued)
}

data class DashboardState(
    val range: Int = 7,
    val today: LocalDate = LocalDate.now(),
    val doomscrollTodayMs: Long = 0L,
    val appTodayMs: Long = 0L,
    val phoneTodayMs: Long = 0L,
    val caughtToday: Int = 0,
    /** First day doomscroll time was recorded; null until the service has counted anything. */
    val trackingSince: LocalDate? = null,
    val weekDoomscroll: Comparison<Long> = Comparison(0L, null),
    val weekAppTime: Comparison<Long> = Comparison(0L, null),
    val weekCaught: Comparison<Int> = Comparison(0, null),
    val weekCloseRate: Comparison<Float?> = Comparison(null, null),
    /** Oldest first, ending today. */
    val days: List<DayStat> = emptyList(),
    /** Interventions per hour of day, 0..23, over the range. */
    val caughtByHour: List<Int> = List(24) { 0 },
    val closed: Int = 0,
    val continued: Int = 0,
    /** Prompts forced by reopening or switching apps right after tapping Close. */
    val hops: Int = 0,
    val apps: List<AppStat> = emptyList(),
) {
    val closeRate: Float? get() = rate(closed, continued)
}

private fun rate(closed: Int, continued: Int): Float? =
    if (closed + continued == 0) null else closed.toFloat() / (closed + continued)

/**
 * Turns raw rows into everything the dashboard shows. Pure, so it's tested without a device.
 *
 * "Watched apps" are the monitored packages; whole-phone time only appears as today's total.
 * The weekly comparison uses the 7 full days ending yesterday against the 7 before them: putting
 * today's partial day in one window and only full days in the other would make every week look
 * better than it was until midnight.
 */
object DashboardStats {

    fun build(
        today: LocalDate,
        range: Int,
        watched: Set<String>,
        usage: List<DailyAppUsageEntity>,
        doomscroll: List<DoomscrollTimeEntity>,
        interventions: List<InterventionEntity>,
        trackingSince: LocalDate?,
    ): DashboardState {
        val usageByDay = usage.groupBy { LocalDate.parse(it.localDate) }
        val appUsage = usage.filter { it.packageName in watched }
        val appMsByDay = appUsage.groupBy { LocalDate.parse(it.localDate) }.mapValues { (_, rows) -> rows.sumOf { it.foregroundMs } }
        val doomMsByDay = doomscroll.groupBy { LocalDate.parse(it.localDate) }.mapValues { (_, rows) -> rows.sumOf { it.ms } }
        val caughtByDay = interventions.groupBy { LocalDate.parse(it.localDate) }

        val thisWeek = today.minusDays(7)..today.minusDays(1)
        val lastWeek = today.minusDays(14)..today.minusDays(8)
        fun sumMs(byDay: Map<LocalDate, Long>, window: ClosedRange<LocalDate>) =
            byDay.filterKeys { it in window }.values.sum()
        fun inWindow(window: ClosedRange<LocalDate>) =
            interventions.filter { LocalDate.parse(it.localDate) in window }

        // Doomscroll time only compares fairly once tracking covered the whole earlier week.
        val doomPrevious = sumMs(doomMsByDay, lastWeek).takeIf { trackingSince != null && trackingSince <= lastWeek.start }
        val appPrevious = sumMs(appMsByDay, lastWeek).takeIf { appMsByDay.keys.any { it in lastWeek } }
        val firstIntervention = interventions.minOfOrNull { LocalDate.parse(it.localDate) }
        val caughtPrevious = inWindow(lastWeek).size.takeIf { firstIntervention != null && firstIntervention <= lastWeek.endInclusive }

        val rangeStart = today.minusDays((range - 1).toLong())
        val inRange = interventions.filter { LocalDate.parse(it.localDate) >= rangeStart }
        val completed = inRange.filter { it.completedAt != null }

        val hours = MutableList(24) { 0 }
        inRange.forEach { hours[it.hourOfDay.coerceIn(0, 23)]++ }

        val appMsInRange = appUsage.filter { LocalDate.parse(it.localDate) >= rangeStart }
            .groupBy { it.packageName }.mapValues { (_, rows) -> rows.sumOf { it.foregroundMs } }
        val doomMsInRange = doomscroll.filter { LocalDate.parse(it.localDate) >= rangeStart }
            .groupBy { it.packageName }.mapValues { (_, rows) -> rows.sumOf { it.ms } }
        val caughtInRange = inRange.groupBy { it.packageName }
        val apps = (watched + caughtInRange.keys + doomMsInRange.keys).map { pkg ->
            val caught = caughtInRange[pkg].orEmpty()
            AppStat(
                packageName = pkg,
                appMs = appMsInRange[pkg] ?: 0L,
                doomscrollMs = doomMsInRange[pkg] ?: 0L,
                caught = caught.size,
                closed = caught.count { it.completedAt != null && !it.continuedAnyway },
                continued = caught.count { it.completedAt != null && it.continuedAnyway },
            )
        }.filter { it.appMs > 0 || it.doomscrollMs > 0 || it.caught > 0 }
            .sortedWith(compareByDescending<AppStat> { it.doomscrollMs }.thenByDescending { it.appMs })

        return DashboardState(
            range = range,
            today = today,
            doomscrollTodayMs = doomMsByDay[today] ?: 0L,
            appTodayMs = appMsByDay[today] ?: 0L,
            phoneTodayMs = usageByDay[today].orEmpty().sumOf { it.foregroundMs },
            caughtToday = caughtByDay[today].orEmpty().size,
            trackingSince = trackingSince,
            weekDoomscroll = Comparison(sumMs(doomMsByDay, thisWeek), doomPrevious),
            weekAppTime = Comparison(sumMs(appMsByDay, thisWeek), appPrevious),
            weekCaught = Comparison(inWindow(thisWeek).size, caughtPrevious),
            weekCloseRate = Comparison(closeRateOf(inWindow(thisWeek)), closeRateOf(inWindow(lastWeek))),
            days = (0 until range).map { offset ->
                val date = rangeStart.plusDays(offset.toLong())
                DayStat(date, appMsByDay[date] ?: 0L, doomMsByDay[date] ?: 0L)
            },
            caughtByHour = hours,
            closed = completed.count { !it.continuedAnyway },
            continued = completed.count { it.continuedAnyway },
            hops = inRange.count {
                it.contextLabel == DoomscrollRepository.CONTEXT_REOPENED ||
                    it.contextLabel == DoomscrollRepository.CONTEXT_SWITCHED
            },
            apps = apps,
        )
    }

    private fun closeRateOf(rows: List<InterventionEntity>): Float? {
        val completed = rows.filter { it.completedAt != null }
        return rate(completed.count { !it.continuedAnyway }, completed.count { it.continuedAnyway })
    }
}
