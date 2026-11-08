package com.nuvio.app.features.livetv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.NuvioModalBottomSheet
import com.nuvio.app.core.ui.NuvioPrimaryButton
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.livetv.data.LiveTvPreferences
import com.nuvio.app.features.livetv.data.LiveTvRepository
import com.nuvio.app.features.livetv.data.StreamValidationRepository
import com.nuvio.app.features.livetv.model.LiveTvClock

/**
 * The Stream validation panel behind Live TV's overflow menu.
 *
 * It checks every playlist stream, leaves the ones that don't answer out of every Live TV list,
 * and keeps the hidden links so a later run can put them back as soon as they work again.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StreamValidationSheet(onDismiss: () -> Unit) {
    val tokens = MaterialTheme.nuvio.colors
    val state by StreamValidationRepository.state.collectAsStateWithLifecycle()
    val hiddenKeys by LiveTvPreferences.validationFailed.collectAsStateWithLifecycle()
    val channels by LiveTvRepository.channels.collectAsStateWithLifecycle()
    val hiddenChannels = remember(channels, hiddenKeys) { channels.filter { it.key in hiddenKeys } }

    NuvioModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 720.dp)
                .padding(horizontal = NuvioTokens.Space.s16),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = NuvioTokens.Space.s10),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Stream validation",
                    style = MaterialTheme.typography.titleLarge,
                    color = tokens.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = tokens.textMuted)
                }
            }

            Text(
                "Checks every channel's stream. Streams that don't answer are hidden from Live TV " +
                    "until a later check finds them working again.",
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.textMuted,
            )

            Spacer(Modifier.height(NuvioTokens.Space.s16))

            if (state.running) {
                val fraction = if (state.total > 0) state.checked.toFloat() / state.total else 0f
                LinearProgressIndicator(
                    progress = fraction.coerceIn(0f, 1f),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(NuvioTokens.Space.s12))
                Text(
                    if (state.recheckHiddenOnly) {
                        "Re-checking hidden streams ${state.checked} of ${state.total}…"
                    } else {
                        "Checking streams ${state.checked} of ${state.total}…"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = tokens.textPrimary,
                )
                Spacer(Modifier.height(NuvioTokens.Space.s4))
                Text(
                    "${state.working} working · ${state.failed} hidden so far",
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.textMuted,
                )
                Spacer(Modifier.height(NuvioTokens.Space.s12))
                TextButton(
                    onClick = { StreamValidationRepository.cancel() },
                    enabled = !state.cancelling,
                ) {
                    Text(if (state.cancelling) "Stopping…" else "Stop")
                }
            } else {
                if (state.lastRunMs > 0L) {
                    Text(
                        "Last check ${relativeTime(state.lastRunMs)} · ${state.working} working · " +
                            "${state.failed} hidden",
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.textMuted,
                    )
                    Spacer(Modifier.height(NuvioTokens.Space.s12))
                }
                state.message?.let { message ->
                    Text(message, style = MaterialTheme.typography.bodyMedium, color = tokens.textMuted)
                    Spacer(Modifier.height(NuvioTokens.Space.s12))
                }
                NuvioPrimaryButton(
                    text = if (state.failed > 0) "Validate all streams again" else "Validate all streams",
                    enabled = channels.isNotEmpty(),
                    onClick = { StreamValidationRepository.start(recheckHiddenOnly = false) },
                )
                if (state.failed > 0) {
                    Spacer(Modifier.height(NuvioTokens.Space.s8))
                    TextButton(
                        onClick = { StreamValidationRepository.start(recheckHiddenOnly = true) },
                        enabled = channels.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Re-check ${state.failed} hidden stream${if (state.failed == 1) "" else "s"}")
                    }
                }
            }

            if (hiddenChannels.isNotEmpty()) {
                Spacer(Modifier.height(NuvioTokens.Space.s8))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Hidden streams (${hiddenChannels.size})",
                        style = MaterialTheme.typography.titleSmall,
                        color = tokens.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { StreamValidationRepository.showAll() }) {
                        Text("Show all")
                    }
                }
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp)) {
                    items(hiddenChannels, key = { it.key }) { channel ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = NuvioTokens.Space.s4),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    channel.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = tokens.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (channel.group.isNotBlank()) {
                                    Text(
                                        channel.group,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = tokens.textMuted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            TextButton(onClick = { StreamValidationRepository.show(channel.key) }) {
                                Text("Show")
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(NuvioTokens.Space.s16))
        }
    }
}

private fun relativeTime(atMs: Long): String {
    val elapsed = (LiveTvClock.nowMs() - atMs).coerceAtLeast(0L)
    val minutes = elapsed / 60_000L
    return when {
        minutes < 1L -> "just now"
        minutes < 60L -> "$minutes min ago"
        minutes < 24L * 60L -> "${minutes / 60L} h ago"
        else -> "${minutes / (24L * 60L)} d ago"
    }
}
