package com.valeri.doomscroll.service

/**
 * Detection tuning. Phase 1 keeps these as constants; Phase 2 moves them into DataStore so
 * they are editable per app from the settings screen.
 */
object DetectionConfig {
    /** How long the app must be backgrounded before a new session can trigger again. */
    const val COOLDOWN_MS = 3 * 60 * 1000L

    /**
     * Scroll events required before we call it doomscrolling. One flick to reach the search
     * bar should not count as a session — and neither should passing the story tray and the
     * first post or two, which is often just a friend's post and not real scrolling yet.
     * Raised from 3 to 5 for that reason.
     */
    const val MIN_SCROLL_EVENTS = 5

    /** Time in the app before a trigger is possible, for the same reason. */
    const val MIN_DWELL_MS = 8_000L

    /**
     * Re-arm during a single long sitting. Without this you get one intervention no matter how
     * long you keep scrolling, which is too lenient to be much use.
     */
    const val RE_ARM_AFTER_MS = 4 * 60 * 1000L

    /** Re-classification is at most this often; results are cached in between. */
    const val CLASSIFY_THROTTLE_MS = 750L

    /**
     * "Close the app" only does GLOBAL_ACTION_HOME — the app itself is still alive in the
     * background, not killed, so reopening it resumes the exact same paused screen. Without
     * this, that reopen is a free cheat: the session's armed/re-arm state survives the trip
     * home untouched (there's no event from the launcher to reset it, since only monitored
     * packages are ever reported to us), so scrolling straight back in doesn't earn a new
     * breathing prompt until reArmAfterMs quietly elapses on its own. Reopening the app within
     * this window of tapping "Close the app" instead forces the breathing prompt right back
     * up immediately, before a single scroll — closing the loophole the delay would otherwise
     * be.
     */
    const val CHEAT_REOPEN_WINDOW_MS = 60 * 1000L
}
