package com.nuvio.app.features.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.NuvioTokens
import kotlinx.coroutines.delay

private const val MINI_PLAYER_CONTROLS_TIMEOUT_MS = 3_000L

/**
 * The in-app picture-in-picture window: a small always-on-top player shown while the user
 * searches and browses. It has its own play/pause, expand and close controls, which fade out
 * and come back when it is touched.
 */
@Composable
internal fun MiniPlayerHost(
    onExpand: (MiniPlayerStream) -> Unit,
    modifier: Modifier = Modifier,
) {
    val stream by MiniPlayerRepository.stream.collectAsStateWithLifecycle()
    stream?.let { current ->
        key(current.url) {
            MiniPlayerWindow(
                stream = current,
                onExpand = { onExpand(current) },
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun MiniPlayerWindow(
    stream: MiniPlayerStream,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var controller by remember { mutableStateOf<PlayerEngineController?>(null) }
    var snapshot by remember { mutableStateOf(PlayerPlaybackSnapshot()) }
    var error by remember { mutableStateOf<String?>(null) }
    var controlsVisible by remember { mutableStateOf(true) }
    var touchCount by remember { mutableStateOf(0) }

    LaunchedEffect(touchCount) {
        controlsVisible = true
        delay(MINI_PLAYER_CONTROLS_TIMEOUT_MS)
        controlsVisible = false
    }

    Box(
        modifier = modifier
            .width(200.dp)
            .aspectRatio(16f / 9f)
            .background(Color.Black, RoundedCornerShape(NuvioTokens.Radius.lg))
            .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(NuvioTokens.Radius.lg))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { touchCount += 1 },
            ),
    ) {
        PlatformPlayerSurface(
            sourceUrl = stream.url,
            sourceAudioUrl = stream.audioUrl,
            sourceHeaders = stream.headers,
            sourceResponseHeaders = stream.responseHeaders,
            streamType = stream.streamType,
            initialPositionMs = stream.initialPositionMs.takeIf { it > 0L },
            resizeMode = PlayerResizeMode.Fit,
            playWhenReady = true,
            allowConcurrentAudio = true,
            modifier = Modifier.fillMaxSize(),
            onControllerReady = { ready ->
                controller = ready
                ready.play()
            },
            onSnapshot = { snapshot = it },
            onError = { error = it },
        )

        if (!snapshot.isPlaying && error == null) {
            Icon(
                Icons.Rounded.PlayArrow,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(36.dp)
                    .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                    .padding(4.dp)
                    .clickable { controller?.play() },
            )
        }

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(start = NuvioTokens.Space.s8, end = NuvioTokens.Space.s2, top = 2.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stream.title,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    stream.subtitle?.takeIf { it.isNotBlank() }?.let { subtitle ->
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                SmallOverlayButton(
                    icon = if (snapshot.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    description = if (snapshot.isPlaying) "Pause" else "Play",
                    onClick = {
                        touchCount += 1
                        if (snapshot.isPlaying) controller?.pause() else controller?.play()
                    },
                )
                SmallOverlayButton(
                    icon = Icons.Rounded.Fullscreen,
                    description = "Open full player",
                    onClick = {
                        touchCount += 1
                        onExpand()
                    },
                )
                SmallOverlayButton(
                    icon = Icons.Filled.Close,
                    description = "Close picture in picture",
                    onClick = {
                        MiniPlayerRepository.hide()
                    },
                )
            }
        }

        error?.let { message ->
            Text(
                message,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(NuvioTokens.Radius.sm))
                    .padding(NuvioTokens.Space.s6),
            )
        }
    }
}

@Composable
private fun SmallOverlayButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, modifier = Modifier.size(28.dp)) {
        Icon(
            icon,
            contentDescription = description,
            tint = Color.White,
            modifier = Modifier.size(NuvioTokens.Icon.xs),
        )
    }
}
