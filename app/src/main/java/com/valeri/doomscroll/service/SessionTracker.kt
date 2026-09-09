package com.valeri.doomscroll.service

/**
 * Per-app session state machine that keeps the overlay from firing on every scroll tick.
 *
 *     ARMED --(enough scrolling on a doomscroll screen)--> TRIGGERED
 *     TRIGGERED --(app backgrounded for >= cooldown)--> ARMED
 *
 * Backgrounding for less than the cooldown does not re-arm, so app-switching your way out of
 * the overlay and straight back in does not earn you a fresh pass.
 */
class SessionTracker(
    @Volatile var cooldownMs: Long = DetectionConfig.COOLDOWN_MS,
    @Volatile var minScrollEvents: Int = DetectionConfig.MIN_SCROLL_EVENTS,
    @Volatile var minDwellMs: Long = DetectionConfig.MIN_DWELL_MS,
    private val now: () -> Long = System::currentTimeMillis,
) {

    private data class State(
        var armed: Boolean = true,
        var foregroundedAt: Long = 0L,
        var backgroundedAt: Long = 0L,
        var scrollEvents: Int = 0,
    )

    private val states = mutableMapOf<String, State>()
    private var currentPackage: String? = null

    fun onAppForegrounded(packageName: String) {
        if (currentPackage == packageName) return
        currentPackage?.let { previous -> states.getOrPut(previous) { State() }.backgroundedAt = now() }
        currentPackage = packageName

        val state = states.getOrPut(packageName) { State() }
        val awayFor = if (state.backgroundedAt == 0L) Long.MAX_VALUE else now() - state.backgroundedAt
        if (awayFor >= cooldownMs) {
            state.armed = true
            state.scrollEvents = 0
        }
        state.foregroundedAt = now()
    }

    fun onLeftMonitoredApps() {
        currentPackage?.let { states.getOrPut(it) { State() }.backgroundedAt = now() }
        currentPackage = null
    }

    /**
     * Records a scroll on a screen already classified as doomscroll.
     * Returns true exactly once per session, when the guards are satisfied.
     */
    fun onDoomscrollScroll(packageName: String): Boolean {
        val state = states.getOrPut(packageName) { State() }
        if (!state.armed) return false

        // The service may have started, or been restarted by the system, while this app was
        // already in the foreground — in which case no window-state event ever arrived and
        // the dwell clock was never started. Treat the first scroll we see as its start,
        // otherwise the dwell check can never pass and the app silently never triggers.
        if (state.foregroundedAt == 0L) state.foregroundedAt = now()

        state.scrollEvents++
        val dwelled = state.foregroundedAt > 0 && now() - state.foregroundedAt >= minDwellMs
        if (state.scrollEvents < minScrollEvents || !dwelled) return false

        state.armed = false
        state.scrollEvents = 0
        return true
    }

    /** Test/debug hook: forget everything and re-arm. */
    fun reset() {
        states.clear()
        currentPackage = null
    }

    fun debugState(packageName: String): String {
        val s = states[packageName] ?: return "no session"
        return "armed=${s.armed} scrolls=${s.scrollEvents}"
    }
}
