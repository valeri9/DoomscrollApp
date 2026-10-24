package com.valeri.doomscroll

import com.valeri.doomscroll.service.SessionTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionTrackerTest {

    private var now = 1_000_000L
    private fun tracker(reArmAfterMs: Long = 0L, cheatReopenWindowMs: Long = 60_000L) = SessionTracker(
        cooldownMs = 180_000L,
        minScrollEvents = 3,
        minDwellMs = 5_000L,
        reArmAfterMs = reArmAfterMs,
        cheatReopenWindowMs = cheatReopenWindowMs,
        now = { now },
    )

    private val pkg = "com.instagram.android"

    @Test
    fun `does not fire before the scroll threshold`() {
        val t = tracker()
        t.noteActivity(pkg)
        now += 10_000

        assertFalse(t.onDoomscrollScroll(pkg))
        assertFalse(t.onDoomscrollScroll(pkg))
        assertTrue("third scroll should trigger", t.onDoomscrollScroll(pkg))
    }

    @Test
    fun `does not fire before the dwell time even with enough scrolls`() {
        val t = tracker()
        t.noteActivity(pkg)
        now += 1_000 // under the 5s dwell

        repeat(10) { assertFalse(t.onDoomscrollScroll(pkg)) }

        now += 5_000
        assertTrue(t.onDoomscrollScroll(pkg))
    }

    @Test
    fun `fires only once per session when re-arm is disabled`() {
        val t = tracker(reArmAfterMs = 0L)
        t.noteActivity(pkg)
        now += 10_000
        repeat(2) { t.onDoomscrollScroll(pkg) }
        assertTrue(t.onDoomscrollScroll(pkg))

        // Keep scrolling, keep sending activity, for a long time — no second nudge.
        repeat(20) {
            now += 30_000
            t.noteActivity(pkg)
            assertFalse("must not re-fire with re-arm disabled", t.onDoomscrollScroll(pkg))
        }
    }

    @Test
    fun `a short trip away does not re-arm`() {
        val t = tracker()
        t.noteActivity(pkg)
        now += 10_000
        repeat(3) { t.onDoomscrollScroll(pkg) }

        now += 60_000 // under the 3 minute cooldown, no events from the app meanwhile
        t.noteActivity(pkg)
        now += 10_000

        repeat(10) { assertFalse("cooldown not elapsed", t.onDoomscrollScroll(pkg)) }
    }

    @Test
    fun `re-arms after the cooldown elapses with no events`() {
        val t = tracker()
        t.noteActivity(pkg)
        now += 10_000
        repeat(3) { t.onDoomscrollScroll(pkg) }

        now += 200_000 // past the 3 minute cooldown, silence the whole time
        t.noteActivity(pkg)
        now += 10_000

        assertFalse(t.onDoomscrollScroll(pkg))
        assertFalse(t.onDoomscrollScroll(pkg))
        assertTrue("should be armed again", t.onDoomscrollScroll(pkg))
    }

    @Test
    fun `switching to a legit screen in the same app does not reset the session`() {
        // Regression: moving from Reels to DMs and back must not grant a fresh session.
        // The framework still delivers events for com.instagram.android the whole time —
        // TYPE_WINDOW_STATE_CHANGED into the DM thread, scrolling the thread, back to Reels —
        // so noteActivity keeps firing and the gap to the last event never opens.
        val t = tracker()
        t.noteActivity(pkg)
        now += 10_000
        repeat(3) { t.onDoomscrollScroll(pkg) }
        assertFalse("armed, but disarmed by the trigger above", t.onDoomscrollScroll(pkg))

        // User checks DMs for 90 seconds — well under the 3 minute cooldown — generating
        // ordinary (non-scroll) accessibility events the whole time.
        repeat(9) {
            now += 10_000
            t.noteActivity(pkg)
        }
        assertFalse("a legit screen visit must not re-arm the session", t.onDoomscrollScroll(pkg))

        // Back on Reels immediately after — still the same session.
        now += 1_000
        t.noteActivity(pkg)
        assertFalse("returning to the feed inside the same visit must not re-arm either", t.onDoomscrollScroll(pkg))
    }

    @Test
    fun `re-arms during one long sitting once the interval elapses`() {
        val t = tracker(reArmAfterMs = 600_000L) // 10 minutes
        t.noteActivity(pkg)
        now += 10_000
        repeat(3) { t.onDoomscrollScroll(pkg) }
        assertFalse(t.onDoomscrollScroll(pkg))

        // Nine minutes of continued scrolling, all inside the same app, no gap ever opening.
        repeat(53) {
            now += 10_000
            t.noteActivity(pkg)
            assertFalse("too soon for a second nudge", t.onDoomscrollScroll(pkg))
        }

        // The loop above only reached 530s since the trigger; push past the 600s mark.
        // Crossing it re-arms the session but — same as the very first arm — still requires
        // its own dwell time and scroll count before actually firing again, so one scroll
        // right at the boundary is not enough on its own.
        now += 80_000
        t.noteActivity(pkg)
        assertFalse("re-armed, but needs its own dwell+scrolls before firing", t.onDoomscrollScroll(pkg))
        assertFalse(t.onDoomscrollScroll(pkg))
        now += 6_000
        assertTrue("should nudge again after a long sitting", t.onDoomscrollScroll(pkg))
    }

    @Test
    fun `sessions are tracked per app`() {
        val t = tracker()
        val tiktok = "com.zhiliaoapp.musically"

        t.noteActivity(pkg)
        now += 10_000
        repeat(3) { t.onDoomscrollScroll(pkg) }
        assertFalse(t.onDoomscrollScroll(pkg))

        t.noteActivity(tiktok)
        now += 10_000
        assertFalse(t.onDoomscrollScroll(tiktok))
        assertFalse(t.onDoomscrollScroll(tiktok))
        assertTrue("TikTok has its own session", t.onDoomscrollScroll(tiktok))
    }

    @Test
    fun `triggers when the service starts with the app already in the foreground`() {
        // No noteActivity call at all before the first scroll: the service was enabled, or
        // restarted by the system, while the user was already scrolling. The dwell clock must
        // still start from the first scroll we see, or the app silently never fires.
        val t = tracker()

        assertFalse(t.onDoomscrollScroll(pkg))
        now += 6_000
        assertFalse(t.onDoomscrollScroll(pkg))
        assertTrue("must still trigger without a prior activity event", t.onDoomscrollScroll(pkg))
    }

    @Test
    fun `reopening right after closing forces the prompt back up`() {
        val t = tracker()
        t.noteClosedByIntervention()
        now += 5_000 // well inside the 60s cheat window

        assertTrue("reopen inside the window must force it", t.consumeForceReopen(pkg))
    }

    @Test
    fun `reopening well after closing does not force the prompt`() {
        val t = tracker()
        t.noteClosedByIntervention()
        now += 90_000 // past the 60s cheat window

        assertFalse("a real return trip later must not force it", t.consumeForceReopen(pkg))
    }

    @Test
    fun `the forced reopen only fires once`() {
        val t = tracker()
        t.noteClosedByIntervention()
        now += 5_000

        assertTrue(t.consumeForceReopen(pkg))
        assertFalse("already consumed; a second check must not re-fire", t.consumeForceReopen(pkg))
    }

    @Test
    fun `closing one app and opening another forces the prompt there`() {
        // Regression: caught on Reels, tap Close, open TikTok to keep scrolling. TikTok's own
        // session is fresh and armed, so without a global mark it would need a full
        // scroll/dwell run before anything fired.
        val t = tracker()
        val tiktok = "com.zhiliaoapp.musically"
        t.noteActivity(pkg)
        now += 10_000
        repeat(3) { t.onDoomscrollScroll(pkg) }
        t.noteClosedByIntervention()
        now += 4_000

        assertTrue("hopping to another watched app must force it", t.consumeForceReopen(tiktok))
        assertFalse("and that forced prompt counts as TikTok's trigger", t.onDoomscrollScroll(tiktok))
    }

    @Test
    fun `an app that was never closed via the button never force-fires`() {
        val t = tracker()
        t.noteActivity(pkg)
        now += 5_000

        assertFalse(t.consumeForceReopen(pkg))
    }

    @Test
    fun `landing on an instant screen does not fire before the grace period`() {
        // Instagram reopening on Reels, or a reel opened from a DM: time to tap away first.
        val t = tracker()
        t.noteActivity(pkg)
        assertFalse(t.onInstantScreen(pkg))
        repeat(9) {
            now += 1_000
            t.noteActivity(pkg)
            assertFalse("still inside the grace period", t.onInstantScreen(pkg))
        }
        now += 1_000
        t.noteActivity(pkg)
        assertTrue("10s on Reels without leaving should fire", t.onInstantScreen(pkg))
    }

    @Test
    fun `a swipe to another reel fires straight away`() {
        val t = tracker()
        t.noteActivity(pkg)
        assertFalse(t.onInstantScreen(pkg, itemIndex = 4))
        now += 500
        assertFalse("same position is layout, not a swipe", t.onInstantScreen(pkg, itemIndex = 4))
        now += 500
        assertTrue(t.onInstantScreen(pkg, itemIndex = 5))
    }

    @Test
    fun `leaving the instant screen inside the grace period never fires`() {
        val t = tracker()
        t.noteActivity(pkg)
        assertFalse(t.onInstantScreen(pkg))
        now += 3_000
        t.noteActivity(pkg)
        t.onLegitScreen(pkg) // tapped through to DMs

        repeat(60) {
            now += 1_000
            t.noteActivity(pkg)
            t.onLegitScreen(pkg)
        }
        // Back to Reels: the grace starts over rather than counting the time in DMs.
        now += 2_000
        t.noteActivity(pkg)
        assertFalse(t.onInstantScreen(pkg))
    }

    @Test
    fun `a gap in events on the instant screen restarts the grace`() {
        val t = tracker()
        t.noteActivity(pkg)
        assertFalse(t.onInstantScreen(pkg))
        now += 8_000 // went home on Reels, came back; well under the cooldown
        t.noteActivity(pkg)
        assertFalse("time away must not count towards the grace", t.onInstantScreen(pkg))
    }

    @Test
    fun `an instant screen still fires only once per session`() {
        // Content changes arrive constantly while a reel plays; each one checks again.
        val t = tracker()
        t.noteActivity(pkg)
        t.onInstantScreen(pkg, itemIndex = 0)
        assertTrue(t.onInstantScreen(pkg, itemIndex = 1))
        repeat(50) {
            now += 1_000
            t.noteActivity(pkg)
            assertFalse(t.onInstantScreen(pkg, itemIndex = it + 2))
        }
    }

    @Test
    fun `feed scrolls just after a story are ignored`() {
        // Swiping between friends' stories briefly exposes the feed underneath.
        val t = tracker()
        t.noteActivity(pkg)
        now += 30_000
        t.onLegitScreen(pkg)
        repeat(10) {
            now += 100
            assertFalse("inside the hold", t.onDoomscrollScroll(pkg))
        }
    }

    @Test
    fun `coming back from stories starts the feed count over`() {
        val t = tracker()
        t.noteActivity(pkg)
        now += 10_000
        repeat(2) { assertFalse(t.onDoomscrollScroll(pkg)) } // one short of the threshold

        t.onLegitScreen(pkg) // a minute of stories
        now += 60_000
        t.noteActivity(pkg)
        t.onLegitScreen(pkg)

        now += 2_000 // past the hold, but dwell restarts from leaving the story viewer
        assertFalse("dwell restarted", t.onDoomscrollScroll(pkg))
        now += 5_000
        t.noteActivity(pkg)
        assertFalse("the two scrolls before stories no longer count", t.onDoomscrollScroll(pkg))
        assertTrue("a full count after returning fires", t.onDoomscrollScroll(pkg))
    }

    @Test
    fun `a legit screen does not hand back a trigger that already fired`() {
        val t = tracker(reArmAfterMs = 0L)
        t.noteActivity(pkg)
        now += 10_000
        repeat(3) { t.onDoomscrollScroll(pkg) }

        now += 5_000
        t.noteActivity(pkg)
        t.onLegitScreen(pkg) // ducking into DMs and back
        now += 5_000
        t.noteActivity(pkg)
        repeat(10) {
            now += 1_000
            assertFalse(t.onDoomscrollScroll(pkg))
        }
    }

    @Test
    fun `a trigger that fails its fresh check does not fire or count`() {
        // A cached feed result while the screen is really a DM thread now.
        val t = tracker()
        t.noteActivity(pkg)
        now += 10_000
        repeat(2) { assertFalse(t.onDoomscrollScroll(pkg)) }
        assertFalse(t.onDoomscrollScroll(pkg, confirm = { false }))
        assertTrue("still armed, and the rejected scroll wasn't counted", t.onDoomscrollScroll(pkg))
    }

    @Test
    fun `an instant trigger that fails its fresh check stays armed`() {
        val t = tracker()
        t.noteActivity(pkg)
        t.onInstantScreen(pkg, itemIndex = 0)
        assertFalse(t.onInstantScreen(pkg, itemIndex = 1, confirm = { false }))
        assertTrue(t.onInstantScreen(pkg, itemIndex = 2))
    }

    @Test
    fun `first ever launch is armed immediately`() {
        val t = tracker()
        t.noteActivity(pkg)
        now += 6_000
        repeat(2) { t.onDoomscrollScroll(pkg) }
        assertTrue(t.onDoomscrollScroll(pkg))
        assertEquals("no session", tracker().debugState("com.unknown"))
    }
}
