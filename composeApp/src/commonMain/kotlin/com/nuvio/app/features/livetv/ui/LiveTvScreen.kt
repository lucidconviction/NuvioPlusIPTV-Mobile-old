package com.nuvio.app.features.livetv.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.CircularProgressIndicator
import com.nuvio.app.core.ui.Menu
import com.nuvio.app.core.ui.MenuItem
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.TheaterComedy
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.livetv_group_channels
import nuvio.composeapp.generated.resources.livetv_group_one_channel
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarViewDay
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.nuvio.app.core.ui.NuvioInputField
import com.nuvio.app.core.ui.NuvioLoadingIndicator
import com.nuvio.app.core.ui.NuvioPrimaryButton
import com.nuvio.app.core.ui.NuvioProgressBar
import com.nuvio.app.core.ui.NuvioScreen
import com.nuvio.app.core.ui.NuvioScreenHeader
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.core.ui.NuvioColorTokens
import com.nuvio.app.core.ui.ScreenActivityEffect
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.livetv.data.LiveTvPreferences
import com.nuvio.app.features.livetv.data.LiveTvRepository
import com.nuvio.app.features.livetv.data.NowNext
import com.nuvio.app.features.livetv.data.StreamValidationRepository
import com.nuvio.app.features.livetv.model.ChannelGroup
import com.nuvio.app.features.livetv.model.LiveChannel
import com.nuvio.app.features.livetv.model.EpgProgram
import com.nuvio.app.features.livetv.model.LiveTvClock
import com.nuvio.app.features.livetv.ondemand.OnDemandRepository
import com.nuvio.app.features.livetv.player.LiveTvCatchup
import com.nuvio.app.features.livetv.record.LiveTvRecorder
import com.nuvio.app.features.livetv.record.LiveTvRecordingScheduler
import com.nuvio.app.features.livetv.sports.SportsLive
import com.nuvio.app.features.livetv.sports.SportsRepository
import com.nuvio.app.features.livetv.sports.GameState
import com.nuvio.app.core.ui.NuvioBottomSheetActionRow
import com.nuvio.app.features.addons.AddonRepository
import com.nuvio.app.features.addons.ManagedAddon
import com.nuvio.app.features.addons.liveTvAddons
import com.nuvio.app.features.catalog.CatalogUiState
import com.nuvio.app.features.catalog.fetchCatalogPage
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.livetv.ui.addonscatalog.AddonCatalogScreen
import com.nuvio.app.features.livetv.ui.quickchannels.QuickChannelList
import com.nuvio.app.features.livetv.ui.quickchannels.QuickChannel
import com.nuvio.app.features.livetv.ui.quickchannels.rememberQuickChannelMatchState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.livetv_add_playlist
import nuvio.composeapp.generated.resources.livetv_empty_body
import nuvio.composeapp.generated.resources.livetv_empty_title
import nuvio.composeapp.generated.resources.livetv_guide
import nuvio.composeapp.generated.resources.livetv_header_guide
import nuvio.composeapp.generated.resources.livetv_header_search
import nuvio.composeapp.generated.resources.livetv_header_settings
import nuvio.composeapp.generated.resources.livetv_header_quick_channels
import nuvio.composeapp.generated.resources.livetv_header_addons
import nuvio.composeapp.generated.resources.livetv_quick_channels_section
import nuvio.composeapp.generated.resources.livetv_quick_channels_open
import nuvio.composeapp.generated.resources.livetv_no_matching_channels
import nuvio.composeapp.generated.resources.livetv_addons_section
import nuvio.composeapp.generated.resources.livetv_addons_open
import nuvio.composeapp.generated.resources.livetv_no_addons_installed
import nuvio.composeapp.generated.resources.livetv_remove_addon
import nuvio.composeapp.generated.resources.livetv_no_results
import nuvio.composeapp.generated.resources.livetv_on_demand
import nuvio.composeapp.generated.resources.livetv_rec_record_now
import nuvio.composeapp.generated.resources.livetv_rec_schedule
import nuvio.composeapp.generated.resources.livetv_rec_stop
import nuvio.composeapp.generated.resources.livetv_recordings
import nuvio.composeapp.generated.resources.livetv_search_hint
import nuvio.composeapp.generated.resources.livetv_settings
import nuvio.composeapp.generated.resources.livetv_sports
import nuvio.composeapp.generated.resources.livetv_manage_menu
import com.nuvio.app.core.ui.Menu
import com.nuvio.app.core.ui.MenuItem
import nuvio.composeapp.generated.resources.livetv_title
import nuvio.composeapp.generated.resources.livetv_update_now
import org.jetbrains.compose.resources.stringResource

/**
 * The Live TV tab: channels grouped (favorites first), each with what's on now and how far
 * through it is. Tap plays; long-press opens the channel's options.
 */
private data class CombinedSection(
    val group: ChannelGroup,
    val channels: List<LiveChannel>,
    val isAddonsSection: Boolean,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LiveTvScreen(
    modifier: Modifier = Modifier,
    scrollToTopRequests: Flow<Unit> = emptyFlow(),
    onPlayChannel: (LiveChannel, nowTitle: String?) -> Unit,
    onOpenGuide: () -> Unit,
    onOpenSettings: () -> Unit,
    /** Replay the show on now from its beginning (catch-up). */
    onPlayCatchup: (LiveChannel, EpgProgram) -> Unit = { _, _ -> },
    /** Opens On Demand (your providers' movies and series). */
    onOpenOnDemand: () -> Unit = {},
    /** Opens Recordings. */
    onOpenRecordings: () -> Unit = {},
    /** Opens the Sports hub. */
    onOpenSports: () -> Unit = {},
    /** Opens the multi-view page. */
    onOpenMultiView: () -> Unit = {},
    /** Sends a channel from the list straight to Multi-view. */
    onAddChannelToMultiView: (LiveChannel) -> Unit = {},
    /** Opens Manage (reorder and show or hide groups and channels). */
    onOpenManage: () -> Unit = {},
    /** Opens an add-on catalog. */
    onOpenAddonCatalog: (manifestUrl: String, com.nuvio.app.features.addons.AddonCatalog) -> Unit = { _, _ -> },
    /** Plays a catalog item (MetaPreview) tapped inside an inline add-on folder. */
    onPlayAddonItem: (MetaPreview) -> Unit = {},
    /** Sends an add-on item straight to the Multi-view page. */
    onAddonItemToMultiView: (MetaPreview) -> Unit = {},
    /** Opens the add-on settings / manage screen. */
    onOpenAddonSettings: () -> Unit = {},
) {
    val tokens = MaterialTheme.nuvio
    // Restore the open groups and the scroll position when coming back from the player.
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = LiveTvListMemory.scrollIndex,
        initialFirstVisibleItemScrollOffset = LiveTvListMemory.scrollOffset,
    )
    DisposableEffect(listState) {
        onDispose { LiveTvListMemory.saveScroll(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) }
    }
    val playlists by LiveTvPreferences.playlists.collectAsStateWithLifecycle()
    val savedPlaylist by PlaylistFilter.selected.collectAsStateWithLifecycle()
    val hasOnDemand by OnDemandRepository.hasContent.collectAsStateWithLifecycle()
    val showOnDemand = hasOnDemand || playlists.any { it.enabled && it.importVod }
    val recordings by remember { LiveTvRecordingScheduler.start(); LiveTvRecorder.recordings }.collectAsStateWithLifecycle()
    val schedules by LiveTvRecordingScheduler.schedules.collectAsStateWithLifecycle()
    val seriesRules by LiveTvRecordingScheduler.series.collectAsStateWithLifecycle()
    val showRecordings = recordings.isNotEmpty() || schedules.isNotEmpty() || seriesRules.isNotEmpty()
    var manualRecording by remember { mutableStateOf<Pair<LiveChannel, Boolean>?>(null) }
    val sportsSettings by remember { SportsRepository.start(); SportsRepository.settings }.collectAsStateWithLifecycle()
    val sportsOn = sportsSettings.showInSidebar
    val state by remember {
        LiveTvRepository.ensureLoaded()
        LiveTvViewState.list
    }.collectAsStateWithLifecycle()
    val status by LiveTvRepository.status.collectAsStateWithLifecycle()
    val programs by LiveTvRepository.programs.collectAsStateWithLifecycle()
    val now by produceState(LiveTvClock.nowMs()) {
        while (true) {
            value = LiveTvClock.nowMs()
            delay(30_000)
        }
    }
    var query by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
    // Groups start collapsed; tapping a header opens it. Kept across the trip to the player.
    val expanded = remember { mutableStateOf(LiveTvListMemory.expanded) }
    var sheetChannel by remember { mutableStateOf<LiveChannel?>(null) }
    var unlockGroup by remember { mutableStateOf<ChannelGroup?>(null) }
    var overflow by remember { mutableStateOf(false) }
    var streamValidation by remember { mutableStateOf(false) }
    val validation by StreamValidationRepository.state.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current

    LaunchedEffect(scrollToTopRequests) {
        scrollToTopRequests.collect { listState.animateScrollToItem(0) }
    }
    ScreenActivityEffect { active ->
        if (active) {
            LiveTvRepository.refreshIfStale()
            while (true) {
                delay(5 * 60_000L)
                LiveTvRepository.refreshWindow()
            }
        }
    }

    // Playlist dropdown: only playlists that have groups here; hidden with fewer than two.
    val playlistChoices = remember(state.sections, playlists) {
        playlists.mapNotNull { p ->
            val n = state.sections.count { it.group.sourceId == p.id }
            if (n > 0) PlaylistChoice(p.id, p.name, if (n == 1) "1 group" else "$n groups") else null
        }
    }
    val playlistId = PlaylistFilter.effective(savedPlaylist, playlistChoices.map { it.id })
    val playlistNames = remember(playlists) { playlists.associate { it.id to it.name } }
    val sections = remember(state.sections, playlistId) {
        if (playlistId == null) state.sections
        else state.sections.filter { it.group.sourceId == null || it.group.sourceId == playlistId }
    }

    val searchResults by remember(state.visible, query, playlistId) {
        derivedStateOf { LiveTvViewState.search(state.visible.filter { playlistId == null || it.sourceId == playlistId }, query) }
    }
    val nowNext: Map<String, NowNext> = remember(programs, now) {
        LiveTvRepository.nowNext(programs.keys, now)
    }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    // Where the sticky header ends, so the open group's name can sit pinned just under it.
    var boxTopPx by remember { mutableStateOf(0f) }
    var headerBottomPx by remember { mutableStateOf(0f) }
    val showList = state.hasSources && !(searching && query.isNotBlank())
    // The open group whose channels are at the top of the list, once its own card has scrolled away.
    val pinnedGroupId by remember(sections, showList) {
        derivedStateOf {
            if (!showList) return@derivedStateOf null
            val visible = listState.layoutInfo.visibleItemsInfo
            val header = visible.firstOrNull { it.index == 0 } ?: return@derivedStateOf null
            val edge = header.offset + header.size
            val first = visible.firstOrNull { it.index > 0 && it.offset + it.size > edge } ?: return@derivedStateOf null
            val key = first.key as? String ?: return@derivedStateOf null
            if (key.startsWith("g:")) return@derivedStateOf null
            sections.filter { it.group.id in expanded.value && key.startsWith(it.group.id + "/") }
                .maxByOrNull { it.group.id.length }?.group?.id
        }
    }
    /** The list position of a group's card (the sticky header is item 0). Mirrors the items below. */
    fun groupCardIndex(groupId: String): Int? {
        var index = 1
        sections.forEach { s ->
            if (s.group.id == groupId) return index
            index += 1 + if (s.group.id in expanded.value) s.channels.size else 0
        }
        return null
    }

    // Build combined list structure: sections with addons inserted after "Recently watched"
    val addonsKey = "addons"
    val combinedSections = remember(sections) {
        sections.map { section ->
            CombinedSection(
                group = section.group,
                channels = section.channels,
                isAddonsSection = false,
            )
        }.let { list ->
            val allIndex = list.indexOfFirst { it.group.id == ChannelGroup.ALL }
            if (allIndex >= 0) {
                list.subList(0, allIndex + 1) +
                listOf(CombinedSection(group = ChannelGroup("", "", 0), channels = emptyList(), isAddonsSection = true)) +
                list.subList(allIndex + 1, list.size)
            } else {
                list + listOf(CombinedSection(group = ChannelGroup("", "", 0), channels = emptyList(), isAddonsSection = true))
            }
        }
    }

    Box(modifier = modifier.fillMaxSize().liveTvSideInsets().onGloballyPositioned { boxTopPx = it.positionInRoot().y }) {
        NuvioScreen(modifier = Modifier.fillMaxSize(), listState = listState) {
            stickyHeader {
                Column(modifier = Modifier.background(tokens.colors.background).onGloballyPositioned { headerBottomPx = it.positionInRoot().y + it.size.height }) {
                    // The title has the top row to itself (with the menu for managing playlists); every page
                    // button sits in one labelled row under it, so none are hidden.
                    val headerActions = buildList {
                        add(HeaderAction(Icons.Filled.Search, stringResource(Res.string.livetv_search_hint), tokens.colors.textPrimary, short = stringResource(Res.string.livetv_header_search), showClose = searching) { searching = !searching; if (!searching) query = "" })
                        add(HeaderAction(Icons.Filled.CalendarViewDay, stringResource(Res.string.livetv_guide), tokens.colors.textPrimary, short = stringResource(Res.string.livetv_header_guide)) { onOpenGuide() })
                        if (showOnDemand) add(HeaderAction(Icons.Filled.Movie, stringResource(Res.string.livetv_on_demand), tokens.colors.textPrimary, short = stringResource(Res.string.livetv_on_demand)) { onOpenOnDemand() })
                        if (sportsOn) add(HeaderAction(Icons.Filled.SportsSoccer, stringResource(Res.string.livetv_sports), tokens.colors.textPrimary, short = stringResource(Res.string.livetv_sports)) { onOpenSports() })
                        add(HeaderAction(Icons.Filled.GridView, "Multi-view", tokens.colors.textPrimary, short = "Multi-view") { onOpenMultiView() })
                        if (showRecordings) add(HeaderAction(Icons.Filled.FiberManualRecord, stringResource(Res.string.livetv_recordings), if (recordings.any { it.isRecording }) tokens.colors.danger else tokens.colors.textPrimary, short = stringResource(Res.string.livetv_recordings)) { onOpenRecordings() })
                        add(HeaderAction(Icons.Filled.Settings, stringResource(Res.string.livetv_settings), tokens.colors.textPrimary, short = stringResource(Res.string.livetv_header_settings)) { onOpenSettings() })
                    }
                    NuvioScreenHeader(
                        title = stringResource(Res.string.livetv_title),
                        actions = {
                            if (state.hasSources) {
                                IconButton(onClick = { overflow = true }) {
                                    Icon(Icons.Filled.MoreVert, contentDescription = null, tint = tokens.colors.textPrimary)
                                }
                                Menu(expanded = overflow, onDismissRequest = { overflow = false }) {
                                    MenuItem(text = stringResource(Res.string.livetv_manage_menu), onClick = { overflow = false; onOpenManage() })
                                    MenuItem(text = stringResource(Res.string.livetv_update_now), onClick = { overflow = false; LiveTvRepository.refreshAll(force = true) })
                                    MenuItem(
                                        text = if (validation.running) {
                                            "Stream validation (${validation.checked}/${validation.total})"
                                        } else {
                                            "Stream validation"
                                        },
                                        onClick = { overflow = false; streamValidation = true },
                                    )
                                }
                            }
                        },
                    )
                    Row(modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp).padding(bottom = NuvioTokens.Space.s4)) {
                        headerActions.forEach { a -> HeaderButton(a, Modifier.weight(1f)) }
                    }
                    AnimatedVisibility(visible = searching) {
                        NuvioInputField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = stringResource(Res.string.livetv_search_hint),
                            modifier = Modifier.padding(bottom = NuvioTokens.Space.s8),
                        )
                    }
                    if (status.loading && status.message != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = NuvioTokens.Space.s8),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8),
                        ) {
                            NuvioLoadingIndicator(size = NuvioTokens.Space.s16)
                            Text(status.message.orEmpty(), style = MaterialTheme.typography.bodySmall, color = tokens.colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    } else if (status.error != null) {
                        Text(
                            text = status.error.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = tokens.colors.danger,
                            modifier = Modifier.padding(bottom = NuvioTokens.Space.s8),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (validation.running) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = NuvioTokens.Space.s8),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8),
                        ) {
                            NuvioLoadingIndicator(size = NuvioTokens.Space.s16)
                            Text(
                                "Checking streams ${validation.checked}/${validation.total}…",
                                style = MaterialTheme.typography.bodySmall,
                                color = tokens.colors.textMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            if (!state.hasSources) {
                item { EmptyLiveTv(onOpenSettings) }
            } else if (searching && query.isNotBlank()) {
                if (searchResults.isEmpty()) {
                    item {
                        Text(stringResource(Res.string.livetv_no_results), style = MaterialTheme.typography.bodyMedium, color = tokens.colors.textMuted, modifier = Modifier.padding(NuvioTokens.Space.s16))
                    }
                }
                items(searchResults.size, key = { "s:" + searchResults[it].key }) { i ->
                    val c = searchResults[i]
                    ChannelRow(c, nowNext[c.key], now, c.key in state.favoriteKeys, onClick = { onPlayChannel(c, nowNext[c.key]?.now?.title) }, onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress); sheetChannel = c
                    })
                }
            } else {
                if (sections.isEmpty() && !status.loading) {
                    item {
                        Column(modifier = Modifier.fillMaxWidth().padding(NuvioTokens.Space.s16), verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s12)) {
                            Text(stringResource(Res.string.livetv_no_results), style = MaterialTheme.typography.bodyMedium, color = tokens.colors.textMuted)
                            NuvioPrimaryButton(text = stringResource(Res.string.livetv_update_now)) { LiveTvRepository.refreshAll(force = true) }
                        }
                    }
                }
                // Inline Quick Channels section
                item {
                    QuickChannelsInlineSection(
                        onPlayChannel = { ch -> onPlayChannel(ch, null) },
                    )
                }
                // The playlist picker sits directly under Quick Channels.
                item(key = "playlist-dropdown") {
                    PlaylistDropdown(
                        choices = playlistChoices,
                        selectedId = playlistId,
                        allDetail = (state.sections.count { it.group.sourceId != null }).let { if (it == 1) "1 group" else "$it groups" },
                        onSelect = { id -> PlaylistFilter.select(id) },
                        modifier = Modifier.padding(horizontal = NuvioTokens.Space.s12, vertical = NuvioTokens.Space.s8),
                    )
                }
                combinedSections.forEachIndexed { index, combinedSection ->
                    if (combinedSection.isAddonsSection) {
                        item(key = addonsKey) {
                            AddonsCatalogInlineSection(
                                onOpenSettings = { onOpenAddonSettings() },
                                onPlayItem = onPlayAddonItem,
                                onAddToMultiView = onAddonItemToMultiView,
                            )
                        }
                    } else {
                        val section = combinedSection
                        item(key = "g:${section.group.id}") {
                            GroupCard(section.group, section.channels, section.group.id !in expanded.value, playlistTag = if (playlistId == null && playlistChoices.size > 1) playlistNames[section.group.sourceId] else null) {
                                if (section.group.locked) unlockGroup = section.group
                                else {
                                    expanded.value = if (section.group.id !in expanded.value) expanded.value + section.group.id else expanded.value - section.group.id
                                    LiveTvListMemory.expanded = expanded.value
                                }
                            }
                        }
                        val isCollapsed = section.group.id !in expanded.value
                        if (!isCollapsed) {
                            items(section.channels.size, key = { "${section.group.id}/" + section.channels[it].key }) { i ->
                                val c = section.channels[i]
                                ChannelRow(c, nowNext[c.key], now, c.key in state.favoriteKeys, onClick = {
                                    LiveTvListMemory.expanded = expanded.value + section.group.id
                                    LiveTvListMemory.saveScroll(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
                                    onPlayChannel(c, nowNext[c.key]?.now?.title)
                                }, onLongClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress); sheetChannel = c
                                }, modifier = Modifier.padding(start = NuvioTokens.Space.s12))
                            }
                        }
                    }
                }
            }
        }
        val pinned = pinnedGroupId?.let { gid -> sections.firstOrNull { it.group.id == gid } }
        if (pinned != null && headerBottomPx > boxTopPx) {
            Box(
                modifier = Modifier
                    .offset(y = with(density) { (headerBottomPx - boxTopPx).toDp() })
                    .fillMaxWidth()
                    .background(tokens.colors.background)
                    .padding(horizontal = tokens.spacing.screenHorizontal)
                    .padding(bottom = NuvioTokens.Space.s6),
            ) {
                PinnedGroupBar(pinned.group) {
                    val gid = pinned.group.id
                    val index = groupCardIndex(gid)
                    expanded.value = expanded.value - gid
                    LiveTvListMemory.expanded = expanded.value
                    // Back to the group's card, just under the header (not hidden behind it).
                    if (index != null) scope.launch {
                        listState.scrollToItem(index)
                        val header = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == 0 }
                        if (header != null) listState.scrollBy(-(header.offset + header.size).toFloat())
                    }
                }
            }
        }
    }

    sheetChannel?.let { channel ->
        ChannelActionsSheet(
            channel = channel,
            isFavorite = channel.key in state.favoriteKeys,
            customGroups = state.customGroups,
            onDismiss = { sheetChannel = null },
            onPlay = { onPlayChannel(channel, nowNext[channel.key]?.now?.title) },
            onAddToMultiView = { onAddChannelToMultiView(channel) },
            onWatchFromStart = nowNext[channel.key]?.now
                ?.takeIf { LiveTvCatchup.canWatchFromStart(channel, it, now) }
                ?.let { program -> { onPlayCatchup(channel, program) } },
            extraRows = { close ->
                val active = recordings.firstOrNull { it.channelKey == channel.key && it.isRecording }
                if (active != null) {
                    NuvioBottomSheetActionRow(title = stringResource(Res.string.livetv_rec_stop), icon = Icons.Filled.Stop, onClick = { close { LiveTvRecorder.stop(active.id) } })
                } else {
                    NuvioBottomSheetActionRow(title = stringResource(Res.string.livetv_rec_record_now), icon = Icons.Filled.FiberManualRecord, onClick = { close { manualRecording = channel to false } })
                }
                NuvioBottomSheetActionRow(title = stringResource(Res.string.livetv_rec_schedule), icon = Icons.Filled.Schedule, onClick = { close { manualRecording = channel to true } })
            },
        )
    }

    manualRecording?.let { (channel, scheduleOnly) ->
        ManualRecordingDialog(channel = channel, scheduleOnly = scheduleOnly, onDismiss = { manualRecording = null })
    }
    unlockGroup?.let { g -> UnlockGroupDialog(g.id, g.title, onDismiss = { unlockGroup = null }) }

    if (streamValidation) {
        StreamValidationSheet(onDismiss = { streamValidation = false })
    }
}

/** Full Quick Channels section shown inline. */
@Composable
private fun QuickChannelsInlineSection(
    onPlayChannel: (com.nuvio.app.features.livetv.model.LiveChannel) -> Unit,
) {
    val tokens = MaterialTheme.nuvio.colors
    val liveChannels by com.nuvio.app.features.livetv.data.LiveTvRepository.displayChannels.collectAsStateWithLifecycle(initialValue = emptyList())
    val categories = QuickChannelList.getChannelCategories()
    var selectedCategory by remember { mutableStateOf(categories.firstOrNull() ?: "all") }
    var quickDataVersion by remember { mutableStateOf(0) }
    val filteredQc = remember(selectedCategory, quickDataVersion) {
        QuickChannelList.getChannelsForCategory(selectedCategory)
    }
    var selectedQc by remember { mutableStateOf<QuickChannel?>(null) }
    val validation = rememberQuickChannelMatchState(selectedQc, liveChannels)

    // Custom region/channel management state
    var showAddRegionDialog by remember { mutableStateOf(false) }
    var showAddChannelDialog by remember { mutableStateOf(false) }
    var showEditRegionDialog by remember { mutableStateOf(false) }
    var showEditChannelDialog by remember { mutableStateOf(false) }
    var editingRegion by remember { mutableStateOf<QuickChannelList.QuickCategory?>(null) }
    var editingChannel by remember { mutableStateOf<QuickChannel?>(null) }
    var newRegionId by remember { mutableStateOf("") }
    var newRegionTitle by remember { mutableStateOf("") }
    var newRegionDescription by remember { mutableStateOf("") }
    var newRegionTags by remember { mutableStateOf("") }
    var newChannelName by remember { mutableStateOf("") }
    var newChannelAliases by remember { mutableStateOf("") }
    var newChannelRegions by remember { mutableStateOf("") }
    var newChannelTags by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = NuvioTokens.Space.s12),
    ) {
        // Section header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = NuvioTokens.Space.s12, vertical = NuvioTokens.Space.s8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Tv,
                contentDescription = null,
                tint = tokens.accent,
                modifier = Modifier.size(NuvioTokens.Space.s20),
            )
            Spacer(Modifier.width(NuvioTokens.Space.s8))
            Text(
                stringResource(Res.string.livetv_quick_channels_section),
                style = MaterialTheme.typography.titleSmall,
                color = tokens.textPrimary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            // Add custom region button
            TextButton(onClick = { showAddRegionDialog = true }) {
                Text("+REG", style = MaterialTheme.typography.labelLarge, color = tokens.accent, fontWeight = FontWeight.SemiBold)
            }
            // Add custom channel button
            TextButton(onClick = { showAddChannelDialog = true }) {
                Text("+CH", style = MaterialTheme.typography.labelLarge, color = tokens.accent, fontWeight = FontWeight.SemiBold)
            }
        }

        // Category row
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s6),
            contentPadding = PaddingValues(horizontal = NuvioTokens.Space.s12, vertical = NuvioTokens.Space.s4),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(categories) { catId ->
                val cat = QuickChannelList.allCategories().find { it.id == catId }
                val title = cat?.title ?: catId
                val isCustom = cat?.let { QuickChannelList.isCustomCategory(it.id) } ?: false
                val isActive = catId == selectedCategory
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (isActive) tokens.accent else tokens.surfaceCard)
                        .clickable { selectedCategory = catId }
                        .padding(horizontal = NuvioTokens.Space.s10, vertical = NuvioTokens.Space.s4),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            title,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isActive) Color.White else tokens.textPrimary,
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                        )
                        if (isCustom) {
                            IconButton(onClick = { QuickChannelList.removeCustomCategory(catId); quickDataVersion += 1; if (selectedCategory == catId) selectedCategory = "all" }, modifier = Modifier.size(18.dp)) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = if (isActive) Color.White else tokens.textMuted, modifier = Modifier.size(14.dp))
                            }
                            IconButton(onClick = { editingRegion = QuickChannelList.allCategories().find { it.id == catId }; showEditRegionDialog = true }, modifier = Modifier.size(18.dp)) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit", tint = if (isActive) Color.White else tokens.textMuted, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }
        }

        // Quick channel chip row
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s6),
            contentPadding = PaddingValues(horizontal = NuvioTokens.Space.s12, vertical = NuvioTokens.Space.s6),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(filteredQc, key = { it.displayName }) { qc ->
                val isCustom = QuickChannelList.isCustomChannel(qc.displayName)
                Box(
                    modifier = Modifier
                        .width(150.dp)
                        .height(92.dp)
                        .clip(RoundedCornerShape(NuvioTokens.Radius.md))
                        .background(if (selectedQc?.displayName == qc.displayName) tokens.accent.copy(alpha = 0.2f) else tokens.surfaceCard)
                        .clickable {
                            selectedQc = qc
                        }
                        .padding(horizontal = NuvioTokens.Space.s10, vertical = NuvioTokens.Space.s10),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            qc.displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (selectedQc?.displayName == qc.displayName) tokens.accent else tokens.textPrimary,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (isCustom) {
                                IconButton(onClick = { editingChannel = qc; showEditChannelDialog = true }, modifier = Modifier.size(22.dp)) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit", tint = tokens.textMuted, modifier = Modifier.size(16.dp))
                                }
                                IconButton(onClick = { QuickChannelList.removeCustomChannel(qc.displayName); quickDataVersion += 1; if (selectedQc?.displayName == qc.displayName) selectedQc = null }, modifier = Modifier.size(22.dp)) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = tokens.textMuted, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        // Match list - scrollable popup
        if (selectedQc != null && validation.matches.isNotEmpty()) {
            Spacer(Modifier.height(NuvioTokens.Space.s6))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = NuvioTokens.Space.s12),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    selectedQc!!.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    color = tokens.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (validation.isChecking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        color = tokens.accent,
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(6.dp))
                }
                // Show only working streams count
                Text(
                    "${validation.workingCount} working",
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.textMuted,
                )
            }
            Spacer(Modifier.height(NuvioTokens.Space.s8))
            // Working streams first, then streams still being checked; dead ones are hidden.
            val sortedMatches = validation.visibleMatches

            if (sortedMatches.isEmpty() && !validation.isChecking) {
                Spacer(Modifier.height(NuvioTokens.Space.s12))
                Text(
                    "No working streams found",
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.textMuted,
                    modifier = Modifier.padding(horizontal = NuvioTokens.Space.s12),
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s6),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp)
                        .padding(horizontal = NuvioTokens.Space.s12),
                ) {
                    items(sortedMatches, key = { it.key }) { ch ->
                        val isAlive = validation.statuses[ch.url]
                        QuickChannelInlineRow(channel = ch, isAlive = isAlive, onClick = { onPlayChannel(ch) })
                    }
                }
            }
        } else if (selectedQc != null && liveChannels.isNotEmpty()) {
            Spacer(Modifier.height(NuvioTokens.Space.s12))
            Text(
                stringResource(Res.string.livetv_no_matching_channels),
                style = MaterialTheme.typography.bodySmall,
                color = tokens.textMuted,
                modifier = Modifier.padding(horizontal = NuvioTokens.Space.s12),
            )
        }
    }

    // Add Custom Region Dialog
    if (showAddRegionDialog) {
        AddRegionDialog(
            onDismiss = { showAddRegionDialog = false },
            onAdd = { id, title, description, tags ->
                val category = QuickChannelList.QuickCategory(
                    id = id.trim().lowercase().replace(" ", "-"),
                    title = title.trim(),
                    description = description.trim(),
                    tags = tags.split(",").map { it.trim() }.filter { it.isNotBlank() }
                )
                QuickChannelList.addCustomCategory(category)
                quickDataVersion += 1
                showAddRegionDialog = false
            },
        )
    }

    // Add Custom Channel Dialog
    if (showAddChannelDialog) {
        AddChannelDialog(
            onDismiss = { showAddChannelDialog = false },
            onAdd = { name, aliases, regions, tags ->
                val channel = QuickChannel(
                    displayName = name.trim(),
                    aliases = aliases.split(",").map { it.trim() }.filter { it.isNotBlank() },
                    regions = regions.split(",").map { it.trim().uppercase() }.filter { it.isNotBlank() },
                    tags = tags.split(",").map { it.trim().lowercase() }.filter { it.isNotBlank() }
                )
                QuickChannelList.addCustomChannel(channel)
                quickDataVersion += 1
                showAddChannelDialog = false
            },
        )
    }

    // Edit Custom Region Dialog
    if (showEditRegionDialog && editingRegion != null) {
        val region = editingRegion!!
        EditRegionDialog(
            region = region,
            onDismiss = { showEditRegionDialog = false; editingRegion = null },
            onEdit = { id, title, description, tags ->
                val category = QuickChannelList.QuickCategory(
                    id = id.trim().lowercase().replace(" ", "-"),
                    title = title.trim(),
                    description = description.trim(),
                    tags = tags.split(",").map { it.trim() }.filter { it.isNotBlank() }
                )
                QuickChannelList.updateCustomCategory(region.id, category)
                quickDataVersion += 1
                showEditRegionDialog = false
                editingRegion = null
            },
        )
    }

    // Edit Custom Channel Dialog
    if (showEditChannelDialog && editingChannel != null) {
        val channel = editingChannel!!
        EditChannelDialog(
            channel = channel,
            onDismiss = { showEditChannelDialog = false; editingChannel = null },
            onEdit = { name, aliases, regions, tags ->
                val updated = QuickChannel(
                    displayName = name.trim(),
                    aliases = aliases.split(",").map { it.trim() }.filter { it.isNotBlank() },
                    regions = regions.split(",").map { it.trim().uppercase() }.filter { it.isNotBlank() },
                    tags = tags.split(",").map { it.trim().lowercase() }.filter { it.isNotBlank() }
                )
                QuickChannelList.updateCustomChannel(channel.displayName, updated)
                quickDataVersion += 1
                showEditChannelDialog = false
                editingChannel = null
            },
        )
    }
}

@Composable
private fun QuickChannelInlineRow(
    channel: com.nuvio.app.features.livetv.model.LiveChannel,
    isAlive: Boolean?,
    onClick: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(NuvioTokens.Radius.md))
            .background(tokens.surfaceCard)
            .clickable(onClick = onClick)
            .padding(horizontal = NuvioTokens.Space.s10, vertical = NuvioTokens.Space.s8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val image = channel.thumbnail?.takeIf { it.isNotBlank() } ?: channel.logo?.takeIf { it.isNotBlank() }
        if (image != null) {
            AsyncImage(
                model = image,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(NuvioTokens.Radius.xs))
                    .background(tokens.background),
            )
            Spacer(Modifier.width(NuvioTokens.Space.s8))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                channel.name,
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.textPrimary,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (channel.group.isNotEmpty()) {
                Text(
                    channel.group,
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.textMuted,
                    maxLines = 1,
                )
            }
        }
        val aliveText = when (isAlive) {
            true -> "✓ Live"
            null -> "…"
            false -> "▶"
        }
        val aliveColor = when (isAlive) {
            true -> Color(0xFF4CAF50)
            null -> tokens.textMuted
            false -> tokens.accent
        }
        Text(
            aliveText,
            style = MaterialTheme.typography.labelSmall,
            color = aliveColor,
            fontWeight = FontWeight.Bold,
        )
    }
}

// ─── Addons inline section: each addon is a collapsible card, catalog items appear below ───

/** Inline add-on section rendered as nested folders, matching the playlist group/channel list. */
@Composable
private fun AddonsCatalogInlineSection(
    onOpenSettings: () -> Unit,
    onPlayItem: (MetaPreview) -> Unit,
    onAddToMultiView: (MetaPreview) -> Unit,
) {
    val tokens = MaterialTheme.nuvio.colors
    // Lazy-load addons only when this section enters the composition (user scrolls here).
    // This prevents crashes caused by premature addon initialization during app startup.
    LaunchedEffect(Unit) {
        runCatching { AddonRepository.initialize() }
    }

    val uiState by AddonRepository.uiState.collectAsStateWithLifecycle()
    val addons = uiState.addons.liveTvAddons()

    Column(verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s4)) {
        if (addons.isEmpty()) {
            AddonEmptyStateCard(tokens = tokens)
        } else {
            // Keep expanded state for each addon keyed by manifest URL
            val expandedAddons = remember { mutableStateMapOf<String, Boolean>() }
            // Keep expanded state for each catalog keyed by "manifestUrl:catalogId"
            val expandedCatalogs = remember { mutableStateMapOf<String, Boolean>() }

            addons.forEach { addon ->
                val manifest = addon.manifest
                if (manifest != null && manifest.catalogs.isNotEmpty()) {
                    val addonKey = addon.manifestUrl
                    val isExpanded = expandedAddons.getOrPut(addonKey) { false }

                    AddonCard(
                        addon = addon,
                        expanded = isExpanded,
                        onToggle = { expandedAddons[addonKey] = !isExpanded },
                    )

                    if (isExpanded) {
                        manifest.catalogs.forEach { catalog ->
                            val catalogKey = "$addonKey:${catalog.id}"
                            val isCatExpanded = expandedCatalogs.getOrPut(catalogKey) { false }

                            ExpandableCatalogSection(
                                manifestUrl = addon.manifestUrl,
                                catalog = catalog,
                                addonName = manifest.name,
                                expanded = isCatExpanded,
                                onToggle = { expandedCatalogs[catalogKey] = !isCatExpanded },
                                onPlayItem = onPlayItem,
                                onAddToMultiView = onAddToMultiView,
                            )
                        }
                    }
                } else {
                    // Addon with no manifest or no catalogs — show as disabled card
                    AddonEmptyCatalogCard(addon = addon, tokens = tokens)
                }
            }
        }
    }
}

// ─── Addon card (like GroupCard) ───────────────────────────────────────────────────────────

@Composable
private fun AddonCard(
    addon: com.nuvio.app.features.addons.ManagedAddon,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio.colors
    val arrow by animateFloatAsState(if (expanded) 180f else 0f)
    val manifest = addon.manifest
    val catalogCount = manifest?.catalogs?.size ?: 0

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(NuvioTokens.Radius.xl))
            .background(tokens.surface)
            .clickable(enabled = catalogCount > 0) { onToggle() }
            .padding(NuvioTokens.Space.s12),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s12),
    ) {
        // Icon box
        if (manifest != null && !manifest.logoUrl.isNullOrBlank()) {
            AsyncImage(
                model = manifest.logoUrl,
                contentDescription = null,
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(NuvioTokens.Radius.lg)),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(NuvioTokens.Radius.lg))
                    .background(tokens.accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Extension,
                    contentDescription = null,
                    tint = tokens.accent,
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        // Title + subtitle
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s4)) {
            Text(
                text = addon.displayTitle,
                style = MaterialTheme.typography.titleMedium,
                color = tokens.textPrimary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            when {
                manifest == null && addon.isRefreshing -> {
                    Text("Loading…", style = MaterialTheme.typography.labelMedium, color = tokens.textMuted)
                }
                manifest == null && addon.errorMessage != null -> {
                    Text(addon.errorMessage!!, style = MaterialTheme.typography.labelMedium, color = tokens.danger)
                }
                catalogCount > 0 -> {
                    Text(
                        "$catalogCount catalog${if (catalogCount > 1) "s" else ""}",
                        style = MaterialTheme.typography.labelMedium,
                        color = tokens.textMuted,
                    )
                }
                else -> {
                    Text("No catalogs", style = MaterialTheme.typography.labelMedium, color = tokens.textMuted)
                }
            }
        }

        // Expand arrow
        if (catalogCount > 0) {
            Icon(
                Icons.Filled.ExpandMore,
                contentDescription = null,
                tint = if (expanded) tokens.accent else tokens.textMuted,
                modifier = Modifier.rotate(arrow),
            )
        }
    }
}

// ─── Disabled addon card (no manifest/catalogs) ────────────────────────────────────────────

@Composable
private fun AddonEmptyCatalogCard(
    addon: com.nuvio.app.features.addons.ManagedAddon,
    tokens: com.nuvio.app.core.ui.NuvioColorTokens,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(NuvioTokens.Radius.xl))
            .background(tokens.surface)
            .padding(NuvioTokens.Space.s12),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s12),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(NuvioTokens.Radius.lg))
                .background(tokens.surfaceCard),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Extension,
                contentDescription = null,
                tint = tokens.textMuted,
                modifier = Modifier.size(22.dp),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s4)) {
            Text(
                addon.displayTitle,
                style = MaterialTheme.typography.titleMedium,
                color = tokens.textMuted,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (addon.manifest == null && addon.isRefreshing) "Loading…"
                else "No catalogs available",
                style = MaterialTheme.typography.labelMedium,
                color = tokens.textMuted,
            )
        }
    }
}

// ─── Catalog folder row (indented sub-folder) ─────────────────────────────────────────────

@Composable
private fun CatalogCard(
    catalog: com.nuvio.app.features.addons.AddonCatalog,
    addonName: String,
    expanded: Boolean,
    isLoading: Boolean,
    errorMessage: String?,
    itemCount: Int,
    onToggle: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio.colors
    val arrow by animateFloatAsState(if (expanded) 180f else 0f)
    val subtitle = buildString {
        append(addonName)
        append("  ·  ")
        append(catalog.type)
        if (!isLoading && errorMessage == null) {
            append("  ·  ")
            append(if (itemCount == 1) "1 item" else "$itemCount items")
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = NuvioTokens.Space.s24)
            .clip(RoundedCornerShape(NuvioTokens.Radius.xl))
            .background(tokens.surface)
            .clickable { onToggle() }
            .padding(NuvioTokens.Space.s12),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s12),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(NuvioTokens.Radius.lg))
                .background(tokens.accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.MenuBook,
                contentDescription = null,
                tint = tokens.accent,
                modifier = Modifier.size(22.dp),
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s4)) {
            Text(
                text = catalog.name,
                style = MaterialTheme.typography.titleMedium,
                color = tokens.textPrimary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = tokens.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            Icons.Filled.ExpandMore,
            contentDescription = null,
            tint = if (expanded) tokens.accent else tokens.textMuted,
            modifier = Modifier.rotate(arrow),
        )
    }
}

// ─── Expandable catalog section with isolated state ──────────────────────────────────────────
@Composable
private fun ExpandableCatalogSection(
    manifestUrl: String,
    catalog: com.nuvio.app.features.addons.AddonCatalog,
    addonName: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    onPlayItem: (MetaPreview) -> Unit,
    onAddToMultiView: (MetaPreview) -> Unit,
) {
    val catalogState = remember { mutableStateOf(CatalogUiState()) }

    LaunchedEffect(expanded, manifestUrl, catalog.id, catalog.type) {
        if (!expanded) {
            catalogState.value = CatalogUiState()
            return@LaunchedEffect
        }
        catalogState.value = CatalogUiState(isLoading = true)
        try {
            val page = fetchCatalogPage(
                manifestUrl = manifestUrl,
                type = catalog.type,
                catalogId = catalog.id,
            )
            catalogState.value = CatalogUiState(
                items = page.items,
                isLoading = false,
                nextSkip = page.nextSkip,
            )
        } catch (e: Exception) {
            catalogState.value = CatalogUiState(
                items = emptyList(),
                isLoading = false,
                errorMessage = e.message,
            )
        }
    }

    val isLoading = catalogState.value.isLoading && catalogState.value.items.isEmpty()

    CatalogCard(
        catalog = catalog,
        addonName = addonName,
        expanded = expanded,
        isLoading = isLoading,
        errorMessage = catalogState.value.errorMessage,
        itemCount = catalogState.value.items.size,
        onToggle = onToggle,
    )

    if (expanded) {
        CatalogItemsList(
            uiState = catalogState.value,
            onPlayItem = onPlayItem,
            onAddToMultiView = onAddToMultiView,
        )
    }
}

// ─── Catalog content rows (indented, like channel rows) ───────────────────────────────────

@Composable
private fun CatalogItemsList(
    uiState: CatalogUiState,
    onPlayItem: (MetaPreview) -> Unit,
    onAddToMultiView: (MetaPreview) -> Unit,
) {
    val tokens = MaterialTheme.nuvio.colors

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = NuvioTokens.Space.s48, end = NuvioTokens.Space.s12, top = NuvioTokens.Space.s4, bottom = NuvioTokens.Space.s8),
        verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s4),
    ) {
        when {
            uiState.isLoading && uiState.items.isEmpty() -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8),
                ) {
                    NuvioLoadingIndicator(size = NuvioTokens.Space.s16)
                    Text("Loading…", style = MaterialTheme.typography.bodySmall, color = tokens.textMuted)
                }
            }

            uiState.errorMessage != null -> {
                Text(
                    text = uiState.errorMessage.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.danger,
                )
            }

            uiState.items.isEmpty() -> {
                Text(
                    text = "No items",
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.textMuted,
                )
            }

            else -> {
                // Plain rows: this section lives inside the screen's LazyColumn, so content is
                // laid out as a normal list instead of a nested lazy grid.
                uiState.items.forEach { item ->
                    AddonItemRow(
                        item = item,
                        onClick = { onPlayItem(item) },
                        onAddToMultiView = { onAddToMultiView(item) },
                    )
                }
            }
        }
    }
}

// ─── Add-on content row (plays in the player) ─────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AddonItemRow(
    item: MetaPreview,
    onClick: () -> Unit,
    onAddToMultiView: (() -> Unit)? = null,
) {
    val tokens = MaterialTheme.nuvio.colors
    val artwork = item.landscapePoster ?: item.poster ?: item.banner
    val subtitle = buildList {
        item.releaseInfo?.takeIf { it.isNotBlank() }?.let { add(it) }
        item.type.takeIf { it.isNotBlank() }?.let { add(it.replaceFirstChar { c -> c.uppercaseChar() }) }
    }.joinToString("  ·  ").ifBlank { item.description.orEmpty() }
    var menuOpen by remember { mutableStateOf(false) }

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(NuvioTokens.Radius.xl))
                .background(tokens.surface)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { menuOpen = true },
                )
                .padding(horizontal = NuvioTokens.Space.s10, vertical = NuvioTokens.Space.s10),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s12),
        ) {
        Box(
            modifier = Modifier
                .width(64.dp)
                .height(44.dp)
                .clip(RoundedCornerShape(NuvioTokens.Radius.md))
                .background(tokens.surfaceCard),
            contentAlignment = Alignment.Center,
        ) {
            if (!artwork.isNullOrBlank()) {
                AsyncImage(
                    model = artwork,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = tokens.textMuted,
                    modifier = Modifier.size(NuvioTokens.Icon.md),
                )
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s2)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyLarge,
                color = tokens.textPrimary,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        }
        Menu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            MenuItem(
                text = "Play",
                onClick = {
                    menuOpen = false
                    onClick()
                },
            )
            if (onAddToMultiView != null) {
                MenuItem(
                    text = "Multi-view",
                    onClick = {
                        menuOpen = false
                        onAddToMultiView()
                    },
                )
            }
        }
    }
}

// ─── Empty state card ──────────────────────────────────────────────────────────────────────

@Composable
private fun AddonEmptyStateCard(tokens: com.nuvio.app.core.ui.NuvioColorTokens) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(NuvioTokens.Radius.xl))
            .background(tokens.surface)
            .padding(NuvioTokens.Space.s12),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s12),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(NuvioTokens.Radius.lg))
                .background(tokens.surfaceCard),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Extension,
                contentDescription = null,
                tint = tokens.textMuted,
                modifier = Modifier.size(22.dp),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s4)) {
            Text(
                stringResource(Res.string.livetv_addons_section),
                style = MaterialTheme.typography.titleMedium,
                color = tokens.textPrimary,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                stringResource(Res.string.livetv_no_addons_installed),
                style = MaterialTheme.typography.labelMedium,
                color = tokens.textMuted,
            )
        }
    }
}

@Composable
private fun EmptyLiveTv(onOpenSettings: () -> Unit) {
    val tokens = MaterialTheme.nuvio
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = NuvioTokens.Space.s48, horizontal = NuvioTokens.Space.s16),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s12),
    ) {
        Icon(Icons.Filled.LiveTv, contentDescription = null, tint = tokens.colors.accent, modifier = Modifier.size(NuvioTokens.Space.s56))
        Text(stringResource(Res.string.livetv_empty_title), style = MaterialTheme.typography.titleLarge, color = tokens.colors.textPrimary)
        Text(stringResource(Res.string.livetv_empty_body), style = MaterialTheme.typography.bodyMedium, color = tokens.colors.textMuted)
        Spacer(Modifier.height(NuvioTokens.Space.s8))
        NuvioPrimaryButton(text = stringResource(Res.string.livetv_add_playlist), onClick = onOpenSettings)
    }
}

@Composable
private fun GroupCard(
    group: ChannelGroup,
    channels: List<LiveChannel>,
    collapsed: Boolean,
    playlistTag: String? = null,
    onToggle: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val settings by LiveTvPreferences.settings.collectAsStateWithLifecycle()
    val open = !collapsed && !group.locked
    val arrow by animateFloatAsState(if (open) 180f else 0f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(NuvioTokens.Radius.xl))
            .background(tokens.colors.surface)
            .clickable(onClick = onToggle)
            .padding(NuvioTokens.Space.s12),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s12),
    ) {
        GroupIconBox(group, size = 40.dp)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s4)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8)) {
                Text(
                    text = group.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = tokens.colors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f, fill = false),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (playlistTag != null) {
                    Text(
                        text = playlistTag,
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.colors.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .widthIn(max = 110.dp)
                            .clip(RoundedCornerShape(NuvioTokens.Radius.xs))
                            .background(tokens.colors.surfaceCard)
                            .padding(horizontal = NuvioTokens.Space.s6, vertical = NuvioTokens.Space.s2),
                    )
                }
            }
            if (!group.locked) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s4)) {
                    // A peek at the group: its first few channel logos.
                    if (settings.phoneChannelLogos) {
                        channels.asSequence().filter { !it.logo.isNullOrBlank() }.take(4).forEach {
                            ChannelLogo(it, width = 30.dp, height = 20.dp, inset = NuvioTokens.Space.s2, radius = NuvioTokens.Radius.xs)
                        }
                    }
                    Text(
                        text = if (group.count == 1) stringResource(Res.string.livetv_group_one_channel) else stringResource(Res.string.livetv_group_channels, group.count),
                        style = MaterialTheme.typography.labelMedium,
                        color = tokens.colors.textMuted,
                        maxLines = 1,
                        modifier = Modifier.padding(start = NuvioTokens.Space.s2),
                    )
                }
            }
        }
        if (!group.locked) {
            Icon(
                imageVector = Icons.Filled.ExpandMore,
                contentDescription = null,
                tint = if (open) tokens.colors.accent else tokens.colors.textMuted,
                modifier = Modifier.rotate(arrow),
            )
        }
    }
}

/** The open group's name, pinned under the header while its channels scroll; tap closes the group. */
@Composable
private fun PinnedGroupBar(group: ChannelGroup, onClose: () -> Unit) {
    val tokens = MaterialTheme.nuvio
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(NuvioTokens.Radius.lg))
            .background(tokens.colors.surface)
            .clickable(onClick = onClose)
            .padding(horizontal = NuvioTokens.Space.s12, vertical = NuvioTokens.Space.s8),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s10),
    ) {
        GroupIconBox(group, size = 28.dp)
        Text(
            text = group.title,
            style = MaterialTheme.typography.titleSmall,
            color = tokens.colors.textPrimary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(Icons.Filled.ExpandLess, contentDescription = null, tint = tokens.colors.accent)
    }
}

@Composable
internal fun GroupIconBox(group: ChannelGroup, size: androidx.compose.ui.unit.Dp) {
    val tokens = MaterialTheme.nuvio
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(NuvioTokens.Radius.lg))
            .background(tokens.colors.accent.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = groupIcon(group),
            contentDescription = null,
            tint = if (group.locked) tokens.colors.textMuted else tokens.colors.accent,
            modifier = Modifier.size(size * 0.55f),
        )
    }
}

/** An icon picked from the group's name (news, sports, kids…); a plain TV for anything else. */
internal fun groupIcon(group: ChannelGroup): ImageVector {
    if (group.locked) return Icons.Filled.Lock
    when (group.id) {
        ChannelGroup.FAVORITES -> return Icons.Filled.Star
        ChannelGroup.RECENT -> return Icons.Filled.History
        ChannelGroup.ALL -> return Icons.Filled.LiveTv
    }
    val t = group.title.lowercase()
    fun has(vararg words: String) = words.any { it in t }
    return when {
        has("news") -> Icons.Filled.Newspaper
        has("sport", "football", "soccer", "nfl", "nba", "nhl", "mlb", "espn", "golf", "tennis", "racing", "fight", "ufc", "boxing", "cricket", "rugby") -> Icons.Filled.SportsSoccer
        has("kid", "child", "cartoon", "family", "junior") -> Icons.Filled.ChildCare
        has("movie", "cinema", "film", "vod") -> Icons.Filled.Movie
        has("music", "radio", "mtv") -> Icons.Filled.MusicNote
        has("doc", "nature", "science", "history", "discovery", "learning", "travel") -> Icons.Filled.Explore
        has("local", "regional", "locals") -> Icons.Filled.Home
        has("entertain", "comedy", "drama", "series", "shows", "general", "variety", "lifestyle", "reality") -> Icons.Filled.TheaterComedy
        else -> Icons.Filled.LiveTv
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ChannelRow(
    channel: LiveChannel,
    nowNext: NowNext?,
    nowMs: Long,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = MaterialTheme.nuvio
    val settings by LiveTvPreferences.settings.collectAsStateWithLifecycle()
    val showLogo = settings.phoneChannelLogos
    val showName = settings.phoneChannelNames
    val showNumber = settings.phoneChannelNumbers
    val logoOnly = showLogo && !showName && !showNumber
    val current = nowNext?.now
    val liveGames by SportsLive.byChannel.collectAsStateWithLifecycle()
    val sportsSettings by SportsRepository.settings.collectAsStateWithLifecycle()
    val game = liveGames[channel.key]?.takeIf { it.state == GameState.LIVE && !sportsSettings.hideScores && sportsSettings.scoreOnInfoBar }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(NuvioTokens.Radius.xl))
            .background(tokens.colors.surface)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = NuvioTokens.Space.s10, vertical = NuvioTokens.Space.s10),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s12),
    ) {
        // Favorites get a small star on the logo's corner (or on the number box when logos are off).
        val starOnTile = showLogo || (showNumber && !showName)
        if (starOnTile) {
            Box {
                if (showLogo) {
                    ChannelLogo(
                        channel,
                        width = if (logoOnly) 104.dp else 64.dp,
                        height = if (logoOnly) 64.dp else 44.dp,
                        inset = if (logoOnly) NuvioTokens.Space.s3 else NuvioTokens.Space.s4,
                    )
                } else {
                    // Numbers without names or logos: the number takes the logo's place.
                    Box(
                        modifier = Modifier.width(64.dp).height(44.dp).clip(RoundedCornerShape(NuvioTokens.Radius.md)).background(tokens.colors.surfaceCard),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(channel.number.toString(), style = MaterialTheme.typography.titleMedium, color = tokens.colors.textPrimary, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    }
                }
                if (isFavorite) {
                    FavoriteBadge(Modifier.align(Alignment.TopEnd).offset(x = 5.dp, y = (-5).dp))
                }
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s2)) {
            val numberInLine = showNumber && (showName || showLogo)
            val starInLine = isFavorite && !starOnTile
            if (showName || numberInLine || starInLine) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s6)) {
                    if (numberInLine) {
                        Text(channel.number.toString(), style = MaterialTheme.typography.labelMedium, color = tokens.colors.textMuted)
                    }
                    if (showName) {
                        Text(
                            text = channel.name,
                            style = MaterialTheme.typography.bodyLarge,
                            color = tokens.colors.textPrimary,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }
                    if (starInLine) {
                        Icon(Icons.Filled.Star, contentDescription = null, tint = tokens.colors.accent, modifier = Modifier.size(NuvioTokens.Icon.sm))
                    }
                }
            }
            if (game != null) {
                // The live score of the game this channel is showing (Sports): "DET 21 – 17 CHI · 3rd 4:12".
                val sides = game.ordered(com.nuvio.app.features.livetv.sports.SportsCatalog.league(game.leagueId)?.sport ?: com.nuvio.app.features.livetv.sports.Sport.OTHER)
                Text(
                    text = sides.joinToString("  –  ") { (it.team.abbr.ifBlank { it.team.name }) + " " + (it.score ?: "") } + (game.status?.let { "  ·  $it" } ?: ""),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.LiveNowLine,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (current != null) {
                Text(
                    text = current.title + (current.episode?.let { "  ·  $it" } ?: ""),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (showName) tokens.colors.textSecondary else tokens.colors.textPrimary,
                    fontWeight = if (showName) FontWeight.Normal else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                NuvioProgressBar(progress = current.progress(nowMs), height = NuvioTokens.Space.s3, fillColor = tokens.colors.accent)
                nowNext.next?.let { next ->
                    Text(
                        text = formatClock(next.startMs) + "  " + next.title,
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.colors.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            } else {
                Text(if (showName) channel.group else channel.name, style = MaterialTheme.typography.bodySmall, color = tokens.colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
internal fun ChannelLogo(channel: LiveChannel, size: androidx.compose.ui.unit.Dp, show: Boolean = true) {
    ChannelLogo(channel, width = size, height = size, show = show)
}

/** The small favorite star that sits on the corner of a channel's logo (a dark circle so it shows on any logo). */
@Composable
internal fun FavoriteBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(18.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.72f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.Star, contentDescription = null, tint = MaterialTheme.nuvio.colors.accent, modifier = Modifier.size(12.dp))
    }
}

/** The channel's logo in a rounded box (or its first letters when it has none). */
@Composable
internal fun ChannelLogo(
    channel: LiveChannel,
    width: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp,
    show: Boolean = true,
    inset: androidx.compose.ui.unit.Dp = NuvioTokens.Space.s4,
    radius: androidx.compose.ui.unit.Dp = NuvioTokens.Radius.md,
) {
    val tokens = MaterialTheme.nuvio
    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .clip(RoundedCornerShape(radius))
            .background(tokens.colors.surfaceCard),
        contentAlignment = Alignment.Center,
    ) {
        val image = channel.thumbnail?.takeIf { show && it.isNotBlank() } ?: channel.logo?.takeIf { show && it.isNotBlank() }
        if (image != null) {
            AsyncImage(
                model = image,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().padding(inset),
                contentScale = ContentScale.Fit,
            )
        } else {
            Text(
                text = channel.name.trim().take(2).uppercase(),
                style = if (minOf(width, height) < 28.dp) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelLarge,
                color = tokens.colors.textMuted,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
    }
}

/** "20:30" or "8:30 PM", following the Live TV 24-hour setting when it's on. */
@Composable
internal fun formatClock(epochMs: Long): String {
    val settings by LiveTvPreferences.settings.collectAsStateWithLifecycle()
    return if (settings.use24HourClock) clock24(epochMs) else com.nuvio.app.core.format.formatLocalTime(epochMs)
}

internal fun clock24(epochMs: Long): String {
    val t = com.nuvio.app.features.livetv.data.CivilTime.at(epochMs, com.nuvio.app.features.livetv.data.LiveTvTimeZones.SYSTEM)
    return t.hour.toString().padStart(2, '0') + ":" + t.minute.toString().padStart(2, '0')
}

internal val Color.Companion.LiveNowLine: Color get() = Color(0xFFE53935)

/**
 * What the channel list looked like when a channel was opened: which groups were open and
 * where the list was scrolled. The Live TV screen leaves composition while the player is up,
 * so this lives outside it; backing out of the player returns to the same group and spot.
 */
internal object LiveTvListMemory {
    var expanded: Set<String> = emptySet()
    var scrollIndex: Int = 0
        private set
    var scrollOffset: Int = 0
        private set

    fun saveScroll(index: Int, offset: Int) {
        scrollIndex = index
        scrollOffset = offset
    }
}

/** A button in the Live TV header row: an icon with its short name under it. */
private class HeaderAction(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val label: String,
    val tint: Color,
    val short: String,
    val showClose: Boolean = false,
    val onClick: () -> Unit,
)

@Composable
private fun HeaderButton(a: HeaderAction, modifier: Modifier = Modifier) {
    val tokens = MaterialTheme.nuvio
    Column(
        modifier = modifier.heightIn(min = 52.dp).clickable(onClick = a.onClick).padding(vertical = NuvioTokens.Space.s4),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(if (a.showClose) Icons.Rounded.Close else a.icon, contentDescription = a.label, tint = a.tint, modifier = Modifier.size(24.dp))
        Text(a.short, style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), color = if (a.tint == tokens.colors.textPrimary) tokens.colors.textSecondary else a.tint, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip)
    }
}

/** Dialog for adding a custom quick channel region. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddRegionDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String, String, String) -> Unit,
) {
    RegionDialogImpl("Add Custom Region", "", "", "", "", "Add", onDismiss, onAdd)
}

/** Dialog for editing an existing custom region. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditRegionDialog(
    region: QuickChannelList.QuickCategory,
    onDismiss: () -> Unit,
    onEdit: (String, String, String, String) -> Unit,
) {
    RegionDialogImpl("Edit Region", region.id, region.title, region.description, region.tags.joinToString(","), "Save", onDismiss, onEdit)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RegionDialogImpl(
    title: String,
    initialId: String,
    initialTitle: String,
    initialDescription: String,
    initialTags: String,
    confirmText: String,
    onDismiss: () -> Unit,
    onSubmit: (String, String, String, String) -> Unit,
) {
    val tokens = MaterialTheme.nuvio.colors
    var regionId by remember { mutableStateOf(initialId) }
    var regionTitle by remember { mutableStateOf(initialTitle) }
    var regionDescription by remember { mutableStateOf(initialDescription) }
    var regionTags by remember { mutableStateOf(initialTags) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleMedium, color = tokens.textPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = regionId,
                    onValueChange = { regionId = it },
                    label = { Text("Region ID (e.g., my-region)", color = tokens.textMuted) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = tokens.surfaceCard,
                        unfocusedContainerColor = tokens.surfaceCard,
                        focusedBorderColor = tokens.accent,
                        unfocusedBorderColor = tokens.textMuted.copy(alpha = 0.4f),
                        focusedLabelColor = tokens.accent,
                        unfocusedLabelColor = tokens.textMuted,
                        cursorColor = tokens.accent,
                    ),
                )
                OutlinedTextField(
                    value = regionTitle,
                    onValueChange = { regionTitle = it },
                    label = { Text("Display Title (e.g., My Region)", color = tokens.textMuted) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = tokens.surfaceCard,
                        unfocusedContainerColor = tokens.surfaceCard,
                        focusedBorderColor = tokens.accent,
                        unfocusedBorderColor = tokens.textMuted.copy(alpha = 0.4f),
                        focusedLabelColor = tokens.accent,
                        unfocusedLabelColor = tokens.textMuted,
                        cursorColor = tokens.accent,
                    ),
                )
                OutlinedTextField(
                    value = regionDescription,
                    onValueChange = { regionDescription = it },
                    label = { Text("Description (optional)", color = tokens.textMuted) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = tokens.surfaceCard,
                        unfocusedContainerColor = tokens.surfaceCard,
                        focusedBorderColor = tokens.accent,
                        unfocusedBorderColor = tokens.textMuted.copy(alpha = 0.4f),
                        focusedLabelColor = tokens.accent,
                        unfocusedLabelColor = tokens.textMuted,
                        cursorColor = tokens.accent,
                    ),
                )
                OutlinedTextField(
                    value = regionTags,
                    onValueChange = { regionTags = it },
                    label = { Text("Tags (comma-separated, e.g., sports,news)", color = tokens.textMuted) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = tokens.surfaceCard,
                        unfocusedContainerColor = tokens.surfaceCard,
                        focusedBorderColor = tokens.accent,
                        unfocusedBorderColor = tokens.textMuted.copy(alpha = 0.4f),
                        focusedLabelColor = tokens.accent,
                        unfocusedLabelColor = tokens.textMuted,
                        cursorColor = tokens.accent,
                    ),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(regionId, regionTitle, regionDescription, regionTags) }) {
                Text(confirmText, color = tokens.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = tokens.textSecondary)
            }
        },
    )
}

/** Dialog for adding a custom quick channel. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddChannelDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String, String, String) -> Unit,
) {
    ChannelDialogImpl("Add Custom Quick Channel", "", "", "", "", "Add", onDismiss, onAdd)
}

/** Dialog for editing an existing custom quick channel. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditChannelDialog(
    channel: QuickChannel,
    onDismiss: () -> Unit,
    onEdit: (String, String, String, String) -> Unit,
) {
    ChannelDialogImpl(
        "Edit Quick Channel",
        channel.displayName,
        channel.aliases.joinToString(","),
        channel.regions.joinToString(","),
        channel.tags.joinToString(","),
        "Save",
        onDismiss,
        onEdit,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChannelDialogImpl(
    title: String,
    initialName: String,
    initialAliases: String,
    initialRegions: String,
    initialTags: String,
    confirmText: String,
    onDismiss: () -> Unit,
    onSubmit: (String, String, String, String) -> Unit,
) {
    val tokens = MaterialTheme.nuvio.colors
    var channelName by remember { mutableStateOf(initialName) }
    var channelAliases by remember { mutableStateOf(initialAliases) }
    var channelRegions by remember { mutableStateOf(initialRegions) }
    var channelTags by remember { mutableStateOf(initialTags) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleMedium, color = tokens.textPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = channelName,
                    onValueChange = { channelName = it },
                    label = { Text("Channel Name (e.g., My Channel)", color = tokens.textMuted) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = tokens.surfaceCard,
                        unfocusedContainerColor = tokens.surfaceCard,
                        focusedBorderColor = tokens.accent,
                        unfocusedBorderColor = tokens.textMuted.copy(alpha = 0.4f),
                        focusedLabelColor = tokens.accent,
                        unfocusedLabelColor = tokens.textMuted,
                        cursorColor = tokens.accent,
                    ),
                )
                OutlinedTextField(
                    value = channelAliases,
                    onValueChange = { channelAliases = it },
                    label = { Text("Aliases (comma-separated)", color = tokens.textMuted) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = tokens.surfaceCard,
                        unfocusedContainerColor = tokens.surfaceCard,
                        focusedBorderColor = tokens.accent,
                        unfocusedBorderColor = tokens.textMuted.copy(alpha = 0.4f),
                        focusedLabelColor = tokens.accent,
                        unfocusedLabelColor = tokens.textMuted,
                        cursorColor = tokens.accent,
                    ),
                )
                OutlinedTextField(
                    value = channelRegions,
                    onValueChange = { channelRegions = it },
                    label = { Text("Regions (comma-separated: US, UK, CA, etc.)", color = tokens.textMuted) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = tokens.surfaceCard,
                        unfocusedContainerColor = tokens.surfaceCard,
                        focusedBorderColor = tokens.accent,
                        unfocusedBorderColor = tokens.textMuted.copy(alpha = 0.4f),
                        focusedLabelColor = tokens.accent,
                        unfocusedLabelColor = tokens.textMuted,
                        cursorColor = tokens.accent,
                    ),
                )
                OutlinedTextField(
                    value = channelTags,
                    onValueChange = { channelTags = it },
                    label = { Text("Tags (comma-separated: sports, news, premium, etc.)", color = tokens.textMuted) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = tokens.surfaceCard,
                        unfocusedContainerColor = tokens.surfaceCard,
                        focusedBorderColor = tokens.accent,
                        unfocusedBorderColor = tokens.textMuted.copy(alpha = 0.4f),
                        focusedLabelColor = tokens.accent,
                        unfocusedLabelColor = tokens.textMuted,
                        cursorColor = tokens.accent,
                    ),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(channelName, channelAliases, channelRegions, channelTags) }) {
                Text(confirmText, color = tokens.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = tokens.textSecondary)
            }
        },
    )
}
