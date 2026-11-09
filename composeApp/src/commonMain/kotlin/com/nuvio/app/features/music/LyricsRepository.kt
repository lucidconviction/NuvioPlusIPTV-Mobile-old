package com.nuvio.app.features.music

import com.nuvio.app.core.network.createApiHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** LRCLIB: free, keyless synced and plain lyrics. */
internal class LyricsRepository(
    private val client: HttpClient = createApiHttpClient(),
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val cache = java.util.Collections.synchronizedMap(HashMap<String, LyricsData>())
    private val lrcLine = Regex("""^\[(\d{2}):(\d{2})\.?(\d{2,3})?](.*)$""")

    suspend fun fetch(track: MusicTrack): LyricsData {
        cache[track.id]?.let { return it }
        val result = withTimeoutOrNull(8_000L) { load(track) } ?: LyricsData(track.id, false, "")
        cache[track.id] = result
        return result
    }

    private suspend fun load(track: MusicTrack): LyricsData {
        val exact = runCatching {
            val params = mutableMapOf(
                "artist_name" to track.artist,
                "track_name" to track.title,
            )
            if (track.album.isNotBlank() && track.album != "Single") params["album_name"] = track.album
            if (track.durationSeconds > 0) params["duration"] = track.durationSeconds.toString()
            get("/api/get", params)
        }.getOrNull()
        parseExact(track, exact)?.let { return it }

        val cleanedTitle = track.title
            .replace(Regex("""\([^)]*\)"""), "")
            .replace(Regex("""\[[^]]*]"""), "")
            .replace(Regex(""" - .*"""), "")
            .replace(Regex("""(?i)\b(feat|ft)\..*"""), "")
            .trim()
        val search = runCatching {
            getList("/api/search", mapOf("q" to "${track.artist} $cleanedTitle"))
        }.getOrNull()
        search?.firstOrNull()?.let { parseExact(track, it) }?.let { return it }
        return LyricsData(track.id, false, "")
    }

    private fun parseExact(track: MusicTrack, obj: JsonObject?): LyricsData? {
        if (obj == null) return null
        val synced = obj["syncedLyrics"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
        val plain = obj["plainLyrics"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
        if (synced == null && plain == null) return null
        val lines = synced?.let(::parseLrc).orEmpty()
        return LyricsData(
            trackId = track.id,
            isSynced = lines.isNotEmpty(),
            plainLyrics = plain ?: synced.orEmpty(),
            syncedLines = lines,
        )
    }

    private fun parseLrc(content: String): List<SyncedLyricLine> {
        val lines = content.lineSequence().mapNotNull { raw ->
            val match = lrcLine.matchEntire(raw.trim()) ?: return@mapNotNull null
            val minutes = match.groupValues[1].toIntOrNull() ?: return@mapNotNull null
            val seconds = match.groupValues[2].toIntOrNull() ?: return@mapNotNull null
            val fraction = match.groupValues[3]
            val millis = when (fraction.length) {
                2 -> (fraction.toIntOrNull() ?: 0) * 10
                3 -> fraction.toIntOrNull() ?: 0
                else -> 0
            }
            SyncedLyricLine(
                timestampMs = minutes * 60_000L + seconds * 1_000L + millis,
                text = match.groupValues[4].trim(),
            )
        }.sortedBy { it.timestampMs }.toList()
        return lines
    }

    private suspend fun get(path: String, params: Map<String, String>): JsonObject = client.get("https://lrclib.net$path") {
        params.forEach { (key, value) -> parameter(key, value) }
        header("User-Agent", "Nuvio/1.0 (music lyrics)")
    }.bodyAsText().let { json.parseToJsonElement(it).jsonObject }

    private suspend fun getList(path: String, params: Map<String, String>): List<JsonObject> =
        client.get("https://lrclib.net$path") {
            params.forEach { (key, value) -> parameter(key, value) }
            header("User-Agent", "Nuvio/1.0 (music lyrics)")
        }.bodyAsText().let { text ->
            (json.parseToJsonElement(text) as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
        }
}
