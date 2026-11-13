package com.nuvio.app.features.livetv.virtual

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Lucid TV's schedule is deterministic and contiguous on every platform. */
class VirtualScheduleTest {

    private fun item(id: String, runtimeMinutes: Int? = null) = VirtualItem(
        id = id,
        type = "movie",
        title = id,
        runtimeMs = runtimeMinutes?.times(60_000L),
    )

    @Test
    fun hashIsStableAndNonNegative() {
        assertEquals(193_485_963, VirtualSchedule.stableHash("abc"))
        assertEquals(VirtualSchedule.stableHash("Lucid TV"), VirtualSchedule.stableHash("Lucid TV"))
        assertTrue(VirtualSchedule.stableHash("anything at all") >= 0)
    }

    @Test
    fun channelOffsetsStayInsideTheSlot() {
        val slot = 90L * 60_000L
        listOf("vc_a", "vc_b", "virtual:vc_c", "").forEach { id ->
            val offset = VirtualSchedule.offsetMs(id, slot)
            assertTrue(offset in 0 until slot, "$id offset $offset")
        }
    }

    @Test
    fun slotContainsNowAndBoundariesAreExact() {
        val slotMs = 90L * 60_000L
        val now = 1_763_000_000_000L
        val slot = VirtualSchedule.slotAt(now, "vc_test", slotMs)
        assertTrue(now >= slot.startMs && now < slot.endMs)
        assertEquals(slot.startMs, VirtualSchedule.slotAt(slot.startMs, "vc_test", slotMs).startMs)
        val next = VirtualSchedule.slotAt(slot.endMs, "vc_test", slotMs)
        assertEquals(slot.endMs, next.startMs)
        assertEquals(slot.number + 1, next.number)
    }

    @Test
    fun negativeEpochsStillWork() {
        val slotMs = 45L * 60_000L
        val now = -10_000_000L
        val slot = VirtualSchedule.slotAt(now, "vc_negative", slotMs)
        assertTrue(now >= slot.startMs && now < slot.endMs)
        assertEquals(6L, VirtualSchedule.floorMod(-1L, 7L))
        assertEquals(-2L, VirtualSchedule.floorDiv(-7L, 5L))
    }

    @Test
    fun saltChangesTheItemButNotTheTiming() {
        val slotMs = 60L * 60_000L
        val now = 1_763_000_000_000L
        val slot = VirtualSchedule.slotAt(now, "vc_salted", slotMs)
        val items = (1..50).map { item("i$it") }
        val picks = (0..9).map { salt -> VirtualSchedule.itemIndex("vc_salted", slot.number, items.size, salt) }
        assertEquals(slot.startMs, VirtualSchedule.slotAt(now, "vc_salted", slotMs).startMs)
        assertTrue(picks.all { it in items.indices })
        assertTrue(picks.toSet().size > 1, "salt should move the pick")
    }

    @Test
    fun rotateProgramsAreContiguous() {
        val items = listOf(item("a"), item("b"), item("c"))
        val slotMs = 90L * 60_000L
        val from = 1_763_000_000_000L
        val programs = VirtualSchedule.rotatePrograms(
            channelId = "vc_rotate",
            items = items,
            slotMs = slotMs,
            salt = 0,
            fromMs = from,
            toMs = from + 6 * slotMs,
        )
        assertTrue(programs.isNotEmpty())
        assertEquals(
            0L,
            VirtualSchedule.floorMod(
                programs.first().startMs - VirtualSchedule.offsetMs("vc_rotate", slotMs),
                slotMs,
            ),
        )
        for (i in 1 until programs.size) {
            assertEquals(programs[i - 1].endMs, programs[i].startMs)
        }
        assertTrue(programs.first().startMs <= from)
        assertTrue(programs.last().endMs >= from + 5 * slotMs)
    }

    @Test
    fun loopSegmentsWrapWithoutGapsOrOverlaps() {
        val items = listOf(item("a", 60), item("b", 120), item("c", 90))
        val cycle = (60 + 120 + 90) * 60_000L
        val from = 1_763_000_000_000L
        val segments = VirtualSchedule.playoutSegments(
            anchorMs = 0L,
            items = items,
            fallbackDurationMs = 30 * 60_000L,
            fromMs = from,
            toMs = from + 2 * cycle,
        )
        assertTrue(segments.size >= 6)
        for (i in 1 until segments.size) {
            assertEquals(segments[i - 1].endMs, segments[i].startMs)
        }
        val now = from + 10_000L
        val current = VirtualSchedule.playoutAt(now, 0L, items, 30 * 60_000L)
        assertNotNull(current)
        assertTrue(now >= current.startMs && now < current.endMs)
    }

    @Test
    fun joinRuleStartsAtTheBeginningOrTheCap() {
        val start = 1_000_000L
        val duration = 1_000_000L
        assertEquals(0L, VirtualSchedule.joinOffsetMs(start + 500, start, duration, VirtualJoin(maxStartPercent = 0), "vc"))
        assertEquals(500L, VirtualSchedule.joinOffsetMs(start + 500, start, duration, VirtualJoin(maxStartPercent = -1), "vc"))
        assertEquals(500L, VirtualSchedule.joinOffsetMs(start + 500, start, duration, VirtualJoin(maxStartPercent = 20), "vc"))
        // Past the cap: without jitter it joins at exactly 20%; with jitter it is deterministic and inside the cap.
        val noJitter = VirtualSchedule.joinOffsetMs(start + 900_000, start, duration, VirtualJoin(maxStartPercent = 20, jitter = false), "vc")
        assertEquals(200_000L, noJitter)
        val jitter1 = VirtualSchedule.joinOffsetMs(start + 900_000, start, duration, VirtualJoin(maxStartPercent = 20, jitter = true), "vc")
        val jitter2 = VirtualSchedule.joinOffsetMs(start + 900_000, start, duration, VirtualJoin(maxStartPercent = 20, jitter = true), "vc")
        assertEquals(jitter1, jitter2)
        assertTrue(jitter1 in 0..200_000L)
    }

    @Test
    fun epgTitlesCarryTheEpisodeAndYear() {
        val episode = VirtualItem(
            id = "ep",
            type = "series",
            title = "Seinfeld",
            year = 1989,
            season = 2,
            episode = 4,
            episodeTitle = "The Phone Message",
        )
        val title = VirtualSchedule.programTitle(episode)
        assertTrue(title.contains("Seinfeld"))
        assertTrue(title.contains("S2E4"))
        assertTrue(title.contains("(1989)"))
        assertEquals("S1E2", VirtualSchedule.episodeLabel(1, 2))
        assertEquals("", VirtualSchedule.episodeLabel(null, 2))
    }
}
