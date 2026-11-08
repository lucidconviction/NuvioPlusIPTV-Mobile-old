package com.nuvio.app.youtube

import android.content.Context
import com.nuvio.app.features.youtube.YouTubeHost
import com.nuvio.app.features.youtube.YouTubePlayRequest
import com.nuvio.app.features.youtube.YouTubeVideoCard
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.concurrent.Volatile
import kotlin.coroutines.resume

/**
 * The Sports page's window into the same YouTube engine the YouTube tabs use.
 *
 * Searches go through [YouTubeViewModel.searchVideos] and playing goes through the feed's own
 * activate/lookup path, so replay cards get the same stream extraction, resume position and
 * "More Videos" queue as a video picked on the YouTube tab.
 */
internal object YouTubeSports {

    @Volatile
    private var model: YouTubeViewModel? = null
    private val searchLock = Mutex()

    /** Search results seen recently, by video id: playing needs the engine's own [YtItem]. */
    private val found = java.util.Collections.synchronizedMap(
        object : LinkedHashMap<String, YtItem>(64, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, YtItem>?): Boolean = size > 600
        },
    )

    fun init(context: Context) {
        if (model != null) return
        synchronized(this) {
            if (model == null) {
                val app = context.applicationContext
                YouTubeContentRegion.apply(app)
                model = YouTubeViewModel(app, autoSelect = false)
                // The player's up-next / More Videos panels use the session feed. Before the
                // YouTube tab is opened, this is the engine that has the Sports queue.
                YouTubeSession.feed = model
            }
        }
    }

    suspend fun search(query: String, limit: Int): List<YouTubeVideoCard> = searchLock.withLock {
        val vm = model ?: return@withLock emptyList()
        val items = vm.searchVideos(query, limit)
        items.forEach { item -> item.videoId?.let { found[it] = item } }
        items.map { item ->
            YouTubeVideoCard(
                videoId = item.videoId.orEmpty(),
                title = item.title,
                author = item.author,
                thumbnail = item.thumbnail,
                badge = item.badge,
            )
        }.filter { it.videoId.isNotBlank() }
    }

    suspend fun play(video: YouTubeVideoCard, queue: List<YouTubeVideoCard>): Result<Unit> {
        val vm = model ?: return Result.failure(IllegalStateException("YouTube isn't ready yet."))
        val item = found[video.videoId]
            ?: return Result.failure(IllegalStateException("That video is no longer available."))
        val from = queue.mapNotNull { found[it.videoId] }.ifEmpty { listOf(item) }
        return suspendCancellableCoroutine { continuation ->
            vm.activate(
                item = item,
                from = from,
                onError = { message ->
                    if (continuation.isActive) {
                        continuation.resume(Result.failure(IllegalStateException(message)))
                    }
                },
                onReady = { playable ->
                    val open = YouTubeHost.openVideo
                    if (open == null) {
                        if (continuation.isActive) {
                            continuation.resume(Result.failure(IllegalStateException("The YouTube player isn't available.")))
                        }
                    } else {
                        open(
                            YouTubePlayRequest(
                                url = playable.url,
                                title = playable.title,
                                poster = playable.poster,
                                videoId = playable.videoId,
                                startPositionMs = playable.startPositionMs,
                            ),
                        )
                        if (continuation.isActive) continuation.resume(Result.success(Unit))
                    }
                },
            )
        }
    }
}
