package com.nuvio.app.features.youtube

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

/** What the YouTube page hands back when you pick a video. */
data class YouTubePlayRequest(
    val url: String,
    val title: String,
    val poster: String?,
    val videoId: String,
    val startPositionMs: Long = 0L,
)

/** A YouTube video shown outside the YouTube tab (Sports replays and event videos). */
data class YouTubeVideoCard(
    val videoId: String,
    val title: String,
    val author: String?,
    val thumbnail: String?,
    val badge: String? = null,
)

/** A resolved YouTube stream, ready for Nuvio's own player. */
data class YouTubeResolvedVideo(
    val url: String,
    val title: String,
    val poster: String?,
    val videoId: String,
    val startPositionMs: Long = 0L,
    /** A direct audio-only stream when YouTube provides one; useful for downloads/audio mode. */
    val audioUrl: String? = null,
    /** Headers the stream URL was issued for (mainly the client's User-Agent). */
    val headers: Map<String, String> = emptyMap(),
)

/**
 * The YouTube page lives in Android-only code (it needs SmartTube's engine). The Android app sets
 * [screen] at start-up; on other platforms it stays empty and the tab is not shown.
 */
/** Set by the player while a YouTube video is playing: what the "YouTube" button in the controls does. */
class YouTubePlayerAction(
    val onClick: () -> Unit,
    val channelImage: String? = null,
    val subscribed: Boolean = false,
    val onSubscribe: (() -> Unit)? = null,
    val onChannel: (() -> Unit)? = null,
    val looping: Boolean = false,
    val onLoop: (() -> Unit)? = null,
    /** The captions button; null leaves Nuvio's own subtitle button in charge. */
    val onCaptions: (() -> Unit)? = null,
    /** The live chat button; null when this stream has no chat. */
    val onChat: (() -> Unit)? = null,
    val chatOn: Boolean = false,
    /** The video's chapters, as (title, start in ms). */
    val chapters: List<Pair<String, Long>> = emptyList(),
)

val LocalYouTubePlayerAction = androidx.compose.runtime.compositionLocalOf<YouTubePlayerAction?> { null }

object YouTubeHost {
    /** The panel opened from the player's YouTube button: like, subscribe, save, comments. */
    var playerPanel: (@Composable (videoId: String, onDismiss: () -> Unit) -> Unit)? = null

    /** SponsorBlock's marked parts of a video, as skip intervals. Empty when it's off or nothing is marked. */
    var sponsorSegments: (suspend (videoId: String) -> List<com.nuvio.app.features.player.skip.SkipInterval>)? = null

    /** True when marked parts should be skipped straight away, false when the Skip button should ask first. */
    var sponsorAutoSkip: (() -> Boolean)? = null

    /** Drawn over the player while a YouTube video plays: the up-next card, and starting the next video when this one ends. */
    var playerOverlay: (@Composable (videoId: String, positionMs: Long, durationMs: Long, ended: Boolean, playNext: (YouTubePlayRequest) -> Unit) -> Unit)? = null

    /** The player's Info/More Videos/Comments buttons and their side panels. */
    var moreVideos: (@Composable (videoId: String, buttonVisible: Boolean, bottomInset: Dp, onPlay: (YouTubePlayRequest) -> Unit) -> Unit)? = null

    /** The data and actions behind the YouTube buttons in the player's controls. [openPanel] shows the panel. */
    var playerControls: (@Composable (videoId: String, openPanel: () -> Unit) -> YouTubePlayerAction)? = null

    /** The YouTube page of the app's Settings. Android only; without it the row isn't shown. */
    var settingsContent: (@Composable () -> Unit)? = null

    /** Where a YouTube video has got to, so it keeps its watched bar and, when signed in, shows in your YouTube history. */
    var recordProgress: ((contentId: String, positionMs: Long, durationMs: Long) -> Unit)? = null

    /** Leaves the player, back to the page underneath. */
    var closePlayer: (() -> Unit)? = null

    /** Replaces the player with another YouTube video. Set by the app shell, which owns navigation. */
    var openVideo: ((YouTubePlayRequest) -> Unit)? = null

    var screen: (@Composable (modifier: Modifier, onPlay: (YouTubePlayRequest) -> Unit) -> Unit)? = null
    val available: Boolean get() = screen != null

    /**
     * Searches YouTube through the same engine as the YouTube tabs, without changing the tab
     * feed. Set on Android; empty elsewhere.
     */
    var searchVideos: (suspend (query: String, limit: Int) -> List<YouTubeVideoCard>)? = null

    /**
     * Plays one of [searchVideos]' results in the app's YouTube player, using [queue] as the
     * list for "More Videos" and autoplay. Fails with a user-readable message when YouTube
     * won't play the video.
     */
    var playVideo: (suspend (video: YouTubeVideoCard, queue: List<YouTubeVideoCard>) -> Result<Unit>)? = null

    /**
     * Resolves a video by id to a stream URL, without opening the YouTube page. Used by Home's
     * Continue Watching and the Music page. Set on Android.
     */
    var resolveVideo: (suspend (videoId: String, title: String, poster: String?, startPositionMs: Long) -> Result<YouTubeResolvedVideo>)? = null

    /** True when the Sports page can show YouTube videos. */
    val videosAvailable: Boolean get() = searchVideos != null && playVideo != null
}
