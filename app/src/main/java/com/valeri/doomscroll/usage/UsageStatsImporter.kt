package com.valeri.doomscroll.usage

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.util.Log
import com.valeri.doomscroll.data.db.DailyAppUsageEntity
import com.valeri.doomscroll.data.db.UsageSyncStateEntity
import com.valeri.doomscroll.data.repo.DoomscrollRepository
import com.valeri.doomscroll.service.Tag
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Reads whole-phone screen time from the OS.
 *
 * This is a completely separate data source from the accessibility service: it is polled on a
 * slow schedule and knows nothing about doomscrolling, it just answers "how long was each app
 * in the foreground each day". The dashboard puts the two side by side.
 *
 * How far back this can see is an OS limit, not ours. Android keeps daily buckets for roughly
 * one to four weeks and raw events for about a week, so expect a few weeks of history at most.
 */
class UsageStatsImporter(private val context: Context) {

    private val repo = DoomscrollRepository.get(context)
    private val zone: ZoneId = ZoneId.systemDefault()

    private val usageStats: UsageStatsManager?
        get() = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager

    /**
     * One-time history import. Uses the daily aggregate buckets, which are coarse but reach
     * further back than the raw event stream.
     */
    suspend fun backfill(days: Int = 60): Int {
        val manager = usageStats ?: return 0
        val today = LocalDate.now(zone)
        val rows = mutableListOf<DailyAppUsageEntity>()

        for (offset in days downTo 1) {
            val date = today.minusDays(offset.toLong())
            val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
            val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

            val stats = runCatching {
                manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end)
            }.getOrNull().orEmpty()

            stats.asSequence()
                .filter { it.totalTimeInForeground > 0 }
                // A bucket can overlap the window; keep only what actually belongs to this day.
                .filter { it.lastTimeUsed in start until end || it.firstTimeStamp >= start }
                .groupBy { it.packageName }
                .forEach { (pkg, entries) ->
                    rows += DailyAppUsageEntity(
                        localDate = date.toString(),
                        packageName = pkg,
                        foregroundMs = entries.sumOf { it.totalTimeInForeground },
                    )
                }
        }

        if (rows.isNotEmpty()) repo.usageDao.upsertAll(rows)
        repo.usageDao.setSyncState(
            UsageSyncStateEntity(
                backfillCompletedAt = System.currentTimeMillis(),
                lastSyncedAt = System.currentTimeMillis(),
            )
        )
        Log.i(Tag.SERVICE, "usage backfill wrote ${rows.size} rows over $days days")
        return rows.size
    }

    /**
     * Rolling sync for recent days. Reconstructs foreground sessions from the raw event stream,
     * which is accurate enough to trust for today's running total.
     */
    suspend fun syncRecent(days: Int = 3): Int {
        val manager = usageStats ?: return 0
        val today = LocalDate.now(zone)
        val start = today.minusDays((days - 1).toLong()).atStartOfDay(zone).toInstant().toEpochMilli()
        val now = System.currentTimeMillis()

        val events = runCatching { manager.queryEvents(start, now) }.getOrNull() ?: return 0

        // packageName -> day -> accumulated ms
        val totals = mutableMapOf<Pair<String, String>, Long>()
        val resumedAt = mutableMapOf<String, Long>()
        val event = UsageEvents.Event()

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName ?: continue
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> resumedAt[pkg] = event.timeStamp
                UsageEvents.Event.ACTIVITY_PAUSED,
                UsageEvents.Event.ACTIVITY_STOPPED -> {
                    val began = resumedAt.remove(pkg) ?: continue
                    accumulate(totals, pkg, began, event.timeStamp)
                }
            }
        }
        // Whatever is still in the foreground counts up to now.
        resumedAt.forEach { (pkg, began) -> accumulate(totals, pkg, began, now) }

        val rows = totals.map { (key, ms) ->
            DailyAppUsageEntity(localDate = key.second, packageName = key.first, foregroundMs = ms)
        }
        if (rows.isNotEmpty()) repo.usageDao.upsertAll(rows)

        val previous = repo.usageDao.syncState()
        repo.usageDao.setSyncState(
            UsageSyncStateEntity(
                backfillCompletedAt = previous?.backfillCompletedAt,
                lastSyncedAt = now,
            )
        )
        Log.d(Tag.SERVICE, "usage sync wrote ${rows.size} rows")
        return rows.size
    }

    /** Splits a foreground session across midnight so daily totals stay honest. */
    private fun accumulate(
        totals: MutableMap<Pair<String, String>, Long>,
        pkg: String,
        beganAt: Long,
        endedAt: Long,
    ) {
        if (endedAt <= beganAt) return
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
}
