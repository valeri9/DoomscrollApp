package com.valeri.doomscroll

import com.valeri.doomscroll.ui.formatDuration
import org.junit.Assert.assertEquals
import org.junit.Test

class FormatDurationTest {
    @Test
    fun `under a minute shows seconds instead of 0m`() {
        assertEquals("7s", formatDuration(7_606))
        assertEquals("59s", formatDuration(59_999))
    }

    @Test
    fun `minutes and hours`() {
        assertEquals("0m", formatDuration(0))
        assertEquals("0m", formatDuration(77))
        assertEquals("1m", formatDuration(60_000))
        assertEquals("2h 5m", formatDuration((2 * 60 + 5) * 60_000L))
    }
}
