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
 * "Close the app" and immediately reopening it: that "close" is only GLOBAL_ACTION_HOME, the
 * app is never actually killed, so without this the reopen would silently inherit the old
 * armed=false state and buy several free minutes of scrolling before the normal re-arm timer
 * caught up.
 *
 * KNOWN TRADE-OFF (not a bug to fix, just to know about): that same hook means sitting through
 * the breathing prompt fully and then tapping "Close the app" right after is itself a reopen —
 * reopening within cheatReopenWindowMs forces the prompt again, with no cap on how many times
 * in a row. Repeatedly closing and reopening therefore repeats the prompt every time, which is
 * annoying if you keep doing it on purpose, but the alternative (letting some number of closes
 * through unchallenged) is exactly the loophole this hook exists to close. Left as-is for now.
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
        /** Set when "Close the app" is tapped; consumed by consumeForceReopen(). */
        var closedByInterventionAt: Long = 0L,
    )

    private val states = mutableMapOf<String, State>()

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
     */
    fun onDoomscrollScroll(packageName: String): Boolean {
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
        if (state.scrollEvents < minScrollEvents || !dwelled) return false

        state.armed = false
        state.scrollEvents = 0
        state.triggeredAt = moment
        return true
    }

    /** Call when "Close the app" is tapped, so a reopen inside cheatReopenWindowMs is caught. */
    fun noteClosedByIntervention(packageName: String) {
        states.getOrPut(packageName) { State() }.closedByInterventionAt = now()
    }

    /**
     * Call on every reopen (TYPE_WINDOW_STATE_CHANGED) of a monitored app, before any normal
     * classification/scroll gating. Returns true — once — if this reopen lands inside
     * cheatReopenWindowMs of a prior "Close the app" tap, meaning the caller should force the
     * breathing prompt straight back up rather than waiting for scroll/dwell thresholds.
     *
     * One-shot by design: the mark is cleared the moment it's checked, whether or not the
     * window was still open, so a later reopen of the same visit is judged fresh rather than
     * matching the same close indefinitely.
     */
    fun consumeForceReopen(packageName: String): Boolean {
        val state = states[packageName] ?: return false
        val closedAt = state.closedByInterventionAt
        if (closedAt == 0L) return false
        state.closedByInterventionAt = 0L
        val moment = now()
        if (moment - closedAt > cheatReopenWindowMs) return false

        // Treat this like a genuine trigger so re-arm timing stays consistent with a normal
        // scroll-driven one.
        state.armed = false
        state.scrollEvents = 0
        state.sessionStartedAt = moment
        state.triggeredAt = moment
        return true
    }

    fun reset() = states.clear()

    fun debugState(packageName: String): String {
        val s = states[packageName] ?: return "no session"
        val quiet = if (s.lastSeenAt == 0L) 0 else (now() - s.lastSeenAt) / 1000
        return "armed=${s.armed} scrolls=${s.scrollEvents} quiet=${quiet}s"
    }
}
