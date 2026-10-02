package com.valeri.doomscroll.service

/**
 * Per-app session state machine that keeps the overlay from firing on every scroll tick.
 *
 *     ARMED --(enough scrolling on a doomscroll screen)--> TRIGGERED
 *     TRIGGERED --(no events from the app for >= cooldown)--> ARMED      (left and came back)
 *     TRIGGERED --(still scrolling after reArmAfterMs)-----> ARMED      (long single sitting)
 *
 * Re-arming after a break is driven by a *gap in events* rather than by watching the app go to
 * the background. The service asks the framework to deliver events only for monitored apps,
 * which is the single biggest battery saving available to it — but it means we are never told
 * when the user leaves. There is no "backgrounded" event to listen for, only silence. So
 * silence is the signal.
 *
 * State is per app, not per screen: moving between Reels and DMs is one continuous Instagram
 * session, so ducking into messages and back does not earn a fresh pass.
 *
 * A third, separate hook — noteClosedByIntervention()/consumeForceReopen() — catches tapping
 * "Close the app" and then opening ANY watched app within the window: the same one again, or
 * a different one. That "close" is only GLOBAL_ACTION_HOME, so the app itself stays alive and
 * reopening it would silently inherit the old armed=false state; and every other watched app
 * has its own fresh, armed session that still needs a full scroll/dwell run before it fires.
 * Either way the urge just moves somewhere else, so the mark is global, not per app.
 *
 * KNOWN TRADE-OFF (not a bug to fix, just to know about): repeatedly closing and reopening —
 * in the same app or by hopping between them — repeats the prompt every time, with no cap.
 * Annoying if you keep doing it on purpose, but letting some number of closes through
 * unchallenged is exactly the loophole this hook exists to close. Left as-is for now.
 */
class SessionTracker(
    @Volatile var cooldownMs: Long = DetectionConfig.COOLDOWN_MS,
    @Volatile var minScrollEvents: Int = DetectionConfig.MIN_SCROLL_EVENTS,
    @Volatile var minDwellMs: Long = DetectionConfig.MIN_DWELL_MS,
    /** Re-arm during one long sitting. 0 disables it: one intervention per visit. */
    @Volatile var reArmAfterMs: Long = DetectionConfig.RE_ARM_AFTER_MS,
    @Volatile var cheatReopenWindowMs: Long = DetectionConfig.CHEAT_REOPEN_WINDOW_MS,
    private val now: () -> Long = System::currentTimeMillis,
) {

    private data class State(
        var armed: Boolean = true,
        var sessionStartedAt: Long = 0L,
        var lastSeenAt: Long = 0L,
        var triggeredAt: Long = 0L,
        var scrollEvents: Int = 0,
    )

    private val states = mutableMapOf<String, State>()

    /** When "Close the app" was last tapped, in any app. Consumed by consumeForceReopen(). */
    private var closedByInterventionAt: Long = 0L

    /**
     * Called for every event from a monitored app. Starts a new session when the app has been
     * quiet for at least the cooldown.
     */
    fun noteActivity(packageName: String) {
        val state = states.getOrPut(packageName) { State() }
        val moment = now()
        val quietFor = if (state.lastSeenAt == 0L) Long.MAX_VALUE else moment - state.lastSeenAt

        if (quietFor >= cooldownMs) {
            // Away long enough — or this is the first time we have ever seen the app.
            state.armed = true
            state.scrollEvents = 0
            state.sessionStartedAt = moment
            state.triggeredAt = 0L
        } else if (state.sessionStartedAt == 0L) {
            // The service started while this app was already in front, so no window event
            // ever arrived to start the dwell clock. Start it now, or the dwell check
            // compares against zero and the app silently never triggers.
            state.sessionStartedAt = moment
        }
        state.lastSeenAt = moment
    }

    /**
     * Records a scroll on a screen already classified as doomscroll.
     * Returns true once per session, and again each time reArmAfterMs elapses.
     *
     * instant skips the scroll-count and dwell thresholds, for screens that are doomscrolling
     * from the first frame (Reels). Arming still applies, so it fires once per session there
     * too rather than on every event.
     */
    fun onDoomscrollScroll(packageName: String, instant: Boolean = false): Boolean {
        val state = states.getOrPut(packageName) { State() }
        val moment = now()

        if (!state.armed) {
            // Still here, still scrolling, long after the last nudge: nudge again.
            val since = moment - state.triggeredAt
            if (reArmAfterMs <= 0L || state.triggeredAt == 0L || since < reArmAfterMs) return false
            state.armed = true
            state.scrollEvents = 0
            state.sessionStartedAt = moment
        }

        if (state.sessionStartedAt == 0L) state.sessionStartedAt = moment

        state.scrollEvents++
        val dwelled = moment - state.sessionStartedAt >= minDwellMs
        if (!instant && (state.scrollEvents < minScrollEvents || !dwelled)) return false

        state.armed = false
        state.scrollEvents = 0
        state.triggeredAt = moment
        return true
    }

    /** Call when "Close the app" is tapped, in any app. */
    fun noteClosedByIntervention() {
        closedByInterventionAt = now()
    }

    /**
     * Call when any watched app comes to the front (TYPE_WINDOW_STATE_CHANGED), before the
     * normal scroll/dwell gating. Returns true — once — if that happens inside
     * cheatReopenWindowMs of a "Close the app" tap anywhere, meaning the caller should put
     * the breathing prompt straight back up.
     *
     * One-shot: the mark is cleared the moment it's checked, whether or not it was still in
     * the window, so only the first app opened after a close is judged against it.
     */
    fun consumeForceReopen(packageName: String): Boolean {
        val closedAt = closedByInterventionAt
        if (closedAt == 0L) return false
        closedByInterventionAt = 0L
        val moment = now()
        if (moment - closedAt > cheatReopenWindowMs) return false

        // Treat this like a genuine trigger in the app being opened, so its re-arm timing
        // stays consistent with a normal scroll-driven one.
        val state = states.getOrPut(packageName) { State() }
        state.armed = false
        state.scrollEvents = 0
        state.sessionStartedAt = moment
        state.triggeredAt = moment
        return true
    }

    fun reset() {
        states.clear()
        closedByInterventionAt = 0L
    }

    fun debugState(packageName: String): String {
        val s = states[packageName] ?: return "no session"
        val quiet = if (s.lastSeenAt == 0L) 0 else (now() - s.lastSeenAt) / 1000
        return "armed=${s.armed} scrolls=${s.scrollEvents} quiet=${quiet}s"
    }
}
