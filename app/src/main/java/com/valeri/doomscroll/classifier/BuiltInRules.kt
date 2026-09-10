package com.valeri.doomscroll.classifier

/**
 * Shipped starting rules.
 *
 * The Instagram ids below were captured from a real device (S24 Ultra, One UI 8.0.5,
 * Instagram as installed in September 2026) and verified to be mutually exclusive: each
 * action bar appears on exactly one of feed / Explore / DM inbox. They are not guesses.
 *
 * They will still rot. Instagram's internal ids are private and change between releases, so
 * when interventions stop firing, re-run Learn Mode and promote the new ids.
 *
 * One trap worth recording: `swipeable_nav_view_pager_inner_recycler_view` is the obvious
 * candidate for "the main feed list", and it is present on the feed — but also on the DM
 * inbox and on Explore. Using it would have fired an intervention while reading messages,
 * which is the one thing this app must never do. Screen identity comes from the action bar,
 * not from whichever RecyclerView happens to be scrolling.
 *
 * TikTok's rules remain unverified guesses; capture them with Learn Mode.
 */
object BuiltInRules {

    const val INSTAGRAM = "com.instagram.android"
    const val TIKTOK = "com.zhiliaoapp.musically"
    const val TIKTOK_ALT = "com.ss.android.ugc.trill"

    private fun doomscroll(pkg: String, vararg ids: String) =
        ids.map { ContextRule(pkg, RuleKind.DOOMSCROLL, MatchType.VIEW_ID, it) }

    private fun legit(pkg: String, vararg ids: String) =
        ids.map { ContextRule(pkg, RuleKind.LEGIT, MatchType.VIEW_ID, it) }

    /** Verified on device. */
    private val instagram: List<ContextRule> =
        legit(
            INSTAGRAM,
            "direct_inbox_action_bar",                    // DM inbox
            "inbox_refreshable_thread_list_recyclerview", // DM thread list
            "row_thread_composer_edittext",               // typing in a DM thread
            // Stories render on top of the main feed screen without tearing it down, so
            // main_feed_action_bar's node is still attached (and, per its own collapsed
            // scroll state, can still read as on-screen) while a story is playing — a
            // sustained false DOOMSCROLL match, not a brief cache-staleness blip. These are
            // exclusive to the story viewer itself, confirmed against the feed, Explore and
            // DM captures, and as LEGIT rules they win outright over that stale match.
            "reel_viewer_root",
            "reel_viewer_progress_bar",
        ) + doomscroll(
            INSTAGRAM,
            "clips_viewer_view_pager",                    // Reels
            "main_feed_action_bar",                       // home feed
            "explore_action_bar",                         // Explore
        )

    /**
     * Verified on device for TIKTOK (com.zhiliaoapp.musically) only — TIKTOK_ALT
     * (com.ss.android.ugc.trill, a regional build) isn't installed here to check against, so
     * it inherits the same rules on the assumption the two share a codebase; re-verify with
     * Learn Mode if that assumption turns out wrong for that build.
     *
     * Most of TikTok's resource ids are R8-obfuscated to meaningless 3-character codes (bjf,
     * ekn, w3g, ...) that could shift on any update — the opposite of Instagram's descriptive
     * ones, and not worth anchoring rules to. These few survive obfuscation, are semantically
     * named, and were confirmed mutually exclusive between the For You feed and the Inbox
     * (chat list) captures: none of the feed markers appear on Inbox, and vice versa.
     *
     * viewpager itself — the obvious first choice for "the feed" — is a trap here exactly
     * like Instagram's swipeable_nav_view_pager_inner_recycler_view was: it's present on
     * both the feed and Inbox, so it would fire while reading messages.
     *
     * Camera, posting and profile screens are deliberately absent: with fail-closed
     * classification an unrecognised screen is already left alone, so a legit rule is only
     * needed where it must override a doomscroll match.
     */
    private fun tiktokFor(pkg: String): List<ContextRule> =
        legit(
            pkg,
            "user_name",     // Inbox chat-row title
            "v_status_bar",  // Inbox-only status indicator
        ) + doomscroll(
            pkg,
            "player_view",             // For You / Following video player
            "video_container_area",    // video content wrapper
        )

    val all: List<ContextRule> = instagram + tiktokFor(TIKTOK) + tiktokFor(TIKTOK_ALT)

    /**
     * Bump when the rules above change, so an existing install replaces its seeded built-ins
     * instead of keeping stale ones forever. Custom rules are never touched.
     */
    const val VERSION = 4

    val defaultMonitoredPackages: Set<String> = setOf(INSTAGRAM, TIKTOK, TIKTOK_ALT)

    fun forPackage(packageName: String): List<ContextRule> =
        all.filter { it.packageName == packageName }
}
