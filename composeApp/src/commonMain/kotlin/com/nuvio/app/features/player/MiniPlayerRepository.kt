package com.nuvio.app.features.player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * One stream playing in the in-app picture-in-picture window. It keeps playing while the user
 * searches and moves around the app; the player screen is gone by then, so it carries everything
 * needed to rebuild the playback.
 */
data class MiniPlayerStream(
    val profileId: Int,
    val url: String,
    val audioUrl: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val responseHeaders: Map<String, String> = emptyMap(),
    val streamType: String? = null,
    val title: String,
    val subtitle: String? = null,
    val logo: String? = null,
    val providerName: String = "",
    val streamTitle: String = "",
    val providerAddonId: String? = null,
    val contentType: String? = null,
    val videoId: String? = null,
    val parentMetaId: String = "",
    val parentMetaType: String = "",
    val initialPositionMs: Long = 0L,
)

/** The in-app PiP stream, kept above navigation so it survives every screen change. */
object MiniPlayerRepository {
    private val _stream = MutableStateFlow<MiniPlayerStream?>(null)
    val stream: StateFlow<MiniPlayerStream?> = _stream.asStateFlow()

    fun show(stream: MiniPlayerStream) {
        if (stream.url.isBlank()) return
        _stream.value = stream
    }

    fun hide() {
        _stream.value = null
    }
}

/** Rebuilds a full player launch for the expand button. */
internal fun MiniPlayerStream.toPlayerLaunch(): PlayerLaunch = PlayerLaunch(
    profileId = profileId,
    title = title,
    sourceUrl = url,
    sourceAudioUrl = audioUrl,
    sourceHeaders = headers,
    sourceResponseHeaders = responseHeaders,
    streamType = streamType,
    logo = logo,
    streamTitle = streamTitle.ifBlank { title },
    streamSubtitle = subtitle,
    providerName = providerName,
    providerAddonId = providerAddonId,
    contentType = contentType,
    videoId = videoId,
    parentMetaId = parentMetaId,
    parentMetaType = parentMetaType,
    initialPositionMs = initialPositionMs,
)
