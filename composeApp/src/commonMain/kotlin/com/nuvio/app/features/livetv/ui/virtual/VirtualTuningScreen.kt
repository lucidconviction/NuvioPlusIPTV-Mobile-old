package com.nuvio.app.features.livetv.ui.virtual

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.nuvio.app.core.ui.DialogButton
import com.nuvio.app.core.ui.DialogButtonStyle
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.livetv.model.LiveChannel
import com.nuvio.app.features.livetv.player.VirtualChannelPlayback
import com.nuvio.app.features.livetv.virtual.VirtualChannels

/**
 * Shown the instant a Lucid TV channel is picked, so the guide disappears and the viewer sees
 * the channel tuning instead of waiting on the list. The first source is resolved here; when it
 * is ready the player route replaces this screen.
 */
@Composable
fun VirtualTuningScreen(
    channelKey: String,
    profileId: Int,
    channelName: String,
    channelLogo: String?,
    onBack: () -> Unit,
    onReady: (launchId: Long, title: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = MaterialTheme.nuvio
    val channel = remember(channelKey, channelName, channelLogo) {
        LiveChannel(
            key = channelKey,
            sourceId = VirtualChannels.SOURCE_ID,
            sourceName = VirtualChannels.SOURCE_NAME,
            name = channelName,
            tvgId = null,
            tvgName = null,
            logo = channelLogo,
            groupId = VirtualChannels.groupId(VirtualChannels.DEFAULT_GROUP),
            group = VirtualChannels.DEFAULT_GROUP,
            number = 0,
            url = channelKey,
        )
    }
    var attempt by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(attempt) {
        message = null
        if (attempt > 0) VirtualChannelPlayback.invalidate(channelKey)
        val prepared = VirtualChannelPlayback.prepare(channel, profileId)
        if (prepared.launchId != null) {
            onReady(prepared.launchId, channelName)
        } else {
            message = prepared.message ?: "This Lucid TV channel is offline right now."
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(tokens.colors.background)
            .statusBarsPadding(),
    ) {
        IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
            Icon(Icons.Filled.Close, contentDescription = "Close", tint = tokens.colors.textPrimary)
        }
        Column(
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (!channelLogo.isNullOrBlank()) {
                AsyncImage(
                    model = channelLogo,
                    contentDescription = null,
                    modifier = Modifier.size(96.dp).clip(CircleShape),
                )
            }
            Text(
                text = channelName,
                style = MaterialTheme.typography.headlineSmall,
                color = tokens.colors.textPrimary,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            if (message == null) {
                CircularProgressIndicator(color = tokens.colors.accent)
                Text(
                    text = "Tuning…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = tokens.colors.textMuted,
                )
                Text(
                    text = "Finding the best stream. Direct HTTP is tried first.",
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.colors.textMuted,
                    textAlign = TextAlign.Center,
                )
            } else {
                Text(
                    text = message.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = tokens.colors.textPrimary,
                    textAlign = TextAlign.Center,
                )
                DialogButton(
                    text = "Try again",
                    style = DialogButtonStyle.Primary,
                    onClick = { attempt++ },
                )
                DialogButton(
                    text = "Back",
                    onClick = onBack,
                )
            }
        }
    }
}
