package com.nuvio.app.features.livetv.ui.quickchannels

import com.nuvio.app.core.network.createApiHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException

/**
 * Lightweight stream liveness probe used by [QuickChannelsSheet].
 *
 * Tries a Range request (bytes=0-1023) first, falls back to a full HEAD-less GET. Reads the
 * first [SAMPLE_SIZE] bytes and rejects HTML-like responses, dead-page tokens, or timeouts.
 * Results are cached in-memory for [TTL_MS] so repeated open/close of the sheet does not
 * re-probe every URL immediately.
 */
internal object QuickStreamChecker {

    const val TTL_MS = 30 * 60 * 1000L
    private const val TIMEOUT_MS = 6_000L
    private const val MAX_CONCURRENCY = 16
    private const val SAMPLE_SIZE = 1024
    private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36"

    private val htmlPagePrefixes = listOf(
        "<!doctype html", "<!doctype", "<html", "<!", "<?xml",
    )
    private val deadPageTokens = listOf(
        "404", "403", "401", "not found", "forbidden", "unauthorized",
        "invalid user", "invalid username", "account disabled", "account suspended",
        "credits expired", "expired", "offline", "service unavailable",
        "bad gateway", "internal server error", "error", "failed to open stream",
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val client: HttpClient = createApiHttpClient()

    private data class Entry(val alive: Boolean, val checkedAt: Long)

    private val cache = mutableMapOf<String, Entry>()

    suspend fun checkBatch(
        urls: List<String>,
        onProgress: (checked: Int, total: Int) -> Unit = { _, _ -> },
    ): Map<String, Boolean> {
        val semaphore = Semaphore(MAX_CONCURRENCY)
        if (urls.isEmpty()) return emptyMap()
        val results = mutableMapOf<String, Boolean>()
        coroutineScope {
            urls.forEach { url ->
                launch {
                    val allowed = semaphore.withPermit { checkUrl(url) }
                    results[url] = allowed
                    onProgress(urls.indexOf(url) + 1, urls.size)
                }
            }
        }
        return results
    }

    private suspend fun <T> runBlockingCached(block: suspend () -> T): T = block()

    suspend fun checkUrl(url: String): Boolean {
        val now = System.currentTimeMillis()
        val cached = cache[url]
        if (cached != null && now - cached.checkedAt < TTL_MS) return cached.alive
        val alive = probe(url)
        cache[url] = Entry(alive, now)
        return alive
    }

    private suspend fun probe(url: String): Boolean {
        val sample = probeSample(url, "bytes=0-${SAMPLE_SIZE - 1}")
            ?: probeSample(url, null)
            ?: return false
        return looksAlive(sample)
    }

    private suspend fun probeSample(url: String, range: String?): String? {
        val headers = buildList {
            add("User-Agent" to USER_AGENT)
            if (range != null) add("Range" to range)
        }.toMap()
        return runCatching {
            withContext(Dispatchers.IO) {
                withTimeoutOrNull(TIMEOUT_MS) {
                    client.prepareGet(url) {
                        headers.forEach { (k, v) -> header(k, v) }
                    }.execute { resp ->
                        if (resp.status.value in 400..599) return@execute null
                        val body = resp.bodyAsChannel()
                        val sb = StringBuilder()
                        val buf = ByteArray(SAMPLE_SIZE)
                        while (sb.length < SAMPLE_SIZE) {
                            val n = body.readAvailable(buf, 0, minOf(buf.size, SAMPLE_SIZE - sb.length))
                            if (n <= 0) break
                            sb.append(buf, 0, n)
                        }
                        sb.toString()
                    }
                }
            }
        }.getOrNull()
    }

    private fun looksAlive(sample: String): Boolean {
        val trimmed = sample.trimStart('\uFEFF', ' ', '\t', '\r', '\n')
        if (trimmed.isEmpty()) return false
        val lower = trimmed.lowercase()
        if (htmlPagePrefixes.any { lower.startsWith(it) }) return false
        val check = trimmed.take(256)
        if (deadPageTokens.any { check.contains(it, ignoreCase = true) }) return false
        return true
    }

    fun clearCache() {
        cache.clear()
    }
}
