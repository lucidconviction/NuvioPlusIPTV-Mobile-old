package com.nuvio.app.features.music

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Downloads a song's audio-only YouTube stream for offline playback. */
internal object MusicDownloadsRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _active = MutableStateFlow<Set<String>>(emptySet())
    val active: StateFlow<Set<String>> = _active.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun clearMessage() {
        _message.value = null
    }

    fun isDownloading(trackId: String): Boolean = trackId in _active.value

    fun isDownloaded(trackId: String): Boolean = MusicLibraryRepository.downloadFor(trackId) != null

    fun download(track: MusicTrack) {
        if (isDownloading(track.id) || isDownloaded(track.id)) return
        _active.value = _active.value + track.id
        _message.value = null
        scope.launch {
            try {
                val resolved = MusicRepository.resolve(track).getOrElse { error ->
                    _message.value = error.message ?: "Couldn't download this song."
                    return@launch
                }
                val audioUrl = resolved.audioUrl
                    ?: return@launch finish(track.id, "This song has no downloadable audio stream.")
                val path = MusicPlatform.downloadAudio(audioUrl, track.id, resolved.headers)
                    ?: return@launch finish(track.id, "The download failed.")
                MusicLibraryRepository.addDownloaded(track.copy(localFile = path), path)
                finish(track.id, "Downloaded \"${track.title}\"")
            } catch (error: Exception) {
                finish(track.id, error.message ?: "The download failed.")
            }
        }
    }

    private fun finish(trackId: String, message: String) {
        _active.value = _active.value - trackId
        _message.value = message
    }
}
