package com.nuvio.app.features.home

import com.nuvio.app.features.catalog.CatalogTarget
import com.nuvio.app.features.watchprogress.WatchProgressEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HomeSpotlightRowsTest {

    @Test
    fun topTenUsesOnlyAnEnabledRankingCatalogWithTenTitles() {
        val popular = section("popular", "Popular on Addon", (1..12).map { item("Popular $it") })
        val hidden = section("hidden", "Top 10 Hidden", (1..12).map { item("Hidden $it") })
        val rows = buildHomeSpotlightRows(
            sections = listOf(popular, hidden),
            enabledCatalogKeys = setOf("popular"),
            progressEntries = emptyList(),
        )

        assertEquals("popular", rows.topTenSource?.key)
        assertEquals("Top 10 Today", rows.topTen?.title)
        assertEquals(10, rows.topTen?.items?.size)
        assertEquals("Popular 1", rows.topTen?.items?.first()?.name)
    }

    @Test
    fun topTenRequiresTenTitles() {
        val popular = section("popular", "Popular", (1..9).map { item("Popular $it") })
        val rows = buildHomeSpotlightRows(
            sections = listOf(popular),
            enabledCatalogKeys = setOf("popular"),
            progressEntries = emptyList(),
        )

        assertNull(rows.topTen)
        assertNull(rows.topTenSource)
    }

    @Test
    fun disabledCatalogCannotProduceAnyRow() {
        val popular = section("popular", "Popular", (1..12).map { item("Popular $it") })
        val rows = buildHomeSpotlightRows(
            sections = listOf(popular),
            enabledCatalogKeys = emptySet(),
            progressEntries = emptyList(),
        )

        assertNull(rows.topTen)
        assertNull(rows.becauseYouWatched)
    }

    @Test
    fun becauseYouWatchedUsesNewestUnfinishedEntryAndExcludesThatTitle() {
        val drama = section("drama", "Drama", (1..6).map { item("Show $it") })
        val rows = buildHomeSpotlightRows(
            sections = listOf(drama),
            enabledCatalogKeys = setOf("drama"),
            progressEntries = listOf(
                progress("Show 2", updated = 1_000L),
                progress("Show 1", updated = 2_000L, completed = true),
            ),
        )

        val row = rows.becauseYouWatched
        assertEquals("Because you watched Show 2", row?.title)
        assertTrue(row?.items?.none { it.name == "Show 2" } == true)
        assertEquals(5, row?.items?.size)
    }

    @Test
    fun becauseYouWatchedIgnoresCompletedProgress() {
        val drama = section("drama", "Drama", (1..6).map { item("Show $it") })
        val rows = buildHomeSpotlightRows(
            sections = listOf(drama),
            enabledCatalogKeys = setOf("drama"),
            progressEntries = listOf(progress("Show 1", updated = 2_000L, completed = true)),
        )

        assertNull(rows.becauseYouWatched)
    }

    @Test
    fun becauseYouWatchedNeedsAtLeastThreeOtherTitles() {
        val drama = section("drama", "Drama", (1..3).map { item("Show $it") })
        val rows = buildHomeSpotlightRows(
            sections = listOf(drama),
            enabledCatalogKeys = setOf("drama"),
            progressEntries = listOf(progress("Show 1", updated = 2_000L)),
        )

        assertNull(rows.becauseYouWatched)
    }

    @Test
    fun becauseYouWatchedDoesNotReuseTheTopTenSource() {
        val popular = section("popular", "Popular", (1..12).map { item("Popular $it") })
        val rows = buildHomeSpotlightRows(
            sections = listOf(popular),
            enabledCatalogKeys = setOf("popular"),
            progressEntries = listOf(progress("Popular 1", updated = 2_000L)),
        )

        assertEquals("popular", rows.topTenSource?.key)
        assertNull(rows.becauseYouWatched)
    }

    private fun section(key: String, title: String, items: List<MetaPreview>) = HomeCatalogSection(
        key = key,
        title = title,
        subtitle = "Addon",
        addonName = "Addon",
        target = CatalogTarget.Addon(
            manifestUrl = "https://example.com/manifest.json",
            contentType = "movie",
            catalogId = key,
        ),
        items = items,
    )

    private fun item(name: String) = MetaPreview(
        id = name,
        type = "movie",
        name = name,
    )

    private fun progress(title: String, updated: Long, completed: Boolean = false) = WatchProgressEntry(
        contentType = "movie",
        parentMetaId = title,
        parentMetaType = "movie",
        videoId = title,
        title = title,
        lastPositionMs = 100L,
        durationMs = 1_000L,
        lastUpdatedEpochMs = updated,
        isCompleted = completed,
    )
}
