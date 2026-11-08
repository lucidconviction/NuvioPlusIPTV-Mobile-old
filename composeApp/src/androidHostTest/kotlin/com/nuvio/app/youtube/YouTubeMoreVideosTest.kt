package com.nuvio.app.youtube

import android.app.Application
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

/** The player's "More Videos" button and the panel it opens. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w852dp-h1000dp")
class YouTubeMoreVideosTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun buttonOpensTheListPanel() {
        compose.setContent {
            YouTubeMoreVideosContent(
                videoId = "abc123",
                queue = emptyList(),
                buttonVisible = true,
                onPlay = {},
                onActivate = { _, _, _, _ -> },
            )
        }

        compose.onNodeWithTag("yt-more-videos-button").assertIsDisplayed().performClick()

        compose.onNodeWithText("No videos in this list.").assertIsDisplayed()
    }

    @Test
    fun buttonIsHiddenWhenThePlayerControlsAreHidden() {
        compose.setContent {
            YouTubeMoreVideosContent(
                videoId = "abc123",
                queue = emptyList(),
                buttonVisible = false,
                onPlay = {},
                onActivate = { _, _, _, _ -> },
            )
        }

        compose.onNodeWithTag("yt-more-videos-button").assertDoesNotExist()
    }
}
