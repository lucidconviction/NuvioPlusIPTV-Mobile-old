package com.nuvio.app.features.livetv.ui.sports

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.nuvio.app.core.ui.Chip
import com.nuvio.app.core.ui.NuvioLoadingIndicator
import com.nuvio.app.core.ui.NuvioToastController
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.core.ui.nuvioSafeBottomPadding
import com.nuvio.app.features.livetv.sports.Sport
import com.nuvio.app.features.livetv.sports.SportsCatalog
import com.nuvio.app.features.livetv.sports.SportsFollows
import com.nuvio.app.features.livetv.sports.SportsReplays
import com.nuvio.app.features.livetv.sports.SportsSettings
import com.nuvio.app.features.youtube.YouTubeVideoCard
import kotlinx.coroutines.launch

/** What a YouTube row is doing. */
private sealed interface VideoFeed {
    data object Loading : VideoFeed
    data class Ready(val videos: List<YouTubeVideoCard>) : VideoFeed
    data object Empty : VideoFeed
    data object Failed : VideoFeed
}

/**
 * A Sports-page YouTube section: a header, then a horizontal row of video cards from the same
 * engine as the YouTube tabs. Tapping a card opens it in the app's YouTube player.
 */
@Composable
internal fun SportsVideoRow(
    title: String,
    query: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    limit: Int = 12,
    emptyText: String = "No YouTube videos found for this yet.",
) {
    val tokens = MaterialTheme.nuvio
    var attempt by remember(query) { mutableIntStateOf(0) }
    val state by produceState<VideoFeed>(VideoFeed.Loading, query, limit, attempt) {
        value = VideoFeed.Loading
        value = runCatching { SportsReplays.videos(query, limit) }.fold(
            onSuccess = { if (it.isEmpty()) VideoFeed.Empty else VideoFeed.Ready(it) },
            onFailure = { VideoFeed.Failed },
        )
    }
    val scope = rememberCoroutineScope()

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s6)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = tokens.colors.textPrimary, fontWeight = FontWeight.Bold)
                subtitle?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = tokens.colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (state == VideoFeed.Loading) {
                NuvioLoadingIndicator(size = NuvioTokens.Space.s16)
            } else if (state is VideoFeed.Ready) {
                Text(
                    "YouTube",
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.colors.textMuted,
                )
            }
        }

        when (val feed = state) {
            VideoFeed.Loading -> Text(
                "Finding videos on YouTube…",
                style = MaterialTheme.typography.bodySmall,
                color = tokens.colors.textMuted,
                modifier = Modifier.padding(vertical = NuvioTokens.Space.s6),
            )

            VideoFeed.Empty -> Text(
                emptyText,
                style = MaterialTheme.typography.bodySmall,
                color = tokens.colors.textMuted,
                modifier = Modifier.padding(vertical = NuvioTokens.Space.s6),
            )

            VideoFeed.Failed -> Text(
                "Couldn't load YouTube videos. Tap to try again.",
                style = MaterialTheme.typography.bodySmall,
                color = tokens.colors.accent,
                modifier = Modifier
                    .clickable { attempt++ }
                    .padding(vertical = NuvioTokens.Space.s6),
            )

            is VideoFeed.Ready -> LazyRow(
                contentPadding = PaddingValues(vertical = NuvioTokens.Space.s2),
                horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8),
            ) {
                items(feed.videos, key = { it.videoId }) { video ->
                    SportsVideoCard(video) {
                        scope.launch {
                            SportsReplays.play(video, feed.videos)?.let { NuvioToastController.show(it) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SportsVideoCard(video: YouTubeVideoCard, onClick: () -> Unit) {
    val tokens = MaterialTheme.nuvio
    Column(
        modifier = Modifier
            .width(220.dp)
            .clip(tokens.shapes.compactCard)
            .background(tokens.colors.surface)
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = video.thumbnail,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = "Play",
                    tint = Color.White,
                    modifier = Modifier.size(NuvioTokens.Icon.sm),
                )
            }
            video.badge?.takeIf { it.isNotBlank() }?.let { badge ->
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
        Column(
            modifier = Modifier.padding(NuvioTokens.Space.s8),
            verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s2),
        ) {
            Text(
                video.title,
                style = MaterialTheme.typography.bodySmall,
                color = tokens.colors.textPrimary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            video.author?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = tokens.colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/**
 * The Replays tab: YouTube replays and highlight packages for the sports in this region (plus
 * anything you follow), one row per sport. A sport chip narrows it to a single, longer row.
 */
@Composable
internal fun SportsReplaysTab(settings: SportsSettings, follows: SportsFollows) {
    var sportName by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = sportName?.let { name -> Sport.entries.firstOrNull { it.name == name } }
    val sports = remember(settings, follows) {
        val fromRegion = SportsCatalog.leagues.filter { SportsCatalog.shown(it, settings) }.map { it.sport }
        val fromLeagues = follows.leagues.mapNotNull { SportsCatalog.league(it)?.sport }
        val fromTeams = follows.teams.mapNotNull { SportsCatalog.league(it.leagueId)?.sport }
        val wanted = (fromRegion + fromLeagues + fromTeams).toSet()
        // Keep the region's priority order (American Football first in the US, Football in the UK…).
        SportsCatalog.sportsOrder(settings).filter { it in wanted }
    }
    val shown = selected?.let { listOf(it) } ?: sports
    val tokens = MaterialTheme.nuvio

    Column(modifier = Modifier.fillMaxSize()) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = NuvioTokens.Space.s16),
            horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s6),
        ) {
            item(key = "all") {
                Chip("All sports", selected = selected == null, onClick = { sportName = null })
            }
            items(sports, key = { it.name }) { sport ->
                Chip(
                    label = sport.icon + " " + sport.label,
                    selected = selected == sport,
                    onClick = { sportName = if (selected == sport) null else sport.name },
                )
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = NuvioTokens.Space.s16,
                end = NuvioTokens.Space.s16,
                top = NuvioTokens.Space.s6,
                bottom = nuvioSafeBottomPadding(NuvioTokens.Space.s16),
            ),
            verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s14),
        ) {
            if (shown.isEmpty()) {
                item {
                    Text(
                        "No sports to show replays for yet. Add leagues in Sports settings.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = tokens.colors.textMuted,
                        modifier = Modifier.padding(vertical = NuvioTokens.Space.s16),
                    )
                }
            }
            items(shown, key = { "replays:" + it.name }) { sport ->
                SportsVideoRow(
                    title = sport.icon + "  " + sport.label,
                    subtitle = "Latest replays and highlights from YouTube",
                    query = SportsReplays.sportQuery(sport),
                    limit = if (selected != null) 24 else 12,
                    emptyText = "No ${sport.label.lowercase()} replays found right now.",
                )
            }
        }
    }
}
