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
     * bar should not count as a session.
     */
    const val MIN_SCROLL_EVENTS = 3

    /** Time in the app before a trigger is possible, for the same reason. */
    const val MIN_DWELL_MS = 5_000L

    /**
     * Re-arm during a single long sitting. Without this you get one intervention no matter how
     * long you keep scrolling, which is too lenient to be much use.
     */
    const val RE_ARM_AFTER_MS = 4 * 60 * 1000L

    /** Re-classification is at most this often; results are cached in between. */
    const val CLASSIFY_THROTTLE_MS = 750L

    /** Length of the normal-hours breathing exercise. */
    const val BREATHING_SECONDS = 10
}
