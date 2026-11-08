package com.nuvio.app.features.livetv.data

import com.nuvio.app.features.livetv.model.LiveChannel
import com.nuvio.app.features.livetv.model.LiveTvClock
import com.nuvio.app.features.livetv.ui.quickchannels.QuickStreamChecker
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.concurrent.Volatile

/** What the Stream validation sheet shows. */
internal data class StreamValidationState(
    val running: Boolean = false,
    val cancelling: Boolean = false,
    val checked: Int = 0,
    val total: Int = 0,
    /** Streams working (the answered count while a run is in progress, the total after it). */
    val working: Int = 0,
    /** Streams hidden after this run (or the stored count before the first run). */
    val failed: Int = 0,
    /** True while only the streams hidden by an earlier run are being checked. */
    val recheckHiddenOnly: Boolean = false,
    val lastRunMs: Long = 0L,
    val message: String? = null,
)

/**
 * Checks which live streams actually answer and remembers the ones that don't, so Live TV can
 * leave dead channels out of every list.
 *
 * A full run checks every channel, including the ones hidden by a previous run: anything that
 * answers again is put straight back into the playlist. A "re-check hidden" run only touches
 * the currently hidden ones, which is much quicker after a provider has fixed a few links.
 */
internal object StreamValidationRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    private var job: Job? = null

    private val _state = MutableStateFlow(
        StreamValidationState(
            working = LiveTvPreferences.currentValidationWorking(),
            failed = LiveTvPreferences.currentValidationFailed().size,
            lastRunMs = LiveTvPreferences.currentValidationLastRun(),
        )
    )
    val state: StateFlow<StreamValidationState> = _state.asStateFlow()

    /** Starts a validation run. A running run is left alone. */
    fun start(recheckHiddenOnly: Boolean = false) {
        if (job?.isActive == true) return

        val previousFailed = LiveTvPreferences.currentValidationFailed()
        val allChannels = LiveTvRepository.channels.value
        val considered = if (recheckHiddenOnly) allChannels.filter { it.key in previousFailed } else allChannels
        if (considered.isEmpty()) {
            if (recheckHiddenOnly) {
                _state.value = _state.value.copy(
                    running = false,
                    cancelling = false,
                    message = "No hidden streams to check.",
                )
                return
            }
            val now = LiveTvClock.nowMs()
            val workingCount = (allChannels.size - previousFailed.size).coerceAtLeast(0)
            LiveTvPreferences.saveValidationWorking(workingCount)
            LiveTvPreferences.saveValidationLastRun(now)
            _state.value = StreamValidationState(
                running = false,
                checked = 0,
                total = 0,
                working = workingCount,
                failed = previousFailed.size,
                recheckHiddenOnly = false,
                lastRunMs = now,
                message = "No streams to check yet.",
            )
            return
        }

        // A URL is probed once even when several channels share it; the verdict is then
        // applied to every channel with that URL.
        val unique = considered.distinctBy { it.url }
        val invalid = unique.filterNot { StreamUrls.isUsable(it.url) }
        val valid = unique.filter { StreamUrls.isUsable(it.url) }
        val total = unique.size
        val checked = atomic(invalid.size)
        val working = atomic(0)
        val failed = atomic(invalid.size)

        _state.value = StreamValidationState(
            running = true,
            checked = checked.value,
            total = total,
            working = 0,
            failed = failed.value,
            recheckHiddenOnly = recheckHiddenOnly,
            lastRunMs = LiveTvPreferences.currentValidationLastRun(),
        )

        job = scope.launch {
            try {
                val outcomes = LinkedHashMap<String, Boolean>()
                // A malformed link is known to be dead; don't spend a network request on it.
                invalid.forEach { outcomes[it.url] = false }
                val probed = QuickStreamChecker.checkBatch(
                    valid,
                    forceRefresh = true,
                    assumeAliveOnUnknown = false,
                ) { _, alive ->
                    val done = checked.incrementAndGet()
                    if (alive) working.incrementAndGet() else failed.incrementAndGet()
                    _state.value = _state.value.copy(
                        running = true,
                        checked = done,
                        total = total,
                        working = working.value,
                        failed = failed.value,
                    )
                }
                outcomes.putAll(probed)

                val deadKeys = deadChannelKeys(considered, outcomes)
                val existingKeys = allChannels.mapTo(HashSet()) { it.key }
                val failedKeys = if (recheckHiddenOnly) {
                    // Keep the ones that still fail; drop the ones that now answer or are gone.
                    retainedFailedKeys(previousFailed, considered, outcomes, existingKeys)
                } else {
                    deadKeys
                }

                LiveTvPreferences.saveValidationFailed(failedKeys)
                val workingCount = (allChannels.size - failedKeys.size).coerceAtLeast(0)
                LiveTvPreferences.saveValidationWorking(workingCount)
                val now = LiveTvClock.nowMs()
                LiveTvPreferences.saveValidationLastRun(now)
                _state.value = StreamValidationState(
                    running = false,
                    checked = total,
                    total = total,
                    working = workingCount,
                    failed = failedKeys.size,
                    recheckHiddenOnly = recheckHiddenOnly,
                    lastRunMs = now,
                )
            } catch (e: CancellationException) {
                _state.value = _state.value.copy(
                    running = false,
                    cancelling = false,
                    message = "Validation stopped.",
                )
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    running = false,
                    cancelling = false,
                    message = e.message ?: "Validation failed.",
                )
            } finally {
                job = null
            }
        }
    }

    /** Stops a run in progress. The streams hidden by earlier runs stay hidden. */
    fun cancel() {
        if (job?.isActive != true) return
        _state.value = _state.value.copy(cancelling = true)
        job?.cancel()
    }

    /** Puts one hidden stream back without waiting for the next run. */
    fun show(key: String) {
        val current = LiveTvPreferences.currentValidationFailed()
        if (key !in current) return
        val next = current - key
        LiveTvPreferences.saveValidationFailed(next)
        val workingCount = (LiveTvRepository.channels.value.size - next.size).coerceAtLeast(0)
        LiveTvPreferences.saveValidationWorking(workingCount)
        _state.value = _state.value.copy(working = workingCount, failed = next.size)
    }

    /** Puts every hidden stream back (the next run hides the dead ones again). */
    fun showAll() {
        if (LiveTvPreferences.currentValidationFailed().isEmpty()) return
        LiveTvPreferences.saveValidationFailed(emptySet())
        val workingCount = LiveTvRepository.channels.value.size
        LiveTvPreferences.saveValidationWorking(workingCount)
        _state.value = _state.value.copy(working = workingCount, failed = 0)
    }
}

/** Every channel whose URL did not answer during a full run. */
internal fun deadChannelKeys(channels: List<LiveChannel>, outcomes: Map<String, Boolean>): Set<String> =
    channels.filter { outcomes[it.url] == false }.mapTo(HashSet()) { it.key }

/**
 * The hidden set after a "re-check hidden" run: still-dead channels stay hidden, restored ones
 * and channels that no longer exist are dropped.
 */
internal fun retainedFailedKeys(
    previous: Set<String>,
    checked: List<LiveChannel>,
    outcomes: Map<String, Boolean>,
    existingKeys: Set<String>,
): Set<String> {
    val alive = checked.filter { outcomes[it.url] == true }.mapTo(HashSet()) { it.key }
    return (previous - alive).filterTo(HashSet()) { it in existingKeys }
}
