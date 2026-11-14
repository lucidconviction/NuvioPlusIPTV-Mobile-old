package com.nuvio.app.features.livetv.virtual

import com.nuvio.app.features.addons.AddonRepository
import com.nuvio.app.features.addons.ManagedAddon
import com.nuvio.app.features.catalog.fetchCatalogPage
import com.nuvio.app.features.details.MetaDetails
import com.nuvio.app.features.details.MetaDetailsRepository
import com.nuvio.app.features.details.MetaVideo
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.livetv.data.LiveTvPreferences
import com.nuvio.app.features.livetv.data.LiveTvRepository
import com.nuvio.app.features.livetv.model.EpgProgram
import com.nuvio.app.features.livetv.model.LiveChannel
import com.nuvio.app.features.livetv.model.LiveTvClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Lucid TV's channels and pools. Virtual channels are a merge layer: they are never written into
 * `LiveTvRepository._channels` (which playlists rebuild), so playlist refreshes cannot remove them.
 * The guide is generated from the same pure schedule as playback.
 */
internal object VirtualChannelRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val prefs = LiveTvPreferences
    private val codec = VirtualChannelCodec
    private val poolMutex = Mutex()
    private val seriesMutex = Mutex()

    private val _specs = MutableStateFlow<List<VirtualChannelSpec>>(emptyList())
    val specs: StateFlow<List<VirtualChannelSpec>> = _specs.asStateFlow()

    private val _channels = MutableStateFlow<List<LiveChannel>>(emptyList())
    val channels: StateFlow<List<LiveChannel>> = _channels.asStateFlow()

    private val _pools = MutableStateFlow<Map<String, VirtualPoolSnapshot>>(emptyMap())
    val pools: StateFlow<Map<String, VirtualPoolSnapshot>> = _pools.asStateFlow()

    private val _programsWindow = MutableStateFlow<Map<String, List<EpgProgram>>>(emptyMap())
    val programsWindow: StateFlow<Map<String, List<EpgProgram>>> = _programsWindow.asStateFlow()

    private val _errors = MutableStateFlow<Map<String, String>>(emptyMap())
    val errors: StateFlow<Map<String, String>> = _errors.asStateFlow()

    private val _settings = MutableStateFlow(VirtualPlaybackSettings())
    val settings: StateFlow<VirtualPlaybackSettings> = _settings.asStateFlow()

    private val _refreshing = MutableStateFlow<Set<String>>(emptySet())
    val refreshing: StateFlow<Set<String>> = _refreshing.asStateFlow()

    private val seriesCache = HashMap<String, List<VirtualItem>>()
    private val lastAttemptMs = HashMap<String, Long>()
    private var initialized = false
    private var tickerStarted = false
    private var playlistObserverStarted = false
    private var specsObserverStarted = false
    private var settingsObserverStarted = false

    // ---------------------------------------------------------------- lifecycle

    fun ensureLoaded() {
        if (!initialized) {
            initialized = true
            val stored = codec.decodeSpecs(prefs.currentVirtualChannelsJson())
            val normalized = normalizeSpecs(stored)
            _specs.value = normalized
            if (normalized != stored) persistSpecs()
            val decodedPools = codec.decodePools(prefs.currentVirtualPoolsJson())
            val repairedPools = repairPools(decodedPools, normalized)
            _pools.value = repairedPools
            if (repairedPools != decodedPools) persistPools()
            _settings.value = codec.decodePlaybackSettings(prefs.currentVirtualSettingsJson())
            rebuildChannels()
            rebuildWindow()
            refreshStale(forceEmpty = true)
        }
        if (!tickerStarted) {
            tickerStarted = true
            scope.launch {
                while (true) {
                    delay(60_000L)
                    if (_specs.value.isEmpty()) continue
                    rebuildWindow()
                    refreshStale(forceEmpty = false)
                }
            }
        }
        if (!playlistObserverStarted) {
            playlistObserverStarted = true
            // Relay pools are built from Live TV's channels; rebuild them when playlists arrive or change.
            scope.launch {
                LiveTvRepository.channels.collect { channels ->
                    if (channels.isEmpty()) return@collect
                    _specs.value
                        .filter { it.enabled && it.source is VirtualSource.PlaylistGroup }
                        .forEach { spec -> refreshPool(spec.id, force = true) }
                }
            }
        }
        if (!specsObserverStarted) {
            specsObserverStarted = true            // A backup restore or Drive sync can replace the whole channel list; pick it up.
            scope.launch {
                prefs.virtualChannelsJson.collect { raw ->
                    val stored = codec.decodeSpecs(raw)
                    val decoded = normalizeSpecs(stored)
                    if (decoded == _specs.value) return@collect
                    _specs.value = decoded
                    if (decoded != stored) persistSpecs()
                    val ids = decoded.mapTo(HashSet()) { it.id }
                    _pools.value = _pools.value.filterKeys { it in ids }
                    _errors.value = _errors.value.filterKeys { it in ids }
                    rebuildChannels()
                    rebuildWindow()
                    refreshStale(forceEmpty = true)
                }
            }
        }
        if (!settingsObserverStarted) {
            settingsObserverStarted = true
            scope.launch {
                prefs.virtualSettingsJson.collect { raw ->
                    _settings.value = codec.decodePlaybackSettings(raw)
                }
            }
        }
    }
    fun hasChannels(): Boolean = _specs.value.isNotEmpty()

    /** Saves the HTTP/debrid and prefetch choices. */
    fun updateSettings(settings: VirtualPlaybackSettings) {
        val clean = settings.copy(
            prefetchNext = settings.prefetchNext.coerceIn(
                VirtualPlaybackSettings.MIN_PREFETCH,
                VirtualPlaybackSettings.MAX_PREFETCH,
            ),
        )
        _settings.value = clean
        prefs.setVirtualSettingsJson(codec.encodePlaybackSettings(clean))
    }

    /** Regenerates the near-term guide window (called when Live TV refreshes). */
    fun refreshWindow() {
        if (_specs.value.isEmpty()) return
        rebuildWindow()
    }

    // ---------------------------------------------------------------- edits

    fun create(spec: VirtualChannelSpec): VirtualChannelSpec {
        ensureLoaded()
        val now = LiveTvClock.nowMs()
        val taken = _specs.value.mapTo(HashSet()) { it.id }
        val id = spec.id.takeIf { it.isNotBlank() && it !in taken } ?: VirtualChannelCodec.newId()
        val created = spec.copy(
            id = id,
            name = spec.name.trim().ifBlank { "Lucid TV channel" },
            group = VirtualChannels.DEFAULT_GROUP,
            createdAtMs = spec.createdAtMs.takeIf { it > 0 } ?: now,
            updatedAtMs = now,
            schemaVersion = VirtualChannels.SCHEMA_VERSION,
        )
        _specs.value = _specs.value + created
        persistSpecs()
        rebuildChannels()
        rebuildWindow()
        refreshPool(created.id, force = true)
        return created
    }

    fun update(spec: VirtualChannelSpec) {
        ensureLoaded()
        val now = LiveTvClock.nowMs()
        val before = _specs.value.firstOrNull { it.id == spec.id } ?: return
        val updated = spec.copy(
            name = spec.name.trim().ifBlank { before.name },
            group = VirtualChannels.DEFAULT_GROUP,
            createdAtMs = before.createdAtMs,
            updatedAtMs = now,
        )
        _specs.value = _specs.value.map { if (it.id == updated.id) updated else it }
        persistSpecs()
        rebuildChannels()
        rebuildWindow()
        refreshPool(updated.id, force = true)
    }

    fun delete(id: String) {
        ensureLoaded()
        if (_specs.value.none { it.id == id }) return
        _specs.value = _specs.value.filterNot { it.id == id }
        _pools.value = _pools.value - id
        _errors.value = _errors.value - id
        persistSpecs()
        persistPools()
        rebuildChannels()
        rebuildWindow()
    }

    fun setEnabled(id: String, enabled: Boolean) {
        ensureLoaded()
        val spec = _specs.value.firstOrNull { it.id == id } ?: return
        update(spec.copy(enabled = enabled))
    }

    fun duplicate(id: String): VirtualChannelSpec? {
        ensureLoaded()
        val spec = _specs.value.firstOrNull { it.id == id } ?: return null
        return create(
            spec.copy(
                id = "",
                name = "${spec.name} copy",
                number = null,
                createdAtMs = 0L,
                updatedAtMs = 0L,
            )
        )
    }

    fun spec(id: String): VirtualChannelSpec? = _specs.value.firstOrNull { it.id == id }

    fun pool(id: String): VirtualPoolSnapshot? = _pools.value[id]

    // ---------------------------------------------------------------- pools

    fun refreshAll() {
        ensureLoaded()
        _specs.value.filter { it.enabled }.forEach { refreshPool(it.id, force = true) }
    }

    fun refreshPool(id: String, force: Boolean = false) {
        ensureLoaded()
        val spec = _specs.value.firstOrNull { it.id == id } ?: return
        if (spec.enabled.not() && force.not()) return
        if (id in _refreshing.value) return
        if (!force) {
            val snapshot = _pools.value[id]
            val staleAfter = spec.pool.refreshHours.coerceAtLeast(1) * 3_600_000L
            val fresh = snapshot != null && snapshot.items.isNotEmpty() && snapshot.fetchedAtMs > 0L &&
                LiveTvClock.nowMs() - snapshot.fetchedAtMs < staleAfter
            if (fresh) return
            val last = lastAttemptMs[id] ?: 0L
            if (LiveTvClock.nowMs() - last < RETRY_AFTER_MS) return
        }
        lastAttemptMs[id] = LiveTvClock.nowMs()
        _refreshing.value = _refreshing.value + id
        scope.launch {
            try {
                val result = runCatching { buildPool(spec) }
                result.onSuccess { items ->
                    _pools.value = _pools.value + (id to VirtualPoolSnapshot(items = items, fetchedAtMs = LiveTvClock.nowMs()))
                    _errors.value = _errors.value - id
                    persistPools()
                    rebuildChannels()
                    rebuildWindow()
                }.onFailure { error ->
                    if (error is kotlinx.coroutines.CancellationException) throw error
                    val message = error.message?.takeIf { it.isNotBlank() } ?: "Could not load this channel's catalog"
                    _pools.value = _pools.value + (id to (_pools.value[id] ?: VirtualPoolSnapshot()).copy(error = message))
                    _errors.value = _errors.value + (id to message)
                    rebuildWindow()
                }
            } finally {
                _refreshing.value = _refreshing.value - id
            }
        }
    }

    private fun refreshStale(forceEmpty: Boolean) {
        val now = LiveTvClock.nowMs()
        _specs.value.filter { it.enabled }.forEach { spec ->
            val snapshot = _pools.value[spec.id]
            val staleAfter = spec.pool.refreshHours.coerceAtLeast(1) * 3_600_000L
            val empty = snapshot == null || snapshot.items.isEmpty()
            val fresh = snapshot != null && snapshot.items.isNotEmpty() && snapshot.fetchedAtMs > 0L && now - snapshot.fetchedAtMs < staleAfter
            if (fresh) return@forEach
            if (!forceEmpty && empty && (lastAttemptMs[spec.id] ?: 0L) > 0L && now - (lastAttemptMs[spec.id] ?: 0L) < RETRY_AFTER_MS) return@forEach
            refreshPool(spec.id, force = forceEmpty && snapshot == null)
        }
    }

    private suspend fun buildPool(spec: VirtualChannelSpec): List<VirtualItem> = withContext(Dispatchers.Default) {
        val items = when (val source = spec.source) {
            is VirtualSource.AddonCatalog -> {
                fetchCatalogPool(source, spec)
            }
            is VirtualSource.AddonItem -> {
                when (spec.kind) {
                    VirtualChannelKind.SERIES_MARATHON -> {
                        val details = MetaDetailsRepository.fetch(source.itemType, source.itemId) ?: return@withContext emptyList()
                        episodesOf(details)
                    }
                    VirtualChannelKind.COLLECTION_LOOP -> {
                        val details = MetaDetailsRepository.fetch(source.itemType, source.itemId)
                        when {
                            details == null -> listOf(
                                VirtualItem(
                                    id = source.itemId,
                                    streamId = source.itemId,
                                    type = source.itemType,
                                    title = source.itemId,
                                )
                            )
                            source.itemType == "series" -> episodesOf(details)
                            else -> listOf(itemOf(details, source.itemType))
                        }
                    }
                    VirtualChannelKind.ADDON_ROTATE -> {
                        val details = MetaDetailsRepository.fetch(source.itemType, source.itemId)
                        if (details != null && source.itemType == "series") {
                            listOf(seriesItemOf(details))
                        } else if (details != null) {
                            listOf(itemOf(details, source.itemType))
                        } else {
                            listOf(
                                VirtualItem(
                                    id = source.itemId,
                                    streamId = source.itemId,
                                    type = source.itemType,
                                    title = source.itemId,
                                )
                            )
                        }
                    }
                    else -> {
                        val details = MetaDetailsRepository.fetch(source.itemType, source.itemId)
                        if (details != null) listOf(seriesItemOf(details)) else emptyList()
                    }
                }
            }
            is VirtualSource.ManualItems -> source.items
            is VirtualSource.PlaylistGroup -> {
                val all = LiveTvRepository.channels.value
                all.asSequence()
                    .filter { it.sourceId == source.sourceId }
                    .filter { source.groupId == null || it.groupId == source.groupId }
                    .map { channel ->
                        VirtualItem(
                            id = "live:" + channel.key,
                            type = "live",
                            title = channel.name,
                            poster = channel.thumbnail ?: channel.logo,
                            description = channel.group,
                            streamUrl = channel.url,
                            headers = channel.headers,
                        )
                    }
                    .toList()
            }
            is VirtualSource.Rules -> fetchRulesPool(source, spec)
        }
        val filtered = filterItems(items, spec)
        orderItems(filtered.distinctBy { it.id }, spec).take(spec.pool.maxItems.coerceIn(1, 10_000))
    }

    private suspend fun fetchCatalogPool(source: VirtualSource.AddonCatalog, spec: VirtualChannelSpec): List<VirtualItem> {
        val items = ArrayList<MetaPreview>()
        var skip: Int? = 0
        var pages = 0
        val cap = spec.pool.maxItems.coerceIn(1, 10_000)
        // A series catalog is expanded into episodes. Fetching hundreds of shows would be
        // pointless: only the first few fill the pool with episodes.
        val previewCap = if (source.contentType.equals("series", true)) {
            minOf(cap, MAX_SERIES_TO_EXPAND)
        } else {
            cap
        }
        while (skip != null && items.size < previewCap && pages < MAX_CATALOG_PAGES) {
            val page = runCatching {
                fetchCatalogPage(
                    manifestUrl = source.manifestUrl,
                    type = source.contentType,
                    catalogId = source.catalogId,
                    genre = source.genre,
                    skip = skip,
                    maxItems = (previewCap - items.size).coerceAtMost(100),
                )
            }.getOrElse { error ->
                if (items.isEmpty()) throw error
                break
            }
            if (page.items.isEmpty()) break
            items += page.items
            val next = page.nextSkip
            if (next == null || next <= (skip ?: 0)) break
            skip = next
            pages++
        }
        val previews = items
            .filter { source.contentType.isBlank() || it.type.isBlank() || it.type.equals(source.contentType, ignoreCase = true) }
            .map { item -> itemOf(item, source.contentType) }
        val hasSeries = previews.any { it.type.equals("series", true) }
        return if (hasSeries) expandSeriesItems(previews) else previews
    }

    /**
     * A series is not playable without an episode. Expand series catalog entries into their
     * episodes here, so both the guide and the player use the episode id, title and runtime.
     */
    private suspend fun expandSeriesItems(previews: List<VirtualItem>): List<VirtualItem> {
        val series = previews.filter { it.type.equals("series", true) }.take(MAX_SERIES_TO_EXPAND)
        if (series.isEmpty()) return previews
        val details = coroutineScope {
            series.map { item ->
                async {
                    item to runCatching { MetaDetailsRepository.fetch("series", item.id) }
                        .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
                        .getOrNull()
                }
            }.awaitAll()
        }
        val byId = details.toMap()
        val out = ArrayList<VirtualItem>(previews.size)
        previews.forEach { preview ->
            if (!preview.type.equals("series", true)) {
                out += preview
                return@forEach
            }
            val meta = byId[preview]
            val episodes = meta?.let(::episodesOf)
                ?.take(MAX_EPISODES_PER_SERIES)
                .orEmpty()
            if (episodes.isEmpty()) out += preview else out += episodes
        }
        return out
    }

    private suspend fun fetchRulesPool(source: VirtualSource.Rules, spec: VirtualChannelSpec): List<VirtualItem> {
        runCatching { AddonRepository.initialize() }
        val addons = AddonRepository.uiState.value.addons
            .filter { it.enabled && it.manifest != null }
            .filter { addon ->
                (source.manifestUrls.isEmpty() || addon.manifestUrl in source.manifestUrls) &&
                    (source.addonIds.isEmpty() || addon.manifest?.id in source.addonIds)
            }
        val out = ArrayList<VirtualItem>()
        for (addon in addons) {
            val manifest = addon.manifest ?: continue
            val catalogs = manifest.catalogs
                .filter { source.contentType.isBlank() || it.type.equals(source.contentType, ignoreCase = true) }
                .take(MAX_RULE_CATALOGS)
            for (catalog in catalogs) {
                if (out.size >= spec.pool.maxItems) break
                val items = runCatching {
                    fetchCatalogPage(
                        manifestUrl = addon.manifestUrl,
                        type = catalog.type,
                        catalogId = catalog.id,
                        genre = source.genres.firstOrNull(),
                        skip = 0,
                        maxItems = (spec.pool.maxItems - out.size).coerceAtMost(150),
                    ).items
                }.getOrDefault(emptyList())
                out += items.map { item -> itemOf(item, source.contentType) }
            }
            if (out.size >= spec.pool.maxItems) break
        }
        return out
    }

    private fun itemOf(item: MetaPreview, defaultType: String = ""): VirtualItem = VirtualItem(
        id = item.id,
        streamId = item.id,
        type = item.type.ifBlank { defaultType },
        title = item.name,
        poster = item.poster,
        backdrop = item.banner ?: item.landscapePoster,
        description = item.description,
        year = yearOf(item.rawReleaseDate ?: item.releaseInfo),
        rating = item.imdbRating?.toFloatOrNull(),
        runtimeMs = runtimeMsOf(item.runtime),
        genres = item.genres,
    )

    private fun seriesItemOf(details: MetaDetails): VirtualItem = VirtualItem(
        id = details.id,
        streamId = details.id,
        type = "series",
        title = details.name,
        poster = details.poster,
        backdrop = details.background,
        description = details.description,
        year = yearOf(details.releaseInfo),
        rating = details.imdbRating?.toFloatOrNull(),
        runtimeMs = runtimeMsOf(details.runtime),
        genres = details.genres,
        parentId = details.id,
    )

    private fun itemOf(details: MetaDetails, type: String): VirtualItem = VirtualItem(
        id = details.id,
        streamId = details.id,
        type = type,
        title = details.name,
        poster = details.poster,
        backdrop = details.background,
        description = details.description,
        year = yearOf(details.releaseInfo),
        rating = details.imdbRating?.toFloatOrNull(),
        runtimeMs = runtimeMsOf(details.runtime),
        genres = details.genres,
        parentId = details.id,
    )

    private fun episodesOf(details: MetaDetails): List<VirtualItem> =
        details.videos.asSequence()
            .filter { it.available }
            .filter { (it.season ?: 0) > 0 && it.episode != null }
            .sortedWith(compareBy({ it.season ?: 0 }, { it.episode ?: 0 }))
            .map { video -> episodeOf(details, video) }
            .toList()

    private fun episodeOf(details: MetaDetails, video: MetaVideo): VirtualItem {
        val season = video.season
        val episode = video.episode
        val id = video.id.takeIf { it.isNotBlank() }
            ?: "${details.id}:$season:$episode"
        return VirtualItem(
            id = id,
            // Stream add-ons build /stream/series/<videoId>.json. For episodes that id must be
            // the episode id ("tt123:1:1"); the parent series id only belongs in parentId.
            streamId = id,
            type = "series",
            title = details.name,
            poster = video.thumbnail ?: details.poster,
            backdrop = details.background,
            description = video.overview ?: details.description,
            year = yearOf(video.released ?: details.releaseInfo),
            rating = video.rating?.toFloat(),
            runtimeMs = video.runtime?.takeIf { it > 0 }?.times(60_000L),
            genres = details.genres,
            season = season,
            episode = episode,
            episodeTitle = video.title,
            parentId = details.id,
        )
    }

    private fun filterItems(items: List<VirtualItem>, spec: VirtualChannelSpec): List<VirtualItem> {
        val rules = spec.pool
        val source = spec.source
        val include = when (source) {
            is VirtualSource.Rules -> rules.includeGenres + source.genres
            else -> rules.includeGenres
        }
        val minYear = rules.minYear ?: (source as? VirtualSource.Rules)?.minYear
        val maxYear = rules.maxYear ?: (source as? VirtualSource.Rules)?.maxYear
        val minRating = rules.minRating ?: (source as? VirtualSource.Rules)?.minRating
        val keyword = rules.keyword ?: (source as? VirtualSource.Rules)?.keyword
        val keywordLower = keyword?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
        return items.filter { item ->
            val yearOk = (minYear == null || (item.year ?: 0) >= minYear) &&
                (maxYear == null || (item.year ?: Int.MAX_VALUE) <= maxYear)
            val ratingOk = minRating == null || (item.rating ?: 0f) >= minRating
            val keywordOk = keywordLower == null ||
                item.title.lowercase().contains(keywordLower) ||
                item.description.orEmpty().lowercase().contains(keywordLower)
            val genres = itemGenres(item)
            val includeOk = include.isEmpty() || include.any { wanted ->
                genres.any { it.equals(wanted, ignoreCase = true) }
            }
            val excludeOk = rules.excludeGenres.none { unwanted ->
                genres.any { it.equals(unwanted, ignoreCase = true) }
            }
            yearOk && ratingOk && keywordOk && includeOk && excludeOk
        }
    }

    private fun itemGenres(item: VirtualItem): List<String> = item.genres

    private fun orderItems(items: List<VirtualItem>, spec: VirtualChannelSpec): List<VirtualItem> {
        val order = when (spec.playout.order) {
            VirtualOrder.MANUAL -> spec.pool.order
            else -> spec.playout.order
        }
        return when (order) {
            VirtualOrder.SHUFFLE -> items.sortedBy { VirtualSchedule.stableHash("${spec.id}|${it.id}") }
            VirtualOrder.SEQUENTIAL, VirtualOrder.MANUAL -> items
            VirtualOrder.RELEASE -> items.sortedWith(
                compareByDescending<VirtualItem> { it.year ?: 0 }.thenBy { it.title.lowercase() }
            )
            VirtualOrder.AIR -> items.sortedWith(
                compareBy<VirtualItem> { it.season ?: 0 }
                    .thenBy { it.episode ?: 0 }
                    .thenBy { it.title.lowercase() }
            )
        }
    }

    // ---------------------------------------------------------------- channels and EPG

    private fun rebuildChannels() {
        val enabled = _specs.value.filter { it.enabled }
        _channels.value = enabled.mapIndexed { index, spec ->
            VirtualChannels.channelFor(spec, index, _pools.value[spec.id]?.items?.firstOrNull()?.poster)
        }
    }

    private fun rebuildWindow() {
        val now = LiveTvClock.nowMs()
        val map = HashMap<String, List<EpgProgram>>()
        _specs.value.filter { it.enabled }.forEach { spec ->
            val items = _pools.value[spec.id]?.items.orEmpty()
            if (items.isEmpty()) return@forEach
            val programs = programsFor(spec, items, now - WINDOW_PAST_MS, now + WINDOW_FUTURE_MS)
            if (programs.isNotEmpty()) map[spec.channelKey] = programs
        }
        _programsWindow.value = map
    }

    private fun programsFor(
        spec: VirtualChannelSpec,
        items: List<VirtualItem>,
        fromMs: Long,
        toMs: Long,
    ): List<EpgProgram> {
        if (items.isEmpty() || toMs <= fromMs) return emptyList()
        val slotMs = spec.rotationMinutes.coerceAtLeast(1) * 60_000L
        val segments = if (spec.playout.mode == VirtualPlayoutMode.LOOP) {
            VirtualSchedule.playoutSegments(
                anchorMs = spec.playout.anchorMs,
                items = items,
                fallbackDurationMs = slotMs,
                fromMs = fromMs,
                toMs = toMs,
            )
        } else {
            VirtualSchedule.rotatePrograms(
                channelId = spec.channelKey,
                items = items,
                slotMs = slotMs,
                salt = spec.playout.salt,
                fromMs = fromMs,
                toMs = toMs,
            )
        }
        return segments.map(VirtualSchedule::toProgram)
    }

    fun programsIn(keys: Collection<String>, fromMs: Long, toMs: Long): Map<String, List<EpgProgram>> {
        if (keys.isEmpty()) return emptyMap()
        val virtualKeys = keys.filter { VirtualChannels.isVirtualKey(it) }.toSet()
        if (virtualKeys.isEmpty()) return emptyMap()
        val out = HashMap<String, List<EpgProgram>>()
        _specs.value.filter { it.enabled && it.channelKey in virtualKeys }.forEach { spec ->
            val items = _pools.value[spec.id]?.items.orEmpty()
            if (items.isEmpty()) return@forEach
            val programs = programsFor(spec, items, fromMs, toMs)
            if (programs.isNotEmpty()) out[spec.channelKey] = programs
        }
        return out
    }

    fun programDetails(channelKey: String, startMs: Long): EpgProgram? {
        val id = VirtualChannels.specIdOf(channelKey) ?: return null
        val spec = _specs.value.firstOrNull { it.id == id && it.enabled } ?: return null
        val items = _pools.value[spec.id]?.items.orEmpty()
        if (items.isEmpty()) return null
        return programsFor(spec, items, startMs, startMs + 1L)
            .firstOrNull { startMs >= it.startMs && startMs < it.stopMs }
            ?: programsFor(spec, items, startMs - 1L, startMs + 1L).firstOrNull()
    }

    // ---------------------------------------------------------------- tuning

    suspend fun tune(channelKey: String, nowMs: Long = LiveTvClock.nowMs()): VirtualTune? {
        val id = VirtualChannels.specIdOf(channelKey) ?: return null
        val spec = _specs.value.firstOrNull { it.id == id && it.enabled } ?: return null
        val items = _pools.value[spec.id]?.items.orEmpty()
        if (items.isEmpty()) return null
        val slotMs = spec.rotationMinutes.coerceAtLeast(1) * 60_000L
        return if (spec.playout.mode == VirtualPlayoutMode.LOOP) {
            val segment = VirtualSchedule.playoutAt(
                nowMs = nowMs,
                anchorMs = spec.playout.anchorMs,
                items = items,
                fallbackDurationMs = slotMs,
            ) ?: return null
            val duration = segment.item.runtimeMs?.takeIf { it > 0 } ?: (segment.endMs - segment.startMs)
            val relay = segment.item.streamUrl != null
            VirtualTune(
                channelKey = channelKey,
                specId = spec.id,
                item = segment.item,
                season = segment.item.season,
                episode = segment.item.episode,
                episodeTitle = segment.item.episodeTitle,
                title = tuneTitle(segment.item),
                startOffsetMs = if (relay) 0L else VirtualSchedule.joinOffsetMs(nowMs, segment.startMs, duration, spec.join, channelKey),
                slotStartMs = segment.startMs,
                slotEndMs = segment.endMs,
                relay = relay,
            )
        } else {
            var slot = VirtualSchedule.slotAt(nowMs, channelKey, slotMs)
            var item = VirtualSchedule.itemForSlot(channelKey, slot, items, spec.playout.salt) ?: return null
            if (item.type == "series" && item.season == null) {
                resolveEpisode(spec, item, slot.number)?.let { item = it }
            }
            val relay = item.streamUrl != null
            VirtualTune(
                channelKey = channelKey,
                specId = spec.id,
                item = item,
                season = item.season,
                episode = item.episode,
                episodeTitle = item.episodeTitle,
                title = tuneTitle(item),
                startOffsetMs = if (relay) 0L else VirtualSchedule.joinOffsetMs(nowMs, slot.startMs, slotMs, spec.join, channelKey),
                slotStartMs = slot.startMs,
                slotEndMs = slot.endMs,
                relay = relay,
            )
        }
    }

    private fun tuneTitle(item: VirtualItem): String {
        val label = VirtualSchedule.episodeLabel(item.season, item.episode)
        return when {
            item.episodeTitle.isNullOrBlank() && label.isBlank() -> item.title
            item.episodeTitle.isNullOrBlank() -> "${item.title} · $label"
            label.isBlank() -> "${item.title} · ${item.episodeTitle}"
            else -> "${item.title} · $label · ${item.episodeTitle}"
        }
    }

    /**
     * The next [count] items after [fromMs], for prefetching. Mirrors [tune] but does not resolve
     * streams; the playback cache does that in the background.
     */
    suspend fun upcomingTunes(
        channelKey: String,
        fromMs: Long = LiveTvClock.nowMs(),
        count: Int = VirtualPlaybackSettings().prefetchNext,
    ): List<VirtualTune> {
        val id = VirtualChannels.specIdOf(channelKey) ?: return emptyList()
        val spec = _specs.value.firstOrNull { it.id == id && it.enabled } ?: return emptyList()
        val items = _pools.value[spec.id]?.items.orEmpty()
        if (items.isEmpty() || count <= 0) return emptyList()
        val slotMs = spec.rotationMinutes.coerceAtLeast(1) * 60_000L
        val out = ArrayList<VirtualTune>(count)

        if (spec.playout.mode == VirtualPlayoutMode.LOOP) {
            val current = VirtualSchedule.playoutAt(fromMs, spec.playout.anchorMs, items, slotMs)
                ?: return emptyList()
            val durations = VirtualSchedule.durationsOf(items, slotMs)
            val cycle = durations.sum().coerceAtLeast(1L)
            var offset = VirtualSchedule.floorMod(current.startMs - spec.playout.anchorMs, cycle)
            var index = 0
            var accumulated = 0L
            while (index < items.lastIndex && offset >= accumulated + durations[index]) {
                accumulated += durations[index]
                index++
            }
            var cursor = current.endMs
            repeat(count) {
                index = (index + 1) % items.size
                val item = items[index]
                val duration = durations[index]
                out += tuneOf(
                    spec = spec,
                    channelKey = channelKey,
                    item = item,
                    startMs = cursor,
                    endMs = cursor + duration,
                    startOffsetMs = 0L,
                )
                cursor += duration
            }
        } else {
            var slot = VirtualSchedule.slotAt(fromMs, channelKey, slotMs)
            repeat(count) {
                slot = VirtualSchedule.RotateSlot(slot.number + 1, slot.endMs, slot.endMs + slotMs)
                var item = VirtualSchedule.itemForSlot(channelKey, slot, items, spec.playout.salt) ?: return@repeat
                if (item.type == "series" && item.season == null) {
                    resolveEpisode(spec, item, slot.number)?.let { item = it }
                }
                out += tuneOf(
                    spec = spec,
                    channelKey = channelKey,
                    item = item,
                    startMs = slot.startMs,
                    endMs = slot.endMs,
                    startOffsetMs = 0L,
                )
            }
        }
        return out
    }

    private fun tuneOf(
        spec: VirtualChannelSpec,
        channelKey: String,
        item: VirtualItem,
        startMs: Long,
        endMs: Long,
        startOffsetMs: Long,
    ): VirtualTune {
        val relay = item.streamUrl != null
        return VirtualTune(
            channelKey = channelKey,
            specId = spec.id,
            item = item,
            season = item.season,
            episode = item.episode,
            episodeTitle = item.episodeTitle,
            title = tuneTitle(item),
            startOffsetMs = if (relay) 0L else startOffsetMs,
            slotStartMs = startMs,
            slotEndMs = endMs,
            relay = relay,
        )
    }

    private suspend fun resolveEpisode(spec: VirtualChannelSpec, series: VirtualItem, slotNumber: Long): VirtualItem? {
        val episodes = seriesMutex.withLock {
            seriesCache[series.id] ?: run {
                val fetched = runCatching { MetaDetailsRepository.fetch("series", series.id) }
                    .getOrNull()
                    ?.let(::episodesOf)
                    .orEmpty()
                seriesCache[series.id] = fetched
                fetched
            }
        }
        if (episodes.isEmpty()) return null
        val index = VirtualSchedule.floorMod(
            VirtualSchedule.stableHash("${spec.channelKey}:$slotNumber").toLong(),
            episodes.size.toLong(),
        ).toInt()
        return episodes[index]
    }

    // ---------------------------------------------------------------- import / export

    fun exportPack(): String = VirtualChannelCodec.encodePack(
        VirtualChannelPack(
            schemaVersion = VirtualChannels.SCHEMA_VERSION,
            exportedAtMs = LiveTvClock.nowMs(),
            channels = _specs.value,
        )
    )

    /** Imports every channel as a new copy; returns how many were added. */
    fun importPack(raw: String): Int {
        ensureLoaded()
        val pack = VirtualChannelCodec.decodePack(raw)
        if (pack.channels.isEmpty()) return 0
        val taken = _specs.value.mapTo(HashSet()) { it.id }
        val now = LiveTvClock.nowMs()
        val additions = pack.channels.map { spec ->
            val id = spec.id.takeIf { it.isNotBlank() && it !in taken } ?: VirtualChannelCodec.newId()
            spec.copy(
                id = id,
                name = spec.name,
                group = VirtualChannels.DEFAULT_GROUP,
                createdAtMs = now,
                updatedAtMs = now,
                schemaVersion = VirtualChannels.SCHEMA_VERSION,
            )
        }
        _specs.value = _specs.value + additions
        persistSpecs()
        rebuildChannels()
        rebuildWindow()
        additions.forEach { refreshPool(it.id, force = true) }
        return additions.size
    }

    // ---------------------------------------------------------------- persistence

    private fun persistSpecs() {
        prefs.setVirtualChannelsJson(
            if (_specs.value.isEmpty()) null else VirtualChannelCodec.encodeSpecs(_specs.value)
        )
    }

    private fun persistPools() {
        prefs.setVirtualPoolsJson(
            if (_pools.value.isEmpty()) null else VirtualChannelCodec.encodePools(_pools.value)
        )
    }

    // ---------------------------------------------------------------- helpers

    /** Every Lucid TV channel lives in one fixed group, and joins at the real EPG position. */
    private fun normalizeSpecs(list: List<VirtualChannelSpec>): List<VirtualChannelSpec> =
        list.map { spec ->
            var normalized = if (spec.group == VirtualChannels.DEFAULT_GROUP) {
                spec
            } else {
                spec.copy(group = VirtualChannels.DEFAULT_GROUP)
            }
            // 20% was the old mobile default; it made playback start minutes away from the guide.
            if (normalized.join.maxStartPercent == 20) {
                normalized = normalized.copy(join = normalized.join.copy(maxStartPercent = -1))
            }
            // Series catalog channels used to run in fixed ROTATE slots, so a 22-minute episode
            // sat frozen until the 45-minute boundary. Run them as a real-runtime queue.
            if (normalized.kind == VirtualChannelKind.ADDON_SERIES &&
                normalized.playout.mode == VirtualPlayoutMode.ROTATE
            ) {
                normalized = normalized.copy(
                    playout = normalized.playout.copy(
                        mode = VirtualPlayoutMode.LOOP,
                        order = VirtualOrder.AIR,
                    ),
                )
            }
            normalized
        }

    /**
     * Pools saved by the first mobile Lucid build used the parent series id as the stream id,
     * and series catalogs were stored as whole shows. Repair the ids in place and force a
     * refresh for the series catalogs so they are expanded into episodes.
     */
    private fun repairPools(
        pools: Map<String, VirtualPoolSnapshot>,
        specs: List<VirtualChannelSpec>,
    ): Map<String, VirtualPoolSnapshot> {
        if (pools.isEmpty()) return pools
        val byId = specs.associateBy { it.id }
        return pools.mapValues { (id, snapshot) ->
            val repairedItems = snapshot.items.map { item ->
                if (item.season != null && item.episode != null && item.streamId != item.id) {
                    item.copy(streamId = item.id)
                } else {
                    item
                }
            }
            val spec = byId[id]
            val source = spec?.source
            val wholeSeries = (spec?.kind == VirtualChannelKind.ADDON_SERIES) ||
                (source is VirtualSource.AddonCatalog && source.contentType.equals("series", true))
            val hasWholeSeriesItems = wholeSeries && repairedItems.any {
                it.type == "series" && it.season == null
            }
            snapshot.copy(
                items = repairedItems,
                fetchedAtMs = if (hasWholeSeriesItems) 0L else snapshot.fetchedAtMs,
            )
        }
    }

    private fun yearOf(text: String?): Int? {
        if (text.isNullOrBlank()) return null
        return Regex("""(19|20)\d{2}""").find(text)?.value?.toIntOrNull()
    }

    private fun runtimeMsOf(text: String?): Long? {
        if (text.isNullOrBlank()) return null
        val minutes = Regex("""(\d+)\s*(min|m)\b""", RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1)?.toLongOrNull()
            ?: Regex("""^\d+$""").find(text.trim())?.value?.toLongOrNull()
        return minutes?.takeIf { it > 0 }?.times(60_000L)
    }

    private const val WINDOW_PAST_MS = 2L * 60 * 60 * 1000
    private const val WINDOW_FUTURE_MS = 12L * 60 * 60 * 1000
    private const val RETRY_AFTER_MS = 10L * 60 * 1000
    private const val MAX_CATALOG_PAGES = 50
    private const val MAX_RULE_CATALOGS = 6
    private const val MAX_SERIES_TO_EXPAND = 8
    private const val MAX_EPISODES_PER_SERIES = 40
}
