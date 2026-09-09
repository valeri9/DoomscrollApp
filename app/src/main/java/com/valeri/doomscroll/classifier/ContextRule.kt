package com.valeri.doomscroll.classifier

/**
 * How a screen inside a monitored app is treated.
 *
 * [NEUTRAL] is the important one: it means "I don't recognise this screen". The app fails
 * closed, so a neutral screen never triggers an intervention. Getting a rule wrong costs
 * us a missed doomscroll session, never an interrupted conversation.
 */
enum class ScreenClass { DOOMSCROLL, LEGIT, NEUTRAL }

enum class RuleKind { DOOMSCROLL, LEGIT }

enum class MatchType {
    /**
     * Exact resource-id suffix, e.g. "clips_viewer_view_pager". Resolved through
     * AccessibilityNodeInfo.findAccessibilityNodeInfosByViewId(), which is an indexed
     * lookup rather than a hierarchy walk — cheap enough for the hot path.
     */
    VIEW_ID,

    /**
     * Substring of the window's class name, taken straight off the
     * TYPE_WINDOW_STATE_CHANGED event. Costs nothing at all: no node access required.
     */
    CLASS_CONTAINS,
}

data class ContextRule(
    val packageName: String,
    val kind: RuleKind,
    val match: MatchType,
    val pattern: String,
    val enabled: Boolean = true,
    val isBuiltIn: Boolean = true,
) {
    /** Fully-qualified id the framework expects, e.g. "com.instagram.android:id/foo". */
    val fullViewId: String get() = "$packageName:id/$pattern"
}
