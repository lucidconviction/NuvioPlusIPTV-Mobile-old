package com.nuvio.app.features.livetv.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.nuvio.app.core.ui.NuvioProgressBar
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.core.ui.PlatformBackHandler
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.addons.AddonRepository
import com.nuvio.app.features.addons.AddonCatalog
import com.nuvio.app.features.addons.ManagedAddon
import com.nuvio.app.features.addons.liveTvAddons
import com.nuvio.app.features.catalog.CatalogUiState
import com.nuvio.app.features.catalog.fetchCatalogPage
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.stableKey
import com.nuvio.app.features.livetv.data.LiveTvPreferences
import com.nuvio.app.features.livetv.data.LiveTvRepository
import com.nuvio.app.features.livetv.model.ChannelGroup
import com.nuvio.app.features.livetv.model.EpgProgram
import com.nuvio.app.features.livetv.model.LiveChannel
import com.nuvio.app.features.livetv.model.LiveTvClock
import com.nuvio.app.features.livetv.player.LiveTvPlayback
import com.nuvio.app.features.livetv.ui.quickchannels.QuickChannel
import com.nuvio.app.features.livetv.ui.quickchannels.QuickChannelList
import com.nuvio.app.features.livetv.ui.quickchannels.rememberQuickChannelMatchState
import kotlinx.coroutines.delay
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.action_close
import nuvio.composeapp.generated.resources.livetv_no_matching_channels
import nuvio.composeapp.generated.resources.livetv_player_channels
import nuvio.composeapp.generated.resources.livetv_player_guide
import nuvio.composeapp.generated.resources.livetv_player_quick_channels
import org.jetbrains.compose.resources.stringResource

private const val HOUR_MS = 60L * 60L * 1000L
private const val SLOT_MS = 30L * 60L * 1000L
private val MiniHourWidth = 200.dp
private val MiniRowHeight = 52.dp
private val MiniChannelWidth = 72.dp

/**
 * Over a playing Live TV channel: a Channels button (with the player's controls) that opens a
 * panel of channels, with a Channels / Guide switch, and channel changing by swiping up or down
 * in the middle of the video. The panel sits on the right of a sideways phone, under the video
 * on an upright screen, and along the bottom of a tablet (which opens on the guide).
 */
@Composable
internal fun LiveTvPlayerOverlay(
    currentKey: String?,
    controlsVisible: Boolean,
    enabled: Boolean,
    onPanelOpened: () -> Unit,
    onSwitch: (LiveChannel, nowTitle: String?) -> Unit,
    onOpenAddonCatalog: (manifestUrl: String, catalog: AddonCatalog) -> Unit = { _, _ -> },
    /** Plays an add-on catalog item in place; [onFinished] gets true when playback switched. */
    onPlayAddonCatalogItem: (MetaPreview, ManagedAddon, (Boolean) -> Unit) -> Unit = { _, _, onFinished -> onFinished(false) },
) {
    val tokens = MaterialTheme.nuvio
    val state by remember { LiveTvRepository.ensureLoaded(); LiveTvViewState.list }.collectAsStateWithLifecycle()
    val programs by LiveTvRepository.programs.collectAsStateWithLifecycle()
    val playlists by LiveTvPreferences.playlists.collectAsStateWithLifecycle()
    val savedPlaylist by PlaylistFilter.selected.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { runCatching { AddonRepository.initialize() } }
    val addonUiState by AddonRepository.uiState.collectAsStateWithLifecycle()
    val liveAddons = addonUiState.addons.liveTvAddons()
    val now by produceState(LiveTvClock.nowMs()) {
        while (true) { value = LiveTvClock.nowMs(); delay(30_000) }
    }

    // Same groups as the Live TV list (playlist menu applied), without empty or locked ones.
    val allSections = state.sections.filter { it.channels.isNotEmpty() && !it.group.locked }
    val playlistIds = playlists.map { it.id }.filter { id -> allSections.any { it.group.sourceId == id } }
    val playlistId = PlaylistFilter.effective(savedPlaylist, playlistIds)
    val sections = if (playlistId == null) allSections else allSections.filter { it.group.sourceId == null || it.group.sourceId == playlistId }

    var panelOpen by rememberSaveable { mutableStateOf(false) }
    var groupId by rememberSaveable { mutableStateOf<String?>(null) }
    var showGuide by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var quickChannels by rememberSaveable { mutableStateOf(false) }
    // Add-on opened inside the panel: its catalogs and items replace the channel list.
    var addonTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var addonCatalogId by rememberSaveable { mutableStateOf<String?>(null) }
    // Catalog item whose streams are being resolved; its row shows a spinner until it starts.
    var addonOpeningKey by remember { mutableStateOf<String?>(null) }
    var banner by remember { mutableStateOf<LiveChannel?>(null) }
    // The group you're in: the one picked in the panel, else the first that has the channel.
    val section = sections.firstOrNull { it.group.id == groupId && it.channels.any { c -> c.key == currentKey } }
        ?: sections.firstOrNull { it.group.id == groupId }
        ?: sections.firstOrNull { s -> s.channels.any { it.key == currentKey } }
        ?: sections.firstOrNull()

    fun nowTitle(c: LiveChannel): String? = programs[c.key]?.firstOrNull { it.startMs <= now && it.stopMs > now }?.title

    // Swipe up / down in the middle of the video: next / previous channel in the group.
    val swipeSection by rememberUpdatedState(section)
    val swipeKey by rememberUpdatedState(currentKey)
    val swipeSwitch by rememberUpdatedState(onSwitch)
    DisposableEffect(enabled) {
        val handler: (Boolean) -> Unit = handler@{ next ->
            val list = swipeSection?.channels ?: return@handler
            if (list.size < 2) return@handler
            val i = list.indexOfFirst { it.key == swipeKey }
            val target = when {
                i < 0 -> list.first()
                next -> list[(i + 1) % list.size]
                else -> list[(i - 1 + list.size) % list.size]
            }
            swipeSwitch(target, nowTitle(target))
            banner = target
        }
        if (enabled) LiveTvPlayback.channelSwipe = handler
        onDispose { if (LiveTvPlayback.channelSwipe === handler) LiveTvPlayback.channelSwipe = null }
    }
    LaunchedEffect(banner) {
        if (banner != null) { delay(3_500); banner = null }
    }
    LaunchedEffect(enabled) { if (!enabled) panelOpen = false }
    PlatformBackHandler(enabled = panelOpen) { panelOpen = false }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val tablet = minOf(maxWidth, maxHeight) >= 600.dp
        val landscape = maxWidth > maxHeight
        val guide = showGuide ?: tablet

        // A few seconds of "what's on" after a swipe.
        AnimatedVisibility(
            visible = banner != null && !panelOpen,
            enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopStart).safeDrawingPadding().padding(NuvioTokens.Space.s16),
        ) {
            banner?.let { c -> ChannelBanner(c, nowTitle(c)) }
        }

        AnimatedVisibility(
            visible = controlsVisible && !panelOpen && enabled,
            enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd).safeDrawingPadding().padding(end = NuvioTokens.Space.s16),
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(NuvioTokens.Radius.full))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(NuvioTokens.Radius.full))
                    .clickable { panelOpen = true; onPanelOpened() }
                    .padding(horizontal = NuvioTokens.Space.s14, vertical = NuvioTokens.Space.s10),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s6),
            ) {
                Icon(Icons.Filled.LiveTv, contentDescription = null, tint = Color.White, modifier = Modifier.size(NuvioTokens.Icon.md))
                Text(stringResource(Res.string.livetv_player_channels), style = MaterialTheme.typography.labelLarge, color = Color.White)
            }
        }

        if (panelOpen) {
            // Tapping outside the panel closes it.
            Box(
                modifier = Modifier.fillMaxSize().clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { panelOpen = false },
            )
        }
        val sideways = landscape && !tablet
        val panelTop: Dp = when {
            sideways -> 0.dp
            landscape -> maxHeight * 0.52f
            // Upright: under the video (16:9, centred), leaving at least the lower 45% for channels.
            else -> (maxHeight / 2 + maxWidth * (9f / 32f)).coerceAtMost(maxHeight * 0.55f)
        }
        val panelWidth: Dp = if (sideways) minOf(maxWidth * 0.5f, 420.dp) else maxWidth
        AnimatedVisibility(
            visible = panelOpen,
            enter = if (sideways) slideInHorizontally { it } else slideInVertically { it },
            exit = if (sideways) slideOutHorizontally { it } else slideOutVertically { it },
            modifier = Modifier.align(if (sideways) Alignment.TopEnd else Alignment.BottomCenter),
        ) {
            val shape = if (sideways) RoundedCornerShape(topStart = NuvioTokens.Radius.xl, bottomStart = NuvioTokens.Radius.xl)
            else RoundedCornerShape(topStart = NuvioTokens.Radius.xl, topEnd = NuvioTokens.Radius.xl)
            Column(
                modifier = Modifier
                    .width(panelWidth)
                    .height(maxHeight - panelTop)
                    .clip(shape)
                    .background(tokens.colors.background.copy(alpha = 0.95f))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                    .safeDrawingPadding()
                    .padding(top = NuvioTokens.Space.s8),
            ) {
                // Groups, then the Channels / Guide switch.
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    LazyRow(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = NuvioTokens.Space.s10),
                        horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s6),
                    ) {
                        val allIndex = sections.indexOfFirst { it.group.id == ChannelGroup.ALL }
                        if (allIndex < 0) {
                            items(sections, key = { it.group.id }) { s ->
                                PanelPill(s.group.title, selected = addonTarget == null && s.group.id == section?.group?.id) { addonTarget = null; groupId = s.group.id }
                            }
                            items(liveAddons, key = { "addon:" + it.manifestUrl }) { addon ->
                                addon.manifest?.catalogs?.takeIf { it.isNotEmpty() }?.let {
                                    PanelPill(addon.displayTitle, selected = addonTarget == addon.manifestUrl) {
                                        addonTarget = addon.manifestUrl
                                        addonCatalogId = null
                                    }
                                }
                            }
                        } else {
                            items(sections.take(allIndex + 1), key = { it.group.id }) { s ->
                                PanelPill(s.group.title, selected = addonTarget == null && s.group.id == section?.group?.id) { addonTarget = null; groupId = s.group.id }
                            }
                            items(liveAddons, key = { "addon:" + it.manifestUrl }) { addon ->
                                addon.manifest?.catalogs?.takeIf { it.isNotEmpty() }?.let {
                                    PanelPill(addon.displayTitle, selected = addonTarget == addon.manifestUrl) {
                                        addonTarget = addon.manifestUrl
                                        addonCatalogId = null
                                    }
                                }
                            }
                            items(sections.drop(allIndex + 1), key = { it.group.id }) { s ->
                                PanelPill(s.group.title, selected = addonTarget == null && s.group.id == section?.group?.id) { addonTarget = null; groupId = s.group.id }
                            }
                        }
                    }
                    Box(
                        modifier = Modifier.padding(end = NuvioTokens.Space.s8).size(34.dp).clip(CircleShape)
                            .background(tokens.colors.surface).clickable { panelOpen = false },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(Res.string.action_close), tint = tokens.colors.textPrimary, modifier = Modifier.size(NuvioTokens.Icon.sm))
                    }
                }
                val selectedAddon = addonTarget?.let { url -> liveAddons.firstOrNull { it.manifestUrl == url } }
                if (selectedAddon != null) {
                    PlayerAddonCatalogBrowser(
                        addon = selectedAddon,
                        selectedCatalogId = addonCatalogId,
                        openingItemKey = addonOpeningKey,
                        onSelectCatalog = { addonCatalogId = it },
                        onPlayItem = { item ->
                            if (addonOpeningKey == null) {
                                addonOpeningKey = item.id
                                onPlayAddonCatalogItem(item, selectedAddon) { started ->
                                    addonOpeningKey = null
                                    if (started) panelOpen = false
                                }
                            }
                        },
                        onBack = { addonTarget = null; addonCatalogId = null },
                    )
                } else {
                    Row(
                        modifier = Modifier.padding(horizontal = NuvioTokens.Space.s10, vertical = NuvioTokens.Space.s8)
                            .clip(RoundedCornerShape(NuvioTokens.Radius.md)).background(tokens.colors.surface).padding(NuvioTokens.Space.s2),
                    ) {
                        SegmentButton(stringResource(Res.string.livetv_player_channels), selected = !guide && !quickChannels, modifier = Modifier.weight(1f)) { showGuide = false; quickChannels = false }
                        SegmentButton(stringResource(Res.string.livetv_player_guide), selected = guide, modifier = Modifier.weight(1f)) { showGuide = true; quickChannels = false }
                        SegmentButton(stringResource(Res.string.livetv_player_quick_channels), selected = quickChannels, modifier = Modifier.weight(1f)) { showGuide = false; quickChannels = true }
                    }
                    val channels = section?.channels.orEmpty()
                    if (guide) {
                        MiniGuide(channels, currentKey, now, onSwitch = { c, t -> onSwitch(c, t) })
                    } else if (quickChannels) {
                        PlayerQuickChannelsPanel(onSwitch = onSwitch)
                    } else {
                        val start = channels.indexOfFirst { it.key == currentKey }.coerceAtLeast(0)
                        val listState = rememberLazyListState(initialFirstVisibleItemIndex = start)
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = NuvioTokens.Space.s10, end = NuvioTokens.Space.s10, bottom = NuvioTokens.Space.s12),
                            verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s6),
                        ) {
                            items(channels, key = { it.key }) { c ->
                                val nn = programs[c.key]?.let { list -> list.firstOrNull { it.startMs <= now && it.stopMs > now } }
                                PanelChannelRow(c, nn, now, playing = c.key == currentKey) { onSwitch(c, nn?.title) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelBanner(channel: LiveChannel, nowTitle: String?) {
    Row(
        modifier = Modifier.clip(RoundedCornerShape(NuvioTokens.Radius.lg)).background(Color.Black.copy(alpha = 0.6f))
            .padding(horizontal = NuvioTokens.Space.s12, vertical = NuvioTokens.Space.s8),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s10),
    ) {
        ChannelLogo(channel, width = 52.dp, height = 34.dp)
        Column {
            Text(channel.number.toString() + "  " + channel.name, style = MaterialTheme.typography.titleSmall, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (nowTitle != null) Text(nowTitle, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.75f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun PanelPill(label: String, selected: Boolean, onClick: () -> Unit) {
    val tokens = MaterialTheme.nuvio
    val shape = RoundedCornerShape(NuvioTokens.Radius.full)
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = if (selected) tokens.colors.onAccent else tokens.colors.textSecondary,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        maxLines = 1,
        modifier = Modifier
            .clip(shape)
            .background(if (selected) tokens.colors.accent else Color.Transparent)
            .border(1.dp, if (selected) tokens.colors.accent else tokens.colors.textPrimary.copy(alpha = 0.16f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = NuvioTokens.Space.s12, vertical = NuvioTokens.Space.s6),
    )
}

@Composable
private fun SegmentButton(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val tokens = MaterialTheme.nuvio
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(NuvioTokens.Radius.sm))
            .background(if (selected) tokens.colors.surfaceCard else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = NuvioTokens.Space.s6),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (selected) tokens.colors.textPrimary else tokens.colors.textMuted, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
private fun PanelChannelRow(channel: LiveChannel, now: EpgProgram?, nowMs: Long, playing: Boolean, onClick: () -> Unit) {
    val tokens = MaterialTheme.nuvio
    val settings by LiveTvPreferences.settings.collectAsStateWithLifecycle()
    val shape = RoundedCornerShape(NuvioTokens.Radius.lg)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (playing) tokens.colors.accent.copy(alpha = 0.22f) else tokens.colors.surface)
            .border(if (playing) 1.5.dp else 0.dp, if (playing) tokens.colors.accent else Color.Transparent, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = NuvioTokens.Space.s8, vertical = NuvioTokens.Space.s8),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s10),
    ) {
        if (settings.phoneChannelLogos) ChannelLogo(channel, width = 52.dp, height = 36.dp)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s2)) {
            val head = listOfNotNull(
                channel.number.toString().takeIf { settings.phoneChannelNumbers },
                channel.name.takeIf { settings.phoneChannelNames || !settings.phoneChannelLogos },
            ).joinToString("  ")
            if (head.isNotBlank()) Text(head, style = MaterialTheme.typography.bodyMedium, color = tokens.colors.textPrimary, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (now != null) {
                Text(now.title, style = MaterialTheme.typography.bodySmall, color = tokens.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                NuvioProgressBar(progress = now.progress(nowMs), height = NuvioTokens.Space.s3, fillColor = tokens.colors.accent)
            }
        }
    }
}

/** The next few hours for the group's channels: tap a show on now to switch, a later one for its details. */
@Composable
private fun MiniGuide(channels: List<LiveChannel>, currentKey: String?, now: Long, onSwitch: (LiveChannel, String?) -> Unit) {
    val tokens = MaterialTheme.nuvio
    val density = LocalDensity.current
    val from = (now / SLOT_MS) * SLOT_MS
    val to = from + 4 * HOUR_MS
    var programs by remember { mutableStateOf<Map<String, List<EpgProgram>>>(emptyMap()) }
    LaunchedEffect(channels, from) {
        val out = HashMap<String, List<EpgProgram>>()
        channels.map { it.key }.chunked(100).forEach { part ->
            out += LiveTvRepository.programsIn(part, from, to)
            programs = HashMap(out)
        }
    }
    var detail by remember { mutableStateOf<Pair<LiveChannel, EpgProgram>?>(null) }
    val hScroll = rememberScrollState()
    val pxPerMs = with(density) { MiniHourWidth.toPx() } / HOUR_MS

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = NuvioTokens.Space.s10)) {
        Row(modifier = Modifier.fillMaxWidth().height(22.dp)) {
            Spacer(Modifier.width(MiniChannelWidth + NuvioTokens.Space.s6))
            Row(modifier = Modifier.weight(1f).horizontalScroll(hScroll, enabled = false)) {
                var t = from
                while (t < to) {
                    Text(
                        formatClock(t),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (now >= t && now < t + SLOT_MS) tokens.colors.textPrimary else tokens.colors.textMuted,
                        fontWeight = if (now >= t && now < t + SLOT_MS) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.width(MiniHourWidth / 2).padding(start = NuvioTokens.Space.s4),
                        maxLines = 1,
                    )
                    t += SLOT_MS
                }
            }
        }
        val start = channels.indexOfFirst { it.key == currentKey }.coerceAtLeast(0)
        LazyColumn(
            state = rememberLazyListState(initialFirstVisibleItemIndex = start),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = NuvioTokens.Space.s12),
            verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s4),
        ) {
            items(channels, key = { it.key }) { c ->
                MiniGuideRow(c, programs[c.key].orEmpty(), from, to, now, playing = c.key == currentKey, hScroll = hScroll, pxPerMs = pxPerMs,
                    onChannel = { onSwitch(c, programs[c.key]?.firstOrNull { it.startMs <= now && it.stopMs > now }?.title) },
                    onProgram = { p -> if (p.startMs <= now && p.stopMs > now) onSwitch(c, p.title) else detail = c to p },
                )
            }
        }
    }
    detail?.let { (c, p) ->
        ProgramDetailsDialog(c, p, now, onDismiss = { detail = null }, onPlay = { detail = null; onSwitch(c, p.title) }, onReplay = null)
    }
}

@Composable
private fun MiniGuideRow(
    channel: LiveChannel,
    programs: List<EpgProgram>,
    from: Long,
    to: Long,
    now: Long,
    playing: Boolean,
    hScroll: ScrollState,
    pxPerMs: Float,
    onChannel: () -> Unit,
    onProgram: (EpgProgram) -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val density = LocalDensity.current
    val shape = RoundedCornerShape(NuvioTokens.Radius.md)
    Row(modifier = Modifier.fillMaxWidth().height(MiniRowHeight), horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s6)) {
        Box(
            modifier = Modifier.width(MiniChannelWidth).fillMaxHeight().clip(shape)
                .background(tokens.colors.surface)
                .border(if (playing) 1.5.dp else 0.dp, if (playing) tokens.colors.accent else Color.Transparent, shape)
                .clickable(onClick = onChannel),
            contentAlignment = Alignment.Center,
        ) {
            ChannelLogo(channel, width = MiniChannelWidth - 8.dp, height = MiniRowHeight - 12.dp)
        }
        Box(modifier = Modifier.weight(1f).fillMaxHeight().horizontalScroll(hScroll)) {
            Box(modifier = Modifier.width(with(density) { ((to - from) * pxPerMs).toDp() }).fillMaxHeight()) {
                programs.forEach { p ->
                    val s = p.startMs.coerceAtLeast(from)
                    val e = p.stopMs.coerceAtMost(to)
                    if (e <= s) return@forEach
                    val x = with(density) { ((s - from) * pxPerMs).toDp() }
                    val w = with(density) { ((e - s) * pxPerMs).toDp() } - 3.dp
                    if (w <= 0.dp) return@forEach
                    val live = p.startMs <= now && p.stopMs > now
                    Box(
                        modifier = Modifier.offset(x = x).width(w).fillMaxHeight().clip(shape)
                            .background(if (live) tokens.colors.accent.copy(alpha = 0.25f) else tokens.colors.surface)
                            .border(1.dp, if (live) tokens.colors.accent.copy(alpha = 0.45f) else Color.Transparent, shape)
                            .clickable { onProgram(p) }
                            .padding(horizontal = NuvioTokens.Space.s8, vertical = NuvioTokens.Space.s4),
                    ) {
                        Column {
                            Text(p.title, style = MaterialTheme.typography.bodySmall, color = tokens.colors.textPrimary, fontWeight = if (live) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (w > 60.dp) Text(formatClock(p.startMs), style = MaterialTheme.typography.labelSmall, color = tokens.colors.textMuted, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Quick Channels panel shown inside the player's channel panel: pick a curated QuickChannel bundle
 * and switch to the first matching live channel. Stream liveness is probed so only working channels
 * appear (the "checking" state is shown while probing).
 */
@Composable
private fun PlayerQuickChannelsPanel(onSwitch: (LiveChannel, String?) -> Unit) {
    val tokens = MaterialTheme.nuvio
    val c = tokens.colors
    val liveChannels by LiveTvRepository.channels.collectAsStateWithLifecycle(initialValue = emptyList())
    val categories = QuickChannelList.getChannelCategories()
    var selectedCategory by remember { mutableStateOf(categories.firstOrNull() ?: "all") }
    val filteredQc = remember(selectedCategory) { QuickChannelList.getChannelsForCategory(selectedCategory) }
    var selectedQc by remember { mutableStateOf<QuickChannel?>(null) }
    val validation = rememberQuickChannelMatchState(selectedQc, liveChannels)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = NuvioTokens.Space.s10, vertical = NuvioTokens.Space.s4),
        verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s6),
    ) {
        // Category row
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s4),
            contentPadding = PaddingValues(vertical = NuvioTokens.Space.s2),
        ) {
            items(categories) { catId ->
                val cat = QuickChannelList.allCategories().find { it.id == catId }
                val title = cat?.title ?: catId
                val isActive = catId == selectedCategory
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (isActive) c.accent else c.surfaceCard)
                        .clickable { selectedCategory = catId }
                        .padding(horizontal = NuvioTokens.Space.s8, vertical = NuvioTokens.Space.s4),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(title, style = MaterialTheme.typography.labelMedium, color = if (isActive) Color.White else c.textPrimary, fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
        // Quick channel chip row
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s4),
            contentPadding = PaddingValues(vertical = NuvioTokens.Space.s2),
        ) {
            items(filteredQc, key = { it.displayName }) { qc ->
                val isActive = selectedQc?.displayName == qc.displayName
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(NuvioTokens.Radius.md))
                        .background(if (isActive) c.accent.copy(alpha = 0.2f) else c.surfaceCard)
                        .clickable { selectedQc = if (isActive) null else qc }
                        .padding(horizontal = NuvioTokens.Space.s8, vertical = NuvioTokens.Space.s6),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(qc.displayName, style = MaterialTheme.typography.bodyMedium, color = if (isActive) c.accent else c.textPrimary, fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        // Match list
        if (selectedQc != null && validation.matches.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    selectedQc!!.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    color = c.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (validation.isChecking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        color = c.accent,
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    "${validation.workingCount} working",
                    style = MaterialTheme.typography.labelSmall,
                    color = c.textMuted,
                )
            }
            val sortedMatches = validation.visibleMatches
            if (sortedMatches.isEmpty() && !validation.isChecking) {
                Text(
                    "No working streams found",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textMuted,
                    modifier = Modifier.padding(NuvioTokens.Space.s8),
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s4),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = NuvioTokens.Space.s8),
                ) {
                    items(sortedMatches, key = { it.key }) { ch ->
                        PanelChannelRow(ch, null, LiveTvClock.nowMs(), playing = false) { onSwitch(ch, null) }
                    }
                }
            }
        } else if (selectedQc != null && liveChannels.isNotEmpty()) {
            Text(stringResource(Res.string.livetv_no_matching_channels), style = MaterialTheme.typography.bodySmall, color = c.textMuted, modifier = Modifier.padding(NuvioTokens.Space.s8))
        }
    }
}

/**
 * The add-on browser shown inside the player's channel panel. It stays in the panel so the
 * channel keeps playing while browsing; picking an item switches the player in place.
 */
@Composable
private fun PlayerAddonCatalogBrowser(
    addon: ManagedAddon,
    selectedCatalogId: String?,
    openingItemKey: String?,
    onSelectCatalog: (String) -> Unit,
    onPlayItem: (MetaPreview) -> Unit,
    onBack: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val c = tokens.colors
    val manifest = addon.manifest
    val catalogs = manifest?.catalogs.orEmpty()
    val catalog = catalogs.firstOrNull { it.id == selectedCatalogId } ?: catalogs.firstOrNull()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = NuvioTokens.Space.s10),
        verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s6),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8),
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(c.surface)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Back to channels",
                    tint = c.textPrimary,
                    modifier = Modifier.size(NuvioTokens.Icon.sm),
                )
            }
            Text(
                addon.displayTitle,
                style = MaterialTheme.typography.titleSmall,
                color = c.textPrimary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        when {
            manifest == null && addon.errorMessage != null -> Text(
                addon.errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = c.danger,
                modifier = Modifier.padding(NuvioTokens.Space.s8),
            )
            manifest == null -> Row(
                modifier = Modifier.padding(NuvioTokens.Space.s8),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = c.accent, strokeWidth = 2.dp)
                Text("Loading…", style = MaterialTheme.typography.bodySmall, color = c.textMuted)
            }
            catalogs.isEmpty() -> Text(
                "No catalogs",
                style = MaterialTheme.typography.bodySmall,
                color = c.textMuted,
                modifier = Modifier.padding(NuvioTokens.Space.s8),
            )
            else -> {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s6),
                    contentPadding = PaddingValues(vertical = NuvioTokens.Space.s2),
                ) {
                    items(catalogs, key = { it.id }) { cat ->
                        PanelPill(cat.name, selected = cat.id == catalog?.id) { onSelectCatalog(cat.id) }
                    }
                }
                if (catalog != null) {
                    PlayerAddonCatalogItems(
                        manifestUrl = addon.manifestUrl,
                        catalog = catalog,
                        openingItemKey = openingItemKey,
                        onPlayItem = onPlayItem,
                    )
                }
            }
        }
    }
}

/** Catalog items for the add-on selected in the player panel, fetched once per catalog. */
@Composable
private fun PlayerAddonCatalogItems(
    manifestUrl: String,
    catalog: AddonCatalog,
    openingItemKey: String?,
    onPlayItem: (MetaPreview) -> Unit,
) {
    val tokens = MaterialTheme.nuvio.colors
    var state by remember(manifestUrl, catalog.id, catalog.type) {
        mutableStateOf(CatalogUiState(isLoading = true))
    }
    LaunchedEffect(manifestUrl, catalog.id, catalog.type) {
        state = CatalogUiState(isLoading = true)
        // Catalogs that require a choice up front (usually a genre) would otherwise return
        // nothing; open them with the first available option.
        val requiredGenre = catalog.extra
            .firstOrNull { extra -> extra.isRequired && extra.name == "genre" && extra.options.isNotEmpty() }
            ?.options
            ?.first()
        state = try {
            val page = fetchCatalogPage(
                manifestUrl = manifestUrl,
                type = catalog.type,
                catalogId = catalog.id,
                genre = requiredGenre,
            )
            CatalogUiState(items = page.items, nextSkip = page.nextSkip)
        } catch (e: Exception) {
            CatalogUiState(errorMessage = e.message ?: "Could not load this catalog")
        }
    }
    when {
        state.isLoading && state.items.isEmpty() -> Box(
            modifier = Modifier.fillMaxWidth().padding(NuvioTokens.Space.s16),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(22.dp), color = tokens.accent, strokeWidth = 2.dp)
        }
        state.errorMessage != null -> Text(
            state.errorMessage!!,
            style = MaterialTheme.typography.bodySmall,
            color = tokens.danger,
            modifier = Modifier.padding(NuvioTokens.Space.s8),
        )
        state.items.isEmpty() -> Text(
            "No items",
            style = MaterialTheme.typography.bodySmall,
            color = tokens.textMuted,
            modifier = Modifier.padding(NuvioTokens.Space.s8),
        )
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = NuvioTokens.Space.s12),
            verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s4),
        ) {
            items(state.items, key = { it.stableKey() }) { item ->
                PlayerAddonCatalogItemRow(
                    item = item,
                    opening = item.id == openingItemKey,
                    enabled = openingItemKey == null,
                    onClick = { onPlayItem(item) },
                )
            }
        }
    }
}

/** One add-on catalog entry: artwork, name and type; tapping plays it in the open player. */
@Composable
private fun PlayerAddonCatalogItemRow(
    item: MetaPreview,
    opening: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(NuvioTokens.Radius.md))
            .background(tokens.surfaceCard)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(NuvioTokens.Space.s6),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8),
    ) {
        Box(
            modifier = Modifier
                .width(44.dp)
                .height(60.dp)
                .clip(RoundedCornerShape(NuvioTokens.Radius.xs))
                .background(tokens.surface),
            contentAlignment = Alignment.Center,
        ) {
            val art = item.poster ?: item.landscapePoster
            if (!art.isNullOrBlank()) {
                AsyncImage(
                    model = art,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    Icons.Filled.LiveTv,
                    contentDescription = null,
                    tint = tokens.textMuted,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                item.name,
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.textPrimary,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val subtitle = item.releaseInfo?.takeIf { it.isNotBlank() } ?: item.type
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = tokens.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (opening) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = tokens.accent,
                strokeWidth = 2.dp,
            )
        } else {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = tokens.accent,
                modifier = Modifier.size(NuvioTokens.Icon.md),
            )
        }
    }
}
