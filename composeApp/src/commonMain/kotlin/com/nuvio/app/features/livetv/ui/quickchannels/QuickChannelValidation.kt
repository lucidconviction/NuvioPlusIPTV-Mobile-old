package com.nuvio.app.features.livetv.ui.quickchannels

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.nuvio.app.features.livetv.model.LiveChannel

/**
 * The state of matching one Quick Channel bundle against the live playlist:
 * - [matches] are the channels whose names/aliases matched the bundle.
 * - [statuses] maps a stream URL to `true` (live), `false` (dead) or `null` (still checking).
 *
 * Dead streams are hidden as soon as their probe finishes; a stream that has not been probed yet
 * stays visible with a checking indicator, so rows never disappear and reappear while validating.
 */
internal data class QuickChannelMatchState(
    val matches: List<LiveChannel> = emptyList(),
    val statuses: Map<String, Boolean?> = emptyMap(),
) {
    val isChecking: Boolean
        get() = matches.any { statuses[it.url] == null }

    val workingCount: Int
        get() = matches.count { statuses[it.url] == true }

    /** Matches sorted working-first, with not-yet-checked rows kept and dead streams removed. */
    val visibleMatches: List<LiveChannel>
        get() = matches
            .sortedWith(
                compareByDescending<LiveChannel> { statuses[it.url] == true }
                    .thenByDescending { statuses[it.url] == null },
            )
            .filter { statuses[it.url] != false }
}

/**
 * Resolves [selected] against the current [liveChannels] and probes each matched stream for
 * liveness. Re-runs whenever the playlist changes so a slow playlist load or refresh does not
 * leave a stale (empty or dead) result on screen. Switching bundles cancels the previous probes
 * and ignores any late results from them.
 */
@Composable
internal fun rememberQuickChannelMatchState(
    selected: QuickChannel?,
    liveChannels: List<LiveChannel>,
): QuickChannelMatchState {
    var state by remember { mutableStateOf(QuickChannelMatchState()) }
    val run = remember { intArrayOf(0) }

    LaunchedEffect(selected, liveChannels) {
        val myRun = ++run[0]
        if (selected == null) {
            state = QuickChannelMatchState()
            return@LaunchedEffect
        }
        val matches = liveChannels.filter { QuickChannelList.matches(selected, it) }
        val pending = matches.distinctBy { it.url }.associate { it.url to null as Boolean? }
        state = QuickChannelMatchState(matches = matches, statuses = pending)
        if (matches.isEmpty()) return@LaunchedEffect

        QuickStreamChecker.checkBatch(matches.map { it.url }) { url, alive ->
            if (run[0] == myRun && state.statuses[url] == null) {
                state = state.copy(statuses = state.statuses + (url to alive))
            }
        }
    }

    return state
}
