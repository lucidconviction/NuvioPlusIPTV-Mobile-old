package com.nuvio.app.features.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.DragIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
        BoxWithConstraints(modifier = modifier) {
            key(current.url) {
                MiniPlayerWindow(
                    stream = current,
                    onExpand = { onExpand(current) },
                    availableWidth = maxWidth,
                    availableHeight = maxHeight,
                )
            }
        }
    }
}

@Composable
private fun MiniPlayerWindow(
    stream: MiniPlayerStream,
    onExpand: () -> Unit,
    availableWidth: Dp,
    availableHeight: Dp,
) {
    var controller by remember { mutableStateOf<PlayerEngineController?>(null) }
    var snapshot by remember { mutableStateOf(PlayerPlaybackSnapshot()) }
    var error by remember { mutableStateOf<String?>(null) }
    var controlsVisible by remember { mutableStateOf(true) }
    var touchCount by remember { mutableStateOf(0) }
    // The user can drag the bottom-right corner; the width survives stream swaps and reopen.
    var widthDp by remember { mutableStateOf(MiniPlayerRepository.widthDp.value) }
    val density = LocalDensity.current
    val minWidth = MiniPlayerRepository.MinWidthDp
    val maxWidth = availableWidth.value.coerceAtLeast(minWidth)
    val shownWidth = widthDp.coerceIn(minWidth, maxWidth).dp
    val shownHeight = shownWidth * 9f / 16f
    val positionX by MiniPlayerRepository.positionX.collectAsStateWithLifecycle()
    val positionY by MiniPlayerRepository.positionY.collectAsStateWithLifecycle()
    val freeX = (availableWidth - shownWidth).coerceAtLeast(0.dp)
    val freeY = (availableHeight - shownHeight).coerceAtLeast(0.dp)

    LaunchedEffect(touchCount) {
        controlsVisible = true
        delay(MINI_PLAYER_CONTROLS_TIMEOUT_MS)
        controlsVisible = false
    }

    Box(
        modifier = Modifier
            .offset(x = freeX * positionX, y = freeY * positionY)
            .width(shownWidth)
            .aspectRatio(16f / 9f)
            .background(Color.Black, RoundedCornerShape(NuvioTokens.Radius.lg))
            .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(NuvioTokens.Radius.lg))
            // Tap to bring the controls back; press and hold, then drag anywhere to move the
            // window. Both gestures live in one detector so neither cancels the other.
            .pointerInput(availableWidth, availableHeight, shownWidth) {
                val freeXPx = freeX.toPx()
                val freeYPx = freeY.toPx()
                fun moveBy(dx: Float, dy: Float) {
                    MiniPlayerRepository.setPositionFraction(
                        MiniPlayerRepository.positionX.value +
                            if (freeXPx > 0f) dx / freeXPx else 0f,
                        MiniPlayerRepository.positionY.value +
                            if (freeYPx > 0f) dy / freeYPx else 0f,
                    )
                }
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val longPress = awaitLongPressOrCancellation(down.id)
                    if (longPress == null) {
                        // The pointer went up before the long-press timeout: a tap.
                        touchCount += 1
                        return@awaitEachGesture
                    }
                    longPress.consume()
                    touchCount += 1
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (change.positionChanged()) {
                            val delta = change.positionChange()
                            change.consume()
                            moveBy(delta.x, delta.y)
                        }
                        if (!change.pressed) break
                    }
                }
            },
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
            interceptTouches = false,
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
                Icon(
                    Icons.Rounded.DragIndicator,
                    contentDescription = "Drag to move",
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(2.dp))
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

        // Drag this corner to resize the window; the 16:9 aspect ratio is kept.
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(2.dp)
                .size(26.dp)
                .clip(RoundedCornerShape(topStart = NuvioTokens.Radius.md, bottomEnd = NuvioTokens.Radius.lg))
                .background(Color.Black.copy(alpha = 0.55f))
                .pointerInput(availableWidth) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val delta = with(density) { dragAmount.x.toDp().value }
                        val next = (widthDp + delta).coerceIn(minWidth, maxWidth)
                        widthDp = next
                        MiniPlayerRepository.setWidthDp(next)
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.OpenInFull,
                contentDescription = "Drag to resize",
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.size(13.dp),
            )
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
