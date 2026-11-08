package com.nuvio.app.features.livetv

import com.nuvio.app.features.livetv.model.LiveChannel
import com.nuvio.app.features.livetv.ui.quickchannels.QuickChannel
import com.nuvio.app.features.livetv.ui.quickchannels.QuickChannelList
import com.nuvio.app.features.livetv.ui.quickchannels.QuickStreamChecker
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Quick Channels matching and liveness heuristics that decide which channels are offered. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class QuickChannelsLogicTest {

    @Test
    fun rtDoesNotMatchCartoonNetworkButMatchesItsOwnNames() {
        val rt = QuickChannel("RT", listOf("RT News", "Russia Today"), listOf("US"))

        assertFalse(QuickChannelList.matches(rt, channel("Cartoon Network")))
        assertFalse(QuickChannelList.matches(rt, channel("Business Plus")))
        assertTrue(QuickChannelList.matches(rt, channel("RT")))
        assertTrue(QuickChannelList.matches(rt, channel("RT HD")))
        assertTrue(QuickChannelList.matches(rt, channel("Russia Today")))
    }

    @Test
    fun cnnAliasesMatchSpacingAndSuffixVariants() {
        val cnn = QuickChannel("CNN", listOf("CNN US", "CNN USA", "CNN International"), listOf("US"))

        assertTrue(QuickChannelList.matches(cnn, channel("CNN")))
        assertTrue(QuickChannelList.matches(cnn, channel("CNN US")))
        assertTrue(QuickChannelList.matches(cnn, channel("CNNUS HD")))
        assertTrue(QuickChannelList.matches(cnn, channel("CNN International")))
        assertFalse(QuickChannelList.matches(cnn, channel("Comedy Central")))
    }

    @Test
    fun punctuationAndSpacingDoNotBreakWholePhraseMatching() {
        val show = QuickChannel("Real America's Voice", listOf("RAV"), listOf("US"))

        assertTrue(QuickChannelList.matches(show, channel("Real Americas Voice")))
        assertTrue(QuickChannelList.matches(show, channel("REAL AMERICA'S VOICE HD")))
        assertFalse(QuickChannelList.matches(show, channel("America's Voice")))
    }

    @Test
    fun twoLetterPhrasesOnlyMatchWholeWords() {
        val us = QuickChannel("US", emptyList(), listOf("US"))

        assertTrue(QuickChannelList.matches(us, channel("US")))
        assertTrue(QuickChannelList.matches(us, channel("US | East")))
        assertFalse(QuickChannelList.matches(us, channel("Business")))
        assertFalse(QuickChannelList.matches(us, channel("Russia Today")))
    }

    @Test
    fun regionBundleMatchesNameAndGroupTokens() {
        val us = QuickChannel("US Channels", listOf("usa", "united states"), listOf("US"))

        assertTrue(QuickChannelList.matches(us, channel("CNN", group = "US | News")))
        assertTrue(QuickChannelList.matches(us, channel("USA Network", group = "General")))
        assertFalse(QuickChannelList.matches(us, channel("Business", group = "General")))
    }

    @Test
    fun manifestHeaderIsAliveEvenWhenItMentionsErrors() {
        assertTrue(QuickStreamChecker.looksAlive("#EXTM3U\n#EXT-X-VERSION:3\n#EXTINF:6,error channel"))
        assertTrue(QuickStreamChecker.looksAlive("\uFEFF  \n#EXTM3U\n"))
    }

    @Test
    fun htmlAndErrorPagesAreDead() {
        assertFalse(QuickStreamChecker.looksAlive(""))
        assertFalse(QuickStreamChecker.looksAlive("   \n\t"))
        assertFalse(QuickStreamChecker.looksAlive("<!DOCTYPE html><html><body>Not Found</body></html>"))
        assertFalse(QuickStreamChecker.looksAlive("<html><body>404</body></html>"))
        assertFalse(QuickStreamChecker.looksAlive("Service Unavailable"))
        assertFalse(QuickStreamChecker.looksAlive("invalid username"))
    }

    @Test
    fun binaryTransportStreamSamplesAreAlive() {
        val sample = "G@\u0001\u0002\u0010\u0003\u0000\u0004stream-packet"
        assertEquals(true, QuickStreamChecker.looksAlive(sample))
    }

    @Test
    fun probeBytesDecodeOneCharacterPerByte() {
        // Regression: appending a ByteArray to a StringBuilder used to append the array's
        // toString() (e.g. "[B@1234") instead of the media bytes, so every live probe looked
        // like an unclear/empty sample.
        val ts = byteArrayOf(0x47, 0x40, 0x01, 0x02, 0x10, 0x03, 0x00, 0x04)
        val sample = QuickStreamChecker.decodeSample(ts, ts.size)
        assertEquals(ts.size, sample.length)
        assertEquals(0x47, sample[0].code)
        assertEquals(0x01, sample[2].code)
        assertTrue(QuickStreamChecker.looksAlive(sample))

        val manifest = "#EXTM3U\n#EXT-X-VERSION:3\n".encodeToByteArray()
        val decoded = QuickStreamChecker.decodeSample(manifest, manifest.size)
        assertEquals("#EXTM3U\n#EXT-X-VERSION:3\n", decoded)
        assertTrue(QuickStreamChecker.looksAlive(decoded))
    }

    @Test
    fun binarySamplesContainingErrorWordsAreAlive() {
        // Random bytes in media containers regularly spell "404", "error" or "offline"; those
        // words must not hide an otherwise valid stream.
        val tsSample = "G@\u0001\u0002\u0010\u0003\u0000\u0004 error 404 not found offline"
        assertTrue(QuickStreamChecker.looksAlive(tsSample))
        val mp4Sample = "\u0000\u0000\u0000\u0018ftypisom\u0000\u0000\u0002\u0000isomiso2 error: 401"
        assertTrue(QuickStreamChecker.looksAlive(mp4Sample))
    }

    @Test
    fun dashManifestsAreAliveEvenThoughTheyStartAsXml() {
        assertTrue(
            QuickStreamChecker.looksAlive(
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                    "<MPD xmlns=\"urn:mpeg:dash:schema:mpd:2011\" type=\"dynamic\">" +
                    "<Period id=\"1\"><AdaptationSet mimeType=\"video/mp4\"/></Period></MPD>",
            ),
        )
        assertTrue(QuickStreamChecker.looksAlive("<MPD xmlns=\"urn:mpeg:dash:schema:mpd:2011\"/>"))
    }

    @Test
    fun liveManifestMentionsOfErrorsOrExpiryStayAlive() {
        assertTrue(
            QuickStreamChecker.looksAlive(
                "#EXTM3U\n#EXT-X-VERSION:3\n#EXTINF:6,404 not found channel\n" +
                    "#EXT-X-KEY:METHOD=NONE\nsegment-error-401.ts?expires=1",
            ),
        )
    }

    @Test
    fun textualErrorPagesAreStillDead() {
        assertFalse(QuickStreamChecker.looksAlive("Error: account suspended"))
        assertFalse(QuickStreamChecker.looksAlive("404 Not Found"))
        assertFalse(QuickStreamChecker.looksAlive("Access denied"))
    }

    private fun channel(name: String, group: String = "General"): LiveChannel =
        LiveChannel(
            key = "test:$name:$group",
            sourceId = "source",
            sourceName = "Source",
            name = name,
            tvgId = null,
            tvgName = null,
            logo = null,
            groupId = "group",
            group = group,
            number = 1,
            url = "https://example.com/stream.m3u8",
        )
}
