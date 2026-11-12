package com.nuvio.app.core.text

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FuzzyTextSearchTest {
    @Test
    fun exactAndPartialMatchesStillWork() {
        assertTrue(FuzzyTextSearch.matches("World News 24", "world"))
        assertTrue(FuzzyTextSearch.matches("World News 24", "news 24"))
    }

    @Test
    fun oneTypoIsAcceptedInLongerWords() {
        assertTrue(FuzzyTextSearch.matches("Arena Sports", "sprts"))
        assertTrue(FuzzyTextSearch.matches("Discovery Time", "discovry"))
        assertTrue(FuzzyTextSearch.matches("Cinema One", "cinima"))
    }

    @Test
    fun adjacentLettersCanBeSwapped() {
        assertTrue(FuzzyTextSearch.matches("Arena Sports", "sprots"))
    }

    @Test
    fun shortQueriesDoNotBecomeTooLoose() {
        assertFalse(FuzzyTextSearch.matches("News", "nwx"))
        assertTrue(FuzzyTextSearch.matches("News", "ne"))
    }

    @Test
    fun unrelatedTitlesDoNotMatch() {
        assertFalse(FuzzyTextSearch.matches("Kids Planet", "sports"))
        assertFalse(FuzzyTextSearch.matches("Weather Now", "cinema"))
    }
}
