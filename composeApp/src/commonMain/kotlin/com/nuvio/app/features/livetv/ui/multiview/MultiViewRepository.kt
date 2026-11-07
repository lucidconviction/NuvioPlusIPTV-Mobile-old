package com.nuvio.app.features.livetv.ui.multiview

import com.nuvio.app.features.livetv.data.LiveTvPreferences
import com.nuvio.app.features.livetv.model.LiveChannel
import com.nuvio.app.features.livetv.model.LiveTvClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/** One stream playing in a multi-view window. */
data class MultiViewStream(
    val id: String,
    val url: String,
    val title: String,
    val subtitle: String? = null,
    val logo: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val responseHeaders: Map<String, String> = emptyMap(),
    val streamType: String? = null,
    val providerName: String = "",
    val streamTitle: String = "",
    val parentMetaId: String = "",
    val parentMetaType: String = "",
    val videoId: String? = null,
) {
    val displayTitle: String get() = title.ifBlank { streamTitle }.ifBlank { "Stream" }
}

/** How the open streams are arranged. Auto picks a grid from the number of streams. */
enum class MultiViewLayout {
    AUTO,
    GRID,
    ROW,
    COLUMN,
    FOCUS,
}

/** A layout the user named, with the streams it was saved with. */
data class SavedMultiViewLayout(
    val id: String,
    val name: String,
    val layout: MultiViewLayout,
    val streams: List<MultiViewStream>,
)

/**
 * The streams in the multi-view page. Kept above navigation so a stream sent from the player
 * stays open while the player keeps playing, and so the quick navigation badge can show on
 * every screen.
 */
object MultiViewRepository {
    const val MAX_STREAMS = 9

    private val _streams = MutableStateFlow<List<MultiViewStream>>(emptyList())
    val streams: StateFlow<List<MultiViewStream>> = _streams.asStateFlow()

    /** The most windows this screen allows: 9 on a tablet/wide layout, 4 on a phone in portrait. */
    private val _maxStreams = MutableStateFlow(MAX_STREAMS)
    val maxStreams: StateFlow<Int> = _maxStreams.asStateFlow()

    private val _layout = MutableStateFlow(MultiViewLayout.AUTO)
    val layout: StateFlow<MultiViewLayout> = _layout.asStateFlow()

    /** True while every window should be audible. */
    private val _soundAll = MutableStateFlow(true)
    val soundAll: StateFlow<Boolean> = _soundAll.asStateFlow()

    /** True while every window is paused. */
    private val _pauseAll = MutableStateFlow(false)
    val pauseAll: StateFlow<Boolean> = _pauseAll.asStateFlow()

    /** Bumped by Refresh all: panes rebuild their players from the stream URLs. */
    private val _refreshGeneration = MutableStateFlow(0)
    val refreshGeneration: StateFlow<Int> = _refreshGeneration.asStateFlow()

    private val _savedLayouts = MutableStateFlow<List<SavedMultiViewLayout>>(emptyList())
    val savedLayouts: StateFlow<List<SavedMultiViewLayout>> = _savedLayouts.asStateFlow()

    private var savedLayoutsLoaded = false

    /**
     * Puts [stream] in the next free window. Returns false when it is already open or all nine
     * windows are taken.
     */
    fun add(stream: MultiViewStream): Boolean {
        if (stream.url.isBlank()) return false
        val current = _streams.value
        if (current.any { it.url == stream.url }) return false
        if (current.size >= _maxStreams.value) return false
        _streams.value = current + stream
        return true
    }

    /** Applies the screen's window limit; closing windows past it keeps the first ones. */
    fun setMaxStreams(max: Int) {
        val limit = max.coerceIn(1, MAX_STREAMS)
        _maxStreams.value = limit
        if (_streams.value.size > limit) {
            _streams.value = _streams.value.take(limit)
        }
    }

    /** Puts a Live TV channel in the next free window. */
    fun addChannel(channel: LiveChannel): Boolean = add(
        MultiViewStream(
            id = channel.key,
            url = channel.url,
            title = channel.name,
            subtitle = channel.group,
            logo = channel.logo,
            headers = channel.headers,
            streamType = "live",
            providerName = channel.sourceName,
            streamTitle = channel.name,
            videoId = channel.key,
        ),
    )

    fun remove(id: String) {
        _streams.value = _streams.value.filterNot { it.id == id }
    }

    /** Exchanges two windows' positions, keeping both streams playing. */
    fun swap(firstId: String, secondId: String) {
        if (firstId == secondId) return
        val current = _streams.value
        val first = current.indexOfFirst { it.id == firstId }
        val second = current.indexOfFirst { it.id == secondId }
        if (first < 0 || second < 0) return
        _streams.value = current.toMutableList().also {
            it[first] = current[second]
            it[second] = current[first]
        }
    }

    fun clear() {
        _streams.value = emptyList()
        _pauseAll.value = false
    }

    fun setLayout(newLayout: MultiViewLayout) {
        _layout.value = newLayout
    }

    fun toggleSoundAll(): Boolean {
        _soundAll.value = !_soundAll.value
        return _soundAll.value
    }

    fun togglePauseAll(): Boolean {
        _pauseAll.value = !_pauseAll.value
        return _pauseAll.value
    }

    fun setPaused(paused: Boolean) {
        _pauseAll.value = paused
    }

    fun refreshAll() {
        _refreshGeneration.value += 1
    }

    /** Saves the open streams and the current layout under [name]. */
    fun saveLayout(name: String): SavedMultiViewLayout {
        ensureSavedLayoutsLoaded()
        val layout = SavedMultiViewLayout(
            id = "layout-${LiveTvClock.nowMs()}",
            name = name.trim().ifBlank { "Layout" },
            layout = _layout.value,
            streams = _streams.value,
        )
        val updated = _savedLayouts.value + layout
        _savedLayouts.value = updated
        persistSavedLayouts(updated)
        return layout
    }

    fun deleteSavedLayout(id: String) {
        ensureSavedLayoutsLoaded()
        val updated = _savedLayouts.value.filterNot { it.id == id }
        _savedLayouts.value = updated
        persistSavedLayouts(updated)
    }

    /** Replaces the open windows with a saved layout. */
    fun restore(layout: SavedMultiViewLayout) {
        _streams.value = layout.streams.take(MAX_STREAMS)
        _layout.value = layout.layout
        _pauseAll.value = false
    }

    fun ensureSavedLayoutsLoaded() {
        if (savedLayoutsLoaded) return
        savedLayoutsLoaded = true
        val raw = LiveTvPreferences.currentMultiViewLayouts() ?: return
        _savedLayouts.value = runCatching {
            Json.parseToJsonElement(raw).jsonArray.mapNotNull { element ->
                val o = element.jsonObject
                val name = o.string("name")
                if (name.isBlank()) return@mapNotNull null
                SavedMultiViewLayout(
                    id = o.string("id").ifBlank { "layout-${name.hashCode()}" },
                    name = name,
                    layout = MultiViewLayout.entries.firstOrNull { it.name == o.string("layout") } ?: MultiViewLayout.AUTO,
                    streams = o["streams"]?.jsonArray?.mapNotNull { decodeStream(it as? JsonObject ?: return@mapNotNull null) }.orEmpty(),
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun persistSavedLayouts(layouts: List<SavedMultiViewLayout>) {
        val json = buildJsonArray {
            layouts.forEach { layout ->
                add(buildJsonObject {
                    put("id", layout.id)
                    put("name", layout.name)
                    put("layout", layout.layout.name)
                    put("streams", buildJsonArray { layout.streams.forEach { add(encodeStream(it)) } })
                })
            }
        }
        runCatching { LiveTvPreferences.saveMultiViewLayouts(json.toString()) }
    }

    private fun encodeStream(stream: MultiViewStream): JsonObject = buildJsonObject {
        put("id", stream.id)
        put("url", stream.url)
        put("title", stream.title)
        put("subtitle", stream.subtitle)
        put("logo", stream.logo)
        put("streamType", stream.streamType)
        put("providerName", stream.providerName)
        put("streamTitle", stream.streamTitle)
        put("parentMetaId", stream.parentMetaId)
        put("parentMetaType", stream.parentMetaType)
        put("videoId", stream.videoId)
        put("headers", buildJsonObject { stream.headers.forEach { (k, v) -> put(k, v) } })
        put("responseHeaders", buildJsonObject { stream.responseHeaders.forEach { (k, v) -> put(k, v) } })
    }

    private fun decodeStream(o: JsonObject): MultiViewStream? {
        val url = o.string("url")
        if (url.isBlank()) return null
        return MultiViewStream(
            id = o.string("id").ifBlank { url },
            url = url,
            title = o.string("title"),
            subtitle = o.string("subtitle").ifBlank { null },
            logo = o.string("logo").ifBlank { null },
            headers = o.objectStrings("headers"),
            responseHeaders = o.objectStrings("responseHeaders"),
            streamType = o.string("streamType").ifBlank { null },
            providerName = o.string("providerName"),
            streamTitle = o.string("streamTitle"),
            parentMetaId = o.string("parentMetaId"),
            parentMetaType = o.string("parentMetaType"),
            videoId = o.string("videoId").ifBlank { null },
        )
    }

    private fun JsonObject.string(key: String): String =
        (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()

    private fun JsonObject.objectStrings(key: String): Map<String, String> =
        (this[key] as? JsonObject)?.entries?.mapNotNull { (k, v) ->
            (v as? JsonPrimitive)?.contentOrNull?.let { k to it }
        }?.toMap().orEmpty()
}
