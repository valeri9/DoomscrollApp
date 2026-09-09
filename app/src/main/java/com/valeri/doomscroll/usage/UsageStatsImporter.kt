package com.valeri.doomscroll.usage

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.util.Log
import com.valeri.doomscroll.data.db.DailyAppUsageEntity
import com.valeri.doomscroll.data.db.UsageSyncStateEntity
import com.valeri.doomscroll.data.repo.DoomscrollRepository
import com.valeri.doomscroll.service.Tag
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Reads whole-phone screen time from the OS.
 *
 * A completely separate data source from the accessibility service: polled on a slow
 * schedule, and it knows nothing about doomscrolling. It answers only "how long was each app
 * in the foreground each day". The dashboard puts the two side by side.
 *
 * Everything here reconstructs sessions from the raw event stream rather than reading the
 * daily aggregate buckets. The buckets look tempting — they reach further back — but summing
 * totalTimeInForeground across packages double-counts heavily: on a real device it reported
 * 15.8 hours of screen time for a single day, because dozens of background and system
 * packages each accrue their own overlapping foreground time. Event pairs give one
 * non-overlapping session at a time, which is what "screen time" actually means.
 *
 * The cost is reach: the raw event log is retained for a week or two, so that is as far back
 * as the history goes. Fewer days that are true beats a month that is wrong.
 */
class UsageStatsImporter(private val context: Context) {

    private val repo = DoomscrollRepository.get(context)
    private val zone: ZoneId = ZoneId.systemDefault()

    private val usageStats: UsageStatsManager?
        get() = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager

    /**
     * Only packages with a launcher entry count as screen time.
     *
     * Without this, system chrome dominates the numbers: com.android.systemui accrued 5.5
     * hours and incallui 2.7 hours over ten days on the test device. Neither is an app you
     * decide to open — systemui is the shade and lock screen, and incallui keeps accruing
     * through a call with the screen blanked by the proximity sensor. Filtering to launchable
     * packages is the same rule the app picker uses.
     */
    // Not cached with `by lazy`: queryIntentActivities failing (binder death, a restricted
    // profile, any RuntimeException) used to be indistinguishable from it genuinely
    // returning empty, since both fell through the same getOrDefault(emptySet()) — and a
    // `by lazy` then pinned that empty result, and its "isEmpty() means no filter" escape
    // hatch, for this importer's entire lifetime, silently letting systemui/incallui back
    // into every later import's totals. Caching only the success case means a transient
    // failure is retried on the next call instead of poisoning every import after it.
    private var cachedLaunchablePackages: Set<String>? = null

    private fun launchablePackages(): Set<String>? {
        cachedLaunchablePackages?.let { return it }
        return runCatching {
            context.packageManager.queryIntentActivities(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
            ).mapNotNull { it.activityInfo?.packageName }.toSet()
        }.onFailure {
            Log.w(Tag.SERVICE, "launchable package query failed, not filtering this import: ${it.message}")
        }.getOrNull()?.also { cachedLaunchablePackages = it }
    }

    /**
     * One-time history import, reaching back as far as the event log survives.
     * Anything older simply isn't recoverable from the OS.
     */
    suspend fun backfill(days: Int = 45): Int = importLock.withLock {
        if (repo.usageDao.syncState()?.backfillCompletedAt != null) return@withLock 0

        val start = LocalDate.now(zone)
            .minusDays((days - 1).toLong())
            .atStartOfDay(zone).toInstant().toEpochMilli()
        val written = importRange(start, System.currentTimeMillis())

        repo.usageDao.setSyncState(
            UsageSyncStateEntity(
                backfillCompletedAt = System.currentTimeMillis(),
                lastSyncedAt = System.currentTimeMillis(),
            )
        )
        Log.i(Tag.SERVICE, "usage backfill wrote $written rows (window $days days)")
        written
    }

    /** Rolling sync for recent days. Cheap, and re-derives today's running total. */
    suspend fun syncRecent(days: Int = 3): Int = importLock.withLock {
        val start = LocalDate.now(zone)
            .minusDays((days - 1).toLong())
            .atStartOfDay(zone).toInstant().toEpochMilli()
        val now = System.currentTimeMillis()
        val written = importRange(start, now)

        val previous = repo.usageDao.syncState()
        repo.usageDao.setSyncState(
            UsageSyncStateEntity(
                backfillCompletedAt = previous?.backfillCompletedAt,
                lastSyncedAt = now,
            )
        )
        Log.d(Tag.SERVICE, "usage sync wrote $written rows")
        written
    }

    /**
     * Walks the event stream once and pairs each resume with the matching pause, producing
     * per-app, per-day foreground totals.
     */
    private suspend fun importRange(startMillis: Long, endMillis: Long): Int {
        val manager = usageStats ?: return 0
        val events = runCatching { manager.queryEvents(startMillis, endMillis) }.getOrNull() ?: return 0

        // (packageName, localDate) -> accumulated ms
        val totals = mutableMapOf<Pair<String, String>, Long>()
        val resumedAt = mutableMapOf<String, Long>()
        val event = UsageEvents.Event()
        var earliest = Long.MAX_VALUE

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName ?: continue
            if (event.timeStamp < earliest) earliest = event.timeStamp

            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> resumedAt[pkg] = event.timeStamp

                UsageEvents.Event.ACTIVITY_PAUSED,
                UsageEvents.Event.ACTIVITY_STOPPED -> {
                    val began = resumedAt.remove(pkg) ?: continue
                    accumulate(totals, pkg, began, event.timeStamp)
                }

                // The screen going off ends every session, whether or not a pause arrives.
                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> {
                    resumedAt.forEach { (open, began) -> accumulate(totals, open, began, event.timeStamp) }
                    resumedAt.clear()
                }
            }
        }
        // Whatever is still open runs up to the end of the window.
        resumedAt.forEach { (pkg, began) -> accumulate(totals, pkg, began, endMillis) }

        val launchable = launchablePackages()
        val rows = totals
            .filterKeys { launchable == null || launchable.isEmpty() || it.first in launchable }
            .filterValues { it > 0 }
            .map { (key, ms) ->
                DailyAppUsageEntity(localDate = key.second, packageName = key.first, foregroundMs = ms)
            }
        if (rows.isNotEmpty()) repo.usageDao.upsertAll(rows)

        if (earliest != Long.MAX_VALUE) {
            val oldest = Instant.ofEpochMilli(earliest).atZone(zone).toLocalDate()
            Log.i(Tag.SERVICE, "event log reaches back to $oldest")
        }
        return rows.size
    }

    /** Splits a session across midnight so daily totals stay honest. */
    private fun accumulate(
        totals: MutableMap<Pair<String, String>, Long>,
        pkg: String,
        beganAt: Long,
        endedAt: Long,
    ) {
        if (endedAt <= beganAt) return
        // A session longer than a day means we missed its pause event; don't trust it.
        if (endedAt - beganAt > MAX_SESSION_MS) return

        var cursor = beganAt
        while (cursor < endedAt) {
            val date = Instant.ofEpochMilli(cursor).atZone(zone).toLocalDate()
            val dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val sliceEnd = minOf(dayEnd, endedAt)
            val key = pkg to date.toString()
            totals[key] = (totals[key] ?: 0L) + (sliceEnd - cursor)
            cursor = sliceEnd
        }
    }

    suspend fun hasBackfilled(): Boolean = repo.usageDao.syncState()?.backfillCompletedAt != null

    private companion object {
        /** No single foreground session legitimately runs this long. */
        const val MAX_SESSION_MS = 6 * 60 * 60 * 1000L

        /** The worker and the dashboard can both trigger an import; only one should run. */
        val importLock = Mutex()
    }
}
