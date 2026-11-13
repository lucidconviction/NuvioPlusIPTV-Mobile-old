package com.nuvio.app.features.livetv.player

import com.nuvio.app.features.livetv.data.LiveTvPreferences
import com.nuvio.app.features.livetv.model.LiveChannel
import com.nuvio.app.features.livetv.model.LiveTvClock
import com.nuvio.app.features.livetv.virtual.VirtualChannelRepository
import com.nuvio.app.features.livetv.virtual.VirtualChannels
import com.nuvio.app.features.player.PlayerLaunchStore

/** The launch id for a virtual channel, or the reason it couldn't tune. */
internal data class VirtualPrepareOutcome(
    val launchId: Long? = null,
    val message: String? = null,
)

/**
 * Lucid TV's playback entry point. Stream resolution is handled by [VirtualTuneResolver], which
 * follows the normal player's HTTP/server/debrid/P2P order, caches winners and prefetches the
 * next items.
 */
internal object VirtualChannelPlayback {

    suspend fun resolve(
        channel: LiveChannel,
        profileId: Int,
        nowMs: Long = LiveTvClock.nowMs(),
    ): VirtualPlaybackOutcome = VirtualTuneResolver.resolve(channel, profileId, nowMs)

    /** Resolves and stores the launch; returns its id for a PlayerRoute, or the reason it failed. */
    suspend fun prepare(channel: LiveChannel, profileId: Int): VirtualPrepareOutcome {
        val outcome = resolve(channel, profileId)
        val resolved = outcome.resolved
            ?: return VirtualPrepareOutcome(message = outcome.message)
        LiveTvPreferences.recordWatched(channel.key)
        return VirtualPrepareOutcome(launchId = PlayerLaunchStore.put(resolved.launch))
    }

    /** Warms the current item and the next items the schedule will play. */
    suspend fun prefetch(channelKey: String, nowMs: Long = LiveTvClock.nowMs()) {
        VirtualTuneResolver.prefetch(channelKey, nowMs)
    }

    suspend fun prefetchCurrent(channelKey: String, nowMs: Long = LiveTvClock.nowMs()) {
        VirtualTuneResolver.prefetchCurrent(channelKey, nowMs)
    }

    /** The item on now is kept for a quick warm start; drop it when a source fails. */
    suspend fun invalidate(channelKey: String, failedStream: com.nuvio.app.features.streams.StreamItem? = null) {
        VirtualTuneResolver.invalidate(channelKey, failedStream)
    }

    fun isVirtual(channel: LiveChannel): Boolean = VirtualChannels.isVirtualKey(channel.key)
}
