package com.nuvio.app.features.music

/** Device storage and optional audio downloads for the Music page. */
internal expect object MusicPlatform {
    /** The saved library JSON, or null when nothing is stored yet. */
    fun loadLibrary(): String?

    fun saveLibrary(json: String)

    /** Downloads a direct audio URL to app storage and returns its local path, or null on failure. */
    suspend fun downloadAudio(url: String, id: String, headers: Map<String, String>): String?

    fun fileExists(path: String): Boolean

    /** Removes a downloaded file from device storage; a no-op where downloads are unavailable. */
    fun deleteFile(path: String)
}
