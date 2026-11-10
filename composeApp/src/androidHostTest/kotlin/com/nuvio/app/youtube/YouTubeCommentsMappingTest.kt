package com.nuvio.app.youtube

import com.liskovsoft.mediaserviceinterfaces.data.CommentItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Comments coming back from the YouTube service, including the reply pages. */
class YouTubeCommentsMappingTest {

    private class FakeComment(
        private val id: String? = null,
        private val message: String? = null,
        private val author: String? = null,
        private val nestedKey: String? = null,
        private val replyCount: String? = null,
        private val empty: Boolean = false,
    ) : CommentItem {
        override fun getId(): String? = id
        override fun getMessage(): String? = message
        override fun getAuthorName(): String? = author
        override fun getAuthorPhoto(): String? = null
        override fun getPublishedDate(): String? = null
        override fun getNestedCommentsKey(): String? = nestedKey
        override fun isLiked(): Boolean = false
        override fun getLikeCount(): String? = null
        override fun getReplyCount(): String? = replyCount
        override fun isEmpty(): Boolean = empty
    }

    @Test
    fun repliesAreKeptEvenThoughTheyHaveNothingFurtherToOpen() {
        // A reply has no nested key and no reply count, so CommentItem.isEmpty() is true for it.
        val reply = FakeComment(id = "r1", message = "A reply", author = "Someone", empty = true)

        val rows = listOf<CommentItem?>(reply).toYtComments()

        assertEquals(1, rows.size)
        assertEquals("A reply", rows.single().text)
        assertEquals("Someone", rows.single().author)
        assertNull(rows.single().replyKey)
    }

    @Test
    fun commentsWithoutRepliesAreKept() {
        val comment = FakeComment(id = "c1", message = "Just a comment", author = "Commenter", empty = true)

        val rows = listOf<CommentItem?>(comment).toYtComments()

        assertEquals(1, rows.size)
        assertEquals("Just a comment", rows.single().text)
    }

    @Test
    fun placeholdersWithoutAuthorOrTextAreDropped() {
        val placeholder = FakeComment(id = "loading", empty = true)

        val rows = listOf<CommentItem?>(placeholder, null).toYtComments()

        assertEquals(0, rows.size)
    }

    @Test
    fun commentsWithRepliesKeepTheirReplyKey() {
        val comment = FakeComment(
            id = "c1",
            message = "Has replies",
            author = "Commenter",
            nestedKey = "replies-token",
            replyCount = "3",
        )

        val rows = listOf<CommentItem?>(comment).toYtComments()

        assertEquals("replies-token", rows.single().replyKey)
        assertEquals("3", rows.single().replyCount)
    }
}
