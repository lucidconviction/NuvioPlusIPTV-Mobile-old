package com.nuvio.app.features.web

import android.content.Context
import android.content.SharedPreferences

internal actual val webAppsPlatformAvailable: Boolean = true

internal actual object WebAppsStorage {
    private const val PREFS = "web_apps_prefs"
    private const val KEY_SITES = "sites"
    private const val KEY_SCRIPTS = "userscripts"
    private const val KEY_SETTINGS = "settings"
    private const val GM_PREFS = "web_userscript_values"

    private var prefs: SharedPreferences? = null
    private var gmPrefs: SharedPreferences? = null

    fun initialize(context: Context) {
        val app = context.applicationContext
        prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        gmPrefs = app.getSharedPreferences(GM_PREFS, Context.MODE_PRIVATE)
    }

    actual fun loadSites(): String? = prefs?.getString(KEY_SITES, null)
    actual fun saveSites(json: String) {
        prefs?.edit()?.putString(KEY_SITES, json)?.apply()
    }
    actual fun loadUserscripts(): String? = prefs?.getString(KEY_SCRIPTS, null)
    actual fun saveUserscripts(json: String) {
        prefs?.edit()?.putString(KEY_SCRIPTS, json)?.apply()
    }
    actual fun loadSettings(): String? = prefs?.getString(KEY_SETTINGS, null)
    actual fun saveSettings(json: String) {
        prefs?.edit()?.putString(KEY_SETTINGS, json)?.apply()
    }

    actual fun getUserscriptValue(scriptId: String, key: String): String? =
        gmPrefs?.getString("$scriptId::$key", null)

    actual fun setUserscriptValue(scriptId: String, key: String, value: String) {
        gmPrefs?.edit()?.putString("$scriptId::$key", value)?.apply()
    }

    actual fun deleteUserscriptValue(scriptId: String, key: String) {
        gmPrefs?.edit()?.remove("$scriptId::$key")?.apply()
    }

    actual fun listUserscriptValues(scriptId: String): String {
        val prefix = "$scriptId::"
        return gmPrefs?.all?.keys.orEmpty()
            .filter { it.startsWith(prefix) }
            .map { it.removePrefix(prefix) }
            .joinToString("\n")
    }
}
