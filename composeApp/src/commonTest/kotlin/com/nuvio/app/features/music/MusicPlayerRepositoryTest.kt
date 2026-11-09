package com.nuvio.app.features.music

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MusicPlayerRepositoryTest {

    private fun track(id: String) = MusicTrack(
        id = id,
        title = "Song $id",
        artist = "Artist",
        thumbnail = null,
    )

    @AfterTest
    fun reset() {
        MusicPlayerRepository.close()
        MusicPlayerRepository.clearQueue()
        MusicPlayerRepository.setAudioOnly(false)
        while (MusicPlayerRepository.repeat.value != MusicRepeatMode.OFF) MusicPlayerRepository.cycleRepeat()
        if (MusicPlayerRepository.shuffle.value) MusicPlayerRepository.toggleShuffle()
    }

    @Test
    fun nextAndPreviousWalkTheQueueAndWrapWhenRepeatingAll() {
        val a = track("a")
        val b = track("b")
        val c = track("c")
        MusicPlayerRepository.play(a, listOf(a, b, c))
        MusicPlayerRepository.cycleRepeat() // ALL
        assertEquals("b", MusicPlayerRepository.next()?.id)
        assertEquals("c", MusicPlayerRepository.next()?.id)
        assertEquals("a", MusicPlayerRepository.next()?.id)
        assertEquals("c", MusicPlayerRepository.previous()?.id)
    }

    @Test
    fun repeatOneKeepsTheCurrentTrackAndRepeatOffStopsAtTheEnd() {
        val a = track("a")
        val b = track("b")
        MusicPlayerRepository.play(a, listOf(a, b))
        assertEquals("b", MusicPlayerRepository.next()?.id)
        // OFF at the last track: no wrap.
        assertNull(MusicPlayerRepository.next())
        MusicPlayerRepository.play(a, listOf(a, b))
        MusicPlayerRepository.cycleRepeat() // ALL
        MusicPlayerRepository.cycleRepeat() // ONE
        assertEquals(MusicRepeatMode.ONE, MusicPlayerRepository.repeat.value)
        assertEquals("a", MusicPlayerRepository.advanceOnEnd()?.id)
    }

    @Test
    fun audioToggleIsSharedState() {
        assertFalse(MusicPlayerRepository.audioOnly.value)
        MusicPlayerRepository.toggleAudioOnly()
        assertTrue(MusicPlayerRepository.audioOnly.value)
        MusicPlayerRepository.setAudioOnly(false)
        assertFalse(MusicPlayerRepository.audioOnly.value)
    }

    @Test
    fun closingClearsTheCurrentTrack() {
        MusicPlayerRepository.play(track("a"), emptyList())
        assertEquals("a", MusicPlayerRepository.current.value?.id)
        MusicPlayerRepository.close()
        assertNull(MusicPlayerRepository.current.value)
    }
}
