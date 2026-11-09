package com.nuvio.app.features.music

import com.nuvio.app.features.livetv.model.LiveTvClock
import com.nuvio.app.features.youtube.YouTubeHost
import com.nuvio.app.features.youtube.YouTubeResolvedVideo
import com.nuvio.app.features.youtube.YouTubeVideoCard
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Music discovery: Deezer's public catalogue for metadata (tracks, artists, albums, playlists,
 * artwork, charts and genres), and the app's YouTube engine for the actual video/audio stream.
 * Lyrics come from LRCLIB. Nothing here needs an API key.
 */
internal object MusicRepository {

    private val deezer = DeezerClient()
    private val lyrics = LyricsRepository()

    private const val CACHE_TTL_MS = 60L * 60L * 1000L

    private var homeCache: MusicHomeData? = null
    private var homeCachedAt = 0L
    private val homeLock = Mutex()

    private data class SearchEntry(val atMs: Long, val data: MusicSearchData)
    private val searchCache = object : LinkedHashMap<String, SearchEntry>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, SearchEntry>?): Boolean = size > 30
    }
    private val searchLock = Mutex()

    /** Deezer track id -> YouTube video id, so a song only needs one lookup. */
    private val youtubeIds = java.util.Collections.synchronizedMap(HashMap<String, String>())

    suspend fun home(force: Boolean = false): MusicHomeData {
        val now = LiveTvClock.nowMs()
        homeLock.withLock {
            homeCache?.takeIf { !force && now - homeCachedAt in 0 until CACHE_TTL_MS }?.let { return it }
        }
        val data = coroutineScope {
            val trending = async { deezer.chartTracks(30) }
            val releases = async { deezer.newReleases(20) }
            val artists = async { deezer.chartArtists(20) }
            val playlists = async { deezer.chartPlaylists(20) }
            val genres = async { deezer.genres() }
            val videos = async { youtubeTracks("trending music videos 2026", 12) }
            val fetchedTrending = trending.await()
            MusicHomeData(
                trending = fetchedTrending.ifEmpty { videos.await() },
                newReleases = releases.await(),
                artists = artists.await(),
                playlists = playlists.await(),
                genres = genres.await(),
                youtubeVideos = videos.await(),
            )
        }
        homeLock.withLock {
            homeCache = data
            homeCachedAt = now
        }
        return data
    }

    suspend fun search(query: String): MusicSearchData {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return MusicSearchData()
        val now = LiveTvClock.nowMs()
        searchLock.withLock {
            searchCache[trimmed]?.takeIf { now - it.atMs in 0 until CACHE_TTL_MS }?.let { return it.data }
        }
        val data = coroutineScope {
            val tracks = async { deezer.searchTracks(trimmed, 40) }
            val artists = async { deezer.searchArtists(trimmed, 20) }
            val albums = async { deezer.searchAlbums(trimmed, 20) }
            val playlists = async { deezer.searchPlaylists(trimmed, 20) }
            val fetchedTracks = tracks.await()
            MusicSearchData(
                tracks = fetchedTracks.ifEmpty { youtubeTracks(trimmed, 40) },
                artists = artists.await(),
                albums = albums.await(),
                playlists = playlists.await(),
            )
        }
        searchLock.withLock { searchCache[trimmed] = SearchEntry(now, data) }
        return data
    }

    suspend fun artist(id: String): MusicArtistDetails? = deezer.artistDetails(id)

    suspend fun album(id: String): MusicAlbumDetails? = deezer.albumDetails(id)

    suspend fun playlist(id: String): MusicPlaylistDetails? = deezer.playlistDetails(id)

    suspend fun genreTracks(genre: MusicGenre): List<MusicTrack> = deezer.genreTracks(genre)

    /**
     * Resolves a song to a YouTube stream. Deezer gives us the metadata, while a YouTube search
     * for the artist and title gives us the music video; the result is cached per song.
     */
    suspend fun resolve(track: MusicTrack): Result<YouTubeResolvedVideo> {
        val local = track.localFile?.takeIf { it.isNotBlank() }
            ?: MusicLibraryRepository.downloadFor(track.id)?.localPath
        local?.takeIf { it.isNotBlank() }?.let { path ->
            return Result.success(
                YouTubeResolvedVideo(
                    url = path,
                    title = track.title,
                    poster = track.thumbnail,
                    videoId = track.id,
                    audioUrl = path,
                ),
            )
        }
        val resolve = YouTubeHost.resolveVideo
            ?: return Result.failure(IllegalStateException("YouTube isn't available on this device."))
        val videoId = track.youtubeVideoId?.takeIf { it.isNotBlank() }
            ?: youtubeIds[track.id]
            ?: findYoutubeVideo(track)?.also { youtubeIds[track.id] = it }
            ?: return Result.failure(IllegalStateException("No matching YouTube video found."))
        return resolve(videoId, track.title, track.thumbnail, 0L)
    }

    suspend fun lyrics(track: MusicTrack): LyricsData = lyrics.fetch(track)

    private suspend fun findYoutubeVideo(track: MusicTrack): String? {
        val search = YouTubeHost.searchVideos ?: return null
        val queries = listOf(
            "${track.artist} ${track.title} official music video",
            "${track.artist} ${track.title} audio",
        )
        for (query in queries) {
            val result = runCatching { search(query, 6) }.getOrDefault(emptyList())
            val match = result.firstOrNull { video ->
                val haystack = (video.title + " " + (video.author ?: "")).lowercase()
                track.artist.isNotBlank() && haystack.contains(track.artist.lowercase().substringBefore(" "), ignoreCase = true)
            } ?: result.firstOrNull()
            if (match?.videoId?.isNotBlank() == true) return match.videoId
        }
        return null
    }

    private suspend fun youtubeTracks(query: String, limit: Int): List<MusicTrack> {
        val search = YouTubeHost.searchVideos ?: return emptyList()
        return runCatching { search(query, limit) }.getOrDefault(emptyList()).map { it.toMusicTrack() }
    }

    private fun YouTubeVideoCard.toMusicTrack(): MusicTrack = MusicTrack(
        id = videoId,
        title = title,
        artist = author?.substringBefore(" • ")?.trim()?.ifBlank { null } ?: "YouTube",
        thumbnail = thumbnail,
        badge = badge,
        youtubeVideoId = videoId,
    )
}
