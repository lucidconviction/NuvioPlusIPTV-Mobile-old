package com.nuvio.app.features.web

import kotlinx.serialization.Serializable

/** A saved website or web app shown on the Web-Apps home. */
@Serializable
data class SavedWebApp(
    val id: String,
    val name: String,
    val url: String,
    /** Optional custom icon; otherwise the site's favicon is used. */
    val iconUrl: String? = null,
    /** Local screenshot of the site's home page, captured on first open. */
    val thumbnailPath: String? = null,
    /** Changes whenever [thumbnailPath] is recaptured, so image caches reload it. */
    val thumbnailUpdatedAtMs: Long = 0L,
    val createdAtMs: Long = 0L,
)

/** A Tampermonkey-style script that runs inside the WebView only. */
@Serializable
data class WebUserscript(
    val id: String,
    val name: String,
    val enabled: Boolean = true,
    val code: String,
)

@Serializable
data class WebAppsSettings(
    val popupProtection: Boolean = true,
    /** Block main-frame navigations away from the current site (ad hijacks). */
    val redirectProtection: Boolean = true,
    val adBlockEnabled: Boolean = true,
    /**
     * Request desktop pages. Off by default: the stock Android WebView user agent is what
     * Cloudflare's challenge expects, and a mismatched desktop UA puts many streaming sites
     * into a reload loop.
     */
    val desktopMode: Boolean = false,
    val homeUrl: String = "https://www.google.com",
    /** Hosts the user allows for cross-site redirects and popups (subdomains included). */
    val allowedHosts: Set<String> = emptySet(),
    /** Hosts the user chose "Block always" for redirects/popups. */
    val blockedRedirectHosts: Set<String> = emptySet(),
    val blockedPopupHosts: Set<String> = emptySet(),
    /** Sites where ad blocking is disabled because it broke the page. */
    val adBlockWhitelist: Set<String> = emptySet(),
)

/** One detected, playable web source. */
data class WebMediaSource(
    val url: String,
    val pageUrl: String,
    val title: String,
    val headers: Map<String, String> = emptyMap(),
    /** "hls", "dash" or null. */
    val streamType: String? = null,
    val isHls: Boolean = false,
    val isDash: Boolean = false,
    /** Duration when the page or manifest exposed it; used to rank ads below the real video. */
    val durationMs: Long? = null,
    val width: Int? = null,
    val height: Int? = null,
    val bandwidth: Long? = null,
    /** True when this is an HLS master playlist. */
    val isMaster: Boolean = false,
    /** How many HLS variants were collapsed into this entry by the picker. */
    val variantCount: Int = 1,
    /** A short/small source; shown with an "Ad?" badge and sorted last, never hidden. */
    val likelyAd: Boolean = false,
)

internal fun webMediaScore(source: WebMediaSource): Long {
    val durationSeconds = (source.durationMs ?: 0L) / 1_000L
    val pixels = (source.width ?: 0).toLong() * (source.height ?: 0).toLong()
    var score = durationSeconds + pixels / 1_000L + (source.bandwidth ?: 0L) / 100_000L
    if (source.isMaster) score += 5_000L
    if (source.isHls) score += 100L
    if (source.isDash) score += 90L
    if (source.likelyAd) score -= 1_000_000L
    return score
}

internal fun webMediaDurationLabel(durationMs: Long?): String? {
    val totalSeconds = (durationMs ?: 0L) / 1_000L
    if (totalSeconds <= 0L) return null
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "$hours:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    } else {
        "$minutes:${seconds.toString().padStart(2, '0')}"
    }
}

data class WebBrowserUiState(
    val url: String = "",
    val title: String = "",
    val loading: Boolean = false,
    val progress: Float = 0f,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val detectedMedia: List<WebMediaSource> = emptyList(),
    val error: String? = null,
    /** Bumped when Android kills the WebView renderer, so the host creates a fresh WebView. */
    val rendererGeneration: Int = 0,
)

/** What the common UI needs from the Android WebView. */
interface WebBrowserController {
    val state: kotlinx.coroutines.flow.StateFlow<WebBrowserUiState>
    fun load(url: String)
    fun goBack()
    fun goForward()
    fun reload()
    fun stopLoading()
    fun destroy()
    fun pause() {}
    fun resume() {}
    fun applySettings(settings: WebAppsSettings, scripts: List<WebUserscript>)
    /** Downloads the optional hosts lists in the background. */
    fun updateAdblockLists() {}
    /** Re-scans the current page for playable media (useful for blob/MSE players). */
    fun scanForMedia() {}
    /** Plays a detected source inside the WebView instead of handing it to Nuvio's player. */
    fun playInBrowser(source: WebMediaSource) {}
    /** Captures the next finished page as this saved site's screenshot. */
    fun beginThumbnailCapture(siteId: String) {}
    /** Receives the saved file path of a captured site screenshot. */
    fun setThumbnailCaptureListener(listener: ((siteId: String, path: String) -> Unit)?) {}
}

internal expect val webAppsPlatformAvailable: Boolean

internal expect object WebAppsStorage {
    fun loadSites(): String?
    fun saveSites(json: String)
    fun loadUserscripts(): String?
    fun saveUserscripts(json: String)
    fun loadSettings(): String?
    fun saveSettings(json: String)
    fun getUserscriptValue(scriptId: String, key: String): String?
    fun setUserscriptValue(scriptId: String, key: String, value: String)
    fun deleteUserscriptValue(scriptId: String, key: String)
    fun listUserscriptValues(scriptId: String): String
}

internal fun normalizeWebUrl(raw: String): String? {
    var value = raw.trim()
    if (value.isEmpty()) return null
    if (!value.startsWith("http://", true) && !value.startsWith("https://", true)) {
        value = "https://$value"
    }
    val host = value
        .substringAfter("://")
        .substringBefore('/')
        .substringBefore('?')
        .substringBefore('#')
        .substringAfter('@')
        .substringBefore(':')
        .lowercase()
    if (host.isBlank()) return null
    val validHost = host == "localhost" ||
        host.matches(Regex("""\d{1,3}(\.\d{1,3}){3}""")) ||
        (host.contains('.') && !host.startsWith('.') && !host.endsWith('.'))
    return if (validHost) value else null
}

internal fun webHost(url: String): String =
    url.substringAfter("://").substringBefore('/').substringBefore('?').substringAfter('@').substringBefore(':')

internal fun normalizeWebHost(raw: String): String? {
    val value = raw.trim()
        .removePrefix("http://")
        .removePrefix("https://")
        .substringBefore('/')
        .substringBefore('?')
        .substringBefore('#')
        .trim()
        .trimEnd('.')
        .lowercase()
        .replace(Regex(""":\d+$"""), "")
    return value.takeIf {
        it.isNotBlank() && it.length <= 253 && !it.contains(' ') &&
            (it == "localhost" || it.contains('.'))
    }
}

internal fun webAppFallbackName(url: String): String =
    webHost(url).removePrefix("www.").ifBlank { url.take(40) }

internal fun webFaviconUrl(url: String): String? {
    val host = webHost(url).takeIf { it.isNotBlank() } ?: return null
    return "https://www.google.com/s2/favicons?domain=$host&sz=128"
}

/**
 * Remote preview used until the site has been opened once. The first open replaces it with a
 * locally captured screenshot of the site's actual home page.
 */
internal fun webAppPreviewUrl(url: String): String =
    "https://image.thum.io/get/width/800/crop/500/$url"
