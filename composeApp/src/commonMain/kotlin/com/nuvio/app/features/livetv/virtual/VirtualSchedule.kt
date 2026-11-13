package com.nuvio.app.features.livetv.virtual

import com.nuvio.app.features.livetv.model.EpgProgram

/**
 * The deterministic Lucid TV schedule. Everything here is a pure function of the channel id, the
 * pool and the wall clock, so Android and iOS (and a TV running the same schema) agree on what is
 * on without a server, a database or an XMLTV download.
 *
 * Ported from Debrify's `stremio_tv_rotation.dart`; the same hash and slot math are used so a
 * schedule exported from another client lines up.
 */
object VirtualSchedule {

    /** floor division that also works for negative epochs (Kotlin/Native has no java.lang.Math). */
    fun floorDiv(a: Long, b: Long): Long {
        var q = a / b
        if (a % b != 0L && ((a xor b) < 0)) q--
        return q
    }

    /** floor modulus, always in `[0, b)`. */
    fun floorMod(a: Long, b: Long): Long {
        val r = a % b
        return if (r < 0) r + b else r
    }

    /** djb2, 31-bit and non-negative: stable across Kotlin/JVM and Kotlin/Native. */
    fun stableHash(input: CharSequence): Int {
        var hash = 5381
        for (i in input.indices) {
            hash = ((hash shl 5) + hash) + input[i].code
            hash = hash and 0x7FFFFFFF
        }
        return hash
    }

    /** A per-channel offset inside one slot, so channels don't flip at the same second. */
    fun offsetMs(channelId: String, slotMs: Long): Long {
        var h = stableHash(channelId)
        h = (((h ushr 16) xor h) * 0x45d9f3b) and 0x7FFFFFFF
        h = (((h ushr 16) xor h) * 0x45d9f3b) and 0x7FFFFFFF
        h = (h ushr 16) xor h
        val r = h.toLong() % slotMs
        return if (r < 0) r + slotMs else r
    }

    data class RotateSlot(val number: Long, val startMs: Long, val endMs: Long)

    fun slotAt(nowMs: Long, channelId: String, slotMs: Long): RotateSlot {
        val safeSlot = slotMs.coerceAtLeast(1L)
        val offset = offsetMs(channelId, safeSlot)
        val number = floorDiv(nowMs - offset, safeSlot)
        val start = number * safeSlot + offset
        return RotateSlot(number, start, start + safeSlot)
    }

    fun itemIndex(channelId: String, slotNumber: Long, poolSize: Int, salt: Int): Int {
        if (poolSize <= 0) return 0
        val seed = if (salt == 0) "$channelId:$slotNumber" else "$channelId:$slotNumber:$salt"
        return floorMod(stableHash(seed).toLong(), poolSize.toLong()).toInt()
    }

    fun itemForSlot(
        channelId: String,
        slot: RotateSlot,
        items: List<VirtualItem>,
        salt: Int,
    ): VirtualItem? = items.getOrNull(itemIndex(channelId, slot.number, items.size, salt))

    /** One played item with its real start and end (LOOP), or its slot (ROTATE). */
    data class PlayoutSegment(val item: VirtualItem, val startMs: Long, val endMs: Long)

    /** The item on now for a LOOP channel. */
    fun playoutAt(
        nowMs: Long,
        anchorMs: Long,
        items: List<VirtualItem>,
        fallbackDurationMs: Long,
    ): PlayoutSegment? {
        if (items.isEmpty()) return null
        val durations = durationsOf(items, fallbackDurationMs)
        val cycle = durations.sum()
        if (cycle <= 0L) return null
        val t = floorMod(nowMs - anchorMs, cycle)
        var acc = 0L
        for (i in items.indices) {
            val d = durations[i]
            if (t < acc + d) {
                val start = nowMs - (t - acc)
                return PlayoutSegment(items[i], start, start + d)
            }
            acc += d
        }
        return null
    }

    /** Every played item overlapping `[fromMs, toMs)`, for LOOP channels. */
    fun playoutSegments(
        anchorMs: Long,
        items: List<VirtualItem>,
        fallbackDurationMs: Long,
        fromMs: Long,
        toMs: Long,
        limit: Int = VirtualChannels.MAX_PROGRAMS_PER_CHANNEL,
    ): List<PlayoutSegment> {
        if (items.isEmpty() || toMs <= fromMs) return emptyList()
        val durations = durationsOf(items, fallbackDurationMs)
        val cycle = durations.sum()
        if (cycle <= 0L) return emptyList()
        val out = ArrayList<PlayoutSegment>()
        var cursor = anchorMs + floorDiv(fromMs - anchorMs, cycle) * cycle
        var index = 0
        var guard = 0
        // Walk to the first segment that ends after `fromMs`.
        while (cursor + durations[index] <= fromMs && guard++ < limit * 4) {
            cursor += durations[index]
            index = (index + 1) % items.size
        }
        while (cursor < toMs && out.size < limit && guard++ < limit * 4) {
            val end = cursor + durations[index]
            if (end > fromMs) out += PlayoutSegment(items[index], cursor, end)
            cursor = end
            index = (index + 1) % items.size
        }
        return out
    }

    /** ROTATE listings for `[fromMs, toMs)`, one program per slot. */
    fun rotatePrograms(
        channelId: String,
        items: List<VirtualItem>,
        slotMs: Long,
        salt: Int,
        fromMs: Long,
        toMs: Long,
        limit: Int = VirtualChannels.MAX_PROGRAMS_PER_CHANNEL,
    ): List<PlayoutSegment> {
        if (items.isEmpty() || toMs <= fromMs) return emptyList()
        val out = ArrayList<PlayoutSegment>()
        var slot = slotAt(fromMs, channelId, slotMs)
        while (slot.startMs < toMs && out.size < limit) {
            val item = itemForSlot(channelId, slot, items, salt) ?: break
            if (slot.endMs > fromMs) out += PlayoutSegment(item, slot.startMs, slot.endMs)
            slot = RotateSlot(slot.number + 1, slot.endMs, slot.endMs + slotMs.coerceAtLeast(1L))
        }
        return out
    }

    /** How far into the playing item a viewer joins, applying the channel's join rule. */
    fun joinOffsetMs(
        nowMs: Long,
        itemStartMs: Long,
        itemDurationMs: Long,
        join: VirtualJoin,
        channelId: String,
    ): Long {
        val duration = itemDurationMs.coerceAtLeast(1L)
        val raw = (nowMs - itemStartMs).coerceIn(0L, duration - 1L)
        if (join.maxStartPercent == 0) return 0L
        if (join.maxStartPercent < 0) return raw
        val percent = join.maxStartPercent.coerceIn(0, 100)
        val cap = duration * percent / 100L
        if (raw <= cap) return raw
        if (!join.jitter) return cap
        val jitter = (stableHash(channelId).toLong() % 1000L).coerceAtLeast(0L) * cap / 1000L
        return jitter.coerceIn(0L, cap)
    }

    fun durationsOf(items: List<VirtualItem>, fallbackDurationMs: Long): List<Long> {
        val fallback = fallbackDurationMs.coerceAtLeast(1L)
        return items.map { item -> item.runtimeMs?.takeIf { it > 0L } ?: fallback }
    }

    /** The guide title: the item, its episode and its year, without inventing anything. */
    fun programTitle(item: VirtualItem): String {
        val episode = episodeLabel(item.season, item.episode)
        val base = when {
            episode.isNotBlank() && item.episodeTitle.isNullOrBlank() -> "${item.title} · $episode"
            episode.isNotBlank() -> "${item.title} · $episode · ${item.episodeTitle}"
            else -> item.title
        }
        return if (item.year != null) "$base (${item.year})" else base
    }

    fun episodeLabel(season: Int?, episode: Int?): String = when {
        season == null || episode == null -> ""
        else -> "S${season}E$episode"
    }

    fun toProgram(segment: PlayoutSegment): EpgProgram {
        val item = segment.item
        val label = episodeLabel(item.season, item.episode)
        return EpgProgram(
            startMs = segment.startMs,
            stopMs = segment.endMs,
            title = programTitle(item),
            description = item.description,
            category = item.genres.firstOrNull() ?: item.type,
            episode = label.ifBlank { null },
            icon = item.poster,
            year = item.year,
        )
    }
}
