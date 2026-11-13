package com.nuvio.app.features.livetv.virtual

import com.nuvio.app.features.livetv.data.optBoolean
import com.nuvio.app.features.livetv.data.optInt
import com.nuvio.app.features.livetv.data.optJSONArray
import com.nuvio.app.features.livetv.data.optJSONObject
import com.nuvio.app.features.livetv.data.optLong
import com.nuvio.app.features.livetv.data.optString
import com.nuvio.app.features.livetv.data.parseObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The JSON codec for Lucid TV's channels and pool snapshots. The shapes match the TV app's
 * `virtual_channels` data (and Debrify's schema), so a setup can be exported here, imported on a
 * TV, and scheduled the same way. Unknown fields are ignored on import.
 */
object VirtualChannelCodec {

    // ------------------------------------------------------------------ specs

    fun encodeSpecs(list: List<VirtualChannelSpec>): String = buildJsonArray {
        list.forEach { add(encodeSpec(it)) }
    }.toString()

    fun decodeSpecs(raw: String?): List<VirtualChannelSpec> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = com.nuvio.app.features.livetv.data.liveTvJson.parseToJsonElement(raw) as JsonArray
            array.mapNotNull { element -> (element as? JsonObject)?.let(::decodeSpec) }
        }.getOrDefault(emptyList())
    }

    fun encodePack(pack: VirtualChannelPack): String = buildJsonObject {
        put("schemaVersion", pack.schemaVersion)
        put("exportedAt", pack.exportedAtMs)
        put("channels", buildJsonArray { pack.channels.forEach { add(encodeSpec(it)) } })
    }.toString()

    fun decodePack(raw: String): VirtualChannelPack {
        val element = com.nuvio.app.features.livetv.data.liveTvJson.parseToJsonElement(raw)
        if (element is JsonArray) {
            return VirtualChannelPack(
                channels = element.mapNotNull { (it as? JsonObject)?.let(::decodeSpec) },
            )
        }
        val root = element as JsonObject
        val channels = root.optJSONArray("channels")
            ?.mapNotNull { (it as? JsonObject)?.let(::decodeSpec) }
            ?: emptyList()
        return VirtualChannelPack(
            schemaVersion = root.optInt("schemaVersion", 1).coerceAtLeast(1),
            exportedAtMs = root.optLong("exportedAt"),
            channels = channels,
        )
    }

    fun encodeSpec(spec: VirtualChannelSpec): JsonObject = buildJsonObject {
        put("id", spec.id)
        put("name", spec.name)
        put("kind", spec.kind.name)
        put("group", spec.group)
        put("number", spec.number ?: 0)
        put("logo", spec.logo ?: "")
        put("enabled", spec.enabled)
        put("source", encodeSource(spec.source))
        put("pool", encodePoolRules(spec.pool))
        put("playout", encodePlayout(spec.playout))
        put("join", encodeJoin(spec.join))
        put("createdAtMs", spec.createdAtMs)
        put("updatedAtMs", spec.updatedAtMs)
        put("schemaVersion", spec.schemaVersion)
    }

    fun decodeSpec(o: JsonObject): VirtualChannelSpec? {
        val source = o.optJSONObject("source")?.let(::decodeSource) ?: return null
        val name = o.optString("name").ifBlank { "Lucid TV channel" }
        val id = o.optString("id").ifBlank { newId() }
        val kind = enumOr(o.optString("kind"), VirtualChannelKind.ADDON_ROTATE)
        val number = o.optInt("number", 0).takeIf { it > 0 }
        return VirtualChannelSpec(
            id = id,
            name = name,
            kind = kind,
            group = o.optString("group").ifBlank { VirtualChannels.DEFAULT_GROUP },
            number = number,
            logo = o.optString("logo").ifBlank { null },
            enabled = o.optBoolean("enabled", true),
            source = source,
            pool = o.optJSONObject("pool")?.let(::decodePoolRules) ?: VirtualPoolRules(),
            playout = o.optJSONObject("playout")?.let(::decodePlayout) ?: VirtualPlayout(
                mode = if (kind.isLoop) VirtualPlayoutMode.LOOP else VirtualPlayoutMode.ROTATE,
            ),
            join = o.optJSONObject("join")?.let(::decodeJoin) ?: VirtualJoin(),
            createdAtMs = o.optLong("createdAtMs"),
            updatedAtMs = o.optLong("updatedAtMs"),
            schemaVersion = o.optInt("schemaVersion", VirtualChannels.SCHEMA_VERSION).coerceAtLeast(1),
        )
    }

    // ------------------------------------------------------------------ sources

    private fun encodeSource(source: VirtualSource): JsonObject = buildJsonObject {
        when (source) {
            is VirtualSource.AddonCatalog -> {
                put("type", "addon_catalog")
                put("manifestUrl", source.manifestUrl)
                put("addonId", source.addonId)
                put("addonName", source.addonName)
                put("catalogId", source.catalogId)
                put("catalogName", source.catalogName)
                put("contentType", source.contentType)
                put("genre", source.genre ?: "")
            }
            is VirtualSource.AddonItem -> {
                put("type", "addon_item")
                put("manifestUrl", source.manifestUrl)
                put("addonId", source.addonId)
                put("itemId", source.itemId)
                put("itemType", source.itemType)
            }
            is VirtualSource.ManualItems -> {
                put("type", "manual")
                put("items", buildJsonArray { source.items.forEach { add(encodeItem(it)) } })
            }
            is VirtualSource.PlaylistGroup -> {
                put("type", "playlist_group")
                put("sourceId", source.sourceId)
                put("groupId", source.groupId ?: "")
            }
            is VirtualSource.Rules -> {
                put("type", "rules")
                put("manifestUrls", buildJsonArray { source.manifestUrls.forEach { add(it) } })
                put("addonIds", buildJsonArray { source.addonIds.forEach { add(it) } })
                put("genres", buildJsonArray { source.genres.forEach { add(it) } })
                put("minYear", source.minYear ?: 0)
                put("maxYear", source.maxYear ?: 0)
                put("minRating", source.minRating ?: 0.0)
                put("keyword", source.keyword ?: "")
                put("contentType", source.contentType)
            }
        }
    }

    private fun decodeSource(o: JsonObject): VirtualSource? = when (o.optString("type").lowercase()) {
        "addon_catalog" -> VirtualSource.AddonCatalog(
            manifestUrl = o.optString("manifestUrl"),
            addonId = o.optString("addonId"),
            addonName = o.optString("addonName"),
            catalogId = o.optString("catalogId"),
            catalogName = o.optString("catalogName"),
            contentType = o.optString("contentType").ifBlank { "movie" },
            genre = o.optString("genre").ifBlank { null },
        ).takeIf { it.manifestUrl.isNotBlank() && it.catalogId.isNotBlank() }

        "addon_item" -> VirtualSource.AddonItem(
            manifestUrl = o.optString("manifestUrl"),
            addonId = o.optString("addonId"),
            itemId = o.optString("itemId"),
            itemType = o.optString("itemType").ifBlank { "series" },
        ).takeIf { it.manifestUrl.isNotBlank() && it.itemId.isNotBlank() }

        "manual" -> VirtualSource.ManualItems(
            o.optJSONArray("items")?.mapNotNull { (it as? JsonObject)?.let(::decodeItem) } ?: emptyList(),
        ).takeIf { it.items.isNotEmpty() }

        "playlist_group" -> VirtualSource.PlaylistGroup(
            sourceId = o.optString("sourceId"),
            groupId = o.optString("groupId").ifBlank { null },
        ).takeIf { it.sourceId.isNotBlank() }

        "rules" -> VirtualSource.Rules(
            manifestUrls = o.optJSONArray("manifestUrls")?.stringList() ?: emptyList(),
            addonIds = o.optJSONArray("addonIds")?.stringList() ?: emptyList(),
            genres = (o.optJSONArray("genres")?.stringList() ?: emptyList()).toSet(),
            minYear = o.optInt("minYear", 0).takeIf { it > 0 },
            maxYear = o.optInt("maxYear", 0).takeIf { it > 0 },
            minRating = o.optDouble("minRating", 0.0).takeIf { it > 0.0 }?.toFloat(),
            keyword = o.optString("keyword").ifBlank { null },
            contentType = o.optString("contentType").ifBlank { "movie" },
        ).takeIf { it.manifestUrls.isNotEmpty() || it.addonIds.isNotEmpty() || it.genres.isNotEmpty() }

        else -> null
    }

    // ------------------------------------------------------------------ items

    fun encodeItem(item: VirtualItem): JsonObject = buildJsonObject {
        put("id", item.id)
        put("type", item.type)
        put("title", item.title)
        put("poster", item.poster ?: "")
        put("backdrop", item.backdrop ?: "")
        put("description", item.description ?: "")
        put("year", item.year ?: 0)
        put("rating", item.rating ?: 0.0)
        put("runtimeMs", item.runtimeMs ?: 0L)
        put("genres", buildJsonArray { item.genres.forEach { add(it) } })
        put("season", item.season ?: 0)
        put("episode", item.episode ?: 0)
        put("episodeTitle", item.episodeTitle ?: "")
        put("parentId", item.parentId ?: "")
        put("streamUrl", item.streamUrl ?: "")
        put("headers", buildJsonObject { item.headers.forEach { (k, v) -> put(k, v) } })
    }

    fun decodeItem(o: JsonObject): VirtualItem = VirtualItem(
        id = o.optString("id"),
        type = o.optString("type").ifBlank { "movie" },
        title = o.optString("title").ifBlank { o.optString("id") },
        poster = o.optString("poster").ifBlank { null },
        backdrop = o.optString("backdrop").ifBlank { null },
        description = o.optString("description").ifBlank { null },
        year = o.optInt("year", 0).takeIf { it > 0 },
        rating = o.optDouble("rating", 0.0).takeIf { it > 0.0 }?.toFloat(),
        runtimeMs = o.optLong("runtimeMs").takeIf { it > 0L },
        genres = o.optJSONArray("genres")?.stringList() ?: emptyList(),
        season = o.optInt("season", 0).takeIf { it > 0 },
        episode = o.optInt("episode", 0).takeIf { it > 0 },
        episodeTitle = o.optString("episodeTitle").ifBlank { null },
        parentId = o.optString("parentId").ifBlank { null },
        streamUrl = o.optString("streamUrl").ifBlank { null },
        headers = o.optJSONObject("headers")?.entries
            ?.filter { it.value is JsonPrimitive }
            ?.associate { it.key to (it.value as JsonPrimitive).content }
            .orEmpty(),
    )

    // ------------------------------------------------------------------ pools

    fun encodePools(pools: Map<String, VirtualPoolSnapshot>): String = buildJsonObject {
        pools.forEach { (id, snapshot) ->
            put(id, buildJsonObject {
                put("fetchedAtMs", snapshot.fetchedAtMs)
                put("items", buildJsonArray { snapshot.items.forEach { add(encodeItem(it)) } })
            })
        }
    }.toString()

    fun decodePools(raw: String?): Map<String, VirtualPoolSnapshot> {
        if (raw.isNullOrBlank()) return emptyMap()
        return runCatching {
            val root = parseObject(raw)
            root.keys.mapNotNull { id ->
                val o = root.optJSONObject(id) ?: return@mapNotNull null
                val items = o.optJSONArray("items")?.mapNotNull { (it as? JsonObject)?.let(::decodeItem) } ?: emptyList()
                id to VirtualPoolSnapshot(items = items, fetchedAtMs = o.optLong("fetchedAtMs"))
            }.toMap()
        }.getOrDefault(emptyMap())
    }

    // ------------------------------------------------------------------ settings

    fun encodePlaybackSettings(settings: VirtualPlaybackSettings): String = buildJsonObject {
        put("allowDebrid", settings.allowDebrid)
        put("prefetchNext", settings.prefetchNext)
    }.toString()

    fun decodePlaybackSettings(raw: String?): VirtualPlaybackSettings {
        if (raw.isNullOrBlank()) return VirtualPlaybackSettings()
        return runCatching {
            val o = parseObject(raw)
            VirtualPlaybackSettings(
                allowDebrid = o.optBoolean("allowDebrid", true),
                prefetchNext = o.optInt("prefetchNext", 2)
                    .coerceIn(VirtualPlaybackSettings.MIN_PREFETCH, VirtualPlaybackSettings.MAX_PREFETCH),
            )
        }.getOrDefault(VirtualPlaybackSettings())
    }

    // ------------------------------------------------------------------ playout
    private fun encodePoolRules(rules: VirtualPoolRules): JsonObject = buildJsonObject {
        put("maxItems", rules.maxItems)
        put("refreshHours", rules.refreshHours)
        put("minYear", rules.minYear ?: 0)
        put("maxYear", rules.maxYear ?: 0)
        put("minRating", rules.minRating ?: 0.0)
        put("includeGenres", buildJsonArray { rules.includeGenres.forEach { add(it) } })
        put("excludeGenres", buildJsonArray { rules.excludeGenres.forEach { add(it) } })
        put("keyword", rules.keyword ?: "")
        put("order", rules.order.name)
    }

    private fun decodePoolRules(o: JsonObject): VirtualPoolRules = VirtualPoolRules(
        maxItems = o.optInt("maxItems", 2000).coerceIn(1, 10_000),
        refreshHours = o.optInt("refreshHours", 24).coerceAtLeast(0),
        minYear = o.optInt("minYear", 0).takeIf { it > 0 },
        maxYear = o.optInt("maxYear", 0).takeIf { it > 0 },
        minRating = o.optDouble("minRating", 0.0).takeIf { it > 0.0 }?.toFloat(),
        includeGenres = (o.optJSONArray("includeGenres")?.stringList() ?: emptyList()).toSet(),
        excludeGenres = (o.optJSONArray("excludeGenres")?.stringList() ?: emptyList()).toSet(),
        keyword = o.optString("keyword").ifBlank { null },
        order = enumOr(o.optString("order"), VirtualOrder.SHUFFLE),
    )

    private fun encodePlayout(playout: VirtualPlayout): JsonObject = buildJsonObject {
        put("mode", playout.mode.name)
        put("slotMinutes", playout.slotMinutes)
        put("seriesSlotMinutes", playout.seriesSlotMinutes)
        put("anchorMs", playout.anchorMs)
        put("order", playout.order.name)
        put("salt", playout.salt)
        put("loop", playout.loop)
    }

    private fun decodePlayout(o: JsonObject): VirtualPlayout = VirtualPlayout(
        mode = enumOr(o.optString("mode"), VirtualPlayoutMode.ROTATE),
        slotMinutes = o.optInt("slotMinutes", 90).coerceIn(1, 24 * 60),
        seriesSlotMinutes = o.optInt("seriesSlotMinutes", 45).coerceIn(1, 24 * 60),
        anchorMs = o.optLong("anchorMs"),
        order = enumOr(o.optString("order"), VirtualOrder.SHUFFLE),
        salt = o.optInt("salt", 0).coerceIn(0, 9),
        loop = o.optBoolean("loop", true),
    )

    private fun encodeJoin(join: VirtualJoin): JsonObject = buildJsonObject {
        put("maxStartPercent", join.maxStartPercent)
        put("jitter", join.jitter)
    }

    private fun decodeJoin(o: JsonObject): VirtualJoin = VirtualJoin(
        maxStartPercent = o.optInt("maxStartPercent", -1).coerceIn(-1, 100),
        jitter = o.optBoolean("jitter", true),
    )

    // ------------------------------------------------------------------ helpers

    private fun JsonArray.stringList(): List<String> =
        mapNotNull { (it as? JsonPrimitive)?.content?.takeIf { value -> value.isNotBlank() } }

    private fun JsonObject.optDouble(key: String, fallback: Double = 0.0): Double {
        val primitive = this[key] as? JsonPrimitive ?: return fallback
        return primitive.content.toDoubleOrNull() ?: fallback
    }

    private inline fun <reified T : Enum<T>> enumOr(value: String, fallback: T): T =
        enumValues<T>().firstOrNull { it.name.equals(value, ignoreCase = true) } ?: fallback

    fun newId(): String {
        val alphabet = "0123456789abcdefghijklmnopqrstuvwxyz"
        val body = buildString(20) {
            repeat(20) { append(alphabet[kotlin.random.Random.nextInt(alphabet.length)]) }
        }
        return "vc_$body"
    }
}
