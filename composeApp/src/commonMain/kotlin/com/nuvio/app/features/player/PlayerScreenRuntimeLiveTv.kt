package com.nuvio.app.features.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.features.livetv.data.LiveTvRepository
import com.nuvio.app.features.livetv.model.LiveChannel
import com.nuvio.app.features.livetv.model.LiveTvClock
import com.nuvio.app.features.livetv.player.LiveTvPlayback
import com.nuvio.app.features.livetv.player.VirtualChannelPlayback
import com.nuvio.app.features.livetv.ui.LiveTvPlayerOverlay
import com.nuvio.app.features.livetv.ui.LiveTvViewState
import com.nuvio.app.features.livetv.virtual.VirtualChannels
import kotlinx.coroutines.launch

/** Whether a Live TV channel (or a catch-up replay of one) is playing. */
internal val PlayerScreenRuntime.isLiveTv: Boolean
    get() = LiveTvPlayback.owns(activeProviderAddonId)

/** Whether what's playing came from the Live TV section: live, catch-up, a recording or On Demand. */
internal val PlayerScreenRuntime.isLiveTvContent: Boolean
    get() = LiveTvPlayback.ownsContent(activeProviderAddonId)

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
    if (!VirtualChannels.isVirtualKey(channel.key) &&
        channel.key == activeVideoId && LiveTvPlayback.owns(activeProviderAddonId) && errorMessage == null
    ) return
    if (VirtualChannels.isVirtualKey(channel.key)) {
        // Lucid TV: resolve the item on now in the background, then swap it in. The last channel
        // picked wins when the viewer zaps quickly.
        addonCatalogPlayJob?.cancel()
        addonCatalogPlayJob = null
        virtualSwitchJob?.cancel()
        // Show the channel name/logo while the item resolves, like a tuner.
        streamTransition = PlayerStreamTransition(
            artwork = channel.thumbnail?.takeIf { it.isNotBlank() }
                ?: channel.logo?.takeIf { it.isNotBlank() }
                ?: background ?: poster,
            logo = channel.logo?.takeIf { it.isNotBlank() } ?: logo,
            title = channel.name,
        )
        virtualSwitchJob = scope.launch {
            val outcome = VirtualChannelPlayback.resolve(channel, profileId)
            val resolved = outcome.resolved
            if (resolved == null) {
                com.nuvio.app.core.ui.NuvioToastController.show(
                    outcome.message ?: "This Lucid TV channel is offline right now"
                )
                return@launch
            }
            val tune = resolved.tune
            flushWatchProgress()
            credentialRefreshJob?.cancel()
            errorMessage = null
            externalSubtitles = emptyList()
            activeSourceAudioUrl = null
            activeSourceResponseHeaders = sanitizePlaybackResponseHeaders(resolved.stream.behaviorHints.proxyHeaders?.response)
            // switchToSource adopts the stream's own add-on id and the previous position; Lucid TV
            // puts its own live identity and the join offset back right afterwards.
            activeSeasonNumber = tune.season
            activeEpisodeNumber = tune.episode
            switchToSource(resolved.stream, saveForReuse = false)
            activeStreamTitle = channel.name
            activeStreamSubtitle = tune.title
            activePauseDescription = tune.item.description
            activeProviderName = channel.sourceName
            activeProviderAddonId = LiveTvPlayback.PROVIDER_ID
            activeVideoId = channel.key
            activeEpisodeTitle = tune.episodeTitle
            activeEpisodeThumbnail = tune.item.poster
            activeInitialPositionMs = tune.startOffsetMs
            activeInitialProgressFraction = null
            streamTransition = PlayerStreamTransition(
                artwork = tune.item.backdrop?.takeIf { it.isNotBlank() }
                    ?: tune.item.poster?.takeIf { it.isNotBlank() }
                    ?: channel.thumbnail?.takeIf { it.isNotBlank() }
                    ?: channel.logo?.takeIf { it.isNotBlank() }
                    ?: background ?: poster,
                logo = channel.logo?.takeIf { it.isNotBlank() } ?: logo,
                title = channel.name,
            )
        }
        return
    }
    // A channel switch always wins over a slow add-on catalog lookup.
    addonCatalogPlayJob?.cancel()
    addonCatalogPlayJob = null
    virtualSwitchJob?.cancel()
    virtualSwitchJob = null
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
    // Show the incoming channel's own artwork while it tunes in, even if the previous source
    // never finished loading (the artwork effect below keeps it in sync afterwards).
    streamTransition = PlayerStreamTransition(
        artwork = channel.thumbnail?.takeIf { it.isNotBlank() }
            ?: channel.logo?.takeIf { it.isNotBlank() }
            ?: background ?: poster,
        logo = channel.logo?.takeIf { it.isNotBlank() } ?: logo,
        title = channel.name,
    )
}

/** The artwork/logo/title of the source being tuned in, used by the stream transition overlay. */
internal fun PlayerScreenRuntime.incomingStreamTransition(): PlayerStreamTransition {
    val channel = if (isLiveTv) {
        val key = activeVideoId
        key?.let { channelKey ->
            LiveTvViewState.list.value.visible.firstOrNull { channel -> channel.key == channelKey }
                // A channel just switched to inside the player may not be in the filtered list yet.
                ?: LiveTvPlayback.lastSwitchedChannel?.takeIf { it.key == channelKey }
        }
    } else {
        null
    }
    // When the channel has no thumbnail of its own, its current show's artwork reads better than
    // the (usually transparent) channel logo.
    val nowProgram = channel?.let { c ->
        val now = LiveTvClock.nowMs()
        LiveTvRepository.programs.value[c.key]?.firstOrNull { it.startMs <= now && it.stopMs > now }
    }
    return PlayerStreamTransition(
        artwork = channel?.thumbnail?.takeIf { it.isNotBlank() }
            ?: nowProgram?.icon?.takeIf { it.isNotBlank() }
            ?: channel?.logo?.takeIf { it.isNotBlank() }
            ?: background ?: poster,
        logo = channel?.logo?.takeIf { it.isNotBlank() } ?: logo,
        title = channel?.name ?: activeStreamTitle.takeIf { it.isNotBlank() } ?: title,
    )
}

/** The channel panel and channel swiping, over a playing Live TV channel. */
@Composable
internal fun PlayerScreenRuntime.RenderLiveTvChannels() {
    if (!isLiveTvContent) return
    val isInPip = rememberIsInPictureInPicture()
    // Lucid TV is a broadcast: warm the current item and the next two, then tune at the boundary.
    LaunchedEffect(activeVideoId, isLiveTv) {
        val key = activeVideoId ?: return@LaunchedEffect
        if (!isLiveTv || !VirtualChannels.isVirtualKey(key)) return@LaunchedEffect
        scope.launch { VirtualChannelPlayback.prefetchCurrent(key) }
        var prefetchedSlotStart = Long.MIN_VALUE
        while (true) {
            kotlinx.coroutines.delay(15_000L)
            val now = LiveTvClock.nowMs()
            val tune = com.nuvio.app.features.livetv.virtual.VirtualChannelRepository.tune(key, now) ?: continue
            // Resolve the next two items shortly before the slot changes.
            if (now >= tune.slotEndMs - 30_000L && tune.slotStartMs != prefetchedSlotStart) {
                prefetchedSlotStart = tune.slotStartMs
                scope.launch { VirtualChannelPlayback.prefetch(key, now) }
            }
            if (now < tune.slotEndMs) continue
            if (!playbackSnapshot.isPlaying && !playbackSnapshot.isEnded) continue
            val channel = LiveTvViewState.list.value.visible.firstOrNull { it.key == key } ?: break
            prefetchedSlotStart = Long.MIN_VALUE
            switchLiveChannel(channel, tune.title)
        }
    }
    LiveTvPlayerOverlay(
        currentKey = activeVideoId,
        controlsVisible = controlsVisible,
        enabled = !isInPip && !playerControlsLocked,
        // Only a live channel changes on a swipe; On Demand and recordings keep their gestures.
        swipeEnabled = isLiveTv && !isInPip && !playerControlsLocked,
        onPanelOpened = { controlsVisible = false },
        onSwitch = { channel, nowTitle -> switchLiveChannel(channel, nowTitle) },
        onAddToMultiView = { channel -> args.onAddChannelToMultiView?.invoke(channel) },
        onOpenAddonCatalog = { manifestUrl, catalog -> args.onOpenAddonCatalog?.invoke(manifestUrl, catalog) },
        onPlayAddonCatalogItem = { item, addon, onFinished ->
            playAddonCatalogItem(item, addon, onFinished)
        },
    )
}
