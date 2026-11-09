package com.nuvio.app.features.music

import platform.Foundation.NSUserDefaults

actual object MusicPlatform {
    private const val LIBRARY_KEY = "nuvio_music_library"

    actual fun loadLibrary(): String? =
        NSUserDefaults.standardUserDefaults.stringForKey(LIBRARY_KEY)

    actual fun saveLibrary(json: String) {
        NSUserDefaults.standardUserDefaults.setObject(json, forKey = LIBRARY_KEY)
    }

    actual suspend fun downloadAudio(url: String, id: String, headers: Map<String, String>): String? = null

    actual fun fileExists(path: String): Boolean = false

    actual fun deleteFile(path: String) = Unit
}
