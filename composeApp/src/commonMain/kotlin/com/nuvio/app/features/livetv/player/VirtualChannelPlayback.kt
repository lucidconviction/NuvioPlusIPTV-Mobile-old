package com.nuvio.app.features.livetv.player

import com.nuvio.app.features.livetv.data.LiveTvPreferences
import com.nuvio.app.features.livetv.model.LiveChannel
import com.nuvio.app.features.livetv.virtual.VirtualChannelRepository
import com.nuvio.app.features.livetv.virtual.VirtualChannels
import com.nuvio.app.features.livetv.virtual.VirtualTune
import com.nuvio.app.features.player.PlayerLaunch
import com.nuvio.app.features.player.PlayerLaunchStore
import com.nuvio.app.features.player.sanitizePlaybackHeaders
import com.nuvio.app.features.player.sanitizePlaybackResponseHeaders
import com.nuvio.app.features.servers.ServerPlayback
import com.nuvio.app.features.streams.StreamBehaviorHints
import com.nuvio.app.features.streams.StreamItem
import com.nuvio.app.features.streams.StreamProxyHeaders
import com.nuvio.app.features.streams.StreamsRepository

/**
 * Turns a Lucid TV channel into the same player payload every other live channel uses: resolve the
 * item that is on now, find a playable stream, and start part-way through it. The channel itself
 * stores no stream URL (relay channels are the exception), so a catalog channel can play on any
 * device and any debrid setup.
 */
internal object VirtualChannelPlayback {

    data class Resolved(
        val tune: VirtualTune,
        val stream: StreamItem,
        val launch: PlayerLaunch,
    )

    /** Resolves what is on now and a playable stream. Null means the channel is offline. */
    suspend fun resolve(channel: LiveChannel, profileId: Int, nowMs: Long = com.nuvio.app.features.livetv.model.LiveTvClock.nowMs()): Resolved? {
        if (!VirtualChannels.isVirtualKey(channel.key)) return null
        val tune = VirtualChannelRepository.tune(channel.key, nowMs) ?: return null
        val stream = if (tune.relay) {
            relayStream(tune)
        } else {
            resolveStream(tune)
        } ?: return null
        val requestHeaders = sanitizePlaybackHeaders(stream.behaviorHints.proxyHeaders?.request)
        val responseHeaders = sanitizePlaybackResponseHeaders(stream.behaviorHints.proxyHeaders?.response)
        val launch = PlayerLaunch(
            profileId = profileId,
            title = channel.name,
            sourceUrl = stream.playableDirectUrl ?: return null,
            sourceHeaders = requestHeaders,
            sourceResponseHeaders = responseHeaders,
            streamType = stream.streamType,
            logo = channel.logo,
            poster = tune.item.poster ?: channel.logo,
            background = tune.item.backdrop,
            streamTitle = channel.name,
            streamSubtitle = tune.title,
            pauseDescription = tune.item.description,
            providerName = channel.sourceName,
            providerAddonId = LiveTvPlayback.PROVIDER_ID,
            contentType = LiveTvPlayback.CONTENT_TYPE,
            videoId = channel.key,
            parentMetaId = channel.key,
            parentMetaType = LiveTvPlayback.CONTENT_TYPE,
            initialPositionMs = tune.startOffsetMs,
            stream = stream,
        )
        return Resolved(tune, stream, launch)
    }

    /** Resolves and stores the launch; returns its id for a PlayerRoute, or null when offline. */
    suspend fun prepare(channel: LiveChannel, profileId: Int): Long? {
        val resolved = resolve(channel, profileId) ?: return null
        LiveTvPreferences.recordWatched(channel.key)
        return PlayerLaunchStore.put(resolved.launch)
    }

    private suspend fun resolveStream(tune: VirtualTune): StreamItem? {
        val item = tune.item
        val resolved = StreamsRepository.resolveFirstPlayableStream(
            type = item.type,
            videoId = item.id,
            parentMetaId = item.parentId ?: item.id,
            season = tune.season,
            episode = tune.episode,
            timeoutMs = 25_000L,
        ) ?: return null
        val prepared = if (resolved.needsServerPreparation) {
            runCatching { ServerPlayback.prepare(resolved) }.getOrNull() ?: return null
        } else {
            resolved
        }
        return prepared.takeIf { it.playableDirectUrl != null }
    }

    private fun relayStream(tune: VirtualTune): StreamItem? {
        val url = tune.item.streamUrl ?: return null
        return StreamItem(
            name = tune.item.title,
            title = tune.item.title,
            url = url,
            addonName = VirtualChannels.SOURCE_NAME,
            addonId = VirtualChannels.SOURCE_ID,
            streamType = when {
                url.substringBefore('?').endsWith(".m3u8", ignoreCase = true) -> "hls"
                url.substringBefore('?').endsWith(".mpd", ignoreCase = true) -> "dash"
                else -> null
            },
            behaviorHints = StreamBehaviorHints(
                proxyHeaders = StreamProxyHeaders(request = tune.item.headers),
            ),
            sourceName = VirtualChannels.SOURCE_NAME,
        )
    }
}
