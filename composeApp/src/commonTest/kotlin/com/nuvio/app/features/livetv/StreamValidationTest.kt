package com.nuvio.app.features.livetv

import com.nuvio.app.features.livetv.data.StreamUrls
import com.nuvio.app.features.livetv.data.deadChannelKeys
import com.nuvio.app.features.livetv.data.retainedFailedKeys
import com.nuvio.app.features.livetv.model.LiveChannel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Stream validation: which links count as usable, and how the hidden set changes between runs. */
class StreamValidationTest {

    private fun channel(key: String, url: String) = LiveChannel(
        key = key,
        sourceId = "pl",
        sourceName = "Playlist",
        name = key,
        tvgId = null,
        tvgName = null,
        logo = null,
        groupId = "g",
        group = "Group",
        number = 0,
        url = url,
    )

    @Test
    fun usableStreamAddressesAreAccepted() {
        listOf(
            "http://example.com/live.m3u8",
            "https://example.com:8443/live/user/pass/1.ts",
            "https://user:pass@example.com/live.m3u8?token=a%20b",
            "rtsp://camera.local/stream",
            "rtmp://example.com/live/key",
            "udp://@239.0.0.1:1234",
            "file:///sdcard/movie.mkv",
            "HTTP://EXAMPLE.COM/LIVE",
        ).forEach { url ->
            assertTrue(StreamUrls.isUsable(url), "expected usable: $url")
        }
    }

    @Test
    fun malformedStreamAddressesAreRejected() {
        listOf(
            "",
            "   ",
            "not a url",
            "www.example.com/live.m3u8",
            "http://",
            "http:///live.m3u8",
            "http://:8080/live.m3u8",
            "http://exa mple.com/live.m3u8",
            "http://example.com/live\n.m3u8",
            "\"http://example.com/live.m3u8\"",
        ).forEach { url ->
            assertFalse(StreamUrls.isUsable(url), "expected unusable: $url")
        }
    }

    @Test
    fun fullRunHidesEveryChannelSharingADeadUrl() {
        val channels = listOf(
            channel("a", "http://dead.example/1.m3u8"),
            channel("b", "http://dead.example/1.m3u8"),
            channel("c", "http://ok.example/2.m3u8"),
        )
        val outcomes = mapOf(
            "http://dead.example/1.m3u8" to false,
            "http://ok.example/2.m3u8" to true,
        )
        assertEquals(setOf("a", "b"), deadChannelKeys(channels, outcomes))
    }

    @Test
    fun recheckKeepsStillDeadChannelsAndRestoresWorkingOnes() {
        val previous = setOf("a", "b", "fixed", "gone")
        val checked = listOf(
            channel("a", "http://dead.example/1.m3u8"),
            channel("b", "http://dead.example/2.m3u8"),
            channel("fixed", "http://ok.example/3.m3u8"),
        )
        val outcomes = mapOf(
            "http://dead.example/1.m3u8" to false,
            "http://dead.example/2.m3u8" to false,
            "http://ok.example/3.m3u8" to true,
        )
        val existing = setOf("a", "b", "fixed", "other")
        assertEquals(
            setOf("a", "b"),
            retainedFailedKeys(previous, checked, outcomes, existing),
        )
    }

    @Test
    fun recheckDropsHiddenChannelsThatNoLongerExist() {
        val previous = setOf("missing", "still-there")
        val existing = setOf("still-there")
        assertEquals(
            setOf("still-there"),
            retainedFailedKeys(previous, emptyList(), emptyMap(), existing),
        )
    }
}
