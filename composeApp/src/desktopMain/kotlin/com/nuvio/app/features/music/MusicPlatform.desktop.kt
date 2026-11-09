package com.nuvio.app.features.music

import java.util.prefs.Preferences

actual object MusicPlatform {
    private const val LIBRARY_KEY = "nuvio_music_library"
    private val preferences: Preferences by lazy {
        Preferences.userNodeForPackage(MusicPlatform::class.java)
    }

    actual fun loadLibrary(): String? = preferences.get(LIBRARY_KEY, null)

    actual fun saveLibrary(json: String) {
        preferences.put(LIBRARY_KEY, json)
    }

    actual suspend fun downloadAudio(url: String, id: String, headers: Map<String, String>): String? = null

    actual fun fileExists(path: String): Boolean = false

    actual fun deleteFile(path: String) = Unit
}
