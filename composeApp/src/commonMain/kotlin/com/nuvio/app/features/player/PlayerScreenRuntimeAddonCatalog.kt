package com.nuvio.app.features.player

import com.nuvio.app.core.ui.NuvioToastController
import com.nuvio.app.features.addons.ManagedAddon
import com.nuvio.app.features.debrid.DebridSettingsRepository
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.streams.isSelectableForPlayback
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** How long the in-panel add-on item may take to find a stream before the lookup is abandoned. */
private const val AddonCatalogStreamTimeoutMs = 30_000L

/**
 * Plays an add-on catalog item without leaving the player. The channel that is already playing
 * keeps running while this item's streams are fetched in the background (so browsing an add-on
 * inside the channel panel never interrupts viewing); once a playable stream is found, the player
 * adopts the item's metadata and switches to it in place.
 *
 * [onFinished] is called exactly once with `true` when playback switched to the item, or `false`
 * when no stream could be played, so the panel can stop its "opening" spinner.
 */
internal fun PlayerScreenRuntime.playAddonCatalogItem(
    item: MetaPreview,
    addon: ManagedAddon,
    onFinished: (Boolean) -> Unit = {},
) {
    addonCatalogPlayJob?.cancel()
    addonCatalogPlayJob = scope.launch {
        var started = false
        val startingVideoId = activeVideoId
        val startingProviderId = activeProviderAddonId
        try {
            // Fetch this item's streams while the current channel keeps playing.
            PlayerStreamsRepository.loadSources(type = item.type, videoId = item.id)
            val state = withTimeoutOrNull(AddonCatalogStreamTimeoutMs) {
                PlayerStreamsRepository.sourceState.first { state ->
                    state.allStreams.isNotEmpty() ||
                        (!state.isAnyLoading && state.emptyStateReason != null)
                }
            }
            // A channel switch or another source started while the lookup ran: let it win.
            if (activeVideoId != startingVideoId || activeProviderAddonId != startingProviderId) {
                return@launch
            }
            val debridEnabled = DebridSettingsRepository.uiState.value.canResolvePlayableLinks
            val stream = state?.allStreams?.firstOrNull { it.isSelectableForPlayback(debridEnabled) }
            if (stream == null) {
                NuvioToastController.show("No streams found for ${item.name}")
                return@launch
            }

            // Save the live channel's progress under its own identity before adopting the item.
            flushWatchProgress()
            // The new item starts from the beginning; an empty snapshot also stops
            // switchToSource from carrying the live position over.
            playbackSnapshot = PlayerPlaybackSnapshot()

            val artwork = item.banner ?: item.poster
            args = args.copy(
                title = item.name,
                logo = item.logo,
                poster = item.poster,
                background = item.banner ?: item.poster,
                contentType = item.type,
                videoId = item.id,
                parentMetaId = item.id,
                parentMetaType = item.type,
                providerName = addon.displayTitle,
                providerAddonId = null,
                seasonNumber = null,
                episodeNumber = null,
                episodeTitle = null,
                episodeThumbnail = null,
                initialPositionMs = 0L,
                initialProgressFraction = null,
                torrentInfoHash = null,
                torrentFileIdx = null,
                torrentFilename = null,
                torrentTrackers = emptyList(),
                sourceFilename = null,
                stream = null,
                streamTitle = item.name,
                streamSubtitle = null,
                pauseDescription = null,
                initialBingeGroup = null,
            )
            activeVideoId = item.id
            activeSeasonNumber = null
            activeEpisodeNumber = null
            activeEpisodeTitle = null
            activeEpisodeThumbnail = null
            activeStreamTitle = item.name
            activeStreamSubtitle = null
            activePauseDescription = null
            activeProviderName = addon.displayTitle
            activeProviderAddonId = null
            activeInitialPositionMs = 0L
            activeInitialProgressFraction = null
            externalSubtitles = emptyList()
            errorMessage = null
            streamTransition = PlayerStreamTransition(
                artwork = artwork,
                logo = item.logo,
                title = item.name,
            )
            switchToSource(stream)
            started = true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            NuvioToastController.show(e.message ?: "Could not play ${item.name}")
        } finally {
            onFinished(started)
        }
    }
}
