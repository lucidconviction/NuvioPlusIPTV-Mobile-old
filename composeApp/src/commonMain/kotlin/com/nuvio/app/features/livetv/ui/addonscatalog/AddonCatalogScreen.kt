package com.nuvio.app.features.livetv.ui.addonscatalog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.addons.AddonRepository
import com.nuvio.app.features.addons.AddonCatalog
import com.nuvio.app.features.addons.ManagedAddon
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.addon_title

/**
 * Bottom-sheet browser for enabled addons and their catalogs.
 *
 * Shows each enabled addon with its manifest (when loaded). Tapping an addon row navigates via
 * [onOpenCatalog] with the addon's manifest URL and the selected catalog.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddonCatalogScreen(
    onDismiss: () -> Unit,
    onOpenCatalog: (manifestUrl: String, catalog: AddonCatalog) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = MaterialTheme.nuvio.colors
    val uiState by AddonRepository.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        AddonRepository.initialize()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = tokens.background,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.3f)),
            )
        },
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 600.dp)
                .padding(horizontal = NuvioTokens.Space.s12),
        ) {
            // Title row
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = NuvioTokens.Space.s10),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Add-ons",
                    style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                    color = tokens.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = tokens.textSecondary)
                }
            }

            // Addon list
            val addons = uiState.addons
            if (addons.isEmpty()) {
                Spacer(Modifier.height(NuvioTokens.Space.s24))
                Text(
                    "No add-ons configured. Go to Settings → Add-ons to install one.",
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    color = tokens.textMuted,
                    modifier = Modifier.padding(vertical = NuvioTokens.Space.s8),
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s6),
                    modifier = Modifier.heightIn(max = 480.dp),
                ) {
                    items(addons, key = { it.manifestUrl }) { addon ->
                        AddonItem(
                            addon = addon,
                            tokens = tokens,
                            onOpenCatalog = onOpenCatalog,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AddonItem(
    addon: ManagedAddon,
    tokens: com.nuvio.app.core.ui.NuvioColorTokens,
    onOpenCatalog: (manifestUrl: String, catalog: AddonCatalog) -> Unit,
) {
    val manifest = addon.manifest
    val isLoading = addon.isRefreshing && manifest == null

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(NuvioTokens.Radius.md))
            .background(tokens.surfaceCard)
            .padding(horizontal = NuvioTokens.Space.s10, vertical = NuvioTokens.Space.s8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (manifest != null) {
            if (!manifest.logoUrl.isNullOrBlank()) {
                AsyncImage(
                    model = manifest.logoUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(NuvioTokens.Radius.xs))
                        .background(tokens.background),
                )
                Spacer(Modifier.width(NuvioTokens.Space.s8))
            } else {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(NuvioTokens.Radius.xs))
                        .background(tokens.accent.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Extension,
                        contentDescription = null,
                        tint = tokens.accent,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.width(NuvioTokens.Space.s8))
            }
        } else if (isLoading) {
            androidx.compose.material3.CircularProgressIndicator(
                modifier = Modifier.size(28.dp),
                color = tokens.accent,
                strokeWidth = 2.dp,
            )
            Spacer(Modifier.width(NuvioTokens.Space.s8))
        } else {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(NuvioTokens.Radius.xs))
                    .background(tokens.accent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Extension,
                    contentDescription = null,
                    tint = tokens.textMuted,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(NuvioTokens.Space.s8))
        }

        Column(modifier = Modifier.weight(1f)) {
            val title = addon.displayTitle
            Text(
                title,
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                color = tokens.textPrimary,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (manifest != null) {
                val catalogCount = manifest.catalogs.size
                Text(
                    if (catalogCount > 0) "$catalogCount catalog${if (catalogCount > 1) "s" else ""}" else "No catalogs",
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                    color = tokens.textMuted,
                )
            } else if (addon.errorMessage != null) {
                Text(
                    addon.errorMessage,
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                    color = tokens.danger,
                )
            }
        }

        if (manifest != null && manifest.catalogs.isNotEmpty()) {
            Text(
                "›",
                style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                color = tokens.textMuted,
            )
        }
    }

    // Show catalogs as expandable sub-items
    manifest?.catalogs?.takeIf { it.isNotEmpty() }?.forEach { catalog ->
        CatalogItem(
            catalog = catalog,
            addonName = manifest.name,
            tokens = tokens,
            onTap = { onOpenCatalog(addon.manifestUrl, catalog) },
        )
    }
}

@Composable
private fun CatalogItem(
    catalog: AddonCatalog,
    addonName: String,
    tokens: com.nuvio.app.core.ui.NuvioColorTokens,
    onTap: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = NuvioTokens.Space.s48)
            .clip(RoundedCornerShape(NuvioTokens.Radius.xs))
            .background(tokens.surfaceCard.copy(alpha = 0.6f))
            .clickable(onClick = onTap)
            .padding(horizontal = NuvioTokens.Space.s10, vertical = NuvioTokens.Space.s6),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            catalog.name,
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            color = tokens.textPrimary,
            fontWeight = FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            "(${catalog.type})",
            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
            color = tokens.textMuted,
        )
    }
}
