package com.valeri.doomscroll

import com.valeri.doomscroll.classifier.ContextRule
import com.valeri.doomscroll.classifier.MatchType
import com.valeri.doomscroll.classifier.RuleEngine
import com.valeri.doomscroll.classifier.RuleKind
import com.valeri.doomscroll.classifier.ScreenClass
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Covers the class-name path, which needs no node tree and so runs on the JVM.
 * The view-id path needs a real AccessibilityNodeInfo and is verified on device.
 */
class RuleEngineTest {

    private val pkg = "com.instagram.android"

    private fun engine(vararg rules: ContextRule) = RuleEngine(rules.toList())

    private fun doom(pattern: String) =
        ContextRule(pkg, RuleKind.DOOMSCROLL, MatchType.CLASS_CONTAINS, pattern)

    private fun legit(pattern: String) =
        ContextRule(pkg, RuleKind.LEGIT, MatchType.CLASS_CONTAINS, pattern)

    @Test
    fun `unknown screen is neutral and never fires`() {
        val result = engine(doom("ReelsActivity")).classify(pkg, "SomeUnknownActivity", null)
        assertEquals(ScreenClass.NEUTRAL, result.screenClass)
    }

    @Test
    fun `unmonitored package is neutral`() {
        val result = engine(doom("ReelsActivity")).classify("com.other.app", "ReelsActivity", null)
        assertEquals(ScreenClass.NEUTRAL, result.screenClass)
    }

    @Test
    fun `doomscroll rule matches`() {
        val result = engine(doom("ReelsActivity")).classify(pkg, "com.x.ReelsActivity", null)
        assertEquals(ScreenClass.DOOMSCROLL, result.screenClass)
    }

    @Test
    fun `legit wins when both match`() {
        val e = engine(doom("Activity"), legit("DirectThreadActivity"))
        val result = e.classify(pkg, "com.x.DirectThreadActivity", null)
        assertEquals("a DM thread must never be treated as a feed", ScreenClass.LEGIT, result.screenClass)
    }

    @Test
    fun `disabled rules are ignored`() {
        val e = engine(doom("ReelsActivity").copy(enabled = false))
        assertEquals(ScreenClass.NEUTRAL, e.classify(pkg, "ReelsActivity", null).screenClass)
    }

    @Test
    fun `matching is case insensitive`() {
        val result = engine(doom("reelsactivity")).classify(pkg, "com.x.ReelsActivity", null)
        assertEquals(ScreenClass.DOOMSCROLL, result.screenClass)
    }

    @Test
    fun `null class name with no tree is neutral`() {
        assertEquals(
            ScreenClass.NEUTRAL,
            engine(doom("ReelsActivity")).classify(pkg, null, null).screenClass,
        )
    }

    @Test
    fun `updated rules take effect`() {
        val e = engine(doom("ReelsActivity"))
        assertEquals(ScreenClass.DOOMSCROLL, e.classify(pkg, "ReelsActivity", null).screenClass)

        e.updateRules(listOf(legit("ReelsActivity")))
        assertEquals(ScreenClass.LEGIT, e.classify(pkg, "ReelsActivity", null).screenClass)
    }
}
