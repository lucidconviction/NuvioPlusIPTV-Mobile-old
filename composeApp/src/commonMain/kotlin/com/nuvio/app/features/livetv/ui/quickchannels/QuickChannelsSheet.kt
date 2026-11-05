package com.nuvio.app.features.livetv.ui.quickchannels

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.nuvio.app.core.ui.NuvioModalBottomSheet
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.livetv.data.LiveTvRepository
import com.nuvio.app.features.livetv.model.LiveChannel
import androidx.compose.material3.rememberModalBottomSheetState

/**
 * Bottom-sheet UI that lists curated [QuickChannel] bundles, lets the user pick a category,
 * shows matching [LiveChannel]s, and plays them via [onPlayChannel].
 *
 * [QuickStreamChecker] probes matching channel URLs so the sheet can show a "✓ Live" /
 * "checking" / "Play" badge next to each row.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickChannelsSheet(
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit,
    onPlayChannel: (LiveChannel) -> Unit,
) {
    val tokens = MaterialTheme.nuvio.colors
    var selectedCategory by remember { mutableStateOf(QuickChannelList.getChannelCategories().first()) }
    val categories = QuickChannelList.getChannelCategories()
    var selectedQc by remember { mutableStateOf<QuickChannel?>(null) }
    var matches by remember { mutableStateOf<List<LiveChannel>>(emptyList()) }
    var aliveByUrl by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    val checked = remember(aliveByUrl) { aliveByUrl.values.count { it } }
    val unchecked = remember(aliveByUrl) { aliveByUrl.count { it.value == null } }
    val checking = unchecked > 0
    val liveChannels by LiveTvRepository.channels.collectAsStateWithLifecycle(initialValue = emptyList())

    // Custom region/channel management state
    var showAddRegionDialog by remember { mutableStateOf(false) }
    var showAddChannelDialog by remember { mutableStateOf(false) }
    var showEditRegionDialog by remember { mutableStateOf(false) }
    var showEditChannelDialog by remember { mutableStateOf(false) }
    var editingRegion by remember { mutableStateOf<QuickChannelList.QuickCategory?>(null) }
    var editingChannel by remember { mutableStateOf<QuickChannel?>(null) }

    val filteredQc = remember(selectedCategory) {
        QuickChannelList.getChannelsForCategory(selectedCategory)
    }

    LaunchedEffect(selectedQc) {
        if (selectedQc != null) {
            aliveByUrl = emptyMap()
            val q = selectedQc!!
            matches = liveChannels.filter { QuickChannelList.matches(q, it) }
            if (matches.isNotEmpty()) {
                val statuses = QuickStreamChecker.checkBatch(matches.map { it.url })
                aliveByUrl = statuses
            }
        } else {
            matches = emptyList()
            aliveByUrl = emptyMap()
        }
    }

    NuvioModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 700.dp)
                .padding(horizontal = NuvioTokens.Space.s12),
        ) {
            // Title row
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = NuvioTokens.Space.s10),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Quick Channels",
                    style = MaterialTheme.typography.titleLarge,
                    color = tokens.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                // Add custom region button
                IconButton(onClick = { showAddRegionDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Add Region", tint = tokens.accent)
                }
                // Add custom channel button
                IconButton(onClick = { showAddChannelDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Add Channel", tint = tokens.accent)
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = tokens.textSecondary)
                }
            }

            // Category row
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s6),
                contentPadding = PaddingValues(vertical = NuvioTokens.Space.s4),
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
                                IconButton(onClick = { QuickChannelList.removeCustomCategory(catId); if (selectedCategory == catId) selectedCategory = "all" }, modifier = Modifier.size(18.dp)) {
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

            // Quick-channel chip row
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s6),
                contentPadding = PaddingValues(vertical = NuvioTokens.Space.s6),
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
                                matches = emptyList()
                                aliveByUrl = emptyMap()
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
                                    IconButton(onClick = { QuickChannelList.removeCustomChannel(qc.displayName); if (selectedQc?.displayName == qc.displayName) selectedQc = null }, modifier = Modifier.size(22.dp)) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = tokens.textMuted, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Match list
            if (selectedQc != null && matches.isNotEmpty()) {
                Spacer(Modifier.height(NuvioTokens.Space.s6))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        selectedQc!!.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        color = tokens.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    if (checking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            color = tokens.accent,
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    // Show only working streams count
                    Text(
                        "$checked working",
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.textMuted,
                    )
                }
                Spacer(Modifier.height(NuvioTokens.Space.s8))
                // Sort: working (true) first, then null (checking), then false (not working - hidden)
                val sortedMatches = matches.sortedWith(
                    compareByDescending<LiveChannel> { ch -> aliveByUrl[ch.url] == true }
                        .thenByDescending { ch -> aliveByUrl[ch.url] == null }
                ).filter { ch -> aliveByUrl[ch.url] != false } // Hide non-working streams
                
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s6),
                    modifier = Modifier.heightIn(max = 380.dp),
                ) {
                    items(sortedMatches, key = { it.key }) { ch ->
                        val isAlive = aliveByUrl[ch.url]
                        QuickChannelRow(
                            channel = ch,
                            isAlive = isAlive,
                            onTap = {
                                onPlayChannel(ch)
                                onDismiss()
                            },
                        )
                    }
                }
            } else if (selectedQc != null && matches.isEmpty() && aliveByUrl.isNotEmpty()) {
                Spacer(Modifier.height(NuvioTokens.Space.s24))
                Text(
                    "No matching channels found for ${selectedQc!!.displayName}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = tokens.textMuted,
                    modifier = Modifier.padding(vertical = NuvioTokens.Space.s8),
                )
            } else if (selectedQc == null) {
                Spacer(Modifier.height(NuvioTokens.Space.s24))
                Text(
                    "Tap a channel bundle above to see matching channels",
                    style = MaterialTheme.typography.bodyMedium,
                    color = tokens.textMuted,
                    modifier = Modifier.padding(vertical = NuvioTokens.Space.s8),
                )
            }
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
                showAddRegionDialog = false
            },
        )
    }

    // Add Custom Channel Dialog
    if (showAddChannelDialog) {
        AddChannelDialog(
            onDismiss = { showAddChannelDialog = false },
            onAdd = { name, aliases, regions, tags ->
                val parsedRegions = regions.split(",").map { it.trim().uppercase() }.filter { it.isNotBlank() }
                val channel = QuickChannel(
                    displayName = name.trim(),
                    aliases = aliases.split(",").map { it.trim() }.filter { it.isNotBlank() },
                    regions = if (parsedRegions.isEmpty()) listOf("CUSTOM") else parsedRegions,
                    tags = tags.split(",").map { it.trim().lowercase() }.filter { it.isNotBlank() }
                )
                QuickChannelList.addCustomChannel(channel)
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
                val parsedRegions = regions.split(",").map { it.trim().uppercase() }.filter { it.isNotBlank() }
                val updated = QuickChannel(
                    displayName = name.trim(),
                    aliases = aliases.split(",").map { it.trim() }.filter { it.isNotBlank() },
                    regions = if (parsedRegions.isEmpty()) listOf("CUSTOM") else parsedRegions,
                    tags = tags.split(",").map { it.trim().lowercase() }.filter { it.isNotBlank() }
                )
                QuickChannelList.updateCustomChannel(channel.displayName, updated)
                showEditChannelDialog = false
                editingChannel = null
            },
        )
    }
}

@Composable
private fun AddRegionDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String, String, String) -> Unit,
) {
    RegionDialogImpl(title = "Add Custom Region", initialId = "", initialTitle = "", initialDescription = "", initialTags = "", confirmText = "Add", onDismiss = onDismiss, onSubmit = onAdd)
}

@Composable
private fun EditRegionDialog(
    region: QuickChannelList.QuickCategory,
    onDismiss: () -> Unit,
    onEdit: (String, String, String, String) -> Unit,
) {
    RegionDialogImpl(title = "Edit Region", initialId = region.id, initialTitle = region.title, initialDescription = region.description, initialTags = region.tags.joinToString(","), confirmText = "Save", onDismiss = onDismiss, onSubmit = onEdit)
}

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

@Composable
private fun AddChannelDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String, String, String) -> Unit,
) {
    ChannelDialogImpl(title = "Add Custom Quick Channel", initialName = "", initialAliases = "", initialRegions = "", initialTags = "", confirmText = "Add", onDismiss = onDismiss, onSubmit = onAdd)
}

@Composable
private fun EditChannelDialog(
    channel: QuickChannel,
    onDismiss: () -> Unit,
    onEdit: (String, String, String, String) -> Unit,
) {
    ChannelDialogImpl(title = "Edit Quick Channel", initialName = channel.displayName, initialAliases = channel.aliases.joinToString(","), initialRegions = channel.regions.joinToString(","), initialTags = channel.tags.joinToString(","), confirmText = "Save", onDismiss = onDismiss, onSubmit = onEdit)
}

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

    // Available regions for dropdown
    val availableRegions = remember {
        listOf(
            "CUSTOM", "US", "UK", "CA", "BAY-AREA", "EU", "AU", "IN", "PK", "LA",
            "PREMIUM", "SPORTS", "NEWS", "ENTERTAINMENT", "KIDS", "BROADCAST", "REGIONAL"
        )
    }
    var showRegionDropdown by remember { mutableStateOf(false) }

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
                // Region dropdown
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Regions", style = MaterialTheme.typography.labelMedium, color = tokens.textMuted)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(tokens.surfaceCard)
                            .clip(RoundedCornerShape(NuvioTokens.Radius.md))
                            .clickable { showRegionDropdown = !showRegionDropdown },
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(
                            text = if (channelRegions.isBlank()) "Select regions…" else channelRegions,
                            color = if (channelRegions.isBlank()) tokens.textMuted else tokens.textPrimary,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(NuvioTokens.Space.s12),
                        )
                        Icon(
                            imageVector = Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = tokens.textMuted,
                            modifier = Modifier
                                .padding(NuvioTokens.Space.s12)
                                .align(Alignment.CenterEnd),
                        )
                    }
                    DropdownMenu(
                        expanded = showRegionDropdown,
                        onDismissRequest = { showRegionDropdown = false },
                    ) {
                        availableRegions.forEach { region ->
                            DropdownMenuItem(
                                onClick = {
                                    val currentRegions = channelRegions.split(",").map { it.trim().uppercase() }.filter { it.isNotBlank() }.toMutableSet()
                                    if (currentRegions.contains(region)) {
                                        currentRegions.remove(region)
                                    } else {
                                        currentRegions.add(region)
                                    }
                                    channelRegions = currentRegions.joinToString(", ")
                                    showRegionDropdown = false
                                },
                                text = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text(region, style = MaterialTheme.typography.bodyMedium, color = tokens.textPrimary)
                                        if (channelRegions.split(",").map { it.trim().uppercase() }.contains(region)) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = tokens.accent,
                                            )
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
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

@Composable
private fun QuickChannelRow(
    channel: LiveChannel,
    isAlive: Boolean?,
    onTap: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio.colors
    val aliveText = when (isAlive) {
        true -> "Live"
        null -> "checking"
        false -> "Play"
    }
    val aliveColor = when (isAlive) {
        true -> Color(0xFF4CAF50)
        null -> tokens.textMuted
        false -> tokens.accent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(NuvioTokens.Radius.md))
            .background(tokens.surfaceCard)
            .clickable(onClick = onTap)
            .padding(horizontal = NuvioTokens.Space.s10, vertical = NuvioTokens.Space.s8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!channel.thumbnail.isNullOrBlank()) {
            AsyncImage(
                model = channel.thumbnail,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(NuvioTokens.Radius.xs))
                    .background(tokens.background),
            )
            Spacer(Modifier.width(NuvioTokens.Space.s8))
        } else if (!channel.logo.isNullOrBlank()) {
            AsyncImage(
                model = channel.logo,
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
        Spacer(Modifier.width(NuvioTokens.Space.s8))
        Text(
            aliveText,
            style = MaterialTheme.typography.labelSmall,
            color = aliveColor,
            fontWeight = FontWeight.Bold,
        )
    }
}