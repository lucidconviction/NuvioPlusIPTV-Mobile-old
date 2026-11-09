package com.nuvio.app.features.music

import com.nuvio.app.features.livetv.model.LiveTvClock
import com.nuvio.app.features.youtube.YouTubeHost
import com.nuvio.app.features.youtube.YouTubeResolvedVideo
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One music video/track from YouTube. */
data class MusicTrack(
    val videoId: String,
    val title: String,
    val artist: String,
    val thumbnail: String?,
    val badge: String? = null,
)

/** A row on the Music page and the YouTube search that fills it. */
data class MusicSection(val title: String, val query: String)

/**
 * Music discovery and resolution through the app's YouTube engine. There is no second music
 * provider: the rows are YouTube searches, and playing resolves through the same path as the
 * YouTube tab. Results are cached for an hour.
 */
internal object MusicRepository {

    private const val CACHE_TTL_MS = 60L * 60L * 1000L
    private const val MAX_CACHED = 40

    val sections: List<MusicSection> = listOf(
        MusicSection("Trending now", "trending music videos 2026"),
        MusicSection("Pop", "top pop music videos"),
        MusicSection("Hip-hop & rap", "hip hop rap music videos"),
        MusicSection("Rock", "rock music videos"),
        MusicSection("Electronic & dance", "electronic dance music videos"),
        MusicSection("Chill & lofi", "lofi chill music"),
        MusicSection("Country", "country music videos"),
        MusicSection("Latin", "latin music videos"),
        MusicSection("K-pop", "kpop music videos"),
        MusicSection("R&B & soul", "rnb soul music videos"),
    )

    private data class Entry(val atMs: Long, val tracks: List<MusicTrack>)

    private val cache = object : LinkedHashMap<String, Entry>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?): Boolean = size > MAX_CACHED
    }
    private val lock = Mutex()

    suspend fun search(query: String, limit: Int = 12): List<MusicTrack> {
        if (query.isBlank()) return emptyList()
        val host = YouTubeHost.searchVideos ?: return emptyList()
        val key = "$limit\u0000$query"
        val now = LiveTvClock.nowMs()
        lock.withLock { cache[key] }?.takeIf { now - it.atMs in 0 until CACHE_TTL_MS }?.let { return it.tracks }
        val tracks = runCatching { host(query, limit) }.getOrDefault(emptyList())
            .map { video ->
                MusicTrack(
                    videoId = video.videoId,
                    title = video.title,
                    artist = video.author?.substringBefore(" • ")?.trim()?.ifBlank { null } ?: "YouTube",
                    thumbnail = video.thumbnail,
                    badge = video.badge,
                )
            }
            .filter { it.videoId.isNotBlank() }
        if (tracks.isNotEmpty()) lock.withLock { cache[key] = Entry(now, tracks) }
        return tracks
    }

    suspend fun resolve(track: MusicTrack, startPositionMs: Long = 0L): Result<YouTubeResolvedVideo> {
        val resolve = YouTubeHost.resolveVideo
            ?: return Result.failure(IllegalStateException("YouTube isn't available on this device."))
        return resolve(track.videoId, track.title, track.thumbnail, startPositionMs)
    }
}
