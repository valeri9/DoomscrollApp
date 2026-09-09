package com.valeri.doomscroll.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.valeri.doomscroll.classifier.MatchType
import com.valeri.doomscroll.classifier.RuleKind
import com.valeri.doomscroll.data.Settings
import com.valeri.doomscroll.data.db.ContextRuleEntity
import com.valeri.doomscroll.data.db.ReasonEntity
import com.valeri.doomscroll.data.repo.DoomscrollRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = DoomscrollRepository.get(app)

    init {
        viewModelScope.launch { repo.seedIfEmpty() }
    }

    val settings: StateFlow<Settings> =
        repo.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Settings())

    val monitoredApps = repo.monitoredApps
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun rulesFor(packageName: String) = repo.rulesForPackage(packageName)
    fun reasonsFor(packageName: String) = repo.observeReasonsFor(packageName)

    /** Reasons that apply to every app, shown when editing the shared list. */
    val globalReasons = repo.observeReasonsFor("").map { list -> list.filter { it.packageName == null } }

    fun addApp(packageName: String) = viewModelScope.launch { repo.addMonitoredApp(packageName) }
    fun setAppEnabled(packageName: String, enabled: Boolean) =
        viewModelScope.launch { repo.setAppEnabled(packageName, enabled) }
    fun removeApp(packageName: String) = viewModelScope.launch { repo.removeMonitoredApp(packageName) }

    fun setRuleEnabled(rule: ContextRuleEntity, enabled: Boolean) =
        viewModelScope.launch { repo.updateRule(rule.copy(enabled = enabled)) }

    fun addRule(packageName: String, kind: RuleKind, match: MatchType, pattern: String) =
        viewModelScope.launch {
            val cleaned = pattern.trim()
            if (cleaned.isNotEmpty()) {
                repo.addRule(
                    ContextRuleEntity(
                        packageName = packageName,
                        kind = kind,
                        matchType = match,
                        pattern = cleaned,
                        isBuiltIn = false,
                    )
                )
            }
        }

    fun deleteRule(rule: ContextRuleEntity) = viewModelScope.launch { repo.deleteRule(rule) }

    fun addReason(packageName: String?, label: String) = viewModelScope.launch {
        val cleaned = label.trim()
        if (cleaned.isNotEmpty()) repo.addReason(packageName, cleaned)
    }

    fun deleteReason(reason: ReasonEntity) = viewModelScope.launch { repo.deleteReason(reason) }

    fun setNightWindow(startMinute: Int, endMinute: Int) =
        viewModelScope.launch { repo.settingsStore.setNightWindow(startMinute, endMinute) }

    fun setDayBreathing(seconds: Int) = viewModelScope.launch { repo.settingsStore.setDayBreathing(seconds) }
    fun setNightBreathing(seconds: Int) = viewModelScope.launch { repo.settingsStore.setNightBreathing(seconds) }
    fun setNightEscalation(seconds: Int) = viewModelScope.launch { repo.settingsStore.setNightEscalation(seconds) }
    fun setNightContinueDelay(seconds: Int) = viewModelScope.launch { repo.settingsStore.setNightContinueDelay(seconds) }
    fun setNightMinChars(chars: Int) = viewModelScope.launch { repo.settingsStore.setNightMinReasonChars(chars) }
    fun setCooldown(minutes: Int) = viewModelScope.launch { repo.settingsStore.setCooldownMinutes(minutes) }
    fun setMinScrolls(count: Int) = viewModelScope.launch { repo.settingsStore.setMinScrollEvents(count) }
    fun setMinDwell(seconds: Int) = viewModelScope.launch { repo.settingsStore.setMinDwellSeconds(seconds) }
    fun setEnabled(enabled: Boolean) = viewModelScope.launch { repo.settingsStore.setEnabled(enabled) }
}
