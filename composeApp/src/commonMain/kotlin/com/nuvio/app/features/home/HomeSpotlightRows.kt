package com.nuvio.app.features.home

import com.nuvio.app.features.watchprogress.WatchProgressEntry

internal data class HomeSpotlightRows(
    val topTenSource: HomeCatalogSection?,
    val topTen: HomeCatalogSection?,
    val becauseYouWatched: HomeCatalogSection?,
)

private val TOP_TEN_TITLE = Regex("top|popular|trending|most watched", RegexOption.IGNORE_CASE)

/**
 * Builds the optional Home spotlight rows from already-loaded catalogs.
 *
 * Top 10 only uses an enabled catalog whose name reads like a ranking and which has ten titles.
 * Because you watched uses the newest unfinished progress entry, finds an enabled catalog that
 * contains that title, and suggests other titles from it. No row is invented when the data is not
 * there, so hidden or disabled catalogs never leak into these rows.
 */
internal fun buildHomeSpotlightRows(
    sections: List<HomeCatalogSection>,
    enabledCatalogKeys: Set<String>,
    progressEntries: List<WatchProgressEntry>,
): HomeSpotlightRows {
    val enabledSections = sections.filter { it.key in enabledCatalogKeys }
    val topTenSource = enabledSections
        .filter { it.items.size >= 10 }
        .firstOrNull { TOP_TEN_TITLE.containsMatchIn(it.title) }
    val topTen = topTenSource?.let { section ->
        section.copy(
            key = "home_top_10",
            title = "Top 10 Today",
            subtitle = section.addonName,
            items = section.items.take(10),
            availableItemCount = 10,
            hasMore = false,
        )
    }

    val latest = progressEntries
        .filterNot { it.isCompleted }
        .maxByOrNull { it.lastUpdatedEpochMs }
    val becauseYouWatched = latest?.let { entry ->
        val source = enabledSections.firstOrNull { section ->
            section.key != topTenSource?.key &&
                section.items.any { it.name.equals(entry.title, ignoreCase = true) }
        } ?: return@let null
        val picks = source.items
            .filterNot { it.name.equals(entry.title, ignoreCase = true) }
            .take(10)
        if (picks.size < 3) return@let null
        source.copy(
            key = "home_because_you_watched",
            title = "Because you watched ${entry.title}",
            subtitle = source.addonName,
            items = picks,
            availableItemCount = picks.size,
            hasMore = false,
        )
    }

    return HomeSpotlightRows(
        topTenSource = topTenSource,
        topTen = topTen,
        becauseYouWatched = becauseYouWatched,
    )
}
