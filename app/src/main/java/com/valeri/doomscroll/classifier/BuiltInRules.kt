package com.valeri.doomscroll.classifier

/**
 * Shipped starting rules for the apps most worth catching.
 *
 * These view ids are BEST-EFFORT GUESSES. Instagram's and TikTok's internal resource ids are
 * private implementation details, unversioned, and change between releases — there is no way
 * to verify them without dumping the hierarchy on a real device running your build of the app.
 *
 * That is what Learn Mode is for. Capture the real ids on the device, promote them to rules,
 * and these defaults stop mattering. Until then the fail-closed design means the worst case
 * is that nothing fires — not that you get interrupted mid-conversation.
 */
object BuiltInRules {

    const val INSTAGRAM = "com.instagram.android"
    const val TIKTOK = "com.zhiliaoapp.musically"
    const val TIKTOK_ALT = "com.ss.android.ugc.trill"

    private fun doomscroll(pkg: String, vararg ids: String) =
        ids.map { ContextRule(pkg, RuleKind.DOOMSCROLL, MatchType.VIEW_ID, it) }

    private fun legit(pkg: String, vararg ids: String) =
        ids.map { ContextRule(pkg, RuleKind.LEGIT, MatchType.VIEW_ID, it) }

    private fun legitClass(pkg: String, vararg names: String) =
        names.map { ContextRule(pkg, RuleKind.LEGIT, MatchType.CLASS_CONTAINS, it) }

    private val instagram: List<ContextRule> =
        legit(
            INSTAGRAM,
            "direct_inbox_recycler_view",     // DM inbox list
            "row_thread_composer_edittext",   // typing in a DM thread
            "message_list",
            "thread_message_list",
        ) + legitClass(
            INSTAGRAM,
            "DirectThreadActivity",
            "DirectInboxActivity",
            "MediaCaptureActivity",
            "ShareActivity",
        ) + doomscroll(
            INSTAGRAM,
            "clips_viewer_view_pager",        // Reels
            "clips_video_container",
            "feed_recycler_view",             // main feed
            "explore_recycler_view",          // Explore grid
        )

    private fun tiktokFor(pkg: String): List<ContextRule> =
        legit(
            pkg,
            "chat_list",
            "msg_list",
            "im_chat_recycler",
            "et_input",                       // chat composer
        ) + legitClass(
            pkg,
            "ChatRoomActivity",
            "SessionListActivity",
            "VideoRecordActivity",
            "PublishActivity",
        ) + doomscroll(
            pkg,
            "viewpager",                      // For You / Following pager
            "vs_feed_container",
            "feed_recycler_view",
        )

    val all: List<ContextRule> = instagram + tiktokFor(TIKTOK) + tiktokFor(TIKTOK_ALT)

    /** Packages we ship defaults for. Phase 1 monitors exactly these. */
    val defaultMonitoredPackages: Set<String> = setOf(INSTAGRAM, TIKTOK, TIKTOK_ALT)

    fun forPackage(packageName: String): List<ContextRule> =
        all.filter { it.packageName == packageName }
}
