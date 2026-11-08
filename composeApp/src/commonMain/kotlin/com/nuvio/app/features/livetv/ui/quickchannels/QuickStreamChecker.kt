package com.nuvio.app.features.livetv.ui.quickchannels

import com.nuvio.app.core.network.createApiHttpClient
import com.nuvio.app.features.livetv.data.XtreamClient
import com.nuvio.app.features.livetv.model.LiveChannel
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Lightweight stream liveness probe used by the Quick Channels screens.
 *
 * The probe is deliberately conservative: a stream is hidden only when there is strong evidence
 * that it is dead (an HTTP 404/410/401/403, an HTML error page, or a textual error response).
 * Slow providers, redirects, range-request rejections, server errors and connection hiccups are
 * retried and then kept visible, because hiding a working channel is worse than showing one that
 * the player may fail to open.
 *
 * Request headers from the playlist are sent along with every probe, and a redirected stream is
 * followed exactly like the player follows it. Results are cached in-memory for [TTL_MS].
 */
internal object QuickStreamChecker {

    const val TTL_MS = 30 * 60 * 1000L

    /** First attempt: enough for most providers, short enough to keep the sheet responsive. */
    private const val TIMEOUT_MS = 8_000L

    /** Retry after an inconclusive first attempt (Range rejected, slow encoder, transient 5xx). */
    private const val RETRY_TIMEOUT_MS = 12_000L
    private const val MAX_CONCURRENCY = 8
    private const val SAMPLE_SIZE = 4096
    private const val USER_AGENT = XtreamClient.DEFAULT_UA

    private val htmlPagePrefixes = listOf(
        "<!doctype html", "<!doctype", "<html", "<!", "<?xml",
    )

    /**
     * Text that only shows up on an error page. These are checked against decoded *text* samples
     * only: binary media and HLS/DASH manifests are accepted before this list is consulted, so
     * words like "error" or "404" inside a valid stream can no longer reject it.
     */
    private val deadPageTokens = listOf(
        "404", "403", "401", "not found", "forbidden", "unauthorized",
        "invalid user", "invalid username", "invalid password",
        "account disabled", "account suspended", "account expired",
        "credits expired", "subscription expired", "stream expired", "expired",
        "offline", "service unavailable", "bad gateway", "internal server error",
        "error", "failed to open stream", "stream not found", "channel not found",
        "access denied",
    )

    private val mediaContentTypes = listOf(
        "video/", "audio/", "application/vnd.apple.mpegurl", "application/x-mpegurl",
        "application/dash+xml", "application/octet-stream", "mpegurl", "mp2t",
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val client: HttpClient = createApiHttpClient().config { followRedirects = true }

    private data class Entry(val alive: Boolean, val checkedAt: Long)

    private data class ProbeResponse(
        val status: Int,
        val contentType: String?,
        val sample: String,
    )

    private enum class Verdict { Alive, Dead, Unknown }

    private enum class SampleKind { Media, ErrorPage, Unclear }

    private val cache = mutableMapOf<String, Entry>()
    private val cacheLock = Mutex()

    /**
     * Probes every distinct channel in [channels] and returns the final status for each URL.
     * [onResult] is invoked as each URL finishes checking (including cached results), so callers
     * can show progress instead of waiting for the whole batch.
     */
    suspend fun checkBatch(
        channels: List<LiveChannel>,
        onResult: (url: String, alive: Boolean) -> Unit = { _, _ -> },
    ): Map<String, Boolean> {
        val uniqueChannels = channels
            .filter { it.url.isNotBlank() }
            .distinctBy { it.url }
        if (uniqueChannels.isEmpty()) return emptyMap()

        val results = mutableMapOf<String, Boolean>()
        val resultsLock = Mutex()
        val semaphore = Semaphore(MAX_CONCURRENCY)
        coroutineScope {
            uniqueChannels.forEach { channel ->
                launch {
                    val alive = semaphore.withPermit { checkChannel(channel) }
                    resultsLock.withLock { results[channel.url] = alive }
                    onResult(channel.url, alive)
                }
            }
        }
        return results.toMap()
    }

    suspend fun checkUrl(url: String, headers: Map<String, String> = emptyMap()): Boolean {
        if (url.isBlank()) return false
        return checkChannel(
            LiveChannel(
                key = url,
                sourceId = "",
                sourceName = "",
                name = url,
                tvgId = null,
                tvgName = null,
                logo = null,
                groupId = "",
                group = "",
                number = 0,
                url = url,
                headers = headers,
            ),
        )
    }

    private suspend fun checkChannel(channel: LiveChannel): Boolean {
        val key = cacheKey(channel)
        val now = System.currentTimeMillis()
        val cached = cacheLock.withLock { cache[key] }
        if (cached != null && now - cached.checkedAt < TTL_MS) return cached.alive
        val alive = probe(channel)
        cacheLock.withLock { cache[key] = Entry(alive, now) }
        return alive
    }

    private suspend fun probe(channel: LiveChannel): Boolean {
        val headers = requestHeaders(channel)

        // Range first: it stops the server from pushing a whole live buffer at us. Some panels
        // reject Range requests even though the stream works, so an inconclusive answer is
        // retried as a plain GET with a longer timeout.
        val ranged = probeOnce(channel.url, headers, "bytes=0-${SAMPLE_SIZE - 1}", TIMEOUT_MS)
        when (ranged?.let(::verdictOf)) {
            Verdict.Alive -> return true
            Verdict.Dead -> return false
            Verdict.Unknown, null -> Unit
        }

        val plain = probeOnce(channel.url, headers, null, RETRY_TIMEOUT_MS)
        return when (plain?.let(::verdictOf)) {
            Verdict.Alive -> true
            Verdict.Dead -> false
            // Inconclusive twice: do not hide a stream that may still play. Slow or temporarily
            // unreachable providers are the main source of false "dead" results.
            Verdict.Unknown, null -> true
        }
    }

    private fun requestHeaders(channel: LiveChannel): Map<String, String> {
        val headers = LinkedHashMap<String, String>()
        channel.headers.forEach { (k, v) ->
            if (k.isNotBlank() && v.isNotBlank()) headers[k] = v
        }
        if (headers.keys.none { it.equals(HttpHeaders.UserAgent, ignoreCase = true) }) {
            headers[HttpHeaders.UserAgent] = USER_AGENT
        }
        return headers
    }

    private fun cacheKey(channel: LiveChannel): String {
        if (channel.headers.isEmpty()) return channel.url
        val headerPart = channel.headers.toSortedMap().entries
            .joinToString("&") { "${it.key}=${it.value}" }
        return channel.url + "\n" + headerPart
    }

    private suspend fun probeOnce(
        url: String,
        headers: Map<String, String>,
        range: String?,
        timeoutMs: Long,
    ): ProbeResponse? = runCatching {
        withContext(Dispatchers.IO) {
            withTimeoutOrNull(timeoutMs) {
                client.prepareGet(url) {
                    headers.forEach { (k, v) -> header(k, v) }
                    if (range != null) header(HttpHeaders.Range, range)
                }.execute { resp ->
                    val sample = if (resp.status.value in 200..299) readSample(resp) else ""
                    ProbeResponse(
                        status = resp.status.value,
                        contentType = resp.headers[HttpHeaders.ContentType],
                        sample = sample,
                    )
                }
            }
        }
    }.getOrNull()

    private suspend fun readSample(resp: io.ktor.client.statement.HttpResponse): String {
        val body = resp.bodyAsChannel()
        val sb = StringBuilder()
        val buf = ByteArray(SAMPLE_SIZE)
        while (sb.length < SAMPLE_SIZE) {
            val n = body.readAvailable(buf, 0, minOf(buf.size, SAMPLE_SIZE - sb.length))
            if (n <= 0) break
            sb.append(decodeSample(buf, n))
        }
        return sb.toString()
    }

    /**
     * Turns the raw bytes into one character per byte (Latin-1). This keeps every byte's value
     * intact, so binary media is still recognized by its control bytes and text manifests decode
     * normally.
     */
    internal fun decodeSample(bytes: ByteArray, length: Int): String {
        val chars = CharArray(length.coerceAtMost(bytes.size))
        for (i in chars.indices) chars[i] = (bytes[i].toInt() and 0xFF).toChar()
        return chars.concatToString()
    }

    private fun verdictOf(resp: ProbeResponse): Verdict = when (resp.status) {
        in 200..299 -> when (classifySample(resp.sample)) {
            SampleKind.Media -> Verdict.Alive
            SampleKind.ErrorPage -> Verdict.Dead
            SampleKind.Unclear ->
                // The connection was accepted but no bytes arrived in time. Live encoders may
                // buffer before the first frame, and an empty 2xx body is not proof of death.
                if (resp.contentType.isMediaContentType()) Verdict.Alive else Verdict.Unknown
        }

        // Strong, unambiguous answers from the origin server (or its CDN).
        401, 403, 404, 410 -> Verdict.Dead

        // Range/format problems and transient failures say nothing about the stream itself.
        else -> Verdict.Unknown
    }

    private fun String?.isMediaContentType(): Boolean {
        val value = this?.lowercase() ?: return false
        return mediaContentTypes.any { value.contains(it) }
    }

    /**
     * A payload that starts with an HLS/DASH manifest header is a live stream even if it carries
     * words like "error" or an expiry timestamp further down. Binary media (MPEG-TS, MP4, …) is
     * always trusted: arbitrary bytes would otherwise randomly match error words. Only textual
     * payloads are checked for dead-page tokens.
     */
    internal fun looksAlive(sample: String): Boolean {
        val trimmed = sample.trimStart('\uFEFF', ' ', '\t', '\r', '\n')
        if (trimmed.isEmpty()) return false
        return classifySample(sample) != SampleKind.ErrorPage
    }

    private fun classifySample(sample: String): SampleKind {
        val trimmed = sample.trimStart('\uFEFF', ' ', '\t', '\r', '\n')
        if (trimmed.isEmpty()) return SampleKind.Unclear
        val lower = trimmed.lowercase()

        if (lower.startsWith("#extm3u")) return SampleKind.Media

        // XML-based streaming manifests: DASH (MPD) and SmoothStreaming. These legitimately
        // start with "<?xml", which is an HTML/error prefix for every other XML document.
        if (lower.contains("<mpd") || lower.contains("<smoothstreamingmedia")) {
            return SampleKind.Media
        }

        // Raw media containers and transport streams. Random binary bytes must never be matched
        // against dead-page words.
        if (isBinary(trimmed)) return SampleKind.Media

        if (htmlPagePrefixes.any { lower.startsWith(it) }) return SampleKind.ErrorPage

        val check = lower.take(512)
        if (deadPageTokens.any { check.contains(it) }) return SampleKind.ErrorPage

        // Plain text that is not an error page is unusual but not proof of death.
        return SampleKind.Unclear
    }

    /** Whether the start of the sample looks like raw media rather than a text document. */
    private fun isBinary(sample: String): Boolean {
        val probe = sample.take(256)
        if (probe.isEmpty()) return false
        var control = 0
        for (ch in probe) {
            val code = ch.code
            if (code == 0 || code in 1..8 || code in 11..12 || code in 14..31 || code in 127..159) {
                control++
            }
        }
        return control >= 2
    }

    fun clearCache() {
        scope.launch { cacheLock.withLock { cache.clear() } }
    }
}
