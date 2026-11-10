package com.nuvio.app.youtube

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The player's Info, More Videos and Comments buttons and the panels they open. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w852dp-h1000dp")
class YouTubeMoreVideosTest {

    @get:Rule
    val compose = createComposeRule()

    private val info = YtVideoInfo(
        videoId = "abc123",
        title = "A test video",
        author = "Test Channel",
        channelId = "channel-1",
        authorImage = null,
        subscribers = "1.2M subscribers",
        description = "What this video is about.",
        viewCount = "10K views",
        published = "2 days ago",
        likeCount = "500",
        liked = false,
        disliked = false,
        subscribed = false,
        commentsKey = "comments-key",
        newestCommentsKey = "newest-key",
        liveChatKey = null,
        chapters = listOf("Opening" to 0L),
    )

    private val comment = YtComment(
        author = "Commenter",
        text = "A useful comment",
        likes = "12",
        published = "1 day ago",
        replyKey = "replies-key",
        replyCount = "3",
    )

    private fun content(
        state: YtPlayerState = YtPlayerState(),
        buttonVisible: Boolean = true,
        queue: List<YtItem> = emptyList(),
        onLoadReplies: (String) -> Unit = {},
    ) {
        compose.setContent {
            YouTubePlayerSidePanelsContent(
                videoId = "abc123",
                queue = queue,
                state = state,
                buttonVisible = buttonVisible,
                onPlay = {},
                onActivate = { _, _, _, _ -> },
                onLoadReplies = onLoadReplies,
            )
        }
    }

    @Test
    fun moreVideosButtonOpensTheListPanel() {
        content()

        compose.onNodeWithTag("yt-more-videos-button").assertIsDisplayed().performClick()

        compose.onNodeWithText("No videos in this list.").assertIsDisplayed()
    }

    @Test
    fun buttonsAreHiddenWhenThePlayerControlsAreHidden() {
        content(buttonVisible = false)

        compose.onNodeWithTag("yt-info-button").assertDoesNotExist()
        compose.onNodeWithTag("yt-more-videos-button").assertDoesNotExist()
        compose.onNodeWithTag("yt-comments-button").assertDoesNotExist()
    }

    @Test
    fun infoButtonOpensTheVideoDetails() {
        content(state = YtPlayerState(info = info))

        compose.onNodeWithTag("yt-info-button").assertIsDisplayed().performClick()

        compose.onNodeWithText("Info").assertIsDisplayed()
        compose.onNodeWithText("A test video").assertIsDisplayed()
        compose.onNodeWithText("Test Channel").assertIsDisplayed()
        compose.onNodeWithText("What this video is about.").assertIsDisplayed()
    }

    @Test
    fun tappingACommentOpensItsReplies() {
        compose.setContent {
            var state by remember {
                mutableStateOf(YtPlayerState(info = info, comments = listOf(comment)))
            }
            YouTubePlayerSidePanelsContent(
                videoId = "abc123",
                queue = emptyList(),
                state = state,
                buttonVisible = true,
                onPlay = {},
                onActivate = { _, _, _, _ -> },
                onLoadReplies = {
                    state = state.copy(
                        replies = listOf(
                            YtComment(
                                author = "Reply author",
                                text = "A reply to the comment",
                                likes = null,
                                published = null,
                                replyKey = null,
                                replyCount = null,
                            ),
                        ),
                    )
                },
            )
        }

        compose.onNodeWithTag("yt-comments-button").assertIsDisplayed().performClick()
        compose.onNodeWithText("A useful comment").assertIsDisplayed().performClick()

        compose.onNodeWithText("Replies").assertIsDisplayed()
        compose.onNodeWithText("Replying to Commenter").assertIsDisplayed()
        compose.onNodeWithText("A reply to the comment").assertIsDisplayed()
    }
}
