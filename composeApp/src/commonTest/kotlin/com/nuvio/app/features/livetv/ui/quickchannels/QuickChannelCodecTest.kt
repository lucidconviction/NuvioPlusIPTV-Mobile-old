package com.nuvio.app.features.livetv.ui.quickchannels

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The quick-channel store survives a restart because this JSON round-trips exactly. */
class QuickChannelCodecTest {

    @Test
    fun categoriesAndChannelsRoundTrip() {
        val categories = listOf(
            QuickChannelList.QuickCategory(
                id = "my-region",
                title = "My Region",
                description = "Local favourites",
                tags = listOf("news", "sports"),
            ),
        )
        val channels = listOf(
            QuickChannel(
                displayName = "My Channel",
                aliases = listOf("Mine", "Local One"),
                regions = listOf("CUSTOM"),
                tags = listOf("news"),
            ),
        )
        val decoded = QuickChannelCodec.decode(QuickChannelCodec.encode(categories, channels))
        assertEquals(1, decoded.categories.size)
        assertEquals("my-region", decoded.categories.single().id)
        assertEquals(listOf("news", "sports"), decoded.categories.single().tags)
        assertEquals(1, decoded.channels.size)
        assertEquals("My Channel", decoded.channels.single().displayName)
        assertEquals(listOf("Mine", "Local One"), decoded.channels.single().aliases)
        assertEquals(listOf("CUSTOM"), decoded.channels.single().regions)
    }

    @Test
    fun blankIdsAndNamesAreDropped() {
        val raw = """
            {
              "categories": [ { "id": "", "title": "No id" }, { "id": "ok", "title": "OK" } ],
              "channels": [ { "displayName": "  " }, { "displayName": "Saved" } ]
            }
        """.trimIndent()
        val decoded = QuickChannelCodec.decode(raw)
        assertEquals(listOf("ok"), decoded.categories.map { it.id })
        assertEquals(listOf("Saved"), decoded.channels.map { it.displayName })
    }

    @Test
    fun missingOrBrokenJsonGivesAnEmptyStore() {
        assertTrue(QuickChannelCodec.decode(null).channels.isEmpty())
        assertTrue(QuickChannelCodec.decode("not json").channels.isEmpty())
        assertTrue(QuickChannelCodec.decode("{}").categories.isEmpty())
    }

    @Test
    fun duplicateEntriesAreKeptOnce() {
        val raw = """
            {
              "categories": [ { "id": "x", "title": "X" }, { "id": "x", "title": "X again" } ],
              "channels": [ { "displayName": "One" }, { "displayName": "One" } ]
            }
        """.trimIndent()
        val decoded = QuickChannelCodec.decode(raw)
        assertEquals(1, decoded.categories.size)
        assertEquals(1, decoded.channels.size)
    }
}
