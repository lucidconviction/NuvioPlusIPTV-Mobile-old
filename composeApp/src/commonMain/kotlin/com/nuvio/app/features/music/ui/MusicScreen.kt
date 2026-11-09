package com.nuvio.app.features.music.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.nuvio.app.core.ui.LocalNuvioBottomNavigationOverlayPadding
import com.nuvio.app.core.ui.NuvioInputField
import com.nuvio.app.core.ui.NuvioLoadingIndicator
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.core.ui.PlatformBackHandler
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.music.MusicPlayerRepository
import com.nuvio.app.features.music.MusicRepository
import com.nuvio.app.features.music.MusicTrack
import com.nuvio.app.features.player.PlatformPlayerSurface
import com.nuvio.app.features.player.PlayerEngineController
import com.nuvio.app.features.player.PlayerPlaybackSnapshot
import com.nuvio.app.features.player.PlayerResizeMode
import com.nuvio.app.features.youtube.YouTubeResolvedVideo

/**
 * The Music tab. Browse YouTube music rows, search, and play in a centred PiP-style window.
 * Video and Audio modes share one toggle; changing it on the page or in the player changes both.
 */
@Composable
internal fun MusicScreen(modifier: Modifier = Modifier) {
    val current by MusicPlayerRepository.current.collectAsStateWithLifecycle()
    PlatformBackHandler(enabled = current != null) { MusicPlayerRepository.close() }
    val track = current
    if (track != null) {
        MusicPlayer(track, modifier)
    } else {
        MusicBrowse(modifier)
    }
}

@Composable
private fun MusicBrowse(modifier: Modifier = Modifier) {
    val tokens = MaterialTheme.nuvio
    val bottomPadding = LocalNuvioBottomNavigationOverlayPadding.current
    val audioOnly by MusicPlayerRepository.audioOnly.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize().background(tokens.colors.background),
    ) {
        val tablet = maxWidth >= 768.dp
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(top = if (tablet) 64.dp else 0.dp, bottom = bottomPadding),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = NuvioTokens.Space.s16, vertical = NuvioTokens.Space.s8),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s12),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Music",
                        style = MaterialTheme.typography.headlineMedium,
                        color = tokens.colors.textPrimary,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "Songs and music videos from YouTube",
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.colors.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                MusicModeToggle(audioOnly = audioOnly)
            }
            NuvioInputField(
                value = query,
                onValueChange = { query = it },
                placeholder = "Search songs, artists or videos",
                modifier = Modifier.fillMaxWidth().padding(horizontal = NuvioTokens.Space.s16),
            )
            Spacer(Modifier.height(NuvioTokens.Space.s8))
            if (query.isBlank()) {
                MusicFeed(modifier = Modifier.weight(1f))
            } else {
                MusicSearch(query = query, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MusicFeed(modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            start = NuvioTokens.Space.s16,
            end = NuvioTokens.Space.s16,
            top = NuvioTokens.Space.s4,
            bottom = NuvioTokens.Space.s24,
        ),
        verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s18),
    ) {
        items(MusicRepository.sections, key = { it.title }) { section ->
            MusicSectionRow(section)
        }
    }
}

@Composable
private fun MusicSectionRow(section: com.nuvio.app.features.music.MusicSection) {
    val tokens = MaterialTheme.nuvio
    val tracks by produceState<List<MusicTrack>?>(null, section.query) {
        value = null
        value = MusicRepository.search(section.query, 12)
    }
    val list = tracks
    Column(verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                section.title,
                style = MaterialTheme.typography.titleMedium,
                color = tokens.colors.textPrimary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            if (list == null) NuvioLoadingIndicator(size = NuvioTokens.Space.s16)
        }
        when {
            list == null -> Text(
                "Finding songs…",
                style = MaterialTheme.typography.bodySmall,
                color = tokens.colors.textMuted,
            )
            list.isEmpty() -> Text(
                "No songs found for this row.",
                style = MaterialTheme.typography.bodySmall,
                color = tokens.colors.textMuted,
            )
            else -> LazyRow(horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s10)) {
                items(list, key = { it.videoId }) { track ->
                    MusicCard(track, onClick = { MusicPlayerRepository.play(track, list) })
                }
            }
        }
    }
}

@Composable
private fun MusicCard(track: MusicTrack, onClick: () -> Unit) {
    val tokens = MaterialTheme.nuvio
    Column(
        modifier = Modifier
            .width(200.dp)
            .clip(tokens.shapes.compactCard)
            .background(tokens.colors.surface)
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = track.thumbnail,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier.size(34.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = "Play", tint = Color.White, modifier = Modifier.size(NuvioTokens.Icon.sm))
            }
            track.badge?.takeIf { it.isNotBlank() }?.let { badge ->
                Text(
                    badge,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    maxLines = 1,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(NuvioTokens.Space.s4)
                        .clip(RoundedCornerShape(NuvioTokens.Radius.sm))
                        .background(Color.Black.copy(alpha = 0.75f))
                        .padding(horizontal = NuvioTokens.Space.s4, vertical = 1.dp),
                )
            }
        }
        Column(modifier = Modifier.padding(NuvioTokens.Space.s8), verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s2)) {
            Text(
                track.title,
                style = MaterialTheme.typography.bodySmall,
                color = tokens.colors.textPrimary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                track.artist,
                style = MaterialTheme.typography.labelSmall,
                color = tokens.colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun MusicSearch(query: String, modifier: Modifier = Modifier) {
    val tokens = MaterialTheme.nuvio
    val tracks by produceState<List<MusicTrack>?>(null, query) {
        value = null
        value = MusicRepository.search(query, 40)
    }
    val list = tracks
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            start = NuvioTokens.Space.s16,
            end = NuvioTokens.Space.s16,
            top = NuvioTokens.Space.s4,
            bottom = NuvioTokens.Space.s24,
        ),
        verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s6),
    ) {
        when {
            list == null -> item(key = "loading") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = NuvioTokens.Space.s12),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8),
                ) {
                    NuvioLoadingIndicator(size = NuvioTokens.Space.s16)
                    Text("Searching YouTube…", style = MaterialTheme.typography.bodyMedium, color = tokens.colors.textMuted)
                }
            }
            list.isEmpty() -> item(key = "empty") {
                Text(
                    "No music found for \"$query\".",
                    style = MaterialTheme.typography.bodyMedium,
                    color = tokens.colors.textMuted,
                    modifier = Modifier.padding(vertical = NuvioTokens.Space.s12),
                )
            }
            else -> items(list, key = { it.videoId }) { track ->
                MusicSearchRow(track, onClick = { MusicPlayerRepository.play(track, list) })
            }
        }
    }
}

@Composable
private fun MusicSearchRow(track: MusicTrack, onClick: () -> Unit) {
    val tokens = MaterialTheme.nuvio
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(tokens.shapes.compactCard)
            .background(tokens.colors.surface)
            .clickable(onClick = onClick)
            .padding(NuvioTokens.Space.s6),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s10),
    ) {
        Box(
            modifier = Modifier.width(120.dp).heightIn(min = 68.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(NuvioTokens.Radius.sm)).background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = track.thumbnail,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Icon(Icons.Filled.PlayArrow, contentDescription = "Play", tint = Color.White, modifier = Modifier.size(NuvioTokens.Icon.md))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s2)) {
            Text(
                track.title,
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.colors.textPrimary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                track.artist,
                style = MaterialTheme.typography.bodySmall,
                color = tokens.colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(Icons.Filled.MusicNote, contentDescription = null, tint = tokens.colors.textMuted, modifier = Modifier.size(NuvioTokens.Icon.sm))
    }
}

@Composable
private fun MusicModeToggle(audioOnly: Boolean) {
    val tokens = MaterialTheme.nuvio
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(NuvioTokens.Radius.full))
            .background(tokens.colors.surface)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        ModeButton(
            label = "Video",
            icon = Icons.Filled.Videocam,
            selected = !audioOnly,
            onClick = { MusicPlayerRepository.setAudioOnly(false) },
        )
        ModeButton(
            label = "Audio",
            icon = Icons.Filled.MusicNote,
            selected = audioOnly,
            onClick = { MusicPlayerRepository.setAudioOnly(true) },
        )
    }
}

@Composable
private fun ModeButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(NuvioTokens.Radius.full))
            .background(if (selected) tokens.colors.accent else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = NuvioTokens.Space.s8, vertical = NuvioTokens.Space.s6),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s4),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (selected) tokens.colors.onAccent else tokens.colors.textMuted,
            modifier = Modifier.size(NuvioTokens.Icon.xs),
        )
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) tokens.colors.onAccent else tokens.colors.textMuted,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun MusicPlayer(track: MusicTrack, modifier: Modifier = Modifier) {
    val tokens = MaterialTheme.nuvio
    val queue by MusicPlayerRepository.queue.collectAsStateWithLifecycle()
    val audioOnly by MusicPlayerRepository.audioOnly.collectAsStateWithLifecycle()
    val bottomPadding = LocalNuvioBottomNavigationOverlayPadding.current
    var reload by remember(track.videoId) { mutableIntStateOf(0) }
    val resolved by produceState<Result<YouTubeResolvedVideo>?>(null, track.videoId, reload) {
        value = null
        value = MusicRepository.resolve(track, 0L)
    }
    var controller by remember(track.videoId) { mutableStateOf<PlayerEngineController?>(null) }
    var snapshot by remember(track.videoId) { mutableStateOf(PlayerPlaybackSnapshot()) }
    var error by remember(track.videoId) { mutableStateOf<String?>(null) }
    var dragFraction by remember(track.videoId) { mutableStateOf<Float?>(null) }

    LaunchedEffect(snapshot.isEnded) {
        if (snapshot.isEnded) MusicPlayerRepository.next()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(tokens.colors.background)
            .statusBarsPadding()
            .padding(bottom = bottomPadding),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = NuvioTokens.Space.s8, vertical = NuvioTokens.Space.s6),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8),
        ) {
            IconButton(onClick = { MusicPlayerRepository.close() }) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back to Music", tint = tokens.colors.textPrimary)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Now playing", style = MaterialTheme.typography.titleMedium, color = tokens.colors.textPrimary, fontWeight = FontWeight.SemiBold)
                Text("YouTube · ${queue.size} in queue", style = MaterialTheme.typography.labelSmall, color = tokens.colors.textMuted)
            }
            MusicModeToggle(audioOnly = audioOnly)
        }

        Box(
            modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = NuvioTokens.Space.s16),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 520.dp)
                    .aspectRatio(16f / 9f)
                    .clip(tokens.shapes.card)
                    .background(Color.Black)
                    .padding(1.dp),
                contentAlignment = Alignment.Center,
            ) {
                val video = resolved?.getOrNull()
                if (video != null) {
                    key(track.videoId) {
                        PlatformPlayerSurface(
                            sourceUrl = video.url,
                            streamType = null,
                            resizeMode = PlayerResizeMode.Fit,
                            playWhenReady = true,
                            allowConcurrentAudio = false,
                            interceptTouches = false,
                            modifier = Modifier.fillMaxSize(),
                            onControllerReady = { ready ->
                                controller = ready
                                ready.play()
                            },
                            onSnapshot = { snapshot = it },
                            onError = { error = it },
                        )
                    }
                }
                if (audioOnly) {
                    AsyncImage(
                        model = track.thumbnail,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Box(
                        modifier = Modifier.fillMaxSize().background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)),
                            ),
                        ),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        Icon(
                            Icons.Filled.MusicNote,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.9f),
                            modifier = Modifier.padding(NuvioTokens.Space.s12).size(NuvioTokens.Icon.lg),
                        )
                    }
                }
                when {
                    resolved == null -> NuvioLoadingIndicator(size = NuvioTokens.Space.s28)
                    resolved?.isFailure == true -> {
                        val message = resolved?.exceptionOrNull()?.message ?: "Couldn't play this video."
                        Column(
                            modifier = Modifier.padding(NuvioTokens.Space.s16),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8),
                        ) {
                            Text(message, style = MaterialTheme.typography.bodySmall, color = Color.White, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            Text(
                                "Tap to retry",
                                style = MaterialTheme.typography.labelMedium,
                                color = tokens.colors.accent,
                                modifier = Modifier.clickable { reload++ },
                            )
                        }
                    }
                }
                error?.let { message ->
                    Text(
                        message,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .clip(RoundedCornerShape(NuvioTokens.Radius.sm))
                            .background(Color.Black.copy(alpha = 0.75f))
                            .padding(NuvioTokens.Space.s8),
                    )
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = NuvioTokens.Space.s16, vertical = NuvioTokens.Space.s8),
            verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s6),
        ) {
            Text(
                track.title,
                style = MaterialTheme.typography.titleLarge,
                color = tokens.colors.textPrimary,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                track.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val duration = snapshot.durationMs
            val fraction = dragFraction ?: if (duration > 0L) (snapshot.positionMs.toFloat() / duration).coerceIn(0f, 1f) else 0f
            Slider(
                value = fraction,
                onValueChange = { dragFraction = it },
                onValueChangeFinished = {
                    val target = dragFraction
                    dragFraction = null
                    if (target != null && duration > 0L) controller?.seekTo((target * duration).toLong())
                },
                enabled = duration > 0L,
                colors = SliderDefaults.colors(
                    thumbColor = tokens.colors.accent,
                    activeTrackColor = tokens.colors.accent,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { MusicPlayerRepository.previous() }) {
                    Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous", tint = tokens.colors.textPrimary)
                }
                IconButton(
                    onClick = { controller?.let { if (snapshot.isPlaying) it.pause() else it.play() } },
                    modifier = Modifier
                        .padding(horizontal = NuvioTokens.Space.s16)
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(tokens.colors.accent),
                ) {
                    Icon(
                        if (snapshot.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (snapshot.isPlaying) "Pause" else "Play",
                        tint = tokens.colors.onAccent,
                        modifier = Modifier.size(NuvioTokens.Icon.lg),
                    )
                }
                IconButton(onClick = { MusicPlayerRepository.next() }) {
                    Icon(Icons.Filled.SkipNext, contentDescription = "Next", tint = tokens.colors.textPrimary)
                }
            }
            Spacer(Modifier.height(NuvioTokens.Space.s4))
        }
    }
}
