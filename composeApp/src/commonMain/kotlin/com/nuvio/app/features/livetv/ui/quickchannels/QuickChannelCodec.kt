package com.nuvio.app.features.livetv.ui.quickchannels

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** The user-added regions and quick channels saved on this device. */
internal data class StoredQuickChannels(
    val categories: List<QuickChannelList.QuickCategory> = emptyList(),
    val channels: List<QuickChannel> = emptyList(),
)

/**
 * The JSON stored under `quick_channels`. It is deliberately small and version-free: older builds
 * read the same keys, and unknown keys are ignored. Blank ids/names are dropped so a half-filled
 * dialog can never come back as a ghost row after a restart.
 */
internal object QuickChannelCodec {

    fun encode(categories: List<QuickChannelList.QuickCategory>, channels: List<QuickChannel>): String =
        buildJsonObject {
            put("categories", buildJsonArray {
                categories.forEach { category ->
                    add(buildJsonObject {
                        put("id", category.id)
                        put("title", category.title)
                        put("description", category.description)
                        put("tags", buildJsonArray { category.tags.forEach { add(it) } })
                    })
                }
            })
            put("channels", buildJsonArray {
                channels.forEach { channel ->
                    add(buildJsonObject {
                        put("displayName", channel.displayName)
                        put("aliases", buildJsonArray { channel.aliases.forEach { add(it) } })
                        put("regions", buildJsonArray { channel.regions.forEach { add(it) } })
                        put("tags", buildJsonArray { channel.tags.forEach { add(it) } })
                    })
                }
            })
        }.toString()

    fun decode(raw: String?): StoredQuickChannels {
        if (raw.isNullOrBlank()) return StoredQuickChannels()
        return runCatching {
            val root = Json.parseToJsonElement(raw).jsonObject
            val categories = root["categories"]?.asArray().orEmpty().mapNotNull { element ->
                val o = element.jsonObject
                val id = o.string("id")
                if (id.isBlank()) return@mapNotNull null
                QuickChannelList.QuickCategory(
                    id = id,
                    title = o.string("title").ifBlank { id },
                    description = o.string("description"),
                    tags = o.strings("tags"),
                )
            }.distinctBy { it.id }
            val channels = root["channels"]?.asArray().orEmpty().mapNotNull { element ->
                val o = element.jsonObject
                val name = o.string("displayName").trim()
                if (name.isBlank()) return@mapNotNull null
                QuickChannel(
                    displayName = name,
                    aliases = o.strings("aliases"),
                    regions = o.strings("regions"),
                    tags = o.strings("tags"),
                )
            }.distinctBy { it.displayName }
            StoredQuickChannels(categories = categories, channels = channels)
        }.getOrDefault(StoredQuickChannels())
    }

    private fun JsonObject.string(key: String): String =
        (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()

    private fun JsonObject.strings(key: String): List<String> =
        (this[key] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()

    private fun kotlinx.serialization.json.JsonElement?.asArray(): List<kotlinx.serialization.json.JsonElement> =
        this as? JsonArray ?: emptyList()
}
