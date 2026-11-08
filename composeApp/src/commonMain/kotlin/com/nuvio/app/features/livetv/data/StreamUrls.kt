package com.nuvio.app.features.livetv.data

/**
 * Checks the stream links that come out of a playlist.
 *
 * Providers regularly ship a few broken entries (a bare `http://`, a missing host, a stray
 * quote). Downloading a playlist must not fail because of them, and a channel with such a link
 * can never play, so both the importer and the stream validator skip those entries instead of
 * failing the whole update.
 */
internal object StreamUrls {

    /** `scheme://something`, with no spaces or line breaks anywhere in the link. */
    private val absoluteUrl = Regex("""^[A-Za-z][A-Za-z0-9+.\-]*://\S+$""")

    fun isUsable(raw: String): Boolean {
        val url = raw.trim()
        if (url.isEmpty() || !absoluteUrl.matches(url)) return false
        val scheme = url.substringBefore("://").lowercase()
        val rest = url.substringAfter("://")
        if (rest.isBlank()) return false
        // HTTP(S) links need a real host. Other schemes accepted by the player (rtsp, rtmp,
        // udp, file, ...) are used as they are; only the obvious "no address at all" cases fail.
        if (scheme == "http" || scheme == "https") {
            val authority = rest.substringBefore('/').substringBefore('?').substringBefore('#')
            val host = authority.substringAfterLast('@').substringBefore(':')
            if (host.isBlank()) return false
        }
        return true
    }
}
