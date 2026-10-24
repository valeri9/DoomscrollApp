package com.valeri.doomscroll.usage

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context

/**
 * How long an app was really in front over a short interval, read back from the OS usage log.
 *
 * The accessibility service only hears from watched apps, so it can't tell "watched one reel for
 * 40 seconds" from "went home for 40 seconds and came back" — both are one silent gap. The usage
 * log can: leaving pauses the app's activity, and the screen going off is logged too.
 */
object ForegroundTime {

    enum class Kind { RESUMED, PAUSED, STOPPED, SCREEN_OFF }

    data class Event(val timeMs: Long, val kind: Kind, val packageName: String?, val className: String?)

    /** Stands in for the activity that was already in front at `from`, whose name we never saw. */
    private const val INITIAL = "\u0000initial"

    /**
     * Pure. Assumes the app was in front at [from], which the caller knows from an accessibility
     * event at that moment.
     *
     * Tracked per activity, not per package: moving between two of the app's own activities
     * resumes the new one before the old one stops, so a package-level "stopped" would read as
     * leaving. Only PAUSED retires the unnamed initial activity, since whatever is in front has
     * to pause before anything else can take its place.
     */
    fun foregroundMs(events: List<Event>, packageName: String, from: Long, to: Long): Long {
        val resumed = mutableSetOf(INITIAL)
        var frontSince = from
        var total = 0L
        for (e in events.sortedBy { it.timeMs }) {
            if (e.timeMs < from || e.timeMs > to) continue
            val wasFront = resumed.isNotEmpty()
            val activity = e.className.orEmpty()
            when {
                e.kind == Kind.SCREEN_OFF -> resumed.clear()
                e.packageName != packageName -> continue
                e.kind == Kind.RESUMED -> resumed += activity
                e.kind == Kind.PAUSED -> {
                    resumed -= activity
                    resumed -= INITIAL
                }
                e.kind == Kind.STOPPED -> resumed -= activity
            }
            val isFront = resumed.isNotEmpty()
            if (wasFront && !isFront) total += e.timeMs - frontSince
            if (!wasFront && isFront) frontSince = e.timeMs
        }
        if (resumed.isNotEmpty()) total += to - frontSince
        return total
    }
}

/**
 * The device side of [ForegroundTime]. Without Usage Access the log comes back empty, which reads
 * as "stayed in front throughout" — the same answer the timer gave before it could ask.
 */
class UsageForeground(private val context: Context) {

    fun foregroundMs(packageName: String, from: Long, to: Long): Long? {
        val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return null
        val log = runCatching { manager.queryEvents(from, to) }.getOrNull() ?: return null
        val events = mutableListOf<ForegroundTime.Event>()
        val raw = UsageEvents.Event()
        while (log.hasNextEvent()) {
            log.getNextEvent(raw)
            val kind = when (raw.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> ForegroundTime.Kind.RESUMED
                UsageEvents.Event.ACTIVITY_PAUSED -> ForegroundTime.Kind.PAUSED
                UsageEvents.Event.ACTIVITY_STOPPED -> ForegroundTime.Kind.STOPPED
                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> ForegroundTime.Kind.SCREEN_OFF
                else -> continue
            }
            events += ForegroundTime.Event(raw.timeStamp, kind, raw.packageName, raw.className)
        }
        return ForegroundTime.foregroundMs(events, packageName, from, to)
    }
}
