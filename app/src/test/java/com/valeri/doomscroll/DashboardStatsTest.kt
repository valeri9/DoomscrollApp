package com.valeri.doomscroll

import com.valeri.doomscroll.data.db.DailyAppUsageEntity
import com.valeri.doomscroll.data.db.DoomscrollTimeEntity
import com.valeri.doomscroll.data.db.InterventionEntity
import com.valeri.doomscroll.data.repo.DoomscrollRepository
import com.valeri.doomscroll.ui.DashboardStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class DashboardStatsTest {

    private val today = LocalDate.of(2026, 10, 2)
    private val ig = "com.instagram.android"
    private val tiktok = "com.zhiliaoapp.musically"
    private val youtube = "com.google.android.youtube"
    private val watched = setOf(ig, tiktok)

    private fun usage(daysAgo: Long, pkg: String, minutes: Long) =
        DailyAppUsageEntity(today.minusDays(daysAgo).toString(), pkg, minutes * 60_000)

    private fun doom(daysAgo: Long, pkg: String, minutes: Long) =
        DoomscrollTimeEntity(today.minusDays(daysAgo).toString(), pkg, minutes * 60_000)

    private fun caught(
        daysAgo: Long,
        pkg: String = ig,
        hour: Int = 12,
        continued: Boolean = false,
        completed: Boolean = true,
        context: String = "clips_viewer_view_pager",
    ) = InterventionEntity(
        packageName = pkg,
        contextLabel = context,
        triggeredAt = 0L,
        localDate = today.minusDays(daysAgo).toString(),
        hourOfDay = hour,
        isNightMode = false,
        breathingSeconds = 10,
        reasonLabel = null,
        reasonText = null,
        continuedAnyway = continued,
        completedAt = if (completed) 1L else null,
    )

    private fun build(
        usage: List<DailyAppUsageEntity> = emptyList(),
        doomscroll: List<DoomscrollTimeEntity> = emptyList(),
        interventions: List<InterventionEntity> = emptyList(),
        trackingSince: LocalDate? = null,
        range: Int = 7,
    ) = DashboardStats.build(today, range, watched, usage, doomscroll, interventions, trackingSince)

    @Test
    fun `weekly comparison uses full days and leaves today out`() {
        val s = build(
            usage = listOf(usage(0, ig, 500), usage(1, ig, 30), usage(7, ig, 20), usage(8, ig, 90), usage(14, ig, 10)),
        )
        assertEquals("days 1..7 ago", 50 * 60_000L, s.weekAppTime.current)
        assertEquals("days 8..14 ago", 100 * 60_000L, s.weekAppTime.previous)
    }

    @Test
    fun `only watched apps count as app time, but the whole phone counts for today`() {
        val s = build(usage = listOf(usage(0, ig, 30), usage(0, tiktok, 15), usage(0, youtube, 120)))
        assertEquals(45 * 60_000L, s.appTodayMs)
        assertEquals(165 * 60_000L, s.phoneTodayMs)
        assertEquals(listOf(ig, tiktok), s.apps.map { it.packageName })
    }

    @Test
    fun `doomscroll time isn't compared against a week before tracking existed`() {
        val partial = build(doomscroll = listOf(doom(3, ig, 40)), trackingSince = today.minusDays(10))
        assertEquals(40 * 60_000L, partial.weekDoomscroll.current)
        assertNull("tracking only covered part of the earlier week", partial.weekDoomscroll.previous)

        val full = build(doomscroll = listOf(doom(3, ig, 40), doom(9, ig, 60)), trackingSince = today.minusDays(20))
        assertEquals(60 * 60_000L, full.weekDoomscroll.previous)
    }

    @Test
    fun `close rate ignores prompts that never finished`() {
        val s = build(
            interventions = listOf(
                caught(1), caught(2), caught(3, continued = true), caught(4, completed = false),
            ),
        )
        assertEquals(2, s.closed)
        assertEquals(1, s.continued)
        assertEquals(2f / 3f, s.closeRate!!, 0.0001f)
        assertEquals(4, s.weekCaught.current)
    }

    @Test
    fun `no earlier interventions means nothing to compare against`() {
        val s = build(interventions = listOf(caught(2)))
        assertNull(s.weekCaught.previous)
        assertNull(s.weekCloseRate.previous)
    }

    @Test
    fun `counts prompts forced by reopening or switching apps`() {
        val s = build(
            interventions = listOf(
                caught(0),
                caught(0, context = DoomscrollRepository.CONTEXT_REOPENED),
                caught(0, pkg = tiktok, context = DoomscrollRepository.CONTEXT_SWITCHED),
            ),
        )
        assertEquals(2, s.hops)
        assertEquals(3, s.caughtToday)
    }

    @Test
    fun `groups interventions by hour within the range only`() {
        val s = build(
            interventions = listOf(caught(0, hour = 23), caught(2, hour = 23), caught(5, hour = 8), caught(20, hour = 23)),
            range = 7,
        )
        assertEquals(2, s.caughtByHour[23])
        assertEquals(1, s.caughtByHour[8])
        assertEquals(3, s.caughtByHour.sum())
    }

    @Test
    fun `daily bars cover the range ending today, with gaps filled`() {
        val s = build(usage = listOf(usage(0, ig, 10)), doomscroll = listOf(doom(0, ig, 4)), range = 7)
        assertEquals(7, s.days.size)
        assertEquals(today, s.days.last().date)
        assertEquals(10 * 60_000L, s.days.last().appMs)
        assertEquals(4 * 60_000L, s.days.last().doomscrollMs)
        assertEquals(0L, s.days.first().appMs)
    }
}
