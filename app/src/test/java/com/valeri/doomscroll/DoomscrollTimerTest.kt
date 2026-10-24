package com.valeri.doomscroll

import com.valeri.doomscroll.service.DoomscrollTimer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class DoomscrollTimerTest {

    // 2026-10-02T12:00:00Z
    private var now = 1_790_942_400_000L
    private val day = LocalDate.of(2026, 10, 2)
    private val ig = "com.instagram.android"
    private val tiktok = "com.zhiliaoapp.musically"
    private fun timer() = DoomscrollTimer(maxGapMs = 60_000L, zone = { ZoneOffset.UTC }, now = { now })

    @Test
    fun `credits time between events on a doomscroll screen`() {
        val t = timer()
        t.onEvent(ig, true)
        now += 20_000
        t.onEvent(ig, true)
        now += 10_000
        t.onEvent(ig, true)

        assertEquals(mapOf((day to ig) to 30_000L), t.drain())
    }

    @Test
    fun `time on a legit screen is not counted`() {
        val t = timer()
        t.onEvent(ig, false)
        now += 30_000
        t.onEvent(ig, false)

        assertTrue(t.drain().isEmpty())
    }

    @Test
    fun `the interval that ends by leaving the feed still counts`() {
        val t = timer()
        t.onEvent(ig, true)
        now += 12_000
        t.onEvent(ig, false) // opened DMs: the 12s before that were on the feed
        now += 40_000
        t.onEvent(ig, false)

        assertEquals(mapOf((day to ig) to 12_000L), t.drain())
    }

    @Test
    fun `a gap longer than the limit is dropped, not credited`() {
        val t = timer()
        t.onEvent(ig, true)
        now += 5 * 60_000 // went home and came back; no events in between
        t.onEvent(ig, true)

        assertTrue(t.drain().isEmpty())
    }

    @Test
    fun `stop ends the interval at the intervention`() {
        val t = timer()
        t.onEvent(tiktok, true)
        now += 8_000
        t.stop()
        now += 30_000
        t.onEvent(tiktok, true) // back after the breath: starts fresh

        assertEquals(mapOf((day to tiktok) to 8_000L), t.drain())
    }

    @Test
    fun `drain empties the buffer`() {
        val t = timer()
        t.onEvent(ig, true)
        now += 20_000
        t.onEvent(ig, true)
        assertEquals(20_000L, t.pendingMs)

        t.drain()
        assertEquals(0L, t.pendingMs)
        assertTrue(t.drain().isEmpty())
    }

    @Test
    fun `a long gap is credited only as far as the app was really in front`() {
        var asked: Triple<String, Long, Long>? = null
        val t = DoomscrollTimer(
            maxGapMs = 60_000L,
            checkGapMs = 10_000L,
            foregroundMs = { pkg, from, to -> asked = Triple(pkg, from, to); 12_000L },
            zone = { ZoneOffset.UTC },
            now = { now },
        )
        val start = now
        t.onEvent(ig, true)
        now += 40_000 // went home for most of it
        t.onEvent(ig, true)

        assertEquals(Triple(ig, start, start + 40_000), asked)
        assertEquals(mapOf((day to ig) to 12_000L), t.drain())
    }

    @Test
    fun `short gaps never consult the usage log`() {
        val t = DoomscrollTimer(
            maxGapMs = 60_000L,
            checkGapMs = 10_000L,
            foregroundMs = { _, _, _ -> error("must not be asked") },
            zone = { ZoneOffset.UTC },
            now = { now },
        )
        t.onEvent(ig, true)
        now += 8_000
        t.onEvent(ig, true)
        assertEquals(mapOf((day to ig) to 8_000L), t.drain())
    }

    @Test
    fun `an unknown answer credits the whole gap as before`() {
        val t = DoomscrollTimer(maxGapMs = 60_000L, checkGapMs = 10_000L, zone = { ZoneOffset.UTC }, now = { now })
        t.onEvent(ig, true)
        now += 30_000
        t.onEvent(ig, true)
        assertEquals(mapOf((day to ig) to 30_000L), t.drain())
    }
}
