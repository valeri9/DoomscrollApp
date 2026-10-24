package com.valeri.doomscroll

import com.valeri.doomscroll.usage.ForegroundTime
import com.valeri.doomscroll.usage.ForegroundTime.Event
import com.valeri.doomscroll.usage.ForegroundTime.Kind
import org.junit.Assert.assertEquals
import org.junit.Test

class ForegroundTimeTest {

    private val ig = "com.instagram.android"
    private val main = "com.instagram.mainactivity.MainActivity"

    private fun fg(vararg events: Event) = ForegroundTime.foregroundMs(events.toList(), ig, 0L, 40_000L)

    @Test
    fun `no events means it stayed in front throughout`() {
        assertEquals(40_000L, fg())
    }

    @Test
    fun `a trip home and back is not counted`() {
        assertEquals(
            25_000L,
            fg(
                Event(10_000, Kind.PAUSED, ig, main),
                Event(10_500, Kind.STOPPED, ig, main),
                Event(25_000, Kind.RESUMED, ig, main),
            ),
        )
    }

    @Test
    fun `moving between the app's own activities is not leaving`() {
        val viewer = "com.instagram.ViewerActivity"
        // Only the 50ms hand-over between the two is lost, not the rest of the gap.
        assertEquals(
            39_950L,
            fg(
                Event(10_000, Kind.PAUSED, ig, main),
                Event(10_050, Kind.RESUMED, ig, viewer),
                Event(10_400, Kind.STOPPED, ig, main),
            ),
        )
    }

    @Test
    fun `the screen going off ends it`() {
        assertEquals(5_000L, fg(Event(5_000, Kind.SCREEN_OFF, null, null)))
    }

    @Test
    fun `other apps' events are ignored`() {
        assertEquals(40_000L, fg(Event(10_000, Kind.PAUSED, "com.other", "X")))
    }
}
