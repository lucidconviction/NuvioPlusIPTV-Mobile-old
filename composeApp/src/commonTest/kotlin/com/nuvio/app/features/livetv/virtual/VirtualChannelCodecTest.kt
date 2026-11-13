package com.nuvio.app.features.livetv.virtual

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The JSON exported by Lucid TV matches the TV app's schema and survives a round trip. */
class VirtualChannelCodecTest {

    private fun catalogSpec() = VirtualChannelSpec(
        id = "vc_01",
        name = "90s Action",
        kind = VirtualChannelKind.ADDON_ROTATE,
        group = "My 24/7",
        number = 9001,
        logo = "https://example.com/logo.png",
        source = VirtualSource.AddonCatalog(
            manifestUrl = "https://v3-cinemeta.strem.io/manifest.json",
            addonId = "cinemeta",
            addonName = "Cinemeta",
            catalogId = "top",
            catalogName = "Top movies",
            contentType = "movie",
            genre = "Action",
        ),
        pool = VirtualPoolRules(maxItems = 500, refreshHours = 12, minYear = 1990, maxYear = 1999, minRating = 6f),
        playout = VirtualPlayout(mode = VirtualPlayoutMode.ROTATE, slotMinutes = 90, salt = 3),
        join = VirtualJoin(maxStartPercent = 20, jitter = true),
        createdAtMs = 1_763_000_000_000L,
        updatedAtMs = 1_763_000_000_000L,
    )

    @Test
    fun specRoundTripsWithTheTvAppFieldNames() {
        val encoded = VirtualChannelCodec.encodeSpecs(listOf(catalogSpec()))
        assertTrue(encoded.contains("\"kind\":\"ADDON_ROTATE\""))
        assertTrue(encoded.contains("\"type\":\"addon_catalog\""))
        assertTrue(encoded.contains("\"catalogId\":\"top\""))
        val decoded = VirtualChannelCodec.decodeSpecs(encoded)
        assertEquals(1, decoded.size)
        val spec = decoded.single()
        assertEquals("vc_01", spec.id)
        assertEquals("90s Action", spec.name)
        assertEquals(VirtualChannelKind.ADDON_ROTATE, spec.kind)
        assertEquals("My 24/7", spec.group)
        assertEquals(9001, spec.number)
        val source = spec.source as VirtualSource.AddonCatalog
        assertEquals("Action", source.genre)
        assertEquals(1990, spec.pool.minYear)
        assertEquals(6f, spec.pool.minRating)
        assertEquals(20, spec.join.maxStartPercent)
        assertEquals(3, spec.playout.salt)
    }

    @Test
    fun manualItemsAndHeadersRoundTrip() {
        val spec = VirtualChannelSpec(
            id = "vc_manual",
            name = "My collection",
            kind = VirtualChannelKind.COLLECTION_LOOP,
            source = VirtualSource.ManualItems(
                listOf(
                    VirtualItem(
                        id = "live:one",
                        type = "live",
                        title = "Channel one",
                        streamUrl = "https://example.com/one.m3u8",
                        headers = mapOf("User-Agent" to "Lucid"),
                        runtimeMs = 60_000L,
                    )
                )
            ),
            playout = VirtualPlayout(mode = VirtualPlayoutMode.LOOP, slotMinutes = 60),
        )
        val decoded = VirtualChannelCodec.decodeSpecs(VirtualChannelCodec.encodeSpecs(listOf(spec))).single()
        val items = (decoded.source as VirtualSource.ManualItems).items
        assertEquals(1, items.size)
        assertEquals("https://example.com/one.m3u8", items.single().streamUrl)
        assertEquals("Lucid", items.single().headers["User-Agent"])
    }

    @Test
    fun unknownFieldsAndMissingOptionalFieldsAreTolerated() {
        val raw = """
            {
              "id": "vc_future",
              "name": "Future channel",
              "kind": "SOMETHING_NEW",
              "source": { "type": "manual", "items": [ { "id": "m1", "type": "movie", "title": "M1" } ], "futureField": true },
              "playout": { "mode": "LOOP", "futureNumber": 4 },
              "futureTop": "ignored"
            }
        """.trimIndent()
        val spec = VirtualChannelCodec.decodeSpecs("[$raw]").single()
        // An unknown kind falls back to the catalog rotation instead of dropping the channel.
        assertEquals(VirtualChannelKind.ADDON_ROTATE, spec.kind)
        assertEquals("Future channel", spec.name)
        assertTrue(spec.source is VirtualSource.ManualItems)
        assertEquals(VirtualPlayoutMode.LOOP, spec.playout.mode)
    }

    @Test
    fun aPackCarriesEveryChannel() {
        val pack = VirtualChannelPack(
            schemaVersion = 1,
            exportedAtMs = 1_763_000_000_000L,
            channels = listOf(catalogSpec(), catalogSpec().copy(id = "vc_02", name = "Second")),
        )
        val decoded = VirtualChannelCodec.decodePack(VirtualChannelCodec.encodePack(pack))
        assertEquals(2, decoded.channels.size)
        assertEquals(1_763_000_000_000L, decoded.exportedAtMs)
        assertEquals("Second", decoded.channels[1].name)
    }

    @Test
    fun aSpecWithoutASourceIsIgnored() {
        val decoded = VirtualChannelCodec.decodeSpecs("""[{"id":"vc_none","name":"No source"}]""")
        assertTrue(decoded.isEmpty())
    }

    @Test
    fun groupIdsAreStableAndReadable() {
        assertEquals("virtual:lucid-tv", VirtualChannels.groupId("Lucid TV"))
        assertEquals("virtual:my-24-7", VirtualChannels.groupId(" My 24/7 "))
        assertEquals("virtual:lucid-tv", VirtualChannels.groupId("   "))
        assertEquals("virtual:vc_1", VirtualChannels.channelKey("vc_1"))
        assertEquals("vc_1", VirtualChannels.specIdOf("virtual:vc_1"))
        assertTrue(VirtualChannels.isVirtualKey("virtual:vc_1"))
        assertTrue(!VirtualChannels.isVirtualKey("pl|channel"))
    }

    @Test
    fun poolSnapshotsRoundTrip() {
        val snapshot = VirtualPoolSnapshot(
            items = listOf(VirtualItem(id = "a", type = "movie", title = "A", runtimeMs = 90_000L)),
            fetchedAtMs = 1_763_000_000_000L,
        )
        val decoded = VirtualChannelCodec.decodePools(VirtualChannelCodec.encodePools(mapOf("vc_01" to snapshot)))
        val restored = decoded["vc_01"]
        assertNotNull(restored)
        assertEquals(1, restored.items.size)
        assertEquals("A", restored.items.single().title)
        assertEquals(1_763_000_000_000L, restored.fetchedAtMs)
    }
}
