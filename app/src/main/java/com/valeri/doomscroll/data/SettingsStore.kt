package com.valeri.doomscroll.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalTime

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Global tuning. Per-app data (which apps, which rules, which reasons) lives in Room; only
 * these scalars are here.
 */
data class Settings(
    val nightStartMinute: Int = 0,        // 00:00
    val nightEndMinute: Int = 8 * 60,     // 08:00
    val dayBreathingSeconds: Int = 10,
    val nightBreathingSeconds: Int = 45,
    val nightEscalationSeconds: Int = 10,
    val nightContinueDelaySeconds: Int = 5,
    val nightMinReasonChars: Int = 15,
    val cooldownMinutes: Int = 3,
    val minScrollEvents: Int = 3,
    val minDwellSeconds: Int = 5,
    val enabled: Boolean = true,
) {
    val cooldownMs: Long get() = cooldownMinutes * 60_000L
    val minDwellMs: Long get() = minDwellSeconds * 1000L

    /**
     * Night windows normally wrap past midnight (00:00-08:00 does not, but 22:00-06:00 does),
     * so the comparison differs depending on which way round the bounds are.
     */
    fun isNight(time: LocalTime = LocalTime.now()): Boolean {
        val minute = time.hour * 60 + time.minute
        return if (nightStartMinute <= nightEndMinute) {
            minute >= nightStartMinute && minute < nightEndMinute
        } else {
            minute >= nightStartMinute || minute < nightEndMinute
        }
    }

    /** Start of the current (or most recent) night window, for escalation counting. */
    fun nightWindowStartMillis(now: Long = System.currentTimeMillis()): Long {
        val zone = java.time.ZoneId.systemDefault()
        val nowDateTime = java.time.Instant.ofEpochMilli(now).atZone(zone)
        val todayStart = nowDateTime.toLocalDate()
            .atTime(nightStartMinute / 60, nightStartMinute % 60)
            .atZone(zone)
        val start = if (todayStart.toInstant().toEpochMilli() > now) todayStart.minusDays(1) else todayStart
        return start.toInstant().toEpochMilli()
    }
}

class SettingsStore(private val context: Context) {

    private object Keys {
        val nightStart = intPreferencesKey("night_start_minute")
        val nightEnd = intPreferencesKey("night_end_minute")
        val dayBreathing = intPreferencesKey("day_breathing_seconds")
        val nightBreathing = intPreferencesKey("night_breathing_seconds")
        val nightEscalation = intPreferencesKey("night_escalation_seconds")
        val nightContinueDelay = intPreferencesKey("night_continue_delay_seconds")
        val nightMinChars = intPreferencesKey("night_min_reason_chars")
        val cooldown = intPreferencesKey("cooldown_minutes")
        val minScrolls = intPreferencesKey("min_scroll_events")
        val minDwell = intPreferencesKey("min_dwell_seconds")
        val enabled = booleanPreferencesKey("enabled")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { prefs ->
        val defaults = Settings()
        Settings(
            nightStartMinute = prefs[Keys.nightStart] ?: defaults.nightStartMinute,
            nightEndMinute = prefs[Keys.nightEnd] ?: defaults.nightEndMinute,
            dayBreathingSeconds = prefs[Keys.dayBreathing] ?: defaults.dayBreathingSeconds,
            nightBreathingSeconds = prefs[Keys.nightBreathing] ?: defaults.nightBreathingSeconds,
            nightEscalationSeconds = prefs[Keys.nightEscalation] ?: defaults.nightEscalationSeconds,
            nightContinueDelaySeconds = prefs[Keys.nightContinueDelay] ?: defaults.nightContinueDelaySeconds,
            nightMinReasonChars = prefs[Keys.nightMinChars] ?: defaults.nightMinReasonChars,
            cooldownMinutes = prefs[Keys.cooldown] ?: defaults.cooldownMinutes,
            minScrollEvents = prefs[Keys.minScrolls] ?: defaults.minScrollEvents,
            minDwellSeconds = prefs[Keys.minDwell] ?: defaults.minDwellSeconds,
            enabled = prefs[Keys.enabled] ?: defaults.enabled,
        )
    }

    suspend fun setNightWindow(startMinute: Int, endMinute: Int) = edit {
        it[Keys.nightStart] = startMinute
        it[Keys.nightEnd] = endMinute
    }

    suspend fun setDayBreathing(seconds: Int) = edit { it[Keys.dayBreathing] = seconds }
    suspend fun setNightBreathing(seconds: Int) = edit { it[Keys.nightBreathing] = seconds }
    suspend fun setNightEscalation(seconds: Int) = edit { it[Keys.nightEscalation] = seconds }
    suspend fun setNightContinueDelay(seconds: Int) = edit { it[Keys.nightContinueDelay] = seconds }
    suspend fun setNightMinReasonChars(chars: Int) = edit { it[Keys.nightMinChars] = chars }
    suspend fun setCooldownMinutes(minutes: Int) = edit { it[Keys.cooldown] = minutes }
    suspend fun setMinScrollEvents(count: Int) = edit { it[Keys.minScrolls] = count }
    suspend fun setMinDwellSeconds(seconds: Int) = edit { it[Keys.minDwell] = seconds }
    suspend fun setEnabled(enabled: Boolean) = edit { it[Keys.enabled] = enabled }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }
}
