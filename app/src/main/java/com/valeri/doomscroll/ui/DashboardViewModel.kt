package com.valeri.doomscroll.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.valeri.doomscroll.data.db.DailyTotal
import com.valeri.doomscroll.data.db.PackageCount
import com.valeri.doomscroll.data.db.PackageTotal
import com.valeri.doomscroll.data.db.ReasonCount
import com.valeri.doomscroll.data.repo.DoomscrollRepository
import com.valeri.doomscroll.usage.UsageStatsImporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

data class DayUsage(val date: LocalDate, val totalMs: Long)

data class AppBreakdown(
    val packageName: String,
    val totalMs: Long,
    val interventions: Int,
)

data class DashboardState(
    val range: Int = 7,
    val days: List<DayUsage> = emptyList(),
    val apps: List<AppBreakdown> = emptyList(),
    val reasons: List<ReasonCount> = emptyList(),
    val totalInterventions: Int = 0,
    val nightInterventions: Int = 0,
) {
    val todayMs: Long get() = days.lastOrNull()?.totalMs ?: 0L

    /** Average over prior days only, so today's partial total doesn't drag it down. */
    val averageMs: Long
        get() {
            val prior = days.dropLast(1).filter { it.totalMs > 0 }
            return if (prior.isEmpty()) 0L else prior.sumOf { it.totalMs } / prior.size
        }

    val deltaFraction: Float
        get() = if (averageMs == 0L) 0f else (todayMs - averageMs).toFloat() / averageMs
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DashboardViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = DoomscrollRepository.get(app)
    private val importer = UsageStatsImporter(app)

    private val _range = MutableStateFlow(7)
    val range: StateFlow<Int> = _range.asStateFlow()

    private val _importing = MutableStateFlow(false)
    val importing: StateFlow<Boolean> = _importing.asStateFlow()

    val state: StateFlow<DashboardState> = _range.flatMapLatest { days ->
        val from = LocalDate.now().minusDays((days - 1).toLong())
        combine(
            repo.dailyUsageTotals(from),
            repo.packageUsageTotals(from),
            repo.packageInterventionCounts(from),
            repo.reasonCounts(from),
            repo.totalInterventions(from),
        ) { totals, packageTotals, counts, reasons, total ->
            DashboardState(
                range = days,
                days = fillMissingDays(totals, from, days),
                apps = mergeAppData(packageTotals, counts),
                reasons = reasons,
                totalInterventions = total,
            )
        }
    }.combine(nightCounts()) { state, night -> state.copy(nightInterventions = night) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardState())

    private fun nightCounts() = _range.flatMapLatest { days ->
        repo.nightCount(LocalDate.now().minusDays((days - 1).toLong()))
    }

    fun setRange(days: Int) {
        _range.value = days
    }

    /**
     * Runs on first open of the dashboard once Usage Access has been granted.
     *
     * viewModelScope defaults to Main. UsageStatsManager.queryEvents() is a blocking call,
     * and backfill() then walks up to 45 days of event history synchronously with no
     * dispatcher switch of its own — without withContext(IO) this stalls the main thread
     * on first open, right when the user is looking at the dashboard.
     */
    fun importIfNeeded() = viewModelScope.launch {
        withContext(Dispatchers.IO) {
            if (importer.hasBackfilled()) {
                importer.syncRecent()
                return@withContext
            }
            _importing.value = true
            runCatching { importer.backfill() }
            runCatching { importer.syncRecent() }
            _importing.value = false
        }
    }

    fun refresh() = viewModelScope.launch {
        withContext(Dispatchers.IO) { runCatching { importer.syncRecent() } }
    }

    /** Days with no rows still need a bar, otherwise the chart silently compresses time. */
    private fun fillMissingDays(totals: List<DailyTotal>, from: LocalDate, days: Int): List<DayUsage> {
        val byDate = totals.associate { it.localDate to it.totalMs }
        return (0 until days).map { offset ->
            val date = from.plusDays(offset.toLong())
            DayUsage(date, byDate[date.toString()] ?: 0L)
        }
    }

    private fun mergeAppData(totals: List<PackageTotal>, counts: List<PackageCount>): List<AppBreakdown> {
        val countByPackage = counts.associate { it.packageName to it.count }
        val fromUsage = totals.map {
            AppBreakdown(it.packageName, it.totalMs, countByPackage[it.packageName] ?: 0)
        }
        // An app can have interventions logged but no usage row yet, so don't drop it.
        val missing = countByPackage.keys - totals.map { it.packageName }.toSet()
        return (fromUsage + missing.map { AppBreakdown(it, 0L, countByPackage.getValue(it)) })
            .sortedByDescending { it.totalMs }
    }
}
