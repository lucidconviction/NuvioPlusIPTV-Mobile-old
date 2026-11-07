package com.nuvio.app.features.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.features.livetv.model.LiveChannel
import com.nuvio.app.features.livetv.player.LiveTvPlayback
import com.nuvio.app.features.livetv.ui.LiveTvPlayerOverlay
import com.nuvio.app.features.livetv.ui.LiveTvViewState

/** Whether a Live TV channel (or a catch-up replay of one) is playing. */
internal val PlayerScreenRuntime.isLiveTv: Boolean
    get() = LiveTvPlayback.owns(activeProviderAddonId)

/** The title for the controls: the channel now on screen, which changes as you switch channels. */
internal fun PlayerScreenRuntime.liveTitleOr(fallback: String): String =
    activeStreamTitle.takeIf { isLiveTv && it.isNotBlank() } ?: fallback

/** The logo for the pause screen: the channel now on screen. */
internal fun PlayerScreenRuntime.liveLogoOr(fallback: String?): String? {
    if (!isLiveTv) return fallback
    val key = activeVideoId ?: return fallback
    return LiveTvViewState.list.value.visible.firstOrNull { it.key == key }?.logo ?: fallback
}

/** Changes channel in place (the channel panel or a swipe), keeping the player open. */
internal fun PlayerScreenRuntime.switchLiveChannel(channel: LiveChannel, nowTitle: String?) {
    if (channel.key == activeVideoId && LiveTvPlayback.owns(activeProviderAddonId) && errorMessage == null) return
    // A channel switch always wins over a slow add-on catalog lookup.
    addonCatalogPlayJob?.cancel()
    addonCatalogPlayJob = null
    val req = LiveTvPlayback.switchTo(channel)
    flushWatchProgress()
    credentialRefreshJob?.cancel()
    errorMessage = null
    externalSubtitles = emptyList()
    activeSourceUrl = req.url
    activeSourceAudioUrl = null
    activeSourceHeaders = sanitizePlaybackHeaders(req.headers)
    activeSourceResponseHeaders = sanitizePlaybackResponseHeaders(null)
    activeStreamType = req.streamType
    activeSourceIdentityKey = "url:${req.url}"
    activeSourceFilename = null
    activeStreamFilename = sourceFilenameFromUrl(req.url)
    activeStreamTitle = channel.name
    activeStreamSubtitle = nowTitle ?: channel.group
    activePauseDescription = nowTitle
    activeProviderName = channel.sourceName
    activeProviderAddonId = LiveTvPlayback.PROVIDER_ID
    activeVideoId = channel.key
    activeInitialPositionMs = 0L
    activeInitialProgressFraction = null
}

/** The artwork/logo/title of the source being tuned in, used by the stream transition overlay. */
internal fun PlayerScreenRuntime.incomingStreamTransition(): PlayerStreamTransition {
    val channel = if (isLiveTv) {
        val key = activeVideoId
        key?.let { LiveTvViewState.list.value.visible.firstOrNull { channel -> channel.key == key } }
    } else {
        null
    }
    return PlayerStreamTransition(
        artwork = channel?.thumbnail?.takeIf { it.isNotBlank() }
            ?: channel?.logo?.takeIf { it.isNotBlank() }
            ?: background ?: poster,
        logo = channel?.logo?.takeIf { it.isNotBlank() } ?: logo,
        title = channel?.name ?: activeStreamTitle.takeIf { it.isNotBlank() } ?: title,
    )
}

/** The channel panel and channel swiping, over a playing Live TV channel. */
@Composable
internal fun PlayerScreenRuntime.RenderLiveTvChannels() {
    if (!isLiveTv) return
    val isInPip = rememberIsInPictureInPicture()
    LiveTvPlayerOverlay(
        currentKey = activeVideoId,
        controlsVisible = controlsVisible,
        enabled = !isInPip && !playerControlsLocked,
        onPanelOpened = { controlsVisible = false },
        onSwitch = { channel, nowTitle -> switchLiveChannel(channel, nowTitle) },
        onOpenAddonCatalog = { manifestUrl, catalog -> args.onOpenAddonCatalog?.invoke(manifestUrl, catalog) },
        onPlayAddonCatalogItem = { item, addon, onFinished ->
            playAddonCatalogItem(item, addon, onFinished)
        },
    )
}
