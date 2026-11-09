package com.nuvio.app.features.music

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MusicPlayerRepositoryTest {

    private fun track(id: String) = MusicTrack(
        videoId = id,
        title = "Song $id",
        artist = "Artist",
        thumbnail = null,
    )

    @AfterTest
    fun reset() {
        MusicPlayerRepository.close()
        MusicPlayerRepository.setAudioOnly(false)
    }

    @Test
    fun nextAndPreviousWalkTheQueueAndWrap() {
        val a = track("a")
        val b = track("b")
        val c = track("c")
        MusicPlayerRepository.play(a, listOf(a, b, c))

        assertEquals("b", MusicPlayerRepository.next()?.videoId)
        assertEquals("c", MusicPlayerRepository.next()?.videoId)
        assertEquals("a", MusicPlayerRepository.next()?.videoId)
        assertEquals("c", MusicPlayerRepository.previous()?.videoId)
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
        assertEquals("a", MusicPlayerRepository.current.value?.videoId)
        MusicPlayerRepository.close()
        assertNull(MusicPlayerRepository.current.value)
    }
}
