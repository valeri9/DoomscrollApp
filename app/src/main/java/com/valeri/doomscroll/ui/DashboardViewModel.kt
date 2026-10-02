package com.valeri.doomscroll.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DashboardViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = DoomscrollRepository.get(app)
    private val importer = UsageStatsImporter(app)

    private val _range = MutableStateFlow(7)

    private val _importing = MutableStateFlow(false)
    val importing: StateFlow<Boolean> = _importing.asStateFlow()

    val state: StateFlow<DashboardState> = _range.flatMapLatest { range ->
        // The weekly comparison always needs the last 14 full days, whatever the range.
        val from = LocalDate.now().minusDays(maxOf(range - 1, 14).toLong())
        combine(
            repo.enabledPackages,
            repo.usageDao.observeSince(from.toString()),
            repo.doomscrollTimeSince(from),
            repo.interventionsSince(from),
            repo.doomscrollTrackingSince,
        ) { watched, usage, doomscroll, interventions, since ->
            DashboardStats.build(
                today = LocalDate.now(),
                range = range,
                watched = watched,
                usage = usage,
                doomscroll = doomscroll,
                interventions = interventions,
                trackingSince = since?.let(LocalDate::parse),
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardState())

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
}
