package com.nuvio.app.core.text

/**
 * Small typo-tolerant matcher shared by Live TV and On Demand search.
 *
 * Exact and prefix/contains matches always win. For longer words, one missing, extra, wrong or
 * swapped letter is accepted, so "sprts" still finds "Sports" and "discovry" finds "Discovery".
 */
internal object FuzzyTextSearch {
    fun matches(text: String, query: String): Boolean {
        val q = normalize(query)
        if (q.isEmpty()) return false
        val t = normalize(text)
        if (t.contains(q)) return true

        val queryWords = q.split(' ').filter { it.isNotEmpty() }
        if (queryWords.isEmpty()) return false
        val textWords = t.split(' ').filter { it.isNotEmpty() }
        if (textWords.isEmpty()) return false

        return queryWords.all { queryWord ->
            when {
                queryWord.length < 3 -> textWords.any { it.startsWith(queryWord) }
                else -> textWords.any { textWord ->
                    textWord.startsWith(queryWord) ||
                        (textWord.length >= 3 && isOneEditApart(queryWord, textWord))
                }
            }
        }
    }

    fun normalize(value: String): String = buildString(value.length) {
        var previousSpace = true
        value.lowercase().forEach { character ->
            if (character.isLetterOrDigit()) {
                append(character)
                previousSpace = false
            } else if (!previousSpace) {
                append(' ')
                previousSpace = true
            }
        }
    }.trim()

    /** True when one insertion, deletion, substitution or adjacent swap turns [left] into [right]. */
    private fun isOneEditApart(left: String, right: String): Boolean {
        if (left == right) return true
        if (kotlin.math.abs(left.length - right.length) > 1) return false

        if (left.length == right.length) {
            var firstDifference = -1
            for (index in left.indices) {
                if (left[index] == right[index]) continue
                if (firstDifference >= 0) {
                    // A second difference is only allowed as the swapped pair.
                    return firstDifference == index - 1 &&
                        left[firstDifference] == right[index] &&
                        left[index] == right[firstDifference] &&
                        left.substring(index + 1) == right.substring(index + 1)
                }
                firstDifference = index
            }
            return firstDifference >= 0
        }

        val shorter = if (left.length < right.length) left else right
        val longer = if (left.length < right.length) right else left
        var shortIndex = 0
        var longIndex = 0
        var skipped = false
        while (shortIndex < shorter.length && longIndex < longer.length) {
            if (shorter[shortIndex] == longer[longIndex]) {
                shortIndex++
                longIndex++
                continue
            }
            if (skipped) return false
            skipped = true
            longIndex++
        }
        return true
    }
}
