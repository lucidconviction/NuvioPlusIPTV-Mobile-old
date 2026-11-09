package com.nuvio.app.features.music

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

actual object MusicPlatform {
    private const val PREFERENCES_NAME = "nuvio_music"
    private const val LIBRARY_KEY = "music_library"
    private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36"

    private var preferences: SharedPreferences? = null
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    private var appContext: Context? = null

    fun initialize(context: Context) {
        appContext = context.applicationContext
        preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    }

    actual fun loadLibrary(): String? = preferences?.getString(LIBRARY_KEY, null)

    actual fun saveLibrary(json: String) {
        preferences?.edit()?.putString(LIBRARY_KEY, json)?.apply()
    }

    actual suspend fun downloadAudio(url: String, id: String, headers: Map<String, String>): String? =
        withContext(Dispatchers.IO) {
            val context = appContext ?: return@withContext null
            val dir = File(context.filesDir, "music").apply { mkdirs() }
            val safeId = id.replace(Regex("""[^A-Za-z0-9._-]"""), "_").take(80).ifBlank { "track" }
            val existing = listOf(File(dir, "$safeId.m4a"), File(dir, "$safeId.webm"))
                .firstOrNull { it.isFile && it.length() > 1024L }
            if (existing != null) return@withContext existing.absolutePath
            val tmp = File(dir, "$safeId.part")
            if (tmp.exists()) tmp.delete()
            try {
                val builder = Request.Builder()
                    .url(url)
                    .header("User-Agent", headers["User-Agent"] ?: USER_AGENT)
                    .header("Accept", "*/*")
                // YouTube's direct stream links throttle slow whole-file downloads; a range request
                // keeps the transfer at full speed.
                if (headers["Range"] == null) builder.header("Range", "bytes=0-")
                client.newCall(builder.build()).execute().use { response ->
                    if (!response.isSuccessful) return@withContext null
                    val body = response.body ?: return@withContext null
                    body.byteStream().use { input ->
                        tmp.outputStream().use { output -> input.copyTo(output, 64 * 1024) }
                    }
                    if (tmp.length() <= 1024L) {
                        tmp.delete()
                        return@withContext null
                    }
                    val extension = when {
                        response.header("Content-Type").orEmpty().contains("webm", ignoreCase = true) -> "webm"
                        else -> "m4a"
                    }
                    val file = File(dir, "$safeId.$extension")
                    if (file.exists()) file.delete()
                    if (!tmp.renameTo(file)) {
                        tmp.delete()
                        return@withContext null
                    }
                    file.absolutePath
                }
            } catch (_: Exception) {
                tmp.delete()
                null
            }
        }

    actual fun fileExists(path: String): Boolean = runCatching { File(path).isFile }.getOrDefault(false)

    actual fun deleteFile(path: String) {
        runCatching { File(path).delete() }
    }
}
