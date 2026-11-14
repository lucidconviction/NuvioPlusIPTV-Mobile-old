package com.nuvio.app.features.livetv.ui.virtual

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.nuvio.app.core.ui.Chip
import com.nuvio.app.core.ui.DialogButton
import com.nuvio.app.core.ui.DialogButtonStyle
import com.nuvio.app.core.ui.DialogButtons
import com.nuvio.app.core.ui.DialogSurface
import com.nuvio.app.core.ui.NuvioInputField
import com.nuvio.app.core.ui.NuvioScreenHeader
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.core.ui.nuvioSafeBottomPadding
import com.nuvio.app.features.addons.AddonCatalog
import com.nuvio.app.features.addons.AddonRepository
import com.nuvio.app.features.addons.ManagedAddon
import com.nuvio.app.features.catalog.fetchCatalogPage
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.livetv.ui.LiveTvViewState
import com.nuvio.app.features.livetv.ui.liveTvSideInsets
import com.nuvio.app.features.livetv.virtual.VirtualChannelKind
import com.nuvio.app.features.livetv.virtual.VirtualChannelRepository
import com.nuvio.app.features.livetv.virtual.VirtualChannelSpec
import com.nuvio.app.features.livetv.virtual.VirtualChannels
import com.nuvio.app.features.livetv.virtual.VirtualJoin
import com.nuvio.app.features.livetv.virtual.VirtualOrder
import com.nuvio.app.features.livetv.virtual.VirtualPlayout
import com.nuvio.app.features.livetv.virtual.VirtualPlayoutMode
import com.nuvio.app.features.livetv.virtual.VirtualPoolRules
import com.nuvio.app.features.livetv.virtual.VirtualSource
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.action_cancel
import nuvio.composeapp.generated.resources.lucid_tv_catalog_empty
import nuvio.composeapp.generated.resources.lucid_tv_delete
import nuvio.composeapp.generated.resources.lucid_tv_delete_confirm
import nuvio.composeapp.generated.resources.lucid_tv_enabled
import nuvio.composeapp.generated.resources.lucid_tv_editor_title
import nuvio.composeapp.generated.resources.lucid_tv_genre_any
import nuvio.composeapp.generated.resources.lucid_tv_group
import nuvio.composeapp.generated.resources.lucid_tv_join
import nuvio.composeapp.generated.resources.lucid_tv_join_cap
import nuvio.composeapp.generated.resources.lucid_tv_join_full
import nuvio.composeapp.generated.resources.lucid_tv_join_none
import nuvio.composeapp.generated.resources.lucid_tv_logo
import nuvio.composeapp.generated.resources.lucid_tv_max_items
import nuvio.composeapp.generated.resources.lucid_tv_name
import nuvio.composeapp.generated.resources.lucid_tv_number
import nuvio.composeapp.generated.resources.lucid_tv_playout
import nuvio.composeapp.generated.resources.lucid_tv_playout_loop
import nuvio.composeapp.generated.resources.lucid_tv_playout_rotate
import nuvio.composeapp.generated.resources.lucid_tv_pool_count
import nuvio.composeapp.generated.resources.lucid_tv_pool_empty
import nuvio.composeapp.generated.resources.lucid_tv_pool_loading
import nuvio.composeapp.generated.resources.lucid_tv_refresh_hours
import nuvio.composeapp.generated.resources.lucid_tv_save
import nuvio.composeapp.generated.resources.lucid_tv_search_items
import nuvio.composeapp.generated.resources.lucid_tv_select_catalog
import nuvio.composeapp.generated.resources.lucid_tv_select_group
import nuvio.composeapp.generated.resources.lucid_tv_select_item
import nuvio.composeapp.generated.resources.lucid_tv_slot_minutes
import nuvio.composeapp.generated.resources.lucid_tv_source
import nuvio.composeapp.generated.resources.lucid_tv_source_catalog
import nuvio.composeapp.generated.resources.lucid_tv_source_catalog_description
import nuvio.composeapp.generated.resources.lucid_tv_source_playlist
import nuvio.composeapp.generated.resources.lucid_tv_source_playlist_description
import nuvio.composeapp.generated.resources.lucid_tv_source_series
import nuvio.composeapp.generated.resources.lucid_tv_source_series_description
import org.jetbrains.compose.resources.stringResource

/**
 * The Channel Studio editor: name, grouping, source, filters and playout. Sources are picked from
 * the installed add-ons or from an existing playlist group; nothing here stores credentials or a
 * resolved stream URL.
 */
@Composable
fun VirtualChannelEditorScreen(
    channelId: String?,
    prefillManifestUrl: String? = null,
    prefillAddonId: String? = null,
    prefillAddonName: String? = null,
    prefillCatalogId: String? = null,
    prefillCatalogName: String? = null,
    prefillContentType: String? = null,
    prefillGenre: String? = null,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = MaterialTheme.nuvio
    LaunchedEffect(Unit) {
        VirtualChannelRepository.ensureLoaded()
        runCatching { AddonRepository.initialize() }
    }
    val existing = remember(channelId) { channelId?.let { VirtualChannelRepository.spec(it) } }
    var name by rememberSaveable { mutableStateOf(existing?.name.orEmpty()) }
    var numberText by rememberSaveable { mutableStateOf(existing?.number?.toString().orEmpty()) }
    var logo by rememberSaveable { mutableStateOf(existing?.logo.orEmpty()) }
    var enabled by rememberSaveable { mutableStateOf(existing?.enabled ?: true) }
    var kind by remember { mutableStateOf(existing?.kind ?: VirtualChannelKind.ADDON_ROTATE) }
    var source by remember {
        mutableStateOf(
            existing?.source ?: prefillManifestUrl?.takeIf { it.isNotBlank() }?.let { url ->
                VirtualSource.AddonCatalog(
                    manifestUrl = url,
                    addonId = prefillAddonId.orEmpty(),
                    addonName = prefillAddonName.orEmpty(),
                    catalogId = prefillCatalogId.orEmpty(),
                    catalogName = prefillCatalogName.orEmpty(),
                    contentType = prefillContentType ?: "movie",
                    genre = prefillGenre?.takeIf { it.isNotBlank() },
                )
            }
        )
    }
    var playout by remember { mutableStateOf(existing?.playout ?: VirtualPlayout()) }
    var poolRules by remember { mutableStateOf(existing?.pool ?: VirtualPoolRules()) }
    var join by remember { mutableStateOf(existing?.join ?: VirtualJoin()) }
    var picker by remember { mutableStateOf<PickerMode?>(if (source == null && channelId == null) PickerMode.SOURCE else null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val pools by VirtualChannelRepository.pools.collectAsStateWithLifecycle()
    val refreshing by VirtualChannelRepository.refreshing.collectAsStateWithLifecycle()
    val errors by VirtualChannelRepository.errors.collectAsStateWithLifecycle()
    val snapshot = existing?.let { pools[it.id] }
    val canSave = name.isNotBlank() && source != null

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(tokens.colors.background)
            .statusBarsPadding()
            .liveTvSideInsets(),
    ) {
        NuvioScreenHeader(
            title = stringResource(Res.string.lucid_tv_editor_title),
            includeStatusBarPadding = false,
            onBack = onBack,
            modifier = Modifier.padding(horizontal = NuvioTokens.Space.s8),
            actions = {
                if (existing != null) {
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = null, tint = tokens.colors.textMuted)
                    }
                }
            },
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = NuvioTokens.Space.s16,
                end = NuvioTokens.Space.s16,
                top = NuvioTokens.Space.s8,
                bottom = nuvioSafeBottomPadding(NuvioTokens.Space.s32),
            ),
            verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s16),
        ) {
            item {
                EditorSection(title = stringResource(Res.string.lucid_tv_name)) {
                    NuvioInputField(
                        value = name,
                        onValueChange = { name = it },
                        placeholder = stringResource(Res.string.lucid_tv_name),
                    )
                    Text(
                        text = stringResource(Res.string.lucid_tv_group) + ": " + VirtualChannels.DEFAULT_GROUP,
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.colors.textMuted,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8)) {
                        NuvioInputField(
                            value = numberText,
                            onValueChange = { numberText = it.filter(Char::isDigit).take(5) },
                            placeholder = stringResource(Res.string.lucid_tv_number),
                            modifier = Modifier.weight(1f),
                        )
                        NuvioInputField(
                            value = logo,
                            onValueChange = { logo = it },
                            placeholder = stringResource(Res.string.lucid_tv_logo),
                            modifier = Modifier.weight(2f),
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = stringResource(Res.string.lucid_tv_enabled),
                            style = MaterialTheme.typography.bodyMedium,
                            color = tokens.colors.textPrimary,
                        )
                        Switch(checked = enabled, onCheckedChange = { enabled = it })
                    }
                }
            }
            item {
                EditorSection(title = stringResource(Res.string.lucid_tv_source)) {
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable { picker = PickerMode.SOURCE },
                        shape = tokens.shapes.compactCard,
                        color = tokens.colors.surface,
                    ) {
                        Row(
                            modifier = Modifier.padding(NuvioTokens.Space.s12),
                            horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s12),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s2)) {
                                Text(
                                    text = sourceSummary(source),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = tokens.colors.textPrimary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = kind.name.replace('_', ' '),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = tokens.colors.textMuted,
                                )
                            }
                            Text(
                                text = stringResource(Res.string.lucid_tv_select_catalog),
                                style = MaterialTheme.typography.labelMedium,
                                color = tokens.colors.accent,
                            )
                        }
                    }
                    existing?.let { spec ->
                        val count = snapshot?.items?.size ?: 0
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = when {
                                    spec.id in refreshing -> stringResource(Res.string.lucid_tv_pool_loading)
                                    errors[spec.id] != null -> errors[spec.id].orEmpty()
                                    count == 0 -> stringResource(Res.string.lucid_tv_pool_empty)
                                    else -> stringResource(Res.string.lucid_tv_pool_count, count)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (errors[spec.id] != null) tokens.colors.danger else tokens.colors.textMuted,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = { VirtualChannelRepository.refreshPool(spec.id, force = true) }) {
                                Icon(Icons.Filled.Refresh, contentDescription = null, tint = tokens.colors.textPrimary)
                            }
                        }
                    }
                }
            }
            item {
                EditorSection(title = stringResource(Res.string.lucid_tv_playout)) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s6),
                    ) {
                        Chip(stringResource(Res.string.lucid_tv_playout_rotate), playout.mode == VirtualPlayoutMode.ROTATE, {
                            playout = playout.copy(mode = VirtualPlayoutMode.ROTATE)
                        })
                        Chip(stringResource(Res.string.lucid_tv_playout_loop), playout.mode == VirtualPlayoutMode.LOOP, {
                            playout = playout.copy(mode = VirtualPlayoutMode.LOOP)
                        })
                    }
                    Text(
                        text = stringResource(Res.string.lucid_tv_slot_minutes),
                        style = MaterialTheme.typography.labelMedium,
                        color = tokens.colors.textMuted,
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s6),
                    ) {
                        listOf(30, 45, 60, 90, 120).forEach { minutes ->
                            Chip("$minutes", playout.slotMinutes == minutes, {
                                playout = playout.copy(slotMinutes = minutes, seriesSlotMinutes = minutes)
                            })
                        }
                    }
                    Text(
                        text = "Order",
                        style = MaterialTheme.typography.labelMedium,
                        color = tokens.colors.textMuted,
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s6),
                    ) {
                        VirtualOrder.entries.forEach { order ->
                            Chip(order.name.lowercase().replaceFirstChar { it.uppercase() }, playout.order == order, {
                                playout = playout.copy(order = order)
                            })
                        }
                    }
                }
            }
            item {
                EditorSection(title = stringResource(Res.string.lucid_tv_join)) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s6),
                    ) {
                        Chip(stringResource(Res.string.lucid_tv_join_none), join.maxStartPercent == 0, {
                            join = join.copy(maxStartPercent = 0)
                        })
                        listOf(10, 20, 30, 50).forEach { percent ->
                            Chip(stringResource(Res.string.lucid_tv_join_cap, percent), join.maxStartPercent == percent, {
                                join = join.copy(maxStartPercent = percent)
                            })
                        }
                        Chip(stringResource(Res.string.lucid_tv_join_full), join.maxStartPercent < 0, {
                            join = join.copy(maxStartPercent = -1)
                        })
                    }
                }
            }
            item {
                EditorSection(title = "Pool") {
                    Row(horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8)) {
                        NuvioInputField(
                            value = poolRules.maxItems.toString(),
                            onValueChange = { text -> text.filter(Char::isDigit).take(5).toIntOrNull()?.let { poolRules = poolRules.copy(maxItems = it.coerceAtLeast(1)) } },
                            placeholder = stringResource(Res.string.lucid_tv_max_items),
                            modifier = Modifier.weight(1f),
                        )
                        NuvioInputField(
                            value = poolRules.refreshHours.toString(),
                            onValueChange = { text -> text.filter(Char::isDigit).take(3).toIntOrNull()?.let { poolRules = poolRules.copy(refreshHours = it) } },
                            placeholder = stringResource(Res.string.lucid_tv_refresh_hours),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            item {
                DialogButton(
                    text = stringResource(Res.string.lucid_tv_save),
                    onClick = {
                        val chosen = source ?: return@DialogButton
                        val base = existing ?: VirtualChannelSpec(
                            id = "",
                            name = name,
                            kind = kind,
                            source = chosen,
                        )
                        val updated = base.copy(
                            name = name.trim(),
                            kind = kind,
                            group = VirtualChannels.DEFAULT_GROUP,
                            number = numberText.toIntOrNull(),
                            logo = logo.trim().ifBlank { null },
                            enabled = enabled,
                            source = chosen,
                            playout = playout,
                            pool = poolRules,
                            join = join,
                        )
                        if (existing == null) VirtualChannelRepository.create(updated)
                        else VirtualChannelRepository.update(updated)
                        onBack()
                    },
                    style = DialogButtonStyle.Primary,
                    enabled = canSave,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    picker?.let { mode ->
        SourcePicker(
            mode = mode,
            onDismiss = { picker = null },
            onCatalog = { catalogSource, seriesItem ->
                if (seriesItem != null) {
                    source = VirtualSource.AddonItem(
                        manifestUrl = catalogSource.manifestUrl,
                        addonId = catalogSource.addonId,
                        itemId = seriesItem.id,
                        itemType = seriesItem.type,
                    )
                    kind = VirtualChannelKind.SERIES_MARATHON
                    playout = playout.copy(mode = VirtualPlayoutMode.LOOP, order = VirtualOrder.AIR)
                    join = join.copy(maxStartPercent = -1)
                    if (name.isBlank()) name = seriesItem.name
                } else {
                    source = catalogSource
                    val seriesCatalog = catalogSource.contentType == "series"
                    kind = if (seriesCatalog) VirtualChannelKind.ADDON_SERIES else VirtualChannelKind.ADDON_ROTATE
                    playout = playout.copy(
                        // A series catalog is expanded into episodes. Running it as a LOOP makes
                        // the guide use each episode's real runtime instead of a fixed block.
                        mode = if (seriesCatalog) VirtualPlayoutMode.LOOP else VirtualPlayoutMode.ROTATE,
                        slotMinutes = if (seriesCatalog) 45 else 90,
                        seriesSlotMinutes = 45,
                        order = if (seriesCatalog) VirtualOrder.AIR else playout.order,
                    )
                    join = join.copy(maxStartPercent = -1)
                    if (name.isBlank()) {
                        name = listOf(catalogSource.addonName, catalogSource.catalogName)
                            .filter { it.isNotBlank() }
                            .joinToString(": ")
                    }
                }
                picker = null
            },
            onPlaylistGroup = { sourceId, groupId, title ->
                source = VirtualSource.PlaylistGroup(sourceId = sourceId, groupId = groupId)
                kind = VirtualChannelKind.PLAYLIST_LOOP
                playout = playout.copy(mode = VirtualPlayoutMode.LOOP, slotMinutes = 60, order = VirtualOrder.SEQUENTIAL)
                join = join.copy(maxStartPercent = 0)
                if (name.isBlank()) name = "$title 24/7"
                picker = null
            },
        )
    }

    if (confirmDelete && existing != null) {
        DialogSurface(
            onDismissRequest = { confirmDelete = false },
            title = stringResource(Res.string.lucid_tv_delete_confirm),
            message = existing.name,
        ) {
            DialogButtons {
                DialogButton(text = stringResource(Res.string.action_cancel), onClick = { confirmDelete = false })
                DialogButton(
                    text = stringResource(Res.string.lucid_tv_delete),
                    style = DialogButtonStyle.Destructive,
                    onClick = {
                        VirtualChannelRepository.delete(existing.id)
                        confirmDelete = false
                        onBack()
                    },
                )
            }
        }
    }
}

private enum class PickerMode { SOURCE, CATALOG, SERIES, PLAYLIST }

@Composable
private fun EditorSection(title: String, content: @Composable () -> Unit) {
    val tokens = MaterialTheme.nuvio
    Column(verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = tokens.colors.textMuted,
            fontWeight = FontWeight.SemiBold,
        )
        content()
    }
}

private fun sourceSummary(source: VirtualSource?): String = when (source) {
    null -> "Choose a source"
    is VirtualSource.AddonCatalog -> listOf(source.addonName, source.catalogName)
        .filter { it.isNotBlank() }
        .joinToString(" · ")
        .ifBlank { source.catalogId } + (source.genre?.let { " · $it" } ?: "")
    is VirtualSource.AddonItem -> "Series · ${source.itemId}"
    is VirtualSource.ManualItems -> "Your list · ${source.items.size} items"
    is VirtualSource.PlaylistGroup -> "Playlist group"
    is VirtualSource.Rules -> "Smart channel"
}

@Composable
private fun SourcePicker(
    mode: PickerMode,
    onDismiss: () -> Unit,
    onCatalog: (VirtualSource.AddonCatalog, MetaPreview?) -> Unit,
    onPlaylistGroup: (sourceId: String, groupId: String?, title: String) -> Unit,
) {
    var step by remember(mode) { mutableStateOf(mode) }
    val addonState by AddonRepository.uiState.collectAsStateWithLifecycle()
    val addons = addonState.addons.filter { it.enabled && it.manifest != null }
    var selectedAddon by remember { mutableStateOf<ManagedAddon?>(null) }
    var selectedCatalog by remember { mutableStateOf<AddonCatalog?>(null) }
    var selectedGenre by remember { mutableStateOf<String?>(null) }
    var search by remember { mutableStateOf("") }
    var items by remember { mutableStateOf<List<MetaPreview>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }
    val listState by LiveTvViewState.list.collectAsStateWithLifecycle()

    if (step == PickerMode.CATALOG || step == PickerMode.SERIES) {
        val catalog = selectedCatalog
        LaunchedEffect(catalog, step) {
            if (catalog == null || step != PickerMode.SERIES) return@LaunchedEffect
            loading = true
            loadError = null
            items = runCatching {
                fetchCatalogPage(
                    manifestUrl = selectedAddon?.manifestUrl.orEmpty(),
                    type = catalog.type,
                    catalogId = catalog.id,
                    skip = 0,
                    maxItems = 150,
                ).items
            }.getOrElse { error ->
                loadError = error.message ?: "Could not load this catalog"
                emptyList()
            }
            loading = false
        }
    }

    DialogSurface(
        onDismissRequest = onDismiss,
        title = when (step) {
            PickerMode.SOURCE -> stringResource(Res.string.lucid_tv_source)
            PickerMode.CATALOG, PickerMode.SERIES -> selectedAddon?.displayTitle ?: stringResource(Res.string.lucid_tv_select_catalog)
            PickerMode.PLAYLIST -> stringResource(Res.string.lucid_tv_select_group)
        },
    ) {
        when (step) {
            PickerMode.SOURCE -> {
                SourceOption(
                    title = stringResource(Res.string.lucid_tv_source_catalog),
                    description = stringResource(Res.string.lucid_tv_source_catalog_description),
                ) { step = PickerMode.CATALOG }
                SourceOption(
                    title = stringResource(Res.string.lucid_tv_source_series),
                    description = stringResource(Res.string.lucid_tv_source_series_description),
                ) { step = PickerMode.SERIES }
                SourceOption(
                    title = stringResource(Res.string.lucid_tv_source_playlist),
                    description = stringResource(Res.string.lucid_tv_source_playlist_description),
                ) { step = PickerMode.PLAYLIST }
            }
            PickerMode.CATALOG, PickerMode.SERIES -> {
                val addon = selectedAddon
                val catalog = selectedCatalog
                when {
                    addon == null -> PickerList(
                        emptyText = stringResource(Res.string.lucid_tv_catalog_empty),
                        rows = addons.map { addonItem ->
                            PickerRow(
                                title = addonItem.displayTitle,
                                subtitle = addonItem.manifest?.catalogs?.size?.let { "$it catalogs" }.orEmpty(),
                                poster = addonItem.manifest?.logoUrl,
                            ) {
                                selectedAddon = addonItem
                                selectedCatalog = null
                                selectedGenre = null
                            }
                        },
                    )
                    catalog == null -> {
                        val catalogs = addon.manifest?.catalogs.orEmpty()
                            .filter { step != PickerMode.SERIES || it.type == "series" }
                        PickerList(
                            emptyText = stringResource(Res.string.lucid_tv_catalog_empty),
                            rows = catalogs.map { entry ->
                                PickerRow(
                                    title = entry.name,
                                    subtitle = entry.type,
                                ) {
                                    selectedCatalog = entry
                                    selectedGenre = null
                                }
                            },
                        )
                    }
                    step == PickerMode.CATALOG -> {
                        val genres = catalog.extra.firstOrNull { it.name == "genre" }?.options.orEmpty()
                        if (genres.isEmpty()) {
                            LaunchedEffect(catalog) {
                                onCatalog(
                                    VirtualSource.AddonCatalog(
                                        manifestUrl = addon.manifestUrl,
                                        addonId = addon.manifest?.id.orEmpty(),
                                        addonName = addon.displayTitle,
                                        catalogId = catalog.id,
                                        catalogName = catalog.name,
                                        contentType = catalog.type,
                                        genre = null,
                                    ),
                                    null,
                                )
                            }
                            Box(Modifier.fillMaxWidth().padding(NuvioTokens.Space.s16), contentAlignment = Alignment.Center) {
                                Text(stringResource(Res.string.lucid_tv_pool_loading), color = MaterialTheme.nuvio.colors.textMuted)
                            }
                        } else {
                            Text(
                                text = stringResource(Res.string.lucid_tv_genre_any),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.nuvio.colors.textMuted,
                            )
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s6),
                            ) {
                                Chip(stringResource(Res.string.lucid_tv_genre_any), selectedGenre == null, { selectedGenre = null })
                                genres.forEach { genre ->
                                    Chip(genre, selectedGenre == genre, { selectedGenre = genre })
                                }
                            }
                            DialogButtons {
                                DialogButton(text = stringResource(Res.string.action_cancel), onClick = onDismiss)
                                DialogButton(
                                    text = stringResource(Res.string.lucid_tv_select_catalog),
                                    style = DialogButtonStyle.Primary,
                                    onClick = {
                                        onCatalog(
                                            VirtualSource.AddonCatalog(
                                                manifestUrl = addon.manifestUrl,
                                                addonId = addon.manifest?.id.orEmpty(),
                                                addonName = addon.displayTitle,
                                                catalogId = catalog.id,
                                                catalogName = catalog.name,
                                                contentType = catalog.type,
                                                genre = selectedGenre,
                                            ),
                                            null,
                                        )
                                    },
                                )
                            }
                        }
                    }
                    else -> {
                        Column(verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8)) {
                            NuvioInputField(
                                value = search,
                                onValueChange = { search = it },
                                placeholder = stringResource(Res.string.lucid_tv_search_items),
                            )
                            when {
                                loading -> Text(stringResource(Res.string.lucid_tv_pool_loading), color = MaterialTheme.nuvio.colors.textMuted)
                                loadError != null -> Text(loadError.orEmpty(), color = MaterialTheme.nuvio.colors.danger)
                                else -> PickerList(
                                    emptyText = stringResource(Res.string.lucid_tv_pool_empty),
                                    rows = items
                                        .filter { search.isBlank() || it.name.contains(search, ignoreCase = true) }
                                        .map { item ->
                                            PickerRow(
                                                title = item.name,
                                                subtitle = listOfNotNull(item.releaseInfo, item.type).joinToString(" · "),
                                                poster = item.poster,
                                            ) { onCatalog(catalogSourceOf(addon, catalog, null), item) }
                                        },
                                )
                            }
                        }
                    }
                }
            }
            PickerMode.PLAYLIST -> {
                val rows = listState.sections
                    .filter { it.group.sourceId != null && it.group.sourceId != VirtualChannels.SOURCE_ID && it.channels.isNotEmpty() }
                    .map { section ->
                        PickerRow(
                            title = section.group.title,
                            subtitle = "${section.channels.size} channels",
                        ) {
                            onPlaylistGroup(
                                section.group.sourceId.orEmpty(),
                                section.group.id,
                                section.group.title,
                            )
                        }
                    }
                PickerList(
                    emptyText = "No playlist groups yet. Add a playlist first.",
                    rows = rows,
                )
            }
        }
    }
}

private fun catalogSourceOf(addon: ManagedAddon, catalog: AddonCatalog, genre: String?) = VirtualSource.AddonCatalog(
    manifestUrl = addon.manifestUrl,
    addonId = addon.manifest?.id.orEmpty(),
    addonName = addon.displayTitle,
    catalogId = catalog.id,
    catalogName = catalog.name,
    contentType = catalog.type,
    genre = genre,
)

@Composable
private fun SourceOption(title: String, description: String, onClick: () -> Unit) {
    val tokens = MaterialTheme.nuvio
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = tokens.shapes.compactCard,
        color = tokens.colors.surface,
    ) {
        Column(modifier = Modifier.padding(NuvioTokens.Space.s12), verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s2)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = tokens.colors.textPrimary, fontWeight = FontWeight.SemiBold)
            Text(description, style = MaterialTheme.typography.bodySmall, color = tokens.colors.textMuted)
        }
    }
}

private data class PickerRow(
    val title: String,
    val subtitle: String,
    val poster: String? = null,
    val onClick: () -> Unit,
)

@Composable
private fun PickerList(emptyText: String, rows: List<PickerRow>) {
    val tokens = MaterialTheme.nuvio
    if (rows.isEmpty()) {
        Text(emptyText, style = MaterialTheme.typography.bodySmall, color = tokens.colors.textMuted)
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
        verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s6),
    ) {
        items(rows) { row ->
            Surface(
                modifier = Modifier.fillMaxWidth().clickable(onClick = row.onClick),
                shape = tokens.shapes.compactCard,
                color = tokens.colors.surface,
            ) {
                Row(
                    modifier = Modifier.padding(NuvioTokens.Space.s10),
                    horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s10),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (!row.poster.isNullOrBlank()) {
                        AsyncImage(
                            model = row.poster,
                            contentDescription = null,
                            modifier = Modifier.size(36.dp).background(tokens.colors.background, RoundedCornerShape(NuvioTokens.Radius.md)),
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(row.title, style = MaterialTheme.typography.bodyMedium, color = tokens.colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (row.subtitle.isNotBlank()) {
                            Text(row.subtitle, style = MaterialTheme.typography.labelSmall, color = tokens.colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Icon(Icons.Filled.Check, contentDescription = null, tint = tokens.colors.textMuted, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}
