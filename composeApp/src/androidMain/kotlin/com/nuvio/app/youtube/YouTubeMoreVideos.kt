package com.nuvio.app.youtube

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WatchLater
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.nuvio.app.features.youtube.YouTubePlayRequest
import kotlinx.coroutines.flow.flowOf
import android.widget.Toast

/**
 * The player's YouTube side buttons: Info, More Videos and Comments.
 *
 * The buttons sit in a centered row just above the player's title and progress bar. Each opens the
 * same right-side panel; More Videos lists the queue the current video was picked from, Info shows
 * the video's details, and Comments shows its comments with replies one layer deeper.
 */
@Composable
fun YouTubeMoreVideos(
    videoId: String,
    buttonVisible: Boolean,
    bottomInset: Dp,
    onPlay: (YouTubePlayRequest) -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val vm: YouTubePlayerViewModel = viewModel(key = "yt-player-$videoId") { YouTubePlayerViewModel(context) }
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(videoId) { vm.load(videoId) }
    val feed = YouTubeSession.feed
    val emptyQueue = remember { flowOf(emptyList<YtItem>()) }
    val queue by (feed?.queue ?: emptyQueue).collectAsStateWithLifecycle(initialValue = emptyList())

    YouTubePlayerSidePanelsContent(
        videoId = videoId,
        queue = queue,
        state = state,
        buttonVisible = buttonVisible,
        bottomInset = bottomInset,
        onPlay = onPlay,
        onActivate = { item, from, onReady, onError ->
            if (feed == null) {
                onError("More Videos is only available from the YouTube tab.")
            } else {
                feed.activate(item = item, from = from, onError = onError, onReady = onReady)
            }
        },
        onPrevious = {
            if (feed == null) {
                Toast.makeText(context, "The YouTube queue isn't available.", Toast.LENGTH_SHORT).show()
            } else {
                feed.playPrevious(
                    videoId,
                    { playable ->
                        onPlay(
                            YouTubePlayRequest(
                                url = playable.url,
                                title = playable.title,
                                poster = playable.poster,
                                videoId = playable.videoId,
                                startPositionMs = playable.startPositionMs,
                            )
                        )
                    },
                    { Toast.makeText(context, "There's no earlier video in this list.", Toast.LENGTH_SHORT).show() },
                )
            }
        },
        onNext = {
            if (feed == null) {
                Toast.makeText(context, "The YouTube queue isn't available.", Toast.LENGTH_SHORT).show()
            } else {
                feed.playNext(
                    videoId,
                    { playable ->
                        onPlay(
                            YouTubePlayRequest(
                                url = playable.url,
                                title = playable.title,
                                poster = playable.poster,
                                videoId = playable.videoId,
                                startPositionMs = playable.startPositionMs,
                            )
                        )
                    },
                    { Toast.makeText(context, "That's the end of the list.", Toast.LENGTH_SHORT).show() },
                )
            }
        },
        onToggleLike = vm::toggleLike,
        onToggleDislike = vm::toggleDislike,
        onToggleSubscribe = vm::toggleSubscribe,
        onLoadPlaylists = vm::loadPlaylists,
        onTogglePlaylist = vm::togglePlaylist,
        onCreatePlaylist = vm::createPlaylist,
        onLoadInfo = { vm.load(videoId) },
        onLoadComments = { newest -> vm.loadComments(newest) },
        onLoadMoreComments = vm::loadMoreComments,
        onLoadReplies = vm::loadReplies,
        onLoadMoreReplies = vm::loadMoreReplies,
        onClearReplies = vm::clearReplies,
        onClearMessage = vm::clearMessage,
    )
}

internal enum class SidePanelMode { INFO, MORE, COMMENTS, REPLIES, PLAYLISTS, QUALITY }

/** The buttons and panels, independent of the view models so they can be tested on their own. */
@Composable
internal fun YouTubePlayerSidePanelsContent(
    videoId: String,
    queue: List<YtItem>,
    state: YtPlayerState,
    buttonVisible: Boolean,
    onPlay: (YouTubePlayRequest) -> Unit,
    onActivate: (YtItem, List<YtItem>, (YtPlayable) -> Unit, (String) -> Unit) -> Unit,
    onPrevious: () -> Unit = {},
    onNext: () -> Unit = {},
    onToggleLike: () -> Unit = {},
    onToggleDislike: () -> Unit = {},
    onToggleSubscribe: () -> Unit = {},
    onLoadPlaylists: () -> Unit = {},
    onTogglePlaylist: (YtPlaylistRow) -> Unit = {},
    onCreatePlaylist: (String) -> Unit = {},
    bottomInset: Dp = 0.dp,
    onLoadInfo: () -> Unit = {},
    onLoadComments: (Boolean) -> Unit = {},
    onLoadMoreComments: () -> Unit = {},
    onLoadReplies: (String) -> Unit = {},
    onLoadMoreReplies: () -> Unit = {},
    onClearReplies: () -> Unit = {},
    onClearMessage: () -> Unit = {},
) {
    var mode by remember { mutableStateOf<SidePanelMode?>(null) }
    var lastMode by remember { mutableStateOf(SidePanelMode.MORE) }
    var loadingVideoId by remember { mutableStateOf<String?>(null) }
    var replyingTo by remember { mutableStateOf<YtComment?>(null) }
    val context = LocalContext.current

    // A new video is playing: clear any spinner and close a panel left over from the previous one.
    LaunchedEffect(videoId) {
        mode = null
        loadingVideoId = null
        replyingTo = null
    }
    LaunchedEffect(mode) {
        if (mode != null) lastMode = mode!!
        if (mode == SidePanelMode.PLAYLISTS) onLoadPlaylists()
    }
    // A direct button's result (signed out, saved, …) is shown as a toast while no panel is open.
    LaunchedEffect(state.message, mode) {
        if (mode == null) state.message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            onClearMessage()
        }
    }
    val shownMode = mode ?: lastMode
    val currentItem = queue.firstOrNull { it.videoId == videoId }
    val queueIndex = queue.indexOfFirst { it.videoId == videoId }
    val hasPrevious = queueIndex > 0 && queue.subList(0, queueIndex).any { it.kind == YtKind.VIDEO && !it.isLive }

    fun open(next: SidePanelMode) {
        if (next == SidePanelMode.INFO || next == SidePanelMode.COMMENTS) onLoadInfo()
        mode = next
    }

    fun backToComments() {
        onClearReplies()
        replyingTo = null
        mode = SidePanelMode.COMMENTS
    }

    BackHandler(enabled = mode != null) {
        when (mode) {
            SidePanelMode.REPLIES -> backToComments()
            else -> mode = null
        }
    }

    // Comments need the video's metadata first; load them as soon as it arrives.
    LaunchedEffect(mode, state.info?.videoId) {
        if (mode == SidePanelMode.COMMENTS && state.info != null &&
            state.info.commentsKey != null && state.comments.isEmpty() && !state.commentsLoading
        ) {
            onLoadComments(state.commentsSort == 1)
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // One panel width for all three, so switching between them doesn't resize the overlay.
        val panelWidth = (maxWidth * 0.44f).coerceIn(280.dp, 420.dp)

        AnimatedVisibility(
            visible = buttonVisible && mode == null,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = bottomInset + 10.dp),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .testTag("yt-action-row"),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SidePanelButton(
                    label = "Last",
                    icon = { tint -> Icon(Icons.Filled.SkipPrevious, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp)) },
                    testTag = "yt-previous-button",
                    enabled = hasPrevious,
                    onClick = onPrevious,
                )
                Row(
                    modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SidePanelButton(
                        label = "Info",
                        icon = { tint -> Icon(Icons.Filled.Info, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp)) },
                        testTag = "yt-info-button",
                        onClick = { open(SidePanelMode.INFO) },
                    )
                    SidePanelButton(
                        label = "More Videos",
                        icon = { tint ->
                            Icon(
                                Icons.AutoMirrored.Filled.PlaylistPlay,
                                contentDescription = null,
                                tint = tint,
                                modifier = Modifier.size(20.dp),
                            )
                        },
                        testTag = "yt-more-videos-button",
                        onClick = { open(SidePanelMode.MORE) },
                    )
                    SidePanelButton(
                        label = "Comments",
                        icon = { tint ->
                            Icon(
                                Icons.AutoMirrored.Filled.Comment,
                                contentDescription = null,
                                tint = tint,
                                modifier = Modifier.size(20.dp),
                            )
                        },
                        testTag = "yt-comments-button",
                        onClick = { open(SidePanelMode.COMMENTS) },
                    )
                    val liked = state.info?.liked == true
                    val disliked = state.info?.disliked == true
                    SidePanelButton(
                        label = state.info?.likeCount?.let { "Like  $it" } ?: "Like",
                        icon = { tint -> Icon(Icons.Filled.ThumbUp, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp)) },
                        testTag = "yt-like-button",
                        highlighted = liked,
                        onClick = onToggleLike,
                    )
                    SidePanelButton(
                        label = "Dislike",
                        icon = { tint -> Icon(Icons.Filled.ThumbDown, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp)) },
                        testTag = "yt-dislike-button",
                        highlighted = disliked,
                        onClick = onToggleDislike,
                    )
                    val subscribed = state.info?.subscribed == true
                    SidePanelButton(
                        label = if (subscribed) "Subscribed" else "Subscribe",
                        icon = { tint ->
                            Icon(
                                if (subscribed) Icons.Filled.Check else Icons.Filled.Subscriptions,
                                contentDescription = null,
                                tint = tint,
                                modifier = Modifier.size(20.dp),
                            )
                        },
                        testTag = "yt-subscribe-button",
                        highlighted = subscribed,
                        onClick = onToggleSubscribe,
                    )
                    SidePanelButton(
                        label = "Watch later",
                        icon = { tint -> Icon(Icons.Filled.WatchLater, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp)) },
                        testTag = "yt-watch-later-button",
                        onClick = { open(SidePanelMode.PLAYLISTS) },
                    )
                    SidePanelButton(
                        label = "Quality",
                        icon = { tint -> Icon(Icons.Filled.Tune, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp)) },
                        testTag = "yt-quality-button",
                        onClick = { open(SidePanelMode.QUALITY) },
                    )
                }
                SidePanelButton(
                    label = "Next",
                    icon = { tint -> Icon(Icons.Filled.SkipNext, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp)) },
                    testTag = "yt-next-button",
                    onClick = onNext,
                )
            }
        }

        AnimatedVisibility(
            visible = mode != null,
            modifier = Modifier.align(Alignment.CenterEnd),
            enter = slideInHorizontally { it } + fadeIn(),
            exit = slideOutHorizontally { it } + fadeOut(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(panelWidth)
                    .background(Color(0xF5111114)),
            ) {
                PanelHeader(
                    mode = shownMode,
                    replyingTo = replyingTo,
                    onBack = ::backToComments,
                    onClose = { mode = null },
                )

                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (shownMode) {
                        SidePanelMode.INFO -> InfoPanel(state, currentItem)
                        SidePanelMode.MORE -> MoreVideosPanel(
                            videoId = videoId,
                            queue = queue,
                            loadingVideoId = loadingVideoId,
                            onPlay = onPlay,
                            onActivate = onActivate,
                            onLoading = { loadingVideoId = it },
                        )
                        SidePanelMode.COMMENTS -> CommentsPanel(
                            state = state,
                            onSort = onLoadComments,
                            onMore = onLoadMoreComments,
                            onOpenReplies = { comment ->
                                val key = comment.replyKey ?: return@CommentsPanel
                                replyingTo = comment
                                onLoadReplies(key)
                                mode = SidePanelMode.REPLIES
                            },
                        )
                        SidePanelMode.REPLIES -> RepliesPanel(state, onMore = onLoadMoreReplies)
                        SidePanelMode.PLAYLISTS -> PlaylistsPanel(
                            state = state,
                            onToggle = onTogglePlaylist,
                            onCreate = onCreatePlaylist,
                            onRetry = onLoadPlaylists,
                        )
                        SidePanelMode.QUALITY -> YouTubeQualityPanel()
                    }
                }

                state.message?.let {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(it, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = onClearMessage) { Text("OK") }
                    }
                }
            }
        }
    }
}

@Composable
private fun SidePanelButton(
    label: String,
    icon: @Composable (Color) -> Unit,
    testTag: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    highlighted: Boolean = false,
) {
    Row(
        modifier = Modifier
            .testTag(testTag)
            .clip(RoundedCornerShape(50))
            .background(
                when {
                    highlighted -> Color(0xFFE53935)
                    else -> Color(0xB3000000)
                }
            )
            .border(1.dp, if (highlighted) Color.Transparent else Color.White.copy(alpha = 0.22f), RoundedCornerShape(50))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val tint = if (enabled) Color.White else Color.White.copy(alpha = 0.4f)
        icon(tint)
        Text(
            text = label,
            color = tint,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
private fun PanelHeader(
    mode: SidePanelMode,
    replyingTo: YtComment?,
    onBack: () -> Unit,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (mode == SidePanelMode.REPLIES) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to comments", tint = Color.White)
            }
        } else {
            Icon(
                when (mode) {
                    SidePanelMode.INFO -> Icons.Filled.Info
                    SidePanelMode.MORE -> Icons.AutoMirrored.Filled.PlaylistPlay
                    SidePanelMode.PLAYLISTS -> Icons.Filled.PlaylistAdd
                    SidePanelMode.QUALITY -> Icons.Filled.Tune
                    else -> Icons.AutoMirrored.Filled.Comment
                },
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                when (mode) {
                    SidePanelMode.INFO -> "Info"
                    SidePanelMode.MORE -> "More Videos"
                    SidePanelMode.COMMENTS -> "Comments"
                    SidePanelMode.REPLIES -> "Replies"
                    SidePanelMode.PLAYLISTS -> "Save to playlist"
                    SidePanelMode.QUALITY -> "Quality"
                },
                color = Color.White,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            if (mode == SidePanelMode.REPLIES && replyingTo != null) {
                Text(
                    "Replying to ${replyingTo.author}",
                    color = Color(0xFF9A9A9A),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(onClick = onClose) {
            Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
        }
    }
}

@Composable
private fun InfoPanel(state: YtPlayerState, currentItem: YtItem?) {
    val info = state.info
    when {
        info == null && state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color.White)
        }
        info == null -> Centered(state.message ?: "Couldn't load this video's details.")
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            currentItem?.thumbnail?.takeIf { it.isNotBlank() }?.let { thumbnail ->
                item {
                    AsyncImage(
                        model = thumbnail,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF222222)),
                    )
                }
            }
            item {
                Text(
                    info.title,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    info.authorImage?.let {
                        AsyncImage(
                            model = it,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(36.dp).clip(CircleShape),
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text(info.author, color = Color.White, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        info.subscribers?.takeIf { it.isNotBlank() }?.let {
                            Text(it, color = Color(0xFF9A9A9A), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            val stats = listOfNotNull(
                info.viewCount?.takeIf { it.isNotBlank() }?.let { countLabel(it, "views") },
                info.published?.takeIf { it.isNotBlank() },
                info.likeCount?.takeIf { it.isNotBlank() }?.let { countLabel(it, "likes") },
            )
            if (stats.isNotEmpty()) {
                item {
                    Text(
                        stats.joinToString("  ·  "),
                        color = Color(0xFFB0B0B0),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            info.description?.takeIf { it.isNotBlank() }?.let { description ->
                item {
                    Text(
                        description,
                        color = Color(0xFFD8D8D8),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            if (info.chapters.isNotEmpty()) {
                item {
                    Text("Chapters", color = Color.White, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                }
                itemsIndexed(info.chapters) { _, chapter ->
                    Text(
                        "${formatTime(chapter.second)}  ${chapter.first}",
                        color = Color(0xFFD8D8D8),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun MoreVideosPanel(
    videoId: String,
    queue: List<YtItem>,
    loadingVideoId: String?,
    onPlay: (YouTubePlayRequest) -> Unit,
    onActivate: (YtItem, List<YtItem>, (YtPlayable) -> Unit, (String) -> Unit) -> Unit,
    onLoading: (String?) -> Unit,
) {
    if (queue.isEmpty()) {
        Centered("No videos in this list.")
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(
            items = queue,
            key = { index, item -> item.videoId ?: "slot-$index" },
        ) { _, item ->
            MoreVideoRow(
                item = item,
                current = item.videoId == videoId,
                loading = loadingVideoId == item.videoId,
                enabled = loadingVideoId == null,
                onClick = {
                    val id = item.videoId ?: return@MoreVideoRow
                    onLoading(id)
                    onActivate(
                        item,
                        queue,
                        { playable ->
                            onLoading(null)
                            onPlay(
                                YouTubePlayRequest(
                                    url = playable.url,
                                    title = playable.title,
                                    poster = playable.poster,
                                    videoId = playable.videoId,
                                    startPositionMs = playable.startPositionMs,
                                ),
                            )
                        },
                        { onLoading(null) },
                    )
                },
            )
        }
    }
}

@Composable
private fun PlaylistsPanel(
    state: YtPlayerState,
    onToggle: (YtPlaylistRow) -> Unit,
    onCreate: (String) -> Unit,
    onRetry: () -> Unit,
) {
    if (!state.signedIn) {
        Centered("Sign in on the YouTube page to save videos.")
        return
    }
    var name by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text("New playlist") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    if (name.isNotBlank()) {
                        onCreate(name)
                        name = ""
                    }
                }),
            )
            Button(
                enabled = name.isNotBlank(),
                onClick = {
                    onCreate(name)
                    name = ""
                },
            ) {
                Icon(Icons.Filled.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        }
        val rows = state.playlists
        when {
            rows == null && state.loading ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            rows == null -> Column(
                Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Couldn't load your playlists.", color = Color(0xFFB0B0B0))
                TextButton(onClick = onRetry) { Text("Try again") }
            }
            rows.isEmpty() -> Centered("You don't have any playlists yet. Create one above.")
            else -> LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                items(rows) { row ->
                    val watchLater = row.title.contains("watch later", ignoreCase = true)
                    Row(
                        Modifier.fillMaxWidth().clickable { onToggle(row) }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = row.contains, onCheckedChange = { onToggle(row) })
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = if (watchLater) "Watch later" else row.title,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (watchLater) {
                                Text("YouTube's Watch later playlist", style = MaterialTheme.typography.labelSmall, color = Color(0xFF9A9A9A))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommentsPanel(
    state: YtPlayerState,
    onSort: (Boolean) -> Unit,
    onMore: () -> Unit,
    onOpenReplies: (YtComment) -> Unit,
) {
    val info = state.info
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Sort by", style = MaterialTheme.typography.bodySmall, color = Color(0xFFB0B0B0))
            FilterChip(
                selected = state.commentsSort == 0,
                onClick = { onSort(false) },
                label = { Text("Top") },
            )
            FilterChip(
                selected = state.commentsSort == 1,
                onClick = { onSort(true) },
                label = { Text("Newest") },
            )
        }
        when {
            info == null && state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White)
            }
            info != null && info.commentsKey == null -> Centered("Comments are off for this video.")
            state.commentsLoading && state.comments.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White)
            }
            state.comments.isEmpty() -> Centered("No comments to show.")
            else -> LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                itemsIndexed(state.comments) { _, comment ->
                    CommentRow(comment, onOpenReplies = onOpenReplies)
                }
                if (state.canLoadMoreComments) {
                    item {
                        LaunchedEffect(state.comments.size) { onMore() }
                        Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(24.dp), color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RepliesPanel(state: YtPlayerState, onMore: () -> Unit) {
    when {
        state.repliesLoading && state.replies.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color.White)
        }
        state.replies.isEmpty() -> Centered("No replies to show.")
        else -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            itemsIndexed(state.replies) { _, comment -> CommentRow(comment, onOpenReplies = null) }
            if (state.canLoadMoreReplies) {
                item {
                    LaunchedEffect(state.replies.size) { onMore() }
                    Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(24.dp), color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun CommentRow(c: YtComment, onOpenReplies: ((YtComment) -> Unit)?) {
    val opensReplies = onOpenReplies != null && c.replyKey != null
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = opensReplies) { onOpenReplies?.invoke(c) }
            .padding(horizontal = 6.dp, vertical = 8.dp),
    ) {
        Text(c.author, style = MaterialTheme.typography.labelMedium, color = Color(0xFFB0B0B0))
        Spacer(Modifier.height(2.dp))
        Text(c.text, style = MaterialTheme.typography.bodyMedium, color = Color.White)
        val meta = listOfNotNull(
            c.published,
            c.likes?.takeIf { it.isNotBlank() }?.let { countLabel(it, "likes") },
        ).joinToString("  ·  ")
        if (meta.isNotEmpty()) {
            Text(meta, style = MaterialTheme.typography.bodySmall, color = Color(0xFF909090))
        }
        if (opensReplies) {
            Text(
                replyLabel(c.replyCount),
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFFE53935),
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun MoreVideoRow(
    item: YtItem,
    current: Boolean,
    loading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (current) Color(0xFF242428) else Color.Transparent)
            .clickable(enabled = enabled && !current, onClick = onClick)
            .padding(6.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF222222)),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = item.thumbnail,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            item.badge?.let { badge ->
                Text(
                    text = badge,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xCC000000))
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                )
            }
            when {
                loading -> CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    color = Color.White,
                    strokeWidth = 2.dp,
                )

                current -> Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color(0xCC000000)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = "Now playing",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(
            text = item.title,
            color = if (current) Color(0xFFE53935) else Color.White,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (current) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        item.author?.takeIf { it.isNotBlank() }?.let { author ->
            Text(
                text = author,
                color = Color(0xFF9A9A9A),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun Centered(text: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = Color(0xFFB0B0B0))
    }
}

/** YouTube often hands back "1.2M views" but sometimes a bare number; don't double the noun. */
private fun countLabel(value: String, noun: String): String =
    if (value.any(Char::isLetter)) value else "$value $noun"

private fun replyLabel(replyCount: String?): String =
    replyCount?.takeIf { it.isNotBlank() }?.let { if (it.any(Char::isLetter)) it else "$it replies" } ?: "Replies"

private fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val seconds = total % 60
    return if (hours > 0) {
        "$hours:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    } else {
        "$minutes:${seconds.toString().padStart(2, '0')}"
    }
}
