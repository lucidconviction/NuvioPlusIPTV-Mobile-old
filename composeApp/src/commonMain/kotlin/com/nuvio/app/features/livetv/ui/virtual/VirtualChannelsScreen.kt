package com.nuvio.app.features.livetv.ui.virtual

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.nuvio.app.core.ui.DialogButton
import com.nuvio.app.core.ui.DialogButtonStyle
import com.nuvio.app.core.ui.DialogButtons
import com.nuvio.app.core.ui.DialogSurface
import com.nuvio.app.core.ui.Chip
import com.nuvio.app.core.ui.Menu
import com.nuvio.app.core.ui.MenuItem
import com.nuvio.app.core.ui.NuvioScreenHeader
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.core.ui.nuvioSafeBottomPadding
import com.nuvio.app.features.livetv.model.LiveTvClock
import com.nuvio.app.features.livetv.ui.formatClock
import com.nuvio.app.features.livetv.ui.liveTvSideInsets
import com.nuvio.app.features.livetv.virtual.VirtualChannelRepository
import com.nuvio.app.features.livetv.virtual.VirtualChannelSpec
import com.nuvio.app.features.livetv.virtual.VirtualChannels
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.action_cancel
import nuvio.composeapp.generated.resources.lucid_tv_add
import nuvio.composeapp.generated.resources.lucid_tv_delete
import nuvio.composeapp.generated.resources.lucid_tv_delete_confirm
import nuvio.composeapp.generated.resources.lucid_tv_description
import nuvio.composeapp.generated.resources.lucid_tv_empty
import nuvio.composeapp.generated.resources.lucid_tv_export
import nuvio.composeapp.generated.resources.lucid_tv_export_hint
import nuvio.composeapp.generated.resources.lucid_tv_import
import nuvio.composeapp.generated.resources.lucid_tv_import_done
import nuvio.composeapp.generated.resources.lucid_tv_import_hint
import nuvio.composeapp.generated.resources.lucid_tv_import_invalid
import nuvio.composeapp.generated.resources.lucid_tv_pool_count
import nuvio.composeapp.generated.resources.lucid_tv_pool_empty
import nuvio.composeapp.generated.resources.lucid_tv_pool_loading
import nuvio.composeapp.generated.resources.lucid_tv_refresh_all
import nuvio.composeapp.generated.resources.lucid_tv_title
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Channel Studio's list: every Lucid TV channel with its pool state, what is on now and a way to
 * add, edit, duplicate, delete, import and export.
 */
@Composable
fun VirtualChannelsScreen(
    onBack: () -> Unit,
    onAddChannel: () -> Unit,
    onEditChannel: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = MaterialTheme.nuvio
    LaunchedEffect(Unit) { VirtualChannelRepository.ensureLoaded() }
    val specs by VirtualChannelRepository.specs.collectAsStateWithLifecycle()
    val pools by VirtualChannelRepository.pools.collectAsStateWithLifecycle()
    val refreshing by VirtualChannelRepository.refreshing.collectAsStateWithLifecycle()
    val errors by VirtualChannelRepository.errors.collectAsStateWithLifecycle()
    val programs by VirtualChannelRepository.programsWindow.collectAsStateWithLifecycle()
    val playbackSettings by VirtualChannelRepository.settings.collectAsStateWithLifecycle()
    var menu by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    var showPlaybackSettings by remember { mutableStateOf(false) }
    var exportText by remember { mutableStateOf("") }
    var importText by remember { mutableStateOf("") }
    var importMessage by remember { mutableStateOf<String?>(null) }
    var deleteTarget by remember { mutableStateOf<VirtualChannelSpec?>(null) }
    val importInvalid = stringResource(Res.string.lucid_tv_import_invalid)
    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(tokens.colors.background)
            .statusBarsPadding()
            .liveTvSideInsets(),
    ) {
        NuvioScreenHeader(
            title = stringResource(Res.string.lucid_tv_title),
            includeStatusBarPadding = false,
            onBack = onBack,
            modifier = Modifier.padding(horizontal = NuvioTokens.Space.s8),
            actions = {
                IconButton(onClick = onAddChannel) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(Res.string.lucid_tv_add), tint = tokens.colors.textPrimary)
                }
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = null, tint = tokens.colors.textPrimary)
                }
                Menu(expanded = menu, onDismissRequest = { menu = false }) {
                    MenuItem(text = stringResource(Res.string.lucid_tv_refresh_all), onClick = {
                        menu = false
                        VirtualChannelRepository.refreshAll()
                    })
                    MenuItem(text = stringResource(Res.string.lucid_tv_import), onClick = {
                        menu = false
                        importText = ""
                        importMessage = null
                        importing = true
                    })
                    MenuItem(text = stringResource(Res.string.lucid_tv_export), onClick = {
                        menu = false
                        exportText = VirtualChannelRepository.exportPack()
                        exporting = true
                    })
                    MenuItem(text = "Playback settings", onClick = {
                        menu = false
                        showPlaybackSettings = true
                    })
                }
            },
        )
        Text(
            text = stringResource(Res.string.lucid_tv_description),
            style = MaterialTheme.typography.bodySmall,
            color = tokens.colors.textMuted,
            modifier = Modifier.padding(horizontal = NuvioTokens.Space.s16, vertical = NuvioTokens.Space.s4),
        )
        if (specs.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(NuvioTokens.Space.s24),
                verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8),
            ) {
                Text(
                    text = stringResource(Res.string.lucid_tv_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = tokens.colors.textMuted,
                )
                DialogButton(
                    text = stringResource(Res.string.lucid_tv_add),
                    onClick = onAddChannel,
                    style = DialogButtonStyle.Primary,
                )
            }
        } else {
            val now by produceState(LiveTvClock.nowMs(), specs, programs) {
                while (true) {
                    value = LiveTvClock.nowMs()
                    delay(30_000L)
                }
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = NuvioTokens.Space.s12,
                    end = NuvioTokens.Space.s12,
                    top = NuvioTokens.Space.s8,
                    bottom = nuvioSafeBottomPadding(NuvioTokens.Space.s24),
                ),
                verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8),
            ) {
                items(specs, key = { it.id }) { spec ->
                    val channelPrograms = programs[spec.channelKey].orEmpty()
                    val nowProgram = channelPrograms.firstOrNull { now >= it.startMs && now < it.stopMs }
                    val nextProgram = channelPrograms.firstOrNull { it.startMs >= (nowProgram?.stopMs ?: now) }
                    VirtualChannelCard(
                        spec = spec,
                        poster = pools[spec.id]?.items?.firstOrNull()?.poster,
                        poolCount = pools[spec.id]?.items?.size ?: 0,
                        loading = spec.id in refreshing,
                        error = errors[spec.id],
                        nowText = nowProgram?.let { formatClock(it.startMs) + " · " + it.title },
                        nextText = nextProgram?.let { formatClock(it.startMs) + " · " + it.title },
                        onClick = { onEditChannel(spec.id) },
                        onDelete = { deleteTarget = spec },
                    )
                }
            }
        }
    }

    if (importing) {
        DialogSurface(
            onDismissRequest = { importing = false },
            title = stringResource(Res.string.lucid_tv_import),
            message = stringResource(Res.string.lucid_tv_import_hint),
        ) {
            OutlinedTextField(
                value = importText,
                onValueChange = { importText = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 6,
                maxLines = 12,
                textStyle = MaterialTheme.typography.bodySmall,
            )
            importMessage?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = tokens.colors.textMuted)
            }
            DialogButtons {
                DialogButton(text = stringResource(Res.string.action_cancel), onClick = { importing = false })
                DialogButton(
                    text = stringResource(Res.string.lucid_tv_import),
                    style = DialogButtonStyle.Primary,
                    enabled = importText.isNotBlank(),
                    onClick = {
                        val added = runCatching { VirtualChannelRepository.importPack(importText) }.getOrNull()
                        if (added == null || added == 0) {
                            importMessage = importInvalid
                        } else {
                            importing = false
                            scope.launch { importMessage = getString(Res.string.lucid_tv_import_done, added) }
                        }
                    },
                )
            }
        }
    }

    if (exporting) {
        DialogSurface(
            onDismissRequest = { exporting = false },
            title = stringResource(Res.string.lucid_tv_export),
            message = stringResource(Res.string.lucid_tv_export_hint),
        ) {
            SelectionContainer {
                Text(
                    text = exportText,
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.colors.textMuted,
                    maxLines = 12,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            DialogButtons {
                DialogButton(text = stringResource(Res.string.action_cancel), onClick = { exporting = false })
            }
        }
    }

    deleteTarget?.let { spec ->
        DialogSurface(
            onDismissRequest = { deleteTarget = null },
            title = stringResource(Res.string.lucid_tv_delete_confirm),
            message = spec.name,
        ) {
            DialogButtons {
                DialogButton(text = stringResource(Res.string.action_cancel), onClick = { deleteTarget = null })
                DialogButton(
                    text = stringResource(Res.string.lucid_tv_delete),
                    style = DialogButtonStyle.Destructive,
                    onClick = {
                        VirtualChannelRepository.delete(spec.id)
                        deleteTarget = null
                    },
                )
            }
        }
    }

    if (showPlaybackSettings) {
        DialogSurface(
            onDismissRequest = { showPlaybackSettings = false },
            title = "Lucid TV playback",
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable {
                    VirtualChannelRepository.updateSettings(
                        playbackSettings.copy(allowDebrid = !playbackSettings.allowDebrid)
                    )
                },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s12),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Use debrid when needed",
                        style = MaterialTheme.typography.bodyMedium,
                        color = tokens.colors.textPrimary,
                    )
                    Text(
                        text = "Direct HTTP streams are always tried first. Debrid/torrent sources are used only when no direct stream is available.",
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.colors.textMuted,
                    )
                }
                Switch(
                    checked = playbackSettings.allowDebrid,
                    onCheckedChange = { on ->
                        VirtualChannelRepository.updateSettings(playbackSettings.copy(allowDebrid = on))
                    },
                )
            }
            Text(
                text = "Prefetch next items",
                style = MaterialTheme.typography.labelMedium,
                color = tokens.colors.textMuted,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s6),
                modifier = Modifier.fillMaxWidth(),
            ) {
                listOf(0, 1, 2, 3, 4).forEach { count ->
                    Chip(
                        label = if (count == 0) "Off" else count.toString(),
                        selected = playbackSettings.prefetchNext == count,
                        onClick = {
                            VirtualChannelRepository.updateSettings(playbackSettings.copy(prefetchNext = count))
                        },
                    )
                }
            }
            Text(
                text = "The next items are resolved in the background so channel changes and slot rollovers start faster.",
                style = MaterialTheme.typography.bodySmall,
                color = tokens.colors.textMuted,
            )
            DialogButtons {
                DialogButton(
                    text = "Done",
                    style = DialogButtonStyle.Primary,
                    onClick = { showPlaybackSettings = false },
                )
            }
        }
    }
}

@Composable
private fun VirtualChannelCard(
    spec: VirtualChannelSpec,
    poster: String?,
    poolCount: Int,
    loading: Boolean,
    error: String?,
    nowText: String?,
    nextText: String?,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = tokens.shapes.card,
        color = tokens.colors.surface,
    ) {
        Row(
            modifier = Modifier.padding(tokens.spacing.cardPadding),
            horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(tokens.colors.accent.copy(alpha = 0.14f), RoundedCornerShape(NuvioTokens.Radius.lg)),
                contentAlignment = Alignment.Center,
            ) {
                val logo = spec.logo ?: poster
                if (!logo.isNullOrBlank()) {
                    AsyncImage(
                        model = logo,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().padding(NuvioTokens.Space.s4),
                    )
                } else {
                    Text(
                        text = spec.name.take(1).uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        color = tokens.colors.accent,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s2)) {
                Row(horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = spec.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = tokens.colors.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (!spec.enabled) {
                        Text(
                            text = "OFF",
                            style = MaterialTheme.typography.labelSmall,
                            color = tokens.colors.textMuted,
                        )
                    }
                }
                Text(
                    text = "${spec.group}  ·  ${spec.number ?: "—"}  ·  " + spec.kind.name.replace('_', ' '),
                    style = MaterialTheme.typography.labelMedium,
                    color = tokens.colors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                when {
                    error != null -> Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.colors.danger,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    loading && poolCount == 0 -> Text(
                        text = stringResource(Res.string.lucid_tv_pool_loading),
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.colors.textMuted,
                    )
                    poolCount == 0 -> Text(
                        text = stringResource(Res.string.lucid_tv_pool_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.colors.textMuted,
                    )
                    else -> Text(
                        text = stringResource(Res.string.lucid_tv_pool_count, poolCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.colors.textMuted,
                    )
                }
                nowText?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                nextText?.let {
                    Text(
                        text = "Next  $it",
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.colors.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = null, tint = tokens.colors.textMuted)
            }
        }
    }
}
