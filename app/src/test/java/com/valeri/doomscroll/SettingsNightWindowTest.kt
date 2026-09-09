package com.valeri.doomscroll

import com.valeri.doomscroll.data.Settings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class SettingsNightWindowTest {

    @Test
    fun `default midnight to eight`() {
        val s = Settings()
        assertTrue(s.isNight(LocalTime.of(0, 30)))
        assertTrue(s.isNight(LocalTime.of(3, 0)))
        assertTrue(s.isNight(LocalTime.of(7, 59)))
        assertFalse(s.isNight(LocalTime.of(8, 0)))
        assertFalse(s.isNight(LocalTime.of(14, 0)))
        assertFalse(s.isNight(LocalTime.of(23, 59)))
    }

    @Test
    fun `window wrapping past midnight`() {
        val s = Settings(nightStartMinute = 22 * 60, nightEndMinute = 6 * 60)
        assertTrue("22:30 is inside", s.isNight(LocalTime.of(22, 30)))
        assertTrue("02:00 is inside", s.isNight(LocalTime.of(2, 0)))
        assertTrue("05:59 is inside", s.isNight(LocalTime.of(5, 59)))
        assertFalse("06:00 is outside", s.isNight(LocalTime.of(6, 0)))
        assertFalse("noon is outside", s.isNight(LocalTime.of(12, 0)))
        assertFalse("21:59 is outside", s.isNight(LocalTime.of(21, 59)))
    }

    @Test
    fun `night window start is in the past`() {
        val s = Settings()
        val now = System.currentTimeMillis()
        val start = s.nightWindowStartMillis(now)
        assertTrue("window start must not be in the future", start <= now)
        assertTrue("window start within the last 24h", now - start < 24 * 60 * 60 * 1000L)
    }

    @Test
    fun `derived durations`() {
        val s = Settings(cooldownMinutes = 3, minDwellSeconds = 5)
        assertTrue(s.cooldownMs == 180_000L)
        assertTrue(s.minDwellMs == 5_000L)
    }
}
