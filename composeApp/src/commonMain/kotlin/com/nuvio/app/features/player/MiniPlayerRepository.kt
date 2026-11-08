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
    /** Default width of the floating window, and the range the user can resize it within. */
    const val DefaultWidthDp = 200f
    const val MinWidthDp = 140f
    const val MaxWidthDp = 480f

    private val _stream = MutableStateFlow<MiniPlayerStream?>(null)
    val stream: StateFlow<MiniPlayerStream?> = _stream.asStateFlow()

    private val _widthDp = MutableStateFlow(DefaultWidthDp)

    /** The user-chosen window width, kept while streams are swapped and the window is reopened. */
    val widthDp: StateFlow<Float> = _widthDp.asStateFlow()

    // Where the window sits in the free area, 0..1 on each axis (1,1 is bottom-end). Fractions
    // keep the position sensible after a rotation or on a different screen size.
    private val _positionX = MutableStateFlow(1f)
    val positionX: StateFlow<Float> = _positionX.asStateFlow()

    private val _positionY = MutableStateFlow(1f)
    val positionY: StateFlow<Float> = _positionY.asStateFlow()

    fun setPositionFraction(x: Float, y: Float) {
        _positionX.value = x.coerceIn(0f, 1f)
        _positionY.value = y.coerceIn(0f, 1f)
    }

    fun show(stream: MiniPlayerStream) {
        if (stream.url.isBlank()) return
        _stream.value = stream
    }

    fun hide() {
        _stream.value = null
    }

    /** Resizes the window; clamped to a sane range, and to the screen by the window itself. */
    fun setWidthDp(widthDp: Float) {
        _widthDp.value = widthDp.coerceIn(MinWidthDp, MaxWidthDp)
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
