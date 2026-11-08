package com.nuvio.app.features.livetv.ui.multiview

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.VolumeMute
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.NuvioBackButton
import com.nuvio.app.core.ui.NuvioToastController
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.livetv.model.LiveChannel
import com.nuvio.app.features.livetv.model.LiveTvClock
import com.nuvio.app.features.livetv.ui.quickchannels.QuickChannelsSheet
import com.nuvio.app.features.player.PlatformPlayerSurface
import com.nuvio.app.features.player.PlayerEngineController
import com.nuvio.app.features.player.PlayerPlaybackSnapshot
import com.nuvio.app.features.player.PlayerResizeMode
import kotlin.math.ceil
import kotlin.math.sqrt
import kotlin.time.TimeSource

/** How long the top controls and each window's controls stay on screen after a touch. */
private const val MULTI_VIEW_CHROME_TIMEOUT_MS = 4_000L

/** The volume steps offered in each window. */
private val MULTI_VIEW_VOLUME_STEPS = listOf(0, 20, 40, 60, 80, 100)

/** The layouts offered for a given number of open windows. */
internal fun availableMultiViewLayouts(streamCount: Int): List<MultiViewLayout> =
    MultiViewLayout.entries.filter { layout ->
        when (layout) {
            MultiViewLayout.AUTO -> true
            MultiViewLayout.GRID, MultiViewLayout.ROW, MultiViewLayout.COLUMN -> streamCount >= 2
            MultiViewLayout.FOCUS -> streamCount >= 3
        }
    }

/**
 * Up to nine streams at once (four on a phone in portrait): a row of controls (layouts, saved
 * layouts, Quick CH, sound, pause, refresh and close) over a grid of players. Each window has its
 * own play, seek, refresh, progress, volume, swap and full-screen controls. Everything but the
 * video fades out after a few seconds and comes back when the screen is touched.
 */
@Composable
internal fun LiveTvMultiViewScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val streams by MultiViewRepository.streams.collectAsStateWithLifecycle()
    val layout by MultiViewRepository.layout.collectAsStateWithLifecycle()
    val soundAll by MultiViewRepository.soundAll.collectAsStateWithLifecycle()
    val pauseAll by MultiViewRepository.pauseAll.collectAsStateWithLifecycle()
    val refreshGeneration by MultiViewRepository.refreshGeneration.collectAsStateWithLifecycle()
    val savedLayouts by MultiViewRepository.savedLayouts.collectAsStateWithLifecycle()

    var layoutMenu by remember { mutableStateOf(false) }
    var savedMenu by remember { mutableStateOf(false) }
    var quickChannels by remember { mutableStateOf(false) }
    var saveDialog by remember { mutableStateOf(false) }
    var saveName by remember { mutableStateOf("") }
    var focusedId by remember { mutableStateOf<String?>(null) }
    var swapSource by remember { mutableStateOf<String?>(null) }
    var chromeVisible by remember { mutableStateOf(true) }
    val volumes = remember { mutableStateMapOf<String, Int>() }
    val touchTick = remember { mutableStateOf(0) }
    val touchClock = remember { TimeSource.Monotonic }
    val lastTouchMs = remember { longArrayOf(-1_000L) }

    LaunchedEffect(Unit) { MultiViewRepository.ensureSavedLayoutsLoaded() }

    // Any touch brings the controls back and restarts their timeout.
    LaunchedEffect(touchTick.value) {
        chromeVisible = true
        kotlinx.coroutines.delay(MULTI_VIEW_CHROME_TIMEOUT_MS)
        chromeVisible = false
    }
    LaunchedEffect(streams.map { it.id }) {
        val open = streams.mapTo(HashSet()) { it.id }
        volumes.keys.toList().forEach { id -> if (id !in open) volumes.remove(id) }
        if (focusedId != null && focusedId !in open) focusedId = null
        if (swapSource != null && swapSource !in open) swapSource = null
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial)
                        val now = touchClock.markNow().elapsedNow().inWholeMilliseconds
                        if (now - lastTouchMs[0] > 300L) {
                            lastTouchMs[0] = now
                            touchTick.value += 1
                        }
                    }
                }
            },
    ) {
        // Tablets get all nine windows; a phone (in either orientation) keeps four readable ones.
        // The shortest side is the standard tablet breakpoint, so a landscape phone does not
        // suddenly qualify for nine panes.
        val tablet = minOf(maxWidth, maxHeight) >= 600.dp
        val maxWindows = if (tablet) MultiViewRepository.MAX_STREAMS else 4
        LaunchedEffect(maxWindows) { MultiViewRepository.setMaxStreams(maxWindows) }

        val chrome = MaterialTheme.nuvio.colors

        if (streams.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No streams yet.\nTap Multi-view in the player, or add one with Quick CH.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(NuvioTokens.Space.s24),
                )
            }
        } else {
            MultiViewGrid(
                streams = streams,
                layout = layout,
                refreshGeneration = refreshGeneration,
                focusedId = focusedId,
                swapSource = swapSource,
                volumes = volumes,
                pauseAll = pauseAll,
                soundAll = soundAll,
                chromeVisible = chromeVisible,
                onRemove = { id ->
                    MultiViewRepository.remove(id)
                    if (focusedId == id) focusedId = null
                },
                onSwap = { id ->
                    val source = swapSource
                    when {
                        source == null -> swapSource = id
                        source == id -> swapSource = null
                        else -> {
                            MultiViewRepository.swap(source, id)
                            swapSource = null
                        }
                    }
                },
                onFullscreen = { id -> focusedId = if (focusedId == id) null else id },
                onVolumeChange = { id, percent -> volumes[id] = percent },
                onTouch = { touchTick.value += 1 },
                modifier = Modifier
                    .fillMaxSize()
                    // The always-visible toolbar sits above the grid instead of over it, so
                    // each window's own top-right buttons stay reachable.
                    .padding(top = 60.dp),
            )
        }

        AnimatedVisibility(
            // The top toolbar stays on screen at all times; only the per-window controls fade.
            visible = true,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .zIndex(30f),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.82f), Color.Black.copy(alpha = 0.55f)),
                        ),
                    )
                    .padding(horizontal = NuvioTokens.Space.s8, vertical = NuvioTokens.Space.s6)
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8),
            ) {
                NuvioBackButton(
                    onClick = onBack,
                    containerColor = Color.White.copy(alpha = 0.10f),
                    contentColor = Color.White,
                )
                Text(
                    "Multi-view",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(end = NuvioTokens.Space.s4),
                )

                // Layouts: Auto first, the rest when there are enough windows for them.
                Box {
                    ControlChip(
                        label = layoutName(layout),
                        icon = Icons.Rounded.GridView,
                        active = layoutMenu,
                        onClick = { layoutMenu = true },
                    )
                    DropdownMenu(expanded = layoutMenu, onDismissRequest = { layoutMenu = false }) {
                        availableMultiViewLayouts(streams.size).forEach { option ->
                            DropdownMenuItem(
                                text = { Text(layoutName(option)) },
                                leadingIcon = {
                                    if (option == layout) Icon(Icons.Rounded.GridView, null, tint = chrome.accent)
                                },
                                onClick = {
                                    layoutMenu = false
                                    MultiViewRepository.setLayout(option)
                                },
                            )
                        }
                    }
                }

                // Saved layouts: Save layout at the top of the menu.
                Box {
                    ControlChip(
                        label = "Saved Layouts",
                        icon = Icons.Rounded.Bookmark,
                        active = savedMenu,
                        onClick = { savedMenu = true },
                    )
                    DropdownMenu(expanded = savedMenu, onDismissRequest = { savedMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Save layout") },
                            leadingIcon = { Icon(Icons.Rounded.Save, null, tint = chrome.accent) },
                            enabled = streams.isNotEmpty(),
                            onClick = {
                                savedMenu = false
                                saveName = ""
                                saveDialog = true
                            },
                        )
                        if (savedLayouts.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("No saved layouts yet") },
                                enabled = false,
                                onClick = {},
                            )
                        } else {
                            savedLayouts.forEach { saved ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(saved.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(
                                                "${saved.streams.size} window${if (saved.streams.size == 1) "" else "s"}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = chrome.textMuted,
                                            )
                                        }
                                    },
                                    trailingIcon = {
                                        IconButton(onClick = { MultiViewRepository.deleteSavedLayout(saved.id) }) {
                                            Icon(Icons.Rounded.Delete, "Delete layout", tint = chrome.textMuted, modifier = Modifier.size(NuvioTokens.Icon.md))
                                        }
                                    },
                                    onClick = {
                                        savedMenu = false
                                        MultiViewRepository.restore(saved)
                                    },
                                )
                            }
                        }
                    }
                }

                ControlChip(
                    label = "Quick CH",
                    icon = Icons.Rounded.Tv,
                    onClick = { quickChannels = true },
                )
                ControlChip(
                    label = "Sound All",
                    icon = if (soundAll) Icons.Rounded.VolumeUp else Icons.Rounded.VolumeMute,
                    active = soundAll,
                    onClick = { MultiViewRepository.toggleSoundAll() },
                )
                ControlChip(
                    label = "Pause All",
                    icon = if (pauseAll) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                    active = pauseAll,
                    onClick = { MultiViewRepository.togglePauseAll() },
                )
                ControlChip(
                    label = "Refresh all",
                    icon = Icons.Rounded.Refresh,
                    onClick = { MultiViewRepository.refreshAll() },
                )
                ControlChip(
                    label = "Close all",
                    icon = Icons.Filled.Close,
                    onClick = {
                        MultiViewRepository.clear()
                        focusedId = null
                        swapSource = null
                    },
                )

                if (swapSource != null) {
                    Text(
                        "Tap another window to swap",
                        style = MaterialTheme.typography.labelMedium,
                        color = chrome.accent,
                        modifier = Modifier.padding(start = NuvioTokens.Space.s4),
                    )
                }
            }
        }
    }

    if (quickChannels) {
        QuickChannelsSheet(
            onDismiss = { quickChannels = false },
            onPlayChannel = { channel: LiveChannel ->
                if (!MultiViewRepository.addChannel(channel)) {
                    val max = MultiViewRepository.maxStreams.value
                    NuvioToastController.show(
                        if (MultiViewRepository.streams.value.size >= max) {
                            "Multi-view is full ($max windows)"
                        } else {
                            "Already open in multi-view"
                        },
                    )
                }
            },
        )
    }

    if (saveDialog) {
        AlertDialog(
            onDismissRequest = { saveDialog = false },
            title = { Text("Save layout") },
            text = {
                OutlinedTextField(
                    value = saveName,
                    onValueChange = { saveName = it },
                    singleLine = true,
                    label = { Text("Layout name") },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        MultiViewRepository.saveLayout(saveName)
                        saveDialog = false
                    },
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { saveDialog = false }) { Text("Cancel") }
            },
        )
    }
}

/** Where one window sits inside the page. */
private data class PaneRect(val x: Dp, val y: Dp, val width: Dp, val height: Dp)

private fun paneRect(
    index: Int,
    count: Int,
    layout: MultiViewLayout,
    width: Dp,
    height: Dp,
): PaneRect {
    if (count <= 1) return PaneRect(0.dp, 0.dp, width, height)
    return when (layout) {
        MultiViewLayout.ROW ->
            PaneRect(width / count * index, 0.dp, width / count, height)
        MultiViewLayout.COLUMN ->
            PaneRect(0.dp, height / count * index, width, height / count)
        MultiViewLayout.FOCUS -> {
            if (index == 0) {
                PaneRect(0.dp, 0.dp, width, height * 2 / 3)
            } else {
                val small = count - 1
                PaneRect(width / small * (index - 1), height * 2 / 3, width / small, height / 3)
            }
        }
        MultiViewLayout.AUTO, MultiViewLayout.GRID -> {
            val columns = ceil(sqrt(count.toDouble())).toInt().coerceAtLeast(1)
            val rows = ceil(count.toDouble() / columns).toInt().coerceAtLeast(1)
            PaneRect(
                width / columns * (index % columns),
                height / rows * (index / columns),
                width / columns,
                height / rows,
            )
        }
    }
}

@Composable
private fun MultiViewGrid(
    streams: List<MultiViewStream>,
    layout: MultiViewLayout,
    refreshGeneration: Int,
    focusedId: String?,
    swapSource: String?,
    volumes: Map<String, Int>,
    pauseAll: Boolean,
    soundAll: Boolean,
    chromeVisible: Boolean,
    onRemove: (String) -> Unit,
    onSwap: (String) -> Unit,
    onFullscreen: (String) -> Unit,
    onVolumeChange: (String, Int) -> Unit,
    onTouch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier) {
        streams.forEachIndexed { index, stream ->
            key(stream.id) {
                val focused = stream.id == focusedId
                val rect = if (focused) {
                    PaneRect(0.dp, 0.dp, maxWidth, maxHeight)
                } else {
                    paneRect(index, streams.size, layout, maxWidth, maxHeight)
                }
                MultiViewPane(
                    stream = stream,
                    refreshGeneration = refreshGeneration,
                    chromeVisible = chromeVisible,
                    focused = focused,
                    swapSelected = swapSource == stream.id,
                    swapMode = swapSource != null,
                    volumePercent = volumes[stream.id] ?: 100,
                    pauseAll = pauseAll,
                    soundAll = soundAll,
                    onRemove = { onRemove(stream.id) },
                    onSwap = { onSwap(stream.id) },
                    onToggleFullscreen = { onFullscreen(stream.id) },
                    onVolumeChange = { percent -> onVolumeChange(stream.id, percent) },
                    onTouch = onTouch,
                    modifier = Modifier
                        .offset(rect.x, rect.y)
                        .size(rect.width, rect.height)
                        .zIndex(if (focused) 20f else 1f),
                )
            }
        }
    }
}

@Composable
private fun MultiViewPane(
    stream: MultiViewStream,
    refreshGeneration: Int,
    chromeVisible: Boolean,
    focused: Boolean,
    swapSelected: Boolean,
    swapMode: Boolean,
    volumePercent: Int,
    pauseAll: Boolean,
    soundAll: Boolean,
    onRemove: () -> Unit,
    onSwap: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onVolumeChange: (Int) -> Unit,
    onTouch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var error by remember(stream.id) { mutableStateOf<String?>(null) }
    var controller by remember(stream.id) { mutableStateOf<PlayerEngineController?>(null) }
    var snapshot by remember(stream.id) { mutableStateOf(PlayerPlaybackSnapshot()) }
    var dragFraction by remember(stream.id) { mutableStateOf<Float?>(null) }
    var volumeMenu by remember(stream.id) { mutableStateOf(false) }
    var retryCount by remember(stream.id) { mutableStateOf(0) }
    var lastPositionMs by remember(stream.id) { mutableLongStateOf(-1L) }
    var lastAdvanceAtMs by remember(stream.id) { mutableLongStateOf(LiveTvClock.nowMs()) }
    val tapInteraction = remember(stream.id) { MutableInteractionSource() }

    fun applyVolume() {
        controller?.setVolume(if (soundAll) volumePercent / 100f else 0f)
    }

    LaunchedEffect(soundAll, volumePercent) { applyVolume() }
    LaunchedEffect(pauseAll) {
        controller?.let { if (pauseAll) it.pause() else it.play() }
    }
    LaunchedEffect(controller) {
        // A rebuilt player starts from a clean slate: don't let an old stall clock retry it.
        lastPositionMs = -1L
        lastAdvanceAtMs = LiveTvClock.nowMs()
        retryCount = 0
    }

    // A live stream can stop advancing when its provider rotates tokens or drops the encoder.
    // Keep track of the last real progress so a stalled pane can be re-prepared automatically.
    LaunchedEffect(stream.id, snapshot.positionMs, snapshot.isPlaying) {
        if (snapshot.isPlaying && snapshot.positionMs != lastPositionMs) {
            lastPositionMs = snapshot.positionMs
            lastAdvanceAtMs = LiveTvClock.nowMs()
            retryCount = 0
        }
    }

    // Errors are retried with a short backoff; a permanently dead URL gives up after a few tries.
    LaunchedEffect(stream.id, error) {
        val message = error ?: return@LaunchedEffect
        if (retryCount >= 5) return@LaunchedEffect
        kotlinx.coroutines.delay((1_500L shl retryCount.coerceAtMost(3)))
        if (error == message) {
            error = null
            controller?.retry()
            retryCount += 1
            lastAdvanceAtMs = LiveTvClock.nowMs()
        }
    }

    // While a live player reports it is playing but the position has not moved for a while,
    // rebuild its connection. This keeps token-expired IPTV streams alive without a manual tap.
    LaunchedEffect(stream.id, pauseAll) {
        if (pauseAll) return@LaunchedEffect
        while (true) {
            kotlinx.coroutines.delay(5_000L)
            val stuck = LiveTvClock.nowMs() - lastAdvanceAtMs
            if (controller != null &&
                !pauseAll &&
                (
                    (snapshot.isPlaying && stuck > 20_000L) ||
                        (snapshot.isLoading && stuck > 25_000L)
                    )
            ) {
                controller?.retry()
                lastAdvanceAtMs = LiveTvClock.nowMs()
                retryCount += 1
            }
        }
    }

    key(stream.id, refreshGeneration) {
        Box(
            modifier = modifier
                .padding(1.dp)
                .background(Color.Black)
                .border(1.dp, if (swapSelected) Color.White else Color.White.copy(alpha = 0.10f)),
        ) {
            PlatformPlayerSurface(
                sourceUrl = stream.url,
                sourceHeaders = stream.headers,
                sourceResponseHeaders = stream.responseHeaders,
                streamType = stream.streamType,
                resizeMode = PlayerResizeMode.Fit,
                playWhenReady = !pauseAll,
                allowConcurrentAudio = true,
                interceptTouches = false,
                modifier = Modifier.fillMaxSize(),
                onControllerReady = { ready ->
                    controller = ready
                    applyVolume()
                    if (pauseAll) ready.pause() else ready.play()
                },
                onSnapshot = { snapshot = it },
                onError = { error = it },
            )

            // The video itself is a tap target: it brings the window controls back, and while a
            // swap is pending it picks this window as the other half of the swap.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = tapInteraction,
                        indication = null,
                    ) {
                        if (swapMode) onSwap() else onTouch()
                    },
            )

            AnimatedVisibility(
                visible = chromeVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize(),
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Top-right window actions: swap, full screen, close.
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(NuvioTokens.Space.s4),
                        horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s2),
                    ) {
                        PaneIconButton(
                            icon = Icons.Rounded.SwapHoriz,
                            description = "Swap window",
                            selected = swapSelected,
                            onClick = onSwap,
                        )
                        PaneIconButton(
                            icon = if (focused) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                            description = if (focused) "Exit full screen" else "Full screen",
                            onClick = onToggleFullscreen,
                        )
                        PaneIconButton(
                            icon = Icons.Filled.Close,
                            description = "Close window",
                            onClick = onRemove,
                        )
                    }

                    // Bottom controls: title, progress, play/pause, seeks, refresh and volume.
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.82f)),
                                ),
                            )
                            .padding(horizontal = NuvioTokens.Space.s6, vertical = NuvioTokens.Space.s4),
                    ) {
                        Text(
                            stream.displayTitle,
                            style = MaterialTheme.typography.labelMedium,
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

                        val duration = snapshot.durationMs
                        val fraction = (dragFraction
                            ?: if (duration > 0L) (snapshot.positionMs.toFloat() / duration).coerceIn(0f, 1f) else 0f)
                        Slider(
                            value = fraction,
                            onValueChange = { value -> dragFraction = value },
                            onValueChangeFinished = {
                                val target = dragFraction
                                dragFraction = null
                                if (target != null && duration > 0L) {
                                    controller?.seekTo((target * duration).toLong())
                                }
                            },
                            enabled = duration > 0L,
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = Color.White,
                                inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                                disabledThumbColor = Color.White.copy(alpha = 0.5f),
                                disabledActiveTrackColor = Color.White.copy(alpha = 0.4f),
                                disabledInactiveTrackColor = Color.White.copy(alpha = 0.2f),
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(20.dp),
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            PaneIconButton(
                                icon = Icons.Rounded.FastRewind,
                                description = "Back 10 seconds",
                                onClick = { controller?.seekBy(-10_000L) },
                                modifier = Modifier.weight(1f),
                            )
                            PaneIconButton(
                                icon = if (snapshot.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                description = if (snapshot.isPlaying) "Pause" else "Play",
                                onClick = {
                                    controller?.let { if (snapshot.isPlaying) it.pause() else it.play() }
                                },
                                modifier = Modifier.weight(1f),
                            )
                            PaneIconButton(
                                icon = Icons.Rounded.FastForward,
                                description = "Forward 10 seconds",
                                onClick = { controller?.seekBy(10_000L) },
                                modifier = Modifier.weight(1f),
                            )
                            PaneIconButton(
                                icon = Icons.Rounded.Refresh,
                                description = "Refresh stream",
                                onClick = {
                                    error = null
                                    controller?.retry()
                                },
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.weight(0.4f))
                            Box(
                                modifier = Modifier.weight(1.5f),
                                contentAlignment = Alignment.Center,
                            ) {
                                PaneVolumeButton(
                                    percent = volumePercent,
                                    muted = !soundAll || volumePercent == 0,
                                    onClick = { volumeMenu = true },
                                )
                                DropdownMenu(expanded = volumeMenu, onDismissRequest = { volumeMenu = false }) {
                                    MULTI_VIEW_VOLUME_STEPS.forEach { step ->
                                        DropdownMenuItem(
                                            text = { Text("$step%") },
                                            leadingIcon = {
                                                if (step == volumePercent) {
                                                    Icon(Icons.Rounded.VolumeUp, null, tint = MaterialTheme.nuvio.colors.accent)
                                                }
                                            },
                                            onClick = {
                                                volumeMenu = false
                                                onVolumeChange(step)
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            error?.let { message ->
                Text(
                    message,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(NuvioTokens.Radius.sm))
                        .padding(NuvioTokens.Space.s8),
                )
            }
        }
    }
}

@Composable
private fun PaneIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    selected: Boolean = false,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(34.dp)
            .background(
                if (selected) Color.White else Color.Black.copy(alpha = 0.5f),
                CircleShape,
            ),
    ) {
        Icon(
            icon,
            contentDescription = description,
            tint = if (selected) Color.Black else Color.White,
            modifier = Modifier.size(NuvioTokens.Icon.sm),
        )
    }
}

@Composable
private fun PaneVolumeButton(
    percent: Int,
    muted: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .heightIn(min = 30.dp)
            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(NuvioTokens.Radius.full))
            .clickable(onClick = onClick)
            .padding(horizontal = NuvioTokens.Space.s8, vertical = NuvioTokens.Space.s2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s4),
    ) {
        Icon(
            if (muted) Icons.Rounded.VolumeMute else Icons.Rounded.VolumeUp,
            contentDescription = "Volume",
            tint = Color.White,
            modifier = Modifier.size(NuvioTokens.Icon.xs),
        )
        Text(
            "$percent%",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
        )
    }
}

@Composable
private fun ControlChip(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    active: Boolean = false,
) {
    val colors = MaterialTheme.nuvio.colors
    val shape = RoundedCornerShape(NuvioTokens.Radius.full)
    Row(
        modifier = Modifier
            .heightIn(min = 38.dp)
            .background(if (active) colors.accent else Color.White.copy(alpha = 0.10f), shape)
            .border(1.dp, if (active) colors.accent else Color.White.copy(alpha = 0.22f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = NuvioTokens.Space.s12, vertical = NuvioTokens.Space.s8),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s6),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (active) colors.onAccent else Color.White,
            modifier = Modifier.size(NuvioTokens.Icon.sm),
        )
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (active) colors.onAccent else Color.White,
            maxLines = 1,
        )
    }
}

private fun layoutName(layout: MultiViewLayout): String = when (layout) {
    MultiViewLayout.AUTO -> "Auto"
    MultiViewLayout.GRID -> "Grid"
    MultiViewLayout.ROW -> "Row"
    MultiViewLayout.COLUMN -> "Column"
    MultiViewLayout.FOCUS -> "Focus"
}
