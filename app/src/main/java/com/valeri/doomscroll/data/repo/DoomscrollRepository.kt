package com.valeri.doomscroll.data.repo

import android.content.Context
import com.valeri.doomscroll.classifier.BuiltInRules
import com.valeri.doomscroll.classifier.ContextRule
import com.valeri.doomscroll.data.SettingsStore
import com.valeri.doomscroll.data.db.ContextRuleEntity
import com.valeri.doomscroll.data.db.DoomscrollDatabase
import com.valeri.doomscroll.data.db.InterventionEntity
import com.valeri.doomscroll.data.db.MonitoredAppEntity
import com.valeri.doomscroll.data.db.ReasonEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Single entry point to persisted state, shared by the UI and the accessibility service. */
class DoomscrollRepository private constructor(context: Context) {

    private val db = DoomscrollDatabase.get(context)
    val settingsStore = SettingsStore(context.applicationContext)

    val settings = settingsStore.settings
    val monitoredApps: Flow<List<MonitoredAppEntity>> = db.monitoredApps().observeAll()
    val allRules: Flow<List<ContextRuleEntity>> = db.contextRules().observeAll()

    val enabledPackages: Flow<Set<String>> =
        monitoredApps.map { apps -> apps.filter { it.enabled }.map { it.packageName }.toSet() }

    /**
     * Guards seedIfEmpty(). It is called independently from both the accessibility service
     * (on connect) and the settings screen (on open), which can run at the same moment on a
     * fresh install. Without this, both callers can pass the "is anything seeded yet" check
     * before either has inserted anything, and each then runs its own delete-then-insert —
     * producing two copies of every built-in rule and default reason. The check and the write
     * must be one atomic step, not two.
     */
    private val seedMutex = Mutex()

    /**
     * Seeds the shipped rules, apps and reasons the first time the database is opened.
     * Done lazily rather than in a Room callback so it can be re-checked cheaply and stays
     * ordinary suspending code.
     */
    suspend fun seedIfEmpty() = seedMutex.withLock {
        // A shipped rule set that can never be corrected on an existing install is worse than
        // no rule set at all, so replace the built-ins whenever their version moves. Custom
        // rules the user added are left alone.
        val seededVersion = settingsStore.builtInRulesVersion()
        if (db.contextRules().count() == 0 || seededVersion != BuiltInRules.VERSION) {
            db.contextRules().deleteBuiltIns()
            db.contextRules().insertAll(BuiltInRules.all.map { it.toEntity(isBuiltIn = true) })
            settingsStore.setBuiltInRulesVersion(BuiltInRules.VERSION)
        }
        if (db.monitoredApps().count() == 0) {
            BuiltInRules.defaultMonitoredPackages.forEach {
                db.monitoredApps().upsert(MonitoredAppEntity(packageName = it))
            }
        }
        if (db.reasons().count() == 0) {
            db.reasons().insertAll(DEFAULT_REASONS.mapIndexed { i, label ->
                ReasonEntity(packageName = null, label = label, sortOrder = i)
            })
        }
    }

    // --- rules -------------------------------------------------------------------------

    suspend fun activeRules(): List<ContextRule> =
        db.contextRules().getAll().filter { it.enabled }.map { it.toDomain() }

    fun rulesForPackage(packageName: String): Flow<List<ContextRuleEntity>> =
        db.contextRules().observeForPackage(packageName)

    suspend fun addRule(rule: ContextRuleEntity): Long = db.contextRules().insert(rule)
    suspend fun updateRule(rule: ContextRuleEntity) = db.contextRules().update(rule)
    suspend fun deleteRule(rule: ContextRuleEntity) = db.contextRules().delete(rule)

    // --- monitored apps ----------------------------------------------------------------

    suspend fun addMonitoredApp(packageName: String) =
        db.monitoredApps().upsert(MonitoredAppEntity(packageName = packageName))

    suspend fun setAppEnabled(packageName: String, enabled: Boolean) =
        db.monitoredApps().upsert(MonitoredAppEntity(packageName = packageName, enabled = enabled))

    suspend fun removeMonitoredApp(packageName: String) = db.monitoredApps().remove(packageName)

    suspend fun monitoredPackagesNow(): Set<String> =
        db.monitoredApps().getAll().filter { it.enabled }.map { it.packageName }.toSet()

    // --- reasons -----------------------------------------------------------------------

    suspend fun reasonsFor(packageName: String): List<String> =
        db.reasons().forPackage(packageName).map { it.label }

    fun observeReasonsFor(packageName: String): Flow<List<ReasonEntity>> =
        db.reasons().observeForPackage(packageName)

    suspend fun addReason(packageName: String?, label: String) =
        db.reasons().insert(ReasonEntity(packageName = packageName, label = label))

    suspend fun deleteReason(reason: ReasonEntity) = db.reasons().delete(reason)

    // --- interventions -----------------------------------------------------------------

    suspend fun startIntervention(
        packageName: String,
        contextLabel: String,
        isNightMode: Boolean,
        breathingSeconds: Int,
        triggeredAt: Long = System.currentTimeMillis(),
    ): Long {
        val zoned = Instant.ofEpochMilli(triggeredAt).atZone(ZoneId.systemDefault())
        return db.interventions().insert(
            InterventionEntity(
                packageName = packageName,
                contextLabel = contextLabel,
                triggeredAt = triggeredAt,
                localDate = zoned.toLocalDate().toString(),
                hourOfDay = zoned.hour,
                isNightMode = isNightMode,
                breathingSeconds = breathingSeconds,
                reasonLabel = null,
                reasonText = null,
                continuedAnyway = false,
                completedAt = null,
            )
        )
    }

    suspend fun completeIntervention(id: Long, label: String?, text: String?, continued: Boolean) =
        db.interventions().complete(id, label, text, continued, System.currentTimeMillis())

    suspend fun nightInterventionsSince(since: Long): Int = db.interventions().countNightSince(since)

    fun recentInterventions(limit: Int = 50) = db.interventions().observeRecent(limit)
    fun dailyInterventionCounts(fromDate: LocalDate) =
        db.interventions().observeDailyCounts(fromDate.toString())
    fun packageInterventionCounts(fromDate: LocalDate) =
        db.interventions().observePackageCounts(fromDate.toString())
    fun reasonCounts(fromDate: LocalDate) = db.interventions().observeReasonCounts(fromDate.toString())
    fun nightCount(fromDate: LocalDate) = db.interventions().observeNightCount(fromDate.toString())
    fun totalInterventions(fromDate: LocalDate) =
        db.interventions().observeTotalCount(fromDate.toString())

    // --- usage -------------------------------------------------------------------------

    val usageDao get() = db.usage()

    fun dailyUsageTotals(fromDate: LocalDate) = db.usage().observeDailyTotals(fromDate.toString())
    fun packageUsageTotals(fromDate: LocalDate) = db.usage().observePackageTotals(fromDate.toString())

    companion object {
        private val DEFAULT_REASONS = listOf(
            "Bored",
            "Habit",
            "Came for something specific",
            "Got sidetracked from DMs",
            "Avoiding something",
        )

        @Volatile private var instance: DoomscrollRepository? = null

        fun get(context: Context): DoomscrollRepository = instance ?: synchronized(this) {
            instance ?: DoomscrollRepository(context.applicationContext).also { instance = it }
        }
    }
}

private fun ContextRule.toEntity(isBuiltIn: Boolean) = ContextRuleEntity(
    packageName = packageName,
    kind = kind,
    matchType = match,
    pattern = pattern,
    enabled = enabled,
    isBuiltIn = isBuiltIn,
)

fun ContextRuleEntity.toDomain() = ContextRule(
    packageName = packageName,
    kind = kind,
    match = matchType,
    pattern = pattern,
    enabled = enabled,
    isBuiltIn = isBuiltIn,
)
