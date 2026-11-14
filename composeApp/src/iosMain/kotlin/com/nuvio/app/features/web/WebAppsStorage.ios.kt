package com.nuvio.app.features.web

internal actual val webAppsPlatformAvailable: Boolean = false

internal actual object WebAppsStorage {
    actual fun loadSites(): String? = null
    actual fun saveSites(json: String) = Unit
    actual fun loadUserscripts(): String? = null
    actual fun saveUserscripts(json: String) = Unit
    actual fun loadSettings(): String? = null
    actual fun saveSettings(json: String) = Unit
    actual fun getUserscriptValue(scriptId: String, key: String): String? = null
    actual fun setUserscriptValue(scriptId: String, key: String, value: String) = Unit
    actual fun deleteUserscriptValue(scriptId: String, key: String) = Unit
    actual fun listUserscriptValues(scriptId: String): String = ""
}
