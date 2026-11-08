package com.nuvio.app.features.livetv.sports

import com.nuvio.app.features.livetv.model.LiveTvClock
import com.nuvio.app.features.youtube.YouTubeHost
import com.nuvio.app.features.youtube.YouTubeVideoCard
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * YouTube videos for the Sports page: replay rows and the pre/post-event videos on a game's page.
 *
 * Searches go through [YouTubeHost.searchVideos], which is the app's own YouTube tabs engine
 * (not a separate API), and playback goes through [YouTubeHost.playVideo]. Results are cached for
 * an hour so scrolling back up a page, or reopening a game, does not search again.
 */
internal object SportsReplays {

    private const val CACHE_TTL_MS = 60L * 60L * 1000L
    private const val MAX_CACHED_SEARCHES = 40

    private data class Entry(val atMs: Long, val videos: List<YouTubeVideoCard>)

    private val cache = object : LinkedHashMap<String, Entry>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?): Boolean =
            size > MAX_CACHED_SEARCHES
    }
    private val lock = Mutex()

    /** The videos for [query], from the cache or YouTube. Empty when YouTube isn't available. */
    suspend fun videos(query: String, limit: Int = 12): List<YouTubeVideoCard> {
        if (query.isBlank()) return emptyList()
        val search = YouTubeHost.searchVideos ?: return emptyList()
        val key = "$limit\u0000$query"
        val now = LiveTvClock.nowMs()
        lock.withLock { cache[key] }?.takeIf { now - it.atMs in 0 until CACHE_TTL_MS }?.let { return it.videos }
        val videos = runCatching { search(query, limit) }.getOrDefault(emptyList())
        if (videos.isNotEmpty()) lock.withLock { cache[key] = Entry(now, videos) }
        return videos
    }

    /** Plays [video]; returns the message to show when it can't be played, or null when it can. */
    suspend fun play(video: YouTubeVideoCard, queue: List<YouTubeVideoCard>): String? {
        val play = YouTubeHost.playVideo ?: return "YouTube isn't available on this device."
        val result = play(video, queue)
        if (result.isSuccess) return null
        return result.exceptionOrNull()?.message?.takeIf { it.isNotBlank() }
            ?: "That video can't be played right now."
    }

    /** "Bulls vs Lions" (or the event's name) for a search about [ev]. */
    private fun subject(ev: SportsEvent, sport: Sport): String {
        val names = ev.ordered(sport).map { it.team.name.trim() }.filter { it.isNotEmpty() }
        if (names.size >= 2) return names.take(2).joinToString(" vs ")
        return ev.title.trim().ifBlank { ev.subtitle.orEmpty().trim() }
    }

    private fun leagueName(ev: SportsEvent): String =
        SportsCatalog.league(ev.leagueId)?.name.orEmpty().trim()

    /** Promotion, press conferences, weigh-ins: shown while a game is still to come or on now. */
    fun preEventQuery(ev: SportsEvent, sport: Sport): String {
        val extra = when (sport) {
            Sport.FIGHTING, Sport.WRESTLING -> "weigh in press conference"
            Sport.RACING -> "qualifying preview"
            else -> "preview press conference"
        }
        return listOf(subject(ev, sport), leagueName(ev), extra)
            .filter { it.isNotBlank() }
            .joinToString(" ")
    }

    /** Commentary, highlights, interviews and reaction: shown once a game has finished. */
    fun postEventQuery(ev: SportsEvent, sport: Sport): String =
        listOf(subject(ev, sport), leagueName(ev), "highlights interviews reaction")
            .filter { it.isNotBlank() }
            .joinToString(" ")

    /** The search used by the Replays tab for a whole sport. */
    fun sportQuery(sport: Sport): String = when (sport) {
        Sport.AMERICAN_FOOTBALL -> "NFL game highlights"
        Sport.BASKETBALL -> "NBA game highlights"
        Sport.HOCKEY -> "NHL game highlights"
        Sport.BASEBALL -> "MLB game highlights"
        Sport.FOOTBALL -> "Premier League match highlights"
        Sport.RUGBY -> "rugby match highlights"
        Sport.FIGHTING -> "UFC fight highlights"
        Sport.RACING -> "Formula 1 race highlights"
        Sport.GOLF -> "PGA Tour golf highlights"
        Sport.TENNIS -> "tennis match highlights"
        Sport.CRICKET -> "cricket match highlights"
        Sport.DARTS -> "darts match highlights"
        Sport.SNOOKER -> "snooker match highlights"
        Sport.WRESTLING -> "wrestling match highlights"
        Sport.AUSSIE_RULES -> "AFL match highlights"
        Sport.VOLLEYBALL -> "volleyball match highlights"
        Sport.LACROSSE -> "lacrosse highlights"
        Sport.GAELIC -> "GAA match highlights"
        Sport.NETBALL -> "netball match highlights"
        Sport.OTHER -> "sports highlights"
    }
}
