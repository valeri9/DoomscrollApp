package com.valeri.doomscroll.service

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Measures time actually spent on doomscroll screens (Reels, feed, For You) — not just time in
 * the app, which also counts DMs, posting and the camera.
 *
 * Event-driven like the rest of the service; nothing ticks. Each event from a watched app closes
 * the interval since the previous one, and that interval is credited if the previous event was on
 * a doomscroll screen. Gaps longer than maxGapMs are dropped rather than credited: events only
 * arrive for watched apps, so a long silence usually means the user went home, switched to an
 * unwatched app or turned the screen off, and nothing told us. The cost is that a single video
 * watched for longer than maxGapMs without one accessibility event is undercounted — erring low
 * is the right direction for a number meant to be taken seriously.
 *
 * A shorter silence is ambiguous the same way — one long reel, or a quick trip home — and used
 * to be credited in full, so every brief exit added up to a minute that was never spent
 * scrolling. Gaps over checkGapMs are now checked against the OS usage log via foregroundMs,
 * which says how much of the gap the app really spent in front; only that much is credited.
 * Short gaps, the overwhelming majority, are credited without asking.
 *
 * Not thread-safe: only called from the accessibility event thread.
 */
class DoomscrollTimer(
    private val maxGapMs: Long = DetectionConfig.DOOMSCROLL_MAX_GAP_MS,
    private val checkGapMs: Long = DetectionConfig.DOOMSCROLL_CHECK_GAP_MS,
    /** How long packageName was really in front between from and to; null when it can't tell. */
    private val foregroundMs: (packageName: String, from: Long, to: Long) -> Long? = { _, _, _ -> null },
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private var openPackage: String? = null
    private var openSince = 0L
    private val pending = mutableMapOf<Pair<LocalDate, String>, Long>()

    var pendingMs: Long = 0L
        private set

    fun onEvent(packageName: String, onDoomscrollScreen: Boolean) {
        val moment = now()
        close(moment)
        if (onDoomscrollScreen) {
            openPackage = packageName
            openSince = moment
        }
    }

    /** The user was just taken off the screen by an intervention; stop counting here. */
    fun stop() = close(now())

    /** Everything counted since the last drain, keyed by (day, package). */
    fun drain(): Map<Pair<LocalDate, String>, Long> {
        val out = pending.toMap()
        pending.clear()
        pendingMs = 0L
        return out
    }

    private fun close(moment: Long) {
        val pkg = openPackage ?: return
        openPackage = null
        val gap = moment - openSince
        if (gap <= 0L || gap > maxGapMs) return
        val credited = if (gap > checkGapMs) {
            foregroundMs(pkg, openSince, moment)?.coerceIn(0L, gap) ?: gap
        } else {
            gap
        }
        if (credited <= 0L) return
        val key = Instant.ofEpochMilli(openSince).atZone(zone()).toLocalDate() to pkg
        pending[key] = (pending[key] ?: 0L) + credited
        pendingMs += credited
    }
}
