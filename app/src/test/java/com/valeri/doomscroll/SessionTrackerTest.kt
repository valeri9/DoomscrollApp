package com.valeri.doomscroll

import com.valeri.doomscroll.service.SessionTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionTrackerTest {

    private var now = 1_000_000L
    private fun tracker() = SessionTracker(
        cooldownMs = 180_000L,
        minScrollEvents = 3,
        minDwellMs = 5_000L,
        now = { now },
    )

    private val pkg = "com.instagram.android"

    @Test
    fun `does not fire before the scroll threshold`() {
        val t = tracker()
        t.onAppForegrounded(pkg)
        now += 10_000

        assertFalse(t.onDoomscrollScroll(pkg))
        assertFalse(t.onDoomscrollScroll(pkg))
        assertTrue("third scroll should trigger", t.onDoomscrollScroll(pkg))
    }

    @Test
    fun `does not fire before the dwell time even with enough scrolls`() {
        val t = tracker()
        t.onAppForegrounded(pkg)
        now += 1_000 // under the 5s dwell

        repeat(10) { assertFalse(t.onDoomscrollScroll(pkg)) }

        now += 5_000
        assertTrue(t.onDoomscrollScroll(pkg))
    }

    @Test
    fun `fires only once per session`() {
        val t = tracker()
        t.onAppForegrounded(pkg)
        now += 10_000
        repeat(2) { t.onDoomscrollScroll(pkg) }
        assertTrue(t.onDoomscrollScroll(pkg))

        repeat(20) { assertFalse("must not re-fire in the same session", t.onDoomscrollScroll(pkg)) }
    }

    @Test
    fun `a short trip away does not re-arm`() {
        val t = tracker()
        t.onAppForegrounded(pkg)
        now += 10_000
        repeat(3) { t.onDoomscrollScroll(pkg) }

        t.onLeftMonitoredApps()
        now += 60_000 // under the 3 minute cooldown
        t.onAppForegrounded(pkg)
        now += 10_000

        repeat(10) { assertFalse("cooldown not elapsed", t.onDoomscrollScroll(pkg)) }
    }

    @Test
    fun `re-arms after the cooldown elapses`() {
        val t = tracker()
        t.onAppForegrounded(pkg)
        now += 10_000
        repeat(3) { t.onDoomscrollScroll(pkg) }

        t.onLeftMonitoredApps()
        now += 200_000 // past the 3 minute cooldown
        t.onAppForegrounded(pkg)
        now += 10_000

        assertFalse(t.onDoomscrollScroll(pkg))
        assertFalse(t.onDoomscrollScroll(pkg))
        assertTrue("should be armed again", t.onDoomscrollScroll(pkg))
    }

    @Test
    fun `sessions are tracked per app`() {
        val t = tracker()
        val tiktok = "com.zhiliaoapp.musically"

        t.onAppForegrounded(pkg)
        now += 10_000
        repeat(3) { t.onDoomscrollScroll(pkg) }
        assertFalse(t.onDoomscrollScroll(pkg))

        t.onAppForegrounded(tiktok)
        now += 10_000
        assertFalse(t.onDoomscrollScroll(tiktok))
        assertFalse(t.onDoomscrollScroll(tiktok))
        assertTrue("TikTok has its own session", t.onDoomscrollScroll(tiktok))
    }

    @Test
    fun `first ever launch is armed immediately`() {
        val t = tracker()
        t.onAppForegrounded(pkg)
        now += 6_000
        repeat(2) { t.onDoomscrollScroll(pkg) }
        assertTrue(t.onDoomscrollScroll(pkg))
        assertEquals("no session", tracker().debugState("com.unknown"))
    }
}
