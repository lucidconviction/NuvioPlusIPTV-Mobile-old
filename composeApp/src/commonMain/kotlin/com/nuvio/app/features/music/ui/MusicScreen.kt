package com.nuvio.app.features.music.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.nuvio.app.core.ui.LocalNuvioBottomNavigationOverlayPadding
import com.nuvio.app.core.ui.NuvioInputField
import com.nuvio.app.core.ui.NuvioLoadingIndicator
import com.nuvio.app.core.ui.NuvioModalBottomSheet
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.core.ui.NuvioToastController
import com.nuvio.app.core.ui.PlatformBackHandler
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.music.DownloadedMusicTrack
import com.nuvio.app.features.music.LyricsData
import com.nuvio.app.features.music.MusicAlbum
import com.nuvio.app.features.music.MusicArtist
import com.nuvio.app.features.music.MusicArtistDetails
import com.nuvio.app.features.music.MusicDownloadsRepository
import com.nuvio.app.features.music.MusicGenre
import com.nuvio.app.features.music.MusicHomeData
import com.nuvio.app.features.music.MusicLibraryRepository
import com.nuvio.app.features.music.MusicPlayerRepository
import com.nuvio.app.features.music.MusicPlaylist
import com.nuvio.app.features.music.MusicRepeatMode
import com.nuvio.app.features.music.MusicRepository
import com.nuvio.app.features.music.MusicSearchData
import com.nuvio.app.features.music.MusicTrack
import com.nuvio.app.features.music.MusicAlbumDetails
import com.nuvio.app.features.music.MusicPlaylistDetails
import com.nuvio.app.features.music.UserMusicPlaylist
import com.nuvio.app.features.player.PlatformPlayerSurface
import com.nuvio.app.features.player.PlayerEngineController
import com.nuvio.app.features.player.PlayerPlaybackSnapshot
import com.nuvio.app.features.player.PlayerResizeMode
import com.nuvio.app.features.youtube.YouTubeResolvedVideo

private enum class MusicTab(val label: String) {
    HOME("Home"),
    SEARCH("Search"),
    BROWSE("Browse"),
    LIBRARY("Library"),
    DOWNLOADS("Downloads"),
}

private sealed interface MusicDestination {
    data class Artist(val id: String) : MusicDestination
    data class Album(val id: String) : MusicDestination
    data class Playlist(val id: String) : MusicDestination
    data class LocalPlaylist(val id: String) : MusicDestination
    data class Genre(val genre: MusicGenre) : MusicDestination
}

/** The Music tab: browse, search, library, downloads and the video/audio player. */
@Composable
internal fun MusicScreen(modifier: Modifier = Modifier) {
    MusicLibraryRepository.ensureLoaded()
    val current by MusicPlayerRepository.current.collectAsStateWithLifecycle()
    var destination by remember { mutableStateOf<MusicDestination?>(null) }
    PlatformBackHandler(enabled = current != null) { MusicPlayerRepository.close() }
    PlatformBackHandler(enabled = current == null && destination != null) { destination = null }

    val track = current
    when {
        track != null -> MusicPlayer(track, modifier)
        destination != null -> MusicDetail(
            destination = destination!!,
            onBack = { destination = null },
            onOpenDestination = { destination = it },
            modifier = modifier,
        )
        else -> MusicBrowse(modifier, onOpenDestination = { destination = it })
    }
}

@Composable
private fun MusicBrowse(modifier: Modifier, onOpenDestination: (MusicDestination) -> Unit) {
    val tokens = MaterialTheme.nuvio
    val bottomPadding = LocalNuvioBottomNavigationOverlayPadding.current
    val audioOnly by MusicPlayerRepository.audioOnly.collectAsStateWithLifecycle()
    var tabName by rememberSaveable { mutableStateOf(MusicTab.HOME.name) }
    val tab = MusicTab.entries.firstOrNull { it.name == tabName } ?: MusicTab.HOME
    var query by rememberSaveable { mutableStateOf("") }

    BoxWithConstraints(modifier.fillMaxSize().background(tokens.colors.background)) {
        val tablet = maxWidth >= 768.dp
        Column(
            Modifier.fillMaxSize().statusBarsPadding().padding(top = if (tablet) 64.dp else 0.dp, bottom = bottomPadding),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = NuvioTokens.Space.s16, vertical = NuvioTokens.Space.s8),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s12),
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Music", style = MaterialTheme.typography.headlineMedium, color = tokens.colors.textPrimary, fontWeight = FontWeight.Bold)
                    Text("Deezer catalogue · YouTube videos", style = MaterialTheme.typography.bodySmall, color = tokens.colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                MusicModeToggle(audioOnly)
            }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = NuvioTokens.Space.s16, vertical = NuvioTokens.Space.s4),
                horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s6),
            ) {
                MusicTab.entries.forEach { entry ->
                    MusicTabChip(entry.label, tab == entry) {
                        tabName = entry.name
                        if (entry != MusicTab.SEARCH) query = ""
                    }
                }
            }
            if (tab == MusicTab.HOME || tab == MusicTab.SEARCH) {
                NuvioInputField(
                    value = query,
                    onValueChange = {
                        query = it
                        if (it.isNotBlank()) tabName = MusicTab.SEARCH.name
                    },
                    placeholder = "Search songs, artists, albums or playlists",
                    modifier = Modifier.fillMaxWidth().padding(horizontal = NuvioTokens.Space.s16, vertical = NuvioTokens.Space.s4),
                )
                Spacer(Modifier.height(NuvioTokens.Space.s4))
            }
            when {
                tab == MusicTab.SEARCH || query.isNotBlank() -> MusicSearch(query, onOpenDestination, Modifier.weight(1f))
                tab == MusicTab.HOME -> MusicHome(onOpenDestination, Modifier.weight(1f))
                tab == MusicTab.BROWSE -> MusicBrowseGenres(onOpenDestination, Modifier.weight(1f))
                tab == MusicTab.LIBRARY -> MusicLibrary(onOpenDestination, Modifier.weight(1f))
                tab == MusicTab.DOWNLOADS -> MusicDownloads(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MusicHome(onOpenDestination: (MusicDestination) -> Unit, modifier: Modifier = Modifier) {
    val tokens = MaterialTheme.nuvio
    val home by produceState<MusicHomeData?>(null) { value = MusicRepository.home() }
    val data = home
    if (data == null) {
        Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { NuvioLoadingIndicator(size = NuvioTokens.Space.s28) }
        return
    }
    if (data.trending.isEmpty() && data.newReleases.isEmpty() && data.artists.isEmpty()) {
        Box(modifier.fillMaxWidth().padding(NuvioTokens.Space.s16), contentAlignment = Alignment.Center) {
            Text("No music found right now.", style = MaterialTheme.typography.bodyMedium, color = tokens.colors.textMuted)
        }
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = NuvioTokens.Space.s16, end = NuvioTokens.Space.s16, top = NuvioTokens.Space.s4, bottom = NuvioTokens.Space.s24),
        verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s18),
    ) {
        val hero = data.trending.firstOrNull()
        if (hero != null) {
            item(key = "hero") { MusicHero(hero, data.trending) }
        }
        if (data.trending.isNotEmpty()) {
            item(key = "trending") { MusicTrackRow("Trending songs", data.trending) { playMusic(it, data.trending) } }
        }
        if (data.youtubeVideos.isNotEmpty()) {
            item(key = "videos") { MusicTrackRow("Music videos", data.youtubeVideos) { playMusic(it, data.youtubeVideos) } }
        }
        if (data.newReleases.isNotEmpty()) {
            item(key = "releases") { MusicAlbumRow("New releases", data.newReleases) { onOpenDestination(MusicDestination.Album(it.id)) } }
        }
        if (data.artists.isNotEmpty()) {
            item(key = "artists") { MusicArtistRow("Trending artists", data.artists) { onOpenDestination(MusicDestination.Artist(it.id)) } }
        }
        if (data.playlists.isNotEmpty()) {
            item(key = "playlists") { MusicPlaylistRow("Playlists", data.playlists) { onOpenDestination(MusicDestination.Playlist(it.id)) } }
        }
        if (data.genres.isNotEmpty()) {
            item(key = "genres") { MusicGenreRow("Browse by genre", data.genres) { onOpenDestination(MusicDestination.Genre(it)) } }
        }
    }
}

@Composable
private fun MusicSearch(query: String, onOpenDestination: (MusicDestination) -> Unit, modifier: Modifier = Modifier) {
    val tokens = MaterialTheme.nuvio
    var filter by rememberSaveable(query) { mutableStateOf("All") }
    val result by produceState<MusicSearchData?>(null, query) {
        value = null
        if (query.isBlank()) return@produceState
        kotlinx.coroutines.delay(300)
        value = MusicRepository.search(query)
    }
    val data = result
    Column(modifier.fillMaxWidth()) {
        if (query.isBlank()) {
            Text("Search Deezer for songs, artists, albums and playlists.", style = MaterialTheme.typography.bodyMedium, color = tokens.colors.textMuted, modifier = Modifier.padding(NuvioTokens.Space.s16))
            return
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = NuvioTokens.Space.s16, vertical = NuvioTokens.Space.s4),
            horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s6),
        ) {
            listOf("All", "Songs", "Artists", "Albums", "Playlists").forEach { label ->
                MusicTabChip(label, filter == label) { filter = label }
            }
        }
        if (data == null) {
            Box(Modifier.fillMaxWidth().padding(NuvioTokens.Space.s16), contentAlignment = Alignment.Center) { NuvioLoadingIndicator(size = NuvioTokens.Space.s24) }
            return
        }
        if (data.isEmpty) {
            Text("No music found for \"$query\".", style = MaterialTheme.typography.bodyMedium, color = tokens.colors.textMuted, modifier = Modifier.padding(NuvioTokens.Space.s16))
            return
        }
        LazyColumn(
            contentPadding = PaddingValues(start = NuvioTokens.Space.s16, end = NuvioTokens.Space.s16, bottom = NuvioTokens.Space.s24),
            verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s6),
        ) {
            if ((filter == "All" || filter == "Songs") && data.tracks.isNotEmpty()) {
                item(key = "tracks-h") { SectionTitle("Songs") }
                items(data.tracks, key = { "t:${it.id}" }) { track -> MusicTrackListRow(track) { playMusic(track, data.tracks) } }
            }
            if ((filter == "All" || filter == "Artists") && data.artists.isNotEmpty()) {
                item(key = "artists-h") { SectionTitle("Artists") }
                item(key = "artists-row") { MusicArtistRow(null, data.artists) { onOpenDestination(MusicDestination.Artist(it.id)) } }
            }
            if ((filter == "All" || filter == "Albums") && data.albums.isNotEmpty()) {
                item(key = "albums-h") { SectionTitle("Albums") }
                item(key = "albums-row") { MusicAlbumRow(null, data.albums) { onOpenDestination(MusicDestination.Album(it.id)) } }
            }
            if ((filter == "All" || filter == "Playlists") && data.playlists.isNotEmpty()) {
                item(key = "playlists-h") { SectionTitle("Playlists") }
                item(key = "playlists-row") { MusicPlaylistRow(null, data.playlists) { onOpenDestination(MusicDestination.Playlist(it.id)) } }
            }
        }
    }
}

@Composable
private fun MusicBrowseGenres(onOpenDestination: (MusicDestination) -> Unit, modifier: Modifier = Modifier) {
    val tokens = MaterialTheme.nuvio
    val home by produceState<MusicHomeData?>(null) { value = MusicRepository.home() }
    val genres = home?.genres.orEmpty()
    if (genres.isEmpty()) {
        Box(modifier.fillMaxWidth().padding(NuvioTokens.Space.s16), contentAlignment = Alignment.Center) { NuvioLoadingIndicator(size = NuvioTokens.Space.s24) }
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(NuvioTokens.Space.s16),
        verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s10),
    ) {
        item { SectionTitle("Genres") }
        items(genres, key = { it.id }) { genre ->
            Row(
                Modifier.fillMaxWidth().clip(tokens.shapes.compactCard).background(tokens.colors.surface).clickable {
                    onOpenDestination(MusicDestination.Genre(genre))
                }.padding(NuvioTokens.Space.s10),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s10),
            ) {
                MusicCover(genre.pictureUrl, 56.dp, tokens.shapes.compactCard)
                Text(genre.name, style = MaterialTheme.typography.bodyLarge, color = tokens.colors.textPrimary, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = tokens.colors.textMuted)
            }
        }
    }
}

@Composable
private fun MusicLibrary(onOpenDestination: (MusicDestination) -> Unit, modifier: Modifier = Modifier) {
    val tokens = MaterialTheme.nuvio
    val favorites by MusicLibraryRepository.favorites.collectAsStateWithLifecycle()
    val artists by MusicLibraryRepository.favoriteArtists.collectAsStateWithLifecycle()
    val albums by MusicLibraryRepository.favoriteAlbums.collectAsStateWithLifecycle()
    val playlists by MusicLibraryRepository.playlists.collectAsStateWithLifecycle()
    var showCreate by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = NuvioTokens.Space.s16, end = NuvioTokens.Space.s16, bottom = NuvioTokens.Space.s24),
        verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8),
    ) {
        item(key = "liked-h") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("Liked songs", Modifier.weight(1f))
                if (favorites.isNotEmpty()) Text("${favorites.size}", style = MaterialTheme.typography.labelMedium, color = tokens.colors.textMuted)
            }
        }
        if (favorites.isEmpty()) {
            item(key = "liked-empty") { Text("Songs you like appear here.", style = MaterialTheme.typography.bodySmall, color = tokens.colors.textMuted) }
        } else {
            items(favorites, key = { "f:${it.id}" }) { track -> MusicTrackListRow(track) { playMusic(track, favorites) } }
        }
        item(key = "playlists-h") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("Your playlists", Modifier.weight(1f))
                IconButton(onClick = { showCreate = true }) {
                    Icon(Icons.Filled.Add, contentDescription = "New playlist", tint = tokens.colors.accent)
                }
            }
        }
        if (playlists.isEmpty()) {
            item(key = "playlists-empty") { Text("Make a playlist to collect songs.", style = MaterialTheme.typography.bodySmall, color = tokens.colors.textMuted) }
        } else {
            items(playlists, key = { "p:${it.id}" }) { playlist ->
                Row(
                    Modifier.fillMaxWidth().clip(tokens.shapes.compactCard).background(tokens.colors.surface).clickable {
                        onOpenDestination(MusicDestination.LocalPlaylist(playlist.id))
                    }.padding(NuvioTokens.Space.s10),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s10),
                ) {
                    MusicCover(playlist.tracks.firstOrNull()?.thumbnail, 52.dp, tokens.shapes.compactCard)
                    Column(Modifier.weight(1f)) {
                        Text(playlist.title, style = MaterialTheme.typography.bodyLarge, color = tokens.colors.textPrimary, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${playlist.tracks.size} songs", style = MaterialTheme.typography.labelSmall, color = tokens.colors.textMuted)
                    }
                    Icon(Icons.Filled.MusicNote, contentDescription = null, tint = tokens.colors.textMuted)
                }
            }
        }
        if (artists.isNotEmpty()) {
            item(key = "artists-h") { SectionTitle("Artists") }
            item(key = "artists-row") { MusicArtistRow(null, artists) { onOpenDestination(MusicDestination.Artist(it.id)) } }
        }
        if (albums.isNotEmpty()) {
            item(key = "albums-h") { SectionTitle("Albums") }
            item(key = "albums-row") { MusicAlbumRow(null, albums) { onOpenDestination(MusicDestination.Album(it.id)) } }
        }
    }

    if (showCreate) {
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("New playlist") },
            text = { OutlinedTextField(value = newName, onValueChange = { newName = it }, singleLine = true, label = { Text("Playlist name") }) },
            confirmButton = {
                TextButton(onClick = {
                    MusicLibraryRepository.createPlaylist(newName)
                    newName = ""
                    showCreate = false
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showCreate = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun MusicDownloads(modifier: Modifier = Modifier) {
    val tokens = MaterialTheme.nuvio
    val downloads by MusicLibraryRepository.downloads.collectAsStateWithLifecycle()
    val message by MusicDownloadsRepository.message.collectAsStateWithLifecycle()
    LaunchedEffect(message) {
        if (message != null) {
            NuvioToastController.show(message!!)
            MusicDownloadsRepository.clearMessage()
        }
    }
    val tracks = downloads.map { it.track }
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(NuvioTokens.Space.s16),
        verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s6),
    ) {
        item { SectionTitle("Downloads") }
        if (downloads.isEmpty()) {
            item { Text("Downloaded songs play without a connection.", style = MaterialTheme.typography.bodySmall, color = tokens.colors.textMuted) }
        } else {
            items(downloads, key = { it.track.id }) { entry ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8)) {
                    MusicTrackListRow(entry.track, Modifier.weight(1f)) { playMusic(entry.track, tracks) }
                    IconButton(onClick = { MusicLibraryRepository.removeDownloaded(entry.track.id) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Remove download", tint = tokens.colors.textMuted)
                    }
                }
            }
        }
    }
}

@Composable
private fun MusicDetail(
    destination: MusicDestination,
    onBack: () -> Unit,
    onOpenDestination: (MusicDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = MaterialTheme.nuvio
    val bottomPadding = LocalNuvioBottomNavigationOverlayPadding.current
    Column(modifier.fillMaxSize().background(tokens.colors.background).statusBarsPadding().padding(bottom = bottomPadding)) {
        MusicDetailHeader(onBack)
        when (destination) {
            is MusicDestination.Artist -> MusicArtistDetail(destination.id, onOpenDestination)
            is MusicDestination.Album -> MusicAlbumDetail(destination.id)
            is MusicDestination.Playlist -> MusicPlaylistDetail(destination.id)
            is MusicDestination.LocalPlaylist -> MusicLocalPlaylistDetail(destination.id)
            is MusicDestination.Genre -> MusicGenreDetail(destination.genre)
        }
    }
}

@Composable
private fun MusicArtistDetail(artistId: String, onOpenDestination: (MusicDestination) -> Unit) {
    val tokens = MaterialTheme.nuvio
    val details by produceState<MusicArtistDetails?>(null, artistId) { value = MusicRepository.artist(artistId) }
    val data = details
    if (data == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { NuvioLoadingIndicator(size = NuvioTokens.Space.s28) }
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(NuvioTokens.Space.s16),
        verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s12),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s12)) {
                MusicCover(data.artist.pictureUrl, 96.dp, CircleShape)
                Column(Modifier.weight(1f)) {
                    Text(data.artist.name, style = MaterialTheme.typography.headlineSmall, color = tokens.colors.textPrimary, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (data.artist.fanCount > 0) Text("${data.artist.fanCount} fans", style = MaterialTheme.typography.labelMedium, color = tokens.colors.textMuted)
                }
                IconButton(onClick = { MusicLibraryRepository.toggleFavorite(data.artist) }) {
                    Icon(
                        if (MusicLibraryRepository.isArtistFavorite(data.artist.id)) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = "Save artist",
                        tint = tokens.colors.accent,
                    )
                }
            }
        }
        if (data.topTracks.isNotEmpty()) {
            item { MusicTrackRow("Top songs", data.topTracks) { playMusic(it, data.topTracks) } }
        }
        if (data.albums.isNotEmpty()) {
            item { MusicAlbumRow("Albums", data.albums) { onOpenDestination(MusicDestination.Album(it.id)) } }
        }
        if (data.relatedArtists.isNotEmpty()) {
            item { SectionTitle("Related artists") }
            item { MusicArtistRow(null, data.relatedArtists) { onOpenDestination(MusicDestination.Artist(it.id)) } }
        }
    }
}

@Composable
private fun MusicAlbumDetail(albumId: String) {
    val tokens = MaterialTheme.nuvio
    val details by produceState<MusicAlbumDetails?>(null, albumId) { value = MusicRepository.album(albumId) }
    val data = details
    if (data == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { NuvioLoadingIndicator(size = NuvioTokens.Space.s28) }
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(NuvioTokens.Space.s16),
        verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8),
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s12), verticalAlignment = Alignment.CenterVertically) {
                MusicCover(data.album.coverUrl, 130.dp, tokens.shapes.card)
                Column(Modifier.weight(1f)) {
                    Text(data.album.title, style = MaterialTheme.typography.titleLarge, color = tokens.colors.textPrimary, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(data.album.artistName, style = MaterialTheme.typography.bodyMedium, color = tokens.colors.textMuted)
                    Text(listOfNotNull(data.album.releaseDate.takeIf { it.isNotBlank() }, "${data.album.trackCount} songs".takeIf { data.album.trackCount > 0 }).joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = tokens.colors.textMuted)
                    Row {
                        IconButton(onClick = { MusicLibraryRepository.toggleFavorite(data.album) }) {
                            Icon(if (MusicLibraryRepository.isAlbumFavorite(data.album.id)) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, contentDescription = "Save album", tint = tokens.colors.accent)
                        }
                        if (data.tracks.isNotEmpty()) IconButton(onClick = { playMusic(data.tracks.first(), data.tracks) }) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = "Play album", tint = tokens.colors.accent)
                        }
                    }
                }
            }
        }
        items(data.tracks, key = { it.id }) { track -> MusicTrackListRow(track) { playMusic(track, data.tracks) } }
    }
}

@Composable
private fun MusicPlaylistDetail(playlistId: String) {
    val tokens = MaterialTheme.nuvio
    val details by produceState<MusicPlaylistDetails?>(null, playlistId) { value = MusicRepository.playlist(playlistId) }
    val data = details
    if (data == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { NuvioLoadingIndicator(size = NuvioTokens.Space.s28) }
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(NuvioTokens.Space.s16),
        verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8),
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s12), verticalAlignment = Alignment.CenterVertically) {
                MusicCover(data.playlist.coverUrl, 130.dp, tokens.shapes.card)
                Column(Modifier.weight(1f)) {
                    Text(data.playlist.title, style = MaterialTheme.typography.titleLarge, color = tokens.colors.textPrimary, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (data.playlist.creatorName.isNotBlank()) Text(data.playlist.creatorName, style = MaterialTheme.typography.bodyMedium, color = tokens.colors.textMuted)
                    if (data.playlist.description.isNotBlank()) Text(data.playlist.description, style = MaterialTheme.typography.labelSmall, color = tokens.colors.textMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (data.tracks.isNotEmpty()) IconButton(onClick = { playMusic(data.tracks.first(), data.tracks) }) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = "Play playlist", tint = tokens.colors.accent)
                    }
                }
            }
        }
        items(data.tracks, key = { it.id }) { track -> MusicTrackListRow(track) { playMusic(track, data.tracks) } }
    }
}

@Composable
private fun MusicLocalPlaylistDetail(playlistId: String) {
    val tokens = MaterialTheme.nuvio
    val playlists by MusicLibraryRepository.playlists.collectAsStateWithLifecycle()
    val playlist = playlists.firstOrNull { it.id == playlistId }
    if (playlist == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Playlist not found.", color = tokens.colors.textMuted) }
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(NuvioTokens.Space.s16),
        verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(playlist.title, style = MaterialTheme.typography.titleLarge, color = tokens.colors.textPrimary, fontWeight = FontWeight.Bold)
                    Text("${playlist.tracks.size} songs", style = MaterialTheme.typography.labelMedium, color = tokens.colors.textMuted)
                }
                if (playlist.tracks.isNotEmpty()) IconButton(onClick = { playMusic(playlist.tracks.first(), playlist.tracks) }) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "Play playlist", tint = tokens.colors.accent)
                }
                IconButton(onClick = { MusicLibraryRepository.deletePlaylist(playlist.id) }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete playlist", tint = tokens.colors.textMuted)
                }
            }
        }
        if (playlist.tracks.isEmpty()) {
            item { Text("No songs yet. Add songs from the player.", style = MaterialTheme.typography.bodyMedium, color = tokens.colors.textMuted) }
        } else {
            items(playlist.tracks, key = { it.id }) { track -> MusicTrackListRow(track) { playMusic(track, playlist.tracks) } }
        }
    }
}

@Composable
private fun MusicGenreDetail(genre: MusicGenre) {
    val tokens = MaterialTheme.nuvio
    val tracks by produceState<List<MusicTrack>?>(null, genre.id) { value = MusicRepository.genreTracks(genre) }
    val list = tracks
    if (list == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { NuvioLoadingIndicator(size = NuvioTokens.Space.s28) }
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(NuvioTokens.Space.s16),
        verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8),
    ) {
        item { SectionTitle(genre.name) }
        if (list.isEmpty()) {
            item { Text("No songs found in this genre.", color = tokens.colors.textMuted) }
        } else {
            items(list, key = { it.id }) { track -> MusicTrackListRow(track) { playMusic(track, list) } }
        }
    }
}

@Composable
private fun MusicDetailHeader(onBack: () -> Unit) {
    val tokens = MaterialTheme.nuvio
    Row(
        Modifier.fillMaxWidth().padding(horizontal = NuvioTokens.Space.s8, vertical = NuvioTokens.Space.s6),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8),
    ) {
        IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = tokens.colors.textPrimary) }
        Text("Music", style = MaterialTheme.typography.titleMedium, color = tokens.colors.textPrimary, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun MusicHero(track: MusicTrack, queue: List<MusicTrack>) {
    val tokens = MaterialTheme.nuvio
    Box(Modifier.fillMaxWidth().height(210.dp).clip(tokens.shapes.card).clickable { playMusic(track, queue) }) {
        AsyncImage(model = track.thumbnail, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.88f)))))
        Column(Modifier.align(Alignment.BottomStart).padding(NuvioTokens.Space.s16), verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s4)) {
            Text("FEATURED", style = MaterialTheme.typography.labelSmall, color = tokens.colors.accent, fontWeight = FontWeight.Bold)
            Text(track.title, style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(track.artist, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(
            Modifier.align(Alignment.BottomEnd).padding(NuvioTokens.Space.s16).clip(RoundedCornerShape(NuvioTokens.Radius.full)).background(tokens.colors.accent).clickable { playMusic(track, queue) }.padding(horizontal = NuvioTokens.Space.s12, vertical = NuvioTokens.Space.s8),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s4),
        ) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = tokens.colors.onAccent, modifier = Modifier.size(NuvioTokens.Icon.sm))
            Text("Play", color = tokens.colors.onAccent, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun MusicTrackRow(title: String, tracks: List<MusicTrack>, onPlay: (MusicTrack) -> Unit) {
    val tokens = MaterialTheme.nuvio
    Column(verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8)) {
        SectionTitle(title)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s10)) {
            items(tracks, key = { it.id }) { track ->
                Column(
                    Modifier.width(160.dp).clip(tokens.shapes.compactCard).background(tokens.colors.surface).clickable { onPlay(track) },
                ) {
                    Box(Modifier.fillMaxWidth().aspectRatio(1f).background(Color.Black), contentAlignment = Alignment.Center) {
                        AsyncImage(model = track.thumbnail, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        Box(Modifier.size(32.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = "Play", tint = Color.White, modifier = Modifier.size(NuvioTokens.Icon.sm))
                        }
                    }
                    Column(Modifier.padding(NuvioTokens.Space.s8), verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s2)) {
                        Text(track.title, style = MaterialTheme.typography.bodySmall, color = tokens.colors.textPrimary, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(track.artist, style = MaterialTheme.typography.labelSmall, color = tokens.colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun MusicAlbumRow(title: String?, albums: List<MusicAlbum>, onOpen: (MusicAlbum) -> Unit) {
    val tokens = MaterialTheme.nuvio
    Column(verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8)) {
        title?.let { SectionTitle(it) }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s10)) {
            items(albums, key = { it.id }) { album ->
                Column(Modifier.width(150.dp).clickable { onOpen(album) }) {
                    AsyncImage(model = album.coverUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(tokens.shapes.compactCard).background(tokens.colors.surface))
                    Text(album.title, style = MaterialTheme.typography.bodySmall, color = tokens.colors.textPrimary, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = NuvioTokens.Space.s4))
                    Text(album.artistName, style = MaterialTheme.typography.labelSmall, color = tokens.colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun MusicArtistRow(title: String?, artists: List<MusicArtist>, onOpen: (MusicArtist) -> Unit) {
    val tokens = MaterialTheme.nuvio
    Column(verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8)) {
        title?.let { SectionTitle(it) }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s12)) {
            items(artists, key = { it.id }) { artist ->
                Column(Modifier.width(104.dp).clickable { onOpen(artist) }, horizontalAlignment = Alignment.CenterHorizontally) {
                    AsyncImage(model = artist.pictureUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(88.dp).clip(CircleShape).background(tokens.colors.surface))
                    Text(artist.name, style = MaterialTheme.typography.bodySmall, color = tokens.colors.textPrimary, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.padding(top = NuvioTokens.Space.s4))
                }
            }
        }
    }
}

@Composable
private fun MusicPlaylistRow(title: String?, playlists: List<MusicPlaylist>, onOpen: (MusicPlaylist) -> Unit) {
    val tokens = MaterialTheme.nuvio
    Column(verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8)) {
        title?.let { SectionTitle(it) }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s10)) {
            items(playlists, key = { it.id }) { playlist ->
                Column(Modifier.width(160.dp).clickable { onOpen(playlist) }) {
                    AsyncImage(model = playlist.coverUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(tokens.shapes.compactCard).background(tokens.colors.surface))
                    Text(playlist.title, style = MaterialTheme.typography.bodySmall, color = tokens.colors.textPrimary, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = NuvioTokens.Space.s4))
                    Text(playlist.creatorName.ifBlank { "${playlist.trackCount} songs" }, style = MaterialTheme.typography.labelSmall, color = tokens.colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun MusicGenreRow(title: String, genres: List<MusicGenre>, onOpen: (MusicGenre) -> Unit) {
    val tokens = MaterialTheme.nuvio
    Column(verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8)) {
        SectionTitle(title)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8)) {
            items(genres, key = { it.id }) { genre ->
                Box(
                    Modifier.width(130.dp).height(72.dp).clip(tokens.shapes.compactCard).background(tokens.colors.surfaceElevated).clickable { onOpen(genre) },
                    contentAlignment = Alignment.Center,
                ) {
                    AsyncImage(model = genre.pictureUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    Text(genre.name, style = MaterialTheme.typography.bodySmall, color = Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.padding(NuvioTokens.Space.s6).background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(NuvioTokens.Radius.sm)).padding(horizontal = NuvioTokens.Space.s4, vertical = 1.dp))
                }
            }
        }
    }
}

@Composable
private fun MusicTrackListRow(track: MusicTrack, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val tokens = MaterialTheme.nuvio
    Row(
        modifier.fillMaxWidth().clip(tokens.shapes.compactCard).background(tokens.colors.surface).clickable(onClick = onClick).padding(NuvioTokens.Space.s8),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s10),
    ) {
        MusicCover(track.thumbnail, 52.dp, tokens.shapes.compactCard)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s2)) {
            Text(track.title, style = MaterialTheme.typography.bodyMedium, color = tokens.colors.textPrimary, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track.artist, style = MaterialTheme.typography.bodySmall, color = tokens.colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (track.displayDuration.isNotBlank()) Text(track.displayDuration, style = MaterialTheme.typography.labelSmall, color = tokens.colors.textMuted)
        Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = tokens.colors.textMuted)
    }
}

@Composable
private fun MusicCover(url: String?, size: androidx.compose.ui.unit.Dp, shape: androidx.compose.ui.graphics.Shape) {
    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.size(size).clip(shape).background(MaterialTheme.nuvio.colors.surface),
    )
}

@Composable
private fun SectionTitle(title: String, modifier: Modifier = Modifier) {
    Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.nuvio.colors.textPrimary, fontWeight = FontWeight.Bold, modifier = modifier)
}

@Composable
private fun MusicTabChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val tokens = MaterialTheme.nuvio
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        color = if (selected) tokens.colors.onAccent else tokens.colors.textMuted,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        modifier = Modifier.clip(RoundedCornerShape(NuvioTokens.Radius.full)).background(if (selected) tokens.colors.accent else tokens.colors.surface).clickable(onClick = onClick).padding(horizontal = NuvioTokens.Space.s12, vertical = NuvioTokens.Space.s6),
    )
}

@Composable
private fun MusicModeToggle(audioOnly: Boolean) {
    val tokens = MaterialTheme.nuvio
    Row(
        Modifier.clip(RoundedCornerShape(NuvioTokens.Radius.full)).background(tokens.colors.surface).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        MusicModeButton("Video", Icons.Filled.Videocam, !audioOnly) { MusicPlayerRepository.setAudioOnly(false) }
        MusicModeButton("Audio", Icons.Filled.MusicNote, audioOnly) { MusicPlayerRepository.setAudioOnly(true) }
    }
}

@Composable
private fun MusicModeButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, onClick: () -> Unit) {
    val tokens = MaterialTheme.nuvio
    Row(
        Modifier.clip(RoundedCornerShape(NuvioTokens.Radius.full)).background(if (selected) tokens.colors.accent else Color.Transparent).clickable(onClick = onClick).padding(horizontal = NuvioTokens.Space.s8, vertical = NuvioTokens.Space.s6),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s4),
    ) {
        Icon(icon, contentDescription = null, tint = if (selected) tokens.colors.onAccent else tokens.colors.textMuted, modifier = Modifier.size(NuvioTokens.Icon.xs))
        Text(label, style = MaterialTheme.typography.labelMedium, color = if (selected) tokens.colors.onAccent else tokens.colors.textMuted, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun MusicPlayer(track: MusicTrack, modifier: Modifier = Modifier) {
    val tokens = MaterialTheme.nuvio
    val queue by MusicPlayerRepository.queue.collectAsStateWithLifecycle()
    val audioOnly by MusicPlayerRepository.audioOnly.collectAsStateWithLifecycle()
    val shuffle by MusicPlayerRepository.shuffle.collectAsStateWithLifecycle()
    val repeat by MusicPlayerRepository.repeat.collectAsStateWithLifecycle()
    val favorites by MusicLibraryRepository.favorites.collectAsStateWithLifecycle()
    val downloading by MusicDownloadsRepository.active.collectAsStateWithLifecycle()
    val downloadMessage by MusicDownloadsRepository.message.collectAsStateWithLifecycle()
    val bottomPadding = LocalNuvioBottomNavigationOverlayPadding.current
    val scope = rememberCoroutineScope()
    val controllers = remember { mutableStateMapOf<String, PlayerEngineController>() }
    val snapshots = remember { mutableStateMapOf<String, PlayerPlaybackSnapshot>() }
    var lastTrackId by remember { mutableStateOf(track.id) }
    var transitionCount by remember { mutableIntStateOf(0) }
    var stopOutgoing by remember { mutableIntStateOf(0) }
    var scrimAlpha by remember { mutableFloatStateOf(0f) }
    var scrimReady by remember { mutableStateOf(true) }
    var incomingReady by remember { mutableStateOf(false) }
    SideEffect {
        if (lastTrackId != track.id) {
            lastTrackId = track.id
            transitionCount += 1
            stopOutgoing = 0
            scrimReady = false
            incomingReady = false
        }
    }
    LaunchedEffect(transitionCount) {
        if (transitionCount == 0) return@LaunchedEffect
        // Bring the black cover up before the next surface is created; the new picture then
        // fades in from black once its player is ready.
        animate(scrimAlpha, 1f, animationSpec = tween(300, easing = LinearEasing)) { value, _ ->
            scrimAlpha = value
        }
        scrimReady = true
    }
    LaunchedEffect(incomingReady) {
        if (!incomingReady) return@LaunchedEffect
        animate(scrimAlpha, 0f, animationSpec = tween(450, easing = LinearEasing)) { value, _ ->
            scrimAlpha = value
        }
    }
    LaunchedEffect(scrimReady) {
        if (!scrimReady) return@LaunchedEffect
        // If the next song cannot resolve, don't leave the picture stuck behind the black cover.
        delay(4_000)
        if (scrimAlpha > 0.01f) {
            animate(scrimAlpha, 0f, animationSpec = tween(450, easing = LinearEasing)) { value, _ ->
                scrimAlpha = value
            }
        }
    }
    val controller = controllers[track.id]
    val snapshot = snapshots[track.id] ?: PlayerPlaybackSnapshot()
    val trackChangedThisFrame = lastTrackId != track.id
    // The first track starts at full volume; every later track fades in over the outgoing one.
    val fadeInIncoming = transitionCount > 0 || trackChangedThisFrame
    var dragFraction by remember(track.id) { mutableStateOf<Float?>(null) }
    var showLyrics by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    var showSave by remember { mutableStateOf(false) }
    val isFavorite = favorites.any { it.id == track.id }
    val downloaded = MusicDownloadsRepository.isDownloaded(track.id)
    val isDownloading = track.id in downloading
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    var chromeVisible by remember { mutableStateOf(true) }
    var chromeInteractionTick by remember { mutableIntStateOf(0) }
    val sheetOpen = showLyrics || showQueue || showSave

    fun wakeChrome() {
        chromeVisible = true
        chromeInteractionTick += 1
    }

    LaunchedEffect(chromeVisible) {
        MusicPlayerRepository.setPlayerChromeVisible(chromeVisible)
        if (chromeVisible) focusManager.clearFocus() else focusRequester.requestFocus()
    }
    DisposableEffect(Unit) {
        onDispose { MusicPlayerRepository.setPlayerChromeVisible(true) }
    }
    // The top bar, transport and actions fade out after five seconds without input while playing.
    LaunchedEffect(chromeVisible, chromeInteractionTick, snapshot.isPlaying, snapshot.isEnded, sheetOpen) {
        if (!chromeVisible || !snapshot.isPlaying || snapshot.isEnded || sheetOpen) return@LaunchedEffect
        delay(5_000)
        chromeVisible = false
    }

    LaunchedEffect(downloadMessage) {
        if (downloadMessage != null) {
            NuvioToastController.show(downloadMessage!!)
            MusicDownloadsRepository.clearMessage()
        }
    }
    LaunchedEffect(snapshot.isEnded, controller) {
        if (!snapshot.isEnded) return@LaunchedEffect
        when (repeat) {
            MusicRepeatMode.ONE -> {
                val current = controller ?: return@LaunchedEffect
                current.seekTo(0L)
                current.setVolume(0f)
                current.play()
                scope.launch { fadeMusicVolume(current, 0f, 1f) {} }
            }
            else -> MusicPlayerRepository.advanceOnEnd()
        }
    }

    Column(
        modifier
            .fillMaxSize()
            .background(tokens.colors.background)
            .statusBarsPadding()
            .padding(bottom = bottomPadding)
            .focusRequester(focusRequester)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val wasHidden = !chromeVisible
                wakeChrome()
                // The first D-pad press only wakes the controls; it must not also click one.
                wasHidden
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    wakeChrome()
                }
            }
            .focusable(),
    ) {
        AnimatedVisibility(visible = chromeVisible, enter = fadeIn(), exit = fadeOut()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = NuvioTokens.Space.s8, vertical = NuvioTokens.Space.s6),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8),
            ) {
                IconButton(onClick = { MusicPlayerRepository.close() }) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = tokens.colors.textPrimary) }
                Column(Modifier.weight(1f)) {
                    Text("Now playing", style = MaterialTheme.typography.titleMedium, color = tokens.colors.textPrimary, fontWeight = FontWeight.SemiBold)
                    Text("Deezer · YouTube · ${queue.size} in queue", style = MaterialTheme.typography.labelSmall, color = tokens.colors.textMuted)
                }
                MusicModeToggle(audioOnly)
            }
        }

        Box(Modifier.fillMaxWidth().weight(1f).padding(horizontal = NuvioTokens.Space.s16), contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxWidth().widthIn(max = 520.dp).aspectRatio(16f / 9f).clip(tokens.shapes.card).background(Color.Black), contentAlignment = Alignment.Center) {
                AnimatedContent(
                    targetState = track,
                    transitionSpec = {
                        fadeIn(tween(MUSIC_SURFACE_TRANSITION_MS, easing = LinearEasing)) togetherWith
                            fadeOut(tween(MUSIC_SURFACE_TRANSITION_MS, easing = LinearEasing))
                    },
                    contentKey = { it.id },
                    modifier = Modifier.fillMaxSize(),
                    label = "music-track-crossfade",
                ) { activeTrack ->
                    MusicVideoPane(
                        track = activeTrack,
                        isCurrent = activeTrack.id == track.id,
                        fadeIn = fadeInIncoming,
                        audioOnly = audioOnly,
                        stopSignal = stopOutgoing,
                        showSurface = scrimReady && !trackChangedThisFrame,
                        onControllerReady = { id, ready ->
                            controllers[id] = ready
                            if (id == track.id) incomingReady = true
                        },
                        onSnapshot = { id, snap -> snapshots[id] = snap },
                        onDisposePane = { id ->
                            controllers.remove(id)
                            snapshots.remove(id)
                        },
                    )
                }
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = scrimAlpha)))
            }
        }

        AnimatedVisibility(visible = chromeVisible, enter = fadeIn(), exit = fadeOut()) {
            Column(Modifier.fillMaxWidth().padding(horizontal = NuvioTokens.Space.s16, vertical = NuvioTokens.Space.s8), verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s6)) {
                Text(track.title, style = MaterialTheme.typography.titleLarge, color = tokens.colors.textPrimary, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(listOfNotNull(track.artist, track.album.takeIf { it.isNotBlank() && it != "Single" }).joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = tokens.colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                    colors = SliderDefaults.colors(thumbColor = tokens.colors.accent, activeTrackColor = tokens.colors.accent),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { MusicPlayerRepository.toggleShuffle() }) {
                        Icon(Icons.Filled.Shuffle, contentDescription = "Shuffle", tint = if (shuffle) tokens.colors.accent else tokens.colors.textMuted)
                    }
                    IconButton(onClick = {
                        if (snapshot.positionMs > 3_000L) controller?.seekTo(0L) else MusicPlayerRepository.previous()
                    }) { Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous", tint = tokens.colors.textPrimary) }
                    IconButton(
                        onClick = {
                            controller?.let {
                                if (snapshot.isPlaying) {
                                    it.pause()
                                    // Pause also stops the outgoing track if a crossfade is still running.
                                    stopOutgoing += 1
                                } else {
                                    it.play()
                                }
                            }
                        },
                        modifier = Modifier.padding(horizontal = NuvioTokens.Space.s12).size(56.dp).clip(CircleShape).background(tokens.colors.accent),
                    ) {
                        Icon(if (snapshot.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = if (snapshot.isPlaying) "Pause" else "Play", tint = tokens.colors.onAccent, modifier = Modifier.size(NuvioTokens.Icon.lg))
                    }
                    IconButton(onClick = { MusicPlayerRepository.next() }) { Icon(Icons.Filled.SkipNext, contentDescription = "Next", tint = tokens.colors.textPrimary) }
                    IconButton(onClick = { MusicPlayerRepository.cycleRepeat() }) {
                        Icon(if (repeat == MusicRepeatMode.ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat, contentDescription = "Repeat", tint = if (repeat != MusicRepeatMode.OFF) tokens.colors.accent else tokens.colors.textMuted)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    PlayerAction(if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, if (isFavorite) "Remove from liked" else "Like") {
                        MusicLibraryRepository.toggleFavorite(track)
                    }
                    PlayerAction(Icons.Filled.Subtitles, "Lyrics") { showLyrics = true }
                    PlayerAction(Icons.Filled.QueueMusic, "Queue") { showQueue = true }
                    PlayerAction(
                        if (downloaded) Icons.Filled.Download else Icons.Filled.Download,
                        when { downloaded -> "Downloaded"; isDownloading -> "Downloading…"; else -> "Download" },
                        enabled = !downloaded && !isDownloading,
                    ) { MusicDownloadsRepository.download(track) }
                    PlayerAction(Icons.Filled.Add, "Save to playlist") { showSave = true }
                }
            }
        }
    }

    if (showLyrics) MusicLyricsSheet(track, snapshot.positionMs, onDismiss = { showLyrics = false })
    if (showQueue) MusicQueueSheet(onDismiss = { showQueue = false })
    if (showSave) MusicSaveSheet(track, onDismiss = { showSave = false })
}

/**
 * One track's video surface. Two of these overlap while [MusicPlayer] crossfades, so each one
 * owns its player and reports state back under its own track id.
 */
@Composable
private fun MusicVideoPane(
    track: MusicTrack,
    isCurrent: Boolean,
    fadeIn: Boolean,
    audioOnly: Boolean,
    stopSignal: Int,
    showSurface: Boolean,
    onControllerReady: (String, PlayerEngineController) -> Unit,
    onSnapshot: (String, PlayerPlaybackSnapshot) -> Unit,
    onDisposePane: (String) -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    var reload by remember(track.id) { mutableIntStateOf(0) }
    val resolved by produceState<Result<YouTubeResolvedVideo>?>(null, track.id, reload) {
        value = null
        value = MusicRepository.resolve(track)
    }
    var controller by remember(track.id) { mutableStateOf<PlayerEngineController?>(null) }
    var error by remember(track.id) { mutableStateOf<String?>(null) }
    var volume by remember(track.id) { mutableFloatStateOf(if (fadeIn) 0f else 1f) }
    val hasLocalAudio = track.localFile != null || MusicLibraryRepository.downloadFor(track.id) != null
    val fadeInOnStart = remember(track.id) { fadeIn }
    val stopAtStart = remember(track.id) { stopSignal }

    DisposableEffect(track.id) {
        onDispose { onDisposePane(track.id) }
    }

    LaunchedEffect(isCurrent, controller, stopSignal) {
        val current = controller ?: return@LaunchedEffect
        when {
            !isCurrent && stopSignal > stopAtStart -> {
                current.pause()
                current.setVolume(0f)
                volume = 0f
            }
            !isCurrent -> fadeMusicVolume(current, volume, 0f) { volume = it }
            fadeInOnStart -> fadeMusicVolume(current, volume, 1f) { volume = it }
            else -> {
                current.setVolume(1f)
                volume = 1f
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        val video = resolved?.getOrNull()
        // Hold the incoming surface back until the black cover is up. Outgoing panes keep
        // their surface so the old picture and audio can finish their fade.
        if (video != null && (!isCurrent || showSurface)) {
            key(track.id) {
                PlatformPlayerSurface(
                    sourceUrl = video.url,
                    streamType = null,
                    resizeMode = PlayerResizeMode.Fit,
                    playWhenReady = true,
                    // Both sides of a crossfade must play at once; each one holds no audio focus.
                    allowConcurrentAudio = true,
                    interceptTouches = false,
                    modifier = Modifier.fillMaxSize(),
                    onControllerReady = { ready ->
                        val startVolume = if (fadeInOnStart) 0f else 1f
                        ready.setVolume(startVolume)
                        volume = startVolume
                        controller = ready
                        onControllerReady(track.id, ready)
                        ready.play()
                    },
                    onSnapshot = { onSnapshot(track.id, it) },
                    onError = { if (isCurrent) error = it },
                )
            }
        }
        if (audioOnly || hasLocalAudio) {
            AsyncImage(model = track.thumbnail, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f)))), contentAlignment = Alignment.BottomCenter) {
                Row(Modifier.padding(NuvioTokens.Space.s12).clip(RoundedCornerShape(NuvioTokens.Radius.full)).background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = NuvioTokens.Space.s10, vertical = NuvioTokens.Space.s4), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.MusicNote, contentDescription = null, tint = Color.White, modifier = Modifier.size(NuvioTokens.Icon.xs))
                    Text(if (hasLocalAudio) "Downloaded audio" else "Audio only", color = Color.White, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = NuvioTokens.Space.s4))
                }
            }
        }
        if (isCurrent) {
            when {
                resolved == null -> NuvioLoadingIndicator(size = NuvioTokens.Space.s28)
                resolved?.isFailure == true -> {
                    val message = resolved?.exceptionOrNull()?.message ?: "Couldn't play this song."
                    Column(Modifier.padding(NuvioTokens.Space.s16), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8)) {
                        Text(message, style = MaterialTheme.typography.bodySmall, color = Color.White, textAlign = TextAlign.Center)
                        Text("Tap to retry", style = MaterialTheme.typography.labelMedium, color = tokens.colors.accent, modifier = Modifier.clickable { error = null; reload++ })
                    }
                }
            }
            error?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = Color.White, textAlign = TextAlign.Center, modifier = Modifier.align(Alignment.Center).clip(RoundedCornerShape(NuvioTokens.Radius.sm)).background(Color.Black.copy(alpha = 0.75f)).padding(NuvioTokens.Space.s8))
            }
        }
    }
}

private const val MUSIC_CROSSFADE_MS = 1200
private const val MUSIC_SURFACE_TRANSITION_MS = 2200

/** Ramps a player's own volume; the caller keeps its matching UI state in sync through [onValue]. */
private suspend fun fadeMusicVolume(
    controller: PlayerEngineController,
    from: Float,
    to: Float,
    onValue: (Float) -> Unit,
) {
    if (from == to) {
        controller.setVolume(to)
        onValue(to)
        return
    }
    animate(from, to, animationSpec = tween(MUSIC_CROSSFADE_MS, easing = LinearEasing)) { value, _ ->
        controller.setVolume(value)
        onValue(value)
    }
}

@Composable
private fun PlayerAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(icon, contentDescription = description, tint = if (enabled) tokens.colors.textPrimary else tokens.colors.textMuted.copy(alpha = 0.5f))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MusicLyricsSheet(track: MusicTrack, positionMs: Long, onDismiss: () -> Unit) {
    val tokens = MaterialTheme.nuvio
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val lyrics by produceState<LyricsData?>(null, track.id) { value = MusicRepository.lyrics(track) }
    val data = lyrics
    val listState = rememberLazyListState()
    val active = data?.activeLineIndex(positionMs) ?: -1
    LaunchedEffect(active) { if (active >= 0) listState.animateScrollToItem(active) }
    NuvioModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().heightIn(max = 620.dp).padding(horizontal = NuvioTokens.Space.s16)) {
            Row(Modifier.fillMaxWidth().padding(vertical = NuvioTokens.Space.s10), verticalAlignment = Alignment.CenterVertically) {
                Text("Lyrics · ${track.title}", style = MaterialTheme.typography.titleMedium, color = tokens.colors.textPrimary, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("Close") }
            }
            when {
                data == null -> Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) { NuvioLoadingIndicator(size = NuvioTokens.Space.s24) }
                data.isEmpty -> Text("No lyrics found for this song.", style = MaterialTheme.typography.bodyMedium, color = tokens.colors.textMuted, modifier = Modifier.padding(vertical = NuvioTokens.Space.s16))
                data.isSynced -> LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(max = 520.dp)) {
                    itemsIndexed(data.syncedLines) { index, line ->
                        Text(
                            line.text.ifBlank { " " },
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (index == active) tokens.colors.accent else tokens.colors.textSecondary,
                            fontWeight = if (index == active) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.fillMaxWidth().padding(vertical = NuvioTokens.Space.s4),
                        )
                    }
                }
                else -> LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(max = 520.dp)) {
                    items(data.plainLyrics.lines()) { line ->
                        Text(line, style = MaterialTheme.typography.bodyLarge, color = tokens.colors.textSecondary, modifier = Modifier.fillMaxWidth().padding(vertical = NuvioTokens.Space.s2))
                    }
                }
            }
            Spacer(Modifier.height(NuvioTokens.Space.s16))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MusicQueueSheet(onDismiss: () -> Unit) {
    val tokens = MaterialTheme.nuvio
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val queue by MusicPlayerRepository.queue.collectAsStateWithLifecycle()
    NuvioModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().heightIn(max = 620.dp).padding(horizontal = NuvioTokens.Space.s16)) {
            Row(Modifier.fillMaxWidth().padding(vertical = NuvioTokens.Space.s10), verticalAlignment = Alignment.CenterVertically) {
                Text("Queue · ${queue.size}", style = MaterialTheme.typography.titleMedium, color = tokens.colors.textPrimary, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("Close") }
            }
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 520.dp)) {
                itemsIndexed(queue, key = { _, item -> item.id }) { index, item ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MusicTrackListRow(item, Modifier.weight(1f)) {
                            MusicPlayerRepository.playFromQueue(index)
                            onDismiss()
                        }
                        IconButton(onClick = { MusicPlayerRepository.removeFromQueue(index) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Remove from queue", tint = tokens.colors.textMuted)
                        }
                    }
                }
            }
            Spacer(Modifier.height(NuvioTokens.Space.s16))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MusicSaveSheet(track: MusicTrack, onDismiss: () -> Unit) {
    val tokens = MaterialTheme.nuvio
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val playlists by MusicLibraryRepository.playlists.collectAsStateWithLifecycle()
    var newName by remember { mutableStateOf("") }
    NuvioModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().heightIn(max = 620.dp).padding(horizontal = NuvioTokens.Space.s16)) {
            Text("Save to playlist", style = MaterialTheme.typography.titleMedium, color = tokens.colors.textPrimary, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = NuvioTokens.Space.s10))
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
                items(playlists, key = { it.id }) { playlist ->
                    Row(Modifier.fillMaxWidth().clickable {
                        MusicLibraryRepository.addToPlaylist(playlist.id, track)
                        NuvioToastController.show("Added to ${playlist.title}")
                        onDismiss()
                    }.padding(vertical = NuvioTokens.Space.s8), verticalAlignment = Alignment.CenterVertically) {
                        Text(playlist.title, color = tokens.colors.textPrimary, modifier = Modifier.weight(1f))
                        Text("${playlist.tracks.size} songs", color = tokens.colors.textMuted, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            NuvioInputField(value = newName, onValueChange = { newName = it }, placeholder = "New playlist name", modifier = Modifier.fillMaxWidth().padding(vertical = NuvioTokens.Space.s6))
            TextButton(
                onClick = {
                    val playlist = MusicLibraryRepository.createPlaylist(newName)
                    MusicLibraryRepository.addToPlaylist(playlist.id, track)
                    NuvioToastController.show("Added to ${playlist.title}")
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Create and add") }
            Spacer(Modifier.height(NuvioTokens.Space.s16))
        }
    }
}

private fun playMusic(track: MusicTrack, queue: List<MusicTrack>) {
    MusicLibraryRepository.ensureLoaded()
    MusicPlayerRepository.play(
        MusicLibraryRepository.withDownload(track),
        queue.map(MusicLibraryRepository::withDownload),
    )
}
