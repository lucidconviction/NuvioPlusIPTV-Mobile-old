package com.nuvio.app.features.web

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WebAppsTest {

    @Test
    fun urlsAreNormalisedAndValidated() {
        assertEquals("https://example.com", normalizeWebUrl("example.com"))
        assertEquals("https://example.com/watch?v=1", normalizeWebUrl(" https://example.com/watch?v=1 "))
        assertEquals("http://localhost:8080", normalizeWebUrl("http://localhost:8080"))
        assertEquals(null, normalizeWebUrl("not a site"))
        assertEquals(null, normalizeWebUrl(""))
        assertEquals("www.example.com", webHost("https://www.example.com/a/b"))
    }

    @Test
    fun userscriptHeadersAreParsedAndMatched() {
        val code = """
            // ==UserScript==
            // @name PopupKiller
            // @match https://example.com/*
            // @run-at document-start
            // ==/UserScript==
            window.open = function() { return null; };
        """.trimIndent()
        val parsed = WebUserscriptManager.parse(code)
        assertEquals("PopupKiller", parsed.name)
        assertTrue(parsed.runAtStart)
        assertTrue(parsed.matches.isNotEmpty())

        val script = WebUserscript("us_1", "Popup Killer", code = code)
        val injected = WebUserscriptManager.scriptsFor("https://example.com/page", listOf(script), atStart = true)
        assertEquals(1, injected.size)
        assertTrue(injected.single().contains("NuvioWebBridge"))
        assertTrue(WebUserscriptManager.scriptsFor("https://other.com", listOf(script), atStart = true).isEmpty())
    }

    @Test
    fun disabledScriptsAreNotInjected() {
        val script = WebUserscript("us_2", "Off", enabled = false, code = "window.x=1")
        assertTrue(WebUserscriptManager.scriptsFor("https://example.com", listOf(script), atStart = false).isEmpty())
    }
}
