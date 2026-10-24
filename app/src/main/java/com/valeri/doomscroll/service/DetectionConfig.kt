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
     * Doomscroll rules whose screen triggers the moment it's open, skipping the scroll and dwell
     * thresholds above. Those thresholds exist for the home feed, where the story tray and the
     * first post or two are usually friends, not scrolling yet. Reels has no such lead-in, and
     * since a reel is watched before it's swiped, waiting for 5 swipes took about a minute.
     */
    val INSTANT_PATTERNS = setOf("clips_viewer_view_pager")

    /**
     * Landing on an instant screen isn't doomscrolling yet: Instagram reopens on whatever screen
     * it was left on, and a reel a friend sent in DMs opens the same viewer. So an instant screen
     * fires on the first swipe to another reel, or after this long on it without one — enough
     * time to tap through to messages or a profile first.
     */
    const val INSTANT_GRACE_MS = 10_000L

    /**
     * A silence this long on an instant screen means the user left and came back, so the grace
     * period starts over rather than counting the time away towards it.
     */
    const val INSTANT_CONTINUITY_MS = 5_000L

    /**
     * Doomscroll matches this soon after a legit one are ignored. Swiping between friends'
     * stories briefly detaches the story viewer mid-animation, exposing the feed underneath it,
     * and a swipe landing in that gap used to count as a feed scroll.
     */
    const val LEGIT_HOLD_MS = 1_500L

    /**
     * Re-arm during a single long sitting. Without this you get one intervention no matter how
     * long you keep scrolling, which is too lenient to be much use.
     */
    const val RE_ARM_AFTER_MS = 4 * 60 * 1000L

    /** Re-classification is at most this often; results are cached in between. */
    const val CLASSIFY_THROTTLE_MS = 750L

    /**
     * Opening any watched app within this long of tapping "Close the app" puts the breathing
     * prompt straight back up, before a single scroll. Covers both reopening the same app (it
     * was only sent home, never killed, so it would resume with no prompt due) and hopping to
     * a different one (whose own session is fresh and would need a full scroll/dwell run).
     */
    const val CHEAT_REOPEN_WINDOW_MS = 60 * 1000L

    /**
     * Doomscroll-time tracking credits the gap between two events on a doomscroll screen only
     * if it's at most this long; anything longer is treated as having left without being told.
     */
    const val DOOMSCROLL_MAX_GAP_MS = 60 * 1000L

    /**
     * Gaps longer than this are checked against the OS usage log before being credited, so a
     * quick trip home in the middle doesn't count as doomscrolling. See DoomscrollTimer.
     */
    const val DOOMSCROLL_CHECK_GAP_MS = 10 * 1000L

    /** Counted doomscroll time is written to the database in batches of at least this much. */
    const val DOOMSCROLL_FLUSH_MS = 15 * 1000L
}
