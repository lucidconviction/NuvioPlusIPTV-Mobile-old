package com.nuvio.app.features.music

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The music session: what is playing, the row it came from, and whether the video is shown or
 * only the audio is heard. The toggle lives here so the Music page and the player stay in step.
 */
internal object MusicPlayerRepository {

    private val _current = MutableStateFlow<MusicTrack?>(null)
    val current: StateFlow<MusicTrack?> = _current.asStateFlow()

    private val _queue = MutableStateFlow<List<MusicTrack>>(emptyList())
    val queue: StateFlow<List<MusicTrack>> = _queue.asStateFlow()

    private val _audioOnly = MutableStateFlow(false)
    val audioOnly: StateFlow<Boolean> = _audioOnly.asStateFlow()

    fun play(track: MusicTrack, queue: List<MusicTrack>) {
        _queue.value = queue.ifEmpty { listOf(track) }
        _current.value = track
    }

    fun close() {
        _current.value = null
    }

    fun setAudioOnly(audioOnly: Boolean) {
        _audioOnly.value = audioOnly
    }

    fun toggleAudioOnly() {
        _audioOnly.value = !_audioOnly.value
    }

    fun next(): MusicTrack? = step(1)

    fun previous(): MusicTrack? = step(-1)

    private fun step(delta: Int): MusicTrack? {
        val queue = _queue.value
        if (queue.isEmpty()) return null
        val currentId = _current.value?.videoId
        val index = queue.indexOfFirst { it.videoId == currentId }.takeIf { it >= 0 } ?: 0
        val next = queue[(index + delta).mod(queue.size)]
        _current.value = next
        return next
    }
}
