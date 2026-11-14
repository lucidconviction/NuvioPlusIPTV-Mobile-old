package com.nuvio.app.features.web

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.random.Random

/**
 * Saved Web-Apps sites, userscripts and browser settings. Kept on the device, like the browser
 * itself; the Web-Apps tab is Android-only until the WKWebView port exists.
 */
object WebAppsRepository {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }
    private var loaded = false

    private val _sites = MutableStateFlow<List<SavedWebApp>>(emptyList())
    val sites: StateFlow<List<SavedWebApp>> = _sites.asStateFlow()

    private val _userscripts = MutableStateFlow<List<WebUserscript>>(emptyList())
    val userscripts: StateFlow<List<WebUserscript>> = _userscripts.asStateFlow()

    private val _settings = MutableStateFlow(WebAppsSettings())
    val settings: StateFlow<WebAppsSettings> = _settings.asStateFlow()

    val available: Boolean get() = webAppsPlatformAvailable

    fun ensureLoaded() {
        if (loaded) return
        loaded = true
        _sites.value = decodeSites(WebAppsStorage.loadSites())
        _userscripts.value = decodeScripts(WebAppsStorage.loadUserscripts())
        _settings.value = runCatching {
            WebAppsStorage.loadSettings()?.takeIf { it.isNotBlank() }?.let {
                json.decodeFromString(WebAppsSettings.serializer(), it)
            }
        }.getOrNull() ?: WebAppsSettings()
    }

    fun addSite(name: String, url: String): SavedWebApp? {
        ensureLoaded()
        val normalized = normalizeWebUrl(url) ?: return null
        val cleanName = name.trim().ifBlank { webAppFallbackName(normalized) }
        val site = SavedWebApp(
            id = newId("web"),
            name = cleanName,
            url = normalized,
            createdAtMs = kotlin.time.Clock.System.now().toEpochMilliseconds(),
        )
        _sites.value = _sites.value.filterNot { it.url.equals(normalized, ignoreCase = true) } + site
        persistSites()
        return site
    }

    fun updateSite(site: SavedWebApp): Boolean {
        ensureLoaded()
        val normalized = normalizeWebUrl(site.url) ?: return false
        val existing = _sites.value.firstOrNull { it.id == site.id } ?: return false
        val urlChanged = !existing.url.equals(normalized, ignoreCase = true)
        _sites.value = _sites.value.map {
            if (it.id == site.id) {
                site.copy(
                    url = normalized,
                    name = site.name.trim().ifBlank { webAppFallbackName(normalized) },
                    thumbnailPath = if (urlChanged) null else site.thumbnailPath ?: existing.thumbnailPath,
                )
            } else {
                it
            }
        }
        persistSites()
        return true
    }

    /** Stores the locally captured home-page screenshot used by the saved-site card. */
    fun updateSiteThumbnail(id: String, path: String) {
        ensureLoaded()
        val current = _sites.value.firstOrNull { it.id == id } ?: return
        val now = kotlin.time.Clock.System.now().toEpochMilliseconds()
        _sites.value = _sites.value.map {
            if (it.id == id) {
                it.copy(thumbnailPath = path, thumbnailUpdatedAtMs = now)
            } else {
                it
            }
        }
        persistSites()
    }

    fun removeSite(id: String) {
        ensureLoaded()
        _sites.value = _sites.value.filterNot { it.id == id }
        persistSites()
    }

    fun addUserscript(name: String, code: String): WebUserscript? {
        ensureLoaded()
        if (code.isBlank()) return null
        val script = WebUserscript(
            id = newId("us"),
            name = name.trim().ifBlank { WebUserscriptManager.parse(code).name },
            code = code,
        )
        _userscripts.value = _userscripts.value + script
        persistUserscripts()
        return script
    }

    fun updateUserscript(script: WebUserscript) {
        ensureLoaded()
        _userscripts.value = _userscripts.value.map { if (it.id == script.id) script else it }
        persistUserscripts()
    }

    fun removeUserscript(id: String) {
        ensureLoaded()
        _userscripts.value = _userscripts.value.filterNot { it.id == id }
        persistUserscripts()
    }

    fun updateSettings(settings: WebAppsSettings) {
        ensureLoaded()
        _settings.value = settings
        WebAppsStorage.saveSettings(json.encodeToString(WebAppsSettings.serializer(), settings))
    }

    fun gmGet(scriptId: String, key: String): String? =
        WebAppsStorage.getUserscriptValue(scriptId, key)

    fun gmSet(scriptId: String, key: String, value: String) =
        WebAppsStorage.setUserscriptValue(scriptId, key, value)

    fun gmDelete(scriptId: String, key: String) =
        WebAppsStorage.deleteUserscriptValue(scriptId, key)

    fun gmList(scriptId: String): String =
        WebAppsStorage.listUserscriptValues(scriptId)

    private fun persistSites() {
        WebAppsStorage.saveSites(json.encodeToString(ListSerializer(SavedWebApp.serializer()), _sites.value))
    }

    private fun persistUserscripts() {
        WebAppsStorage.saveUserscripts(json.encodeToString(ListSerializer(WebUserscript.serializer()), _userscripts.value))
    }

    private fun decodeSites(raw: String?): List<SavedWebApp> =
        runCatching {
            raw?.takeIf { it.isNotBlank() }?.let {
                json.decodeFromString(ListSerializer(SavedWebApp.serializer()), it)
            }.orEmpty()
        }.getOrDefault(emptyList())

    private fun decodeScripts(raw: String?): List<WebUserscript> =
        runCatching {
            raw?.takeIf { it.isNotBlank() }?.let {
                json.decodeFromString(ListSerializer(WebUserscript.serializer()), it)
            }.orEmpty()
        }.getOrDefault(emptyList())

    private fun newId(prefix: String): String {
        val alphabet = "0123456789abcdefghijklmnopqrstuvwxyz"
        return prefix + "_" + buildString(14) { repeat(14) { append(alphabet[Random.nextInt(alphabet.length)]) } }
    }
}
