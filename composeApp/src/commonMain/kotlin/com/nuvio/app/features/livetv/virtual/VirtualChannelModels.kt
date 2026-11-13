package com.nuvio.app.features.livetv.virtual

import com.nuvio.app.features.livetv.model.LiveChannel

/**
 * What kind of pool a Lucid TV channel draws from.
 *
 * The first four kinds rotate or loop through catalog items; the last two play real streams.
 */
enum class VirtualChannelKind {
    /** One add-on catalog (optionally one genre); a rotating "Stremio TV" channel. */
    ADDON_ROTATE,

    /** One add-on catalog of series; each slot resolves to a deterministic episode. */
    ADDON_SERIES,

    /** Several catalogs and filters merged into one pool. */
    SMART_ROTATE,

    /** A series played in order as a 24/7 marathon. */
    SERIES_MARATHON,

    /** A hand-picked collection played back to back. */
    COLLECTION_LOOP,

    /** Existing Live TV channels joined into a 24/7 relay channel. */
    PLAYLIST_LOOP;

    val isLoop: Boolean
        get() = this == SERIES_MARATHON || this == COLLECTION_LOOP || this == PLAYLIST_LOOP

    val isSeries: Boolean
        get() = this == ADDON_SERIES || this == SERIES_MARATHON
}

enum class VirtualPlayoutMode { ROTATE, LOOP }

enum class VirtualOrder { SHUFFLE, SEQUENTIAL, RELEASE, AIR, MANUAL }

/**
 * Where a virtual channel's pool comes from. Kept as a small sealed hierarchy so the codec can
 * write the TV app's JSON shapes (`"type": "addon_catalog"`, …) without losing type safety.
 */
sealed interface VirtualSource {
    data class AddonCatalog(
        val manifestUrl: String,
        val addonId: String = "",
        val addonName: String = "",
        val catalogId: String,
        val catalogName: String = "",
        val contentType: String,
        val genre: String? = null,
    ) : VirtualSource

    /** One catalog item: a movie for a loop, or a series for a marathon. */
    data class AddonItem(
        val manifestUrl: String,
        val addonId: String = "",
        val itemId: String,
        val itemType: String,
    ) : VirtualSource

    data class ManualItems(val items: List<VirtualItem>) : VirtualSource

    /** The channels of one playlist (optionally one group) as relay segments. */
    data class PlaylistGroup(
        val sourceId: String,
        val groupId: String? = null,
    ) : VirtualSource

    data class Rules(
        val manifestUrls: List<String> = emptyList(),
        val addonIds: List<String> = emptyList(),
        val genres: Set<String> = emptySet(),
        val minYear: Int? = null,
        val maxYear: Int? = null,
        val minRating: Float? = null,
        val keyword: String? = null,
        val contentType: String = "movie",
    ) : VirtualSource
}

/** One thing a virtual channel can play. Episodes carry their parent series id. */
data class VirtualItem(
    val id: String,
    val type: String,
    val title: String,
    val poster: String? = null,
    val backdrop: String? = null,
    val description: String? = null,
    val year: Int? = null,
    val rating: Float? = null,
    val runtimeMs: Long? = null,
    val genres: List<String> = emptyList(),
    val season: Int? = null,
    val episode: Int? = null,
    val episodeTitle: String? = null,
    val parentId: String? = null,
    /** Relay channels: the real stream URL and its headers. */
    val streamUrl: String? = null,
    val headers: Map<String, String> = emptyMap(),
)

data class VirtualPoolRules(
    val maxItems: Int = 2000,
    val refreshHours: Int = 24,
    val minYear: Int? = null,
    val maxYear: Int? = null,
    val minRating: Float? = null,
    val includeGenres: Set<String> = emptySet(),
    val excludeGenres: Set<String> = emptySet(),
    val keyword: String? = null,
    val order: VirtualOrder = VirtualOrder.SHUFFLE,
)

data class VirtualPlayout(
    val mode: VirtualPlayoutMode = VirtualPlayoutMode.ROTATE,
    val slotMinutes: Int = 90,
    val seriesSlotMinutes: Int = 45,
    val anchorMs: Long = 0L,
    val order: VirtualOrder = VirtualOrder.SHUFFLE,
    val salt: Int = 0,
    val loop: Boolean = true,
)

data class VirtualJoin(
    /** -1: no limit, 0: always start at the beginning, N: cap the join offset at N percent. */
    val maxStartPercent: Int = -1,
    val jitter: Boolean = true,
)

/** How Lucid TV turns a channel into a stream. Direct HTTP is always tried first. */
data class VirtualPlaybackSettings(
    /** When no direct stream is available, allow debrid/torrent sources to be resolved. */
    val allowDebrid: Boolean = true,
    /** How many upcoming items to resolve in the background (0–4). */
    val prefetchNext: Int = 2,
) {
    companion object {
        const val MIN_PREFETCH = 0
        const val MAX_PREFETCH = 4
    }
}

data class VirtualChannelSpec(
    val id: String,
    val name: String,
    val kind: VirtualChannelKind,
    val group: String = VirtualChannels.DEFAULT_GROUP,
    val number: Int? = null,
    val logo: String? = null,
    val enabled: Boolean = true,
    val source: VirtualSource,
    val pool: VirtualPoolRules = VirtualPoolRules(),
    val playout: VirtualPlayout = VirtualPlayout(),
    val join: VirtualJoin = VirtualJoin(),
    val createdAtMs: Long = 0L,
    val updatedAtMs: Long = 0L,
    val schemaVersion: Int = 1,
) {
    /** The slot length this channel uses for ROTATE (series channels are shorter). */
    val rotationMinutes: Int
        get() = if (kind.isSeries) playout.seriesSlotMinutes else playout.slotMinutes

    val channelKey: String get() = VirtualChannels.channelKey(id)
    val groupId: String get() = VirtualChannels.groupId(group)
}

data class VirtualPoolSnapshot(
    val items: List<VirtualItem> = emptyList(),
    val fetchedAtMs: Long = 0L,
    val error: String? = null,
)

/** The current item of a channel, already resolved to a playable identity. */
data class VirtualTune(
    val channelKey: String,
    val specId: String,
    val item: VirtualItem,
    val season: Int? = null,
    val episode: Int? = null,
    val episodeTitle: String? = null,
    val title: String,
    val startOffsetMs: Long,
    val slotStartMs: Long,
    val slotEndMs: Long,
    val relay: Boolean = false,
)

/** A JSON pack exported from (or imported into) Lucid TV. */
data class VirtualChannelPack(
    val schemaVersion: Int = 1,
    val exportedAtMs: Long = 0L,
    val channels: List<VirtualChannelSpec> = emptyList(),
)

/** Naming and identity constants shared by the repository, EPG and UI. */
object VirtualChannels {
    const val SOURCE_ID = "virtual"
    const val SOURCE_NAME = "Lucid TV"
    const val DEFAULT_GROUP = "Lucid TV"
    const val CHANNEL_PREFIX = "virtual:"
    const val GROUP_PREFIX = "virtual:"
    const val NUMBER_BASE = 9000
    const val SCHEMA_VERSION = 1
    const val MAX_PROGRAMS_PER_CHANNEL = 2000

    fun channelKey(id: String): String = CHANNEL_PREFIX + id

    fun groupId(group: String): String {
        val slug = group.trim().lowercase()
            .map { if (it.isLetterOrDigit()) it else '-' }
            .joinToString("")
            .trim('-')
            .replace(Regex("-+"), "-")
        return GROUP_PREFIX + slug.ifBlank { "lucid-tv" }
    }

    fun isVirtualKey(key: String?): Boolean = key != null && key.startsWith(CHANNEL_PREFIX)

    fun specIdOf(key: String): String? =
        key.takeIf { it.startsWith(CHANNEL_PREFIX) }?.removePrefix(CHANNEL_PREFIX)?.takeIf { it.isNotBlank() }

    fun channelFor(spec: VirtualChannelSpec, index: Int, poster: String? = null): LiveChannel = LiveChannel(
        key = spec.channelKey,
        sourceId = SOURCE_ID,
        sourceName = SOURCE_NAME,
        name = spec.name,
        tvgId = null,
        tvgName = null,
        logo = spec.logo?.takeIf { it.isNotBlank() } ?: poster,
        // Every Lucid TV channel lives in the fixed Lucid TV group; the spec's group is only
        // kept for the TV app's JSON schema.
        groupId = groupId(DEFAULT_GROUP),
        group = DEFAULT_GROUP,
        number = spec.number ?: (NUMBER_BASE + index),
        url = spec.channelKey,
    )
}
