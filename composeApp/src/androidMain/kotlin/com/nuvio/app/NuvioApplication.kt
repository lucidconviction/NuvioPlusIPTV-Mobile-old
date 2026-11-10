package com.nuvio.app

import android.app.Application
import android.os.Build
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.network.cachecontrol.CacheControlCacheStrategy
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.CachePolicy
import coil3.request.crossfade
import coil3.svg.SvgDecoder
import com.nuvio.app.core.poster.CustomPosterFallbackInterceptor

/**
 * Custom Application class that implements [SingletonImageLoader.Factory] to guarantee
 * the Coil ImageLoader (with [CustomPosterFallbackInterceptor]) is created before any
 * image request. Without this, Coil's auto-init ContentProvider creates the singleton
 * before MainActivity.onCreate, and interceptors registered later are ignored.
 */
class NuvioApplication : Application(), SingletonImageLoader.Factory {

    override fun onCreate() {
        super.onCreate()
        // 1Tube: apply the chosen YouTube content region before the first feed request.
        com.nuvio.app.youtube.YouTubeContentRegion.apply(this)
        // 1Tube: the YouTube page lives in Android-only code and is plugged in here.
        com.nuvio.app.features.youtube.YouTubeHost.playerPanel = { videoId, onDismiss ->
            com.nuvio.app.youtube.YouTubePlayerPanel(videoId, onDismiss)
        }
        com.nuvio.app.features.youtube.YouTubeHost.sponsorSegments = { videoId ->
            com.nuvio.app.youtube.YouTubeSponsorBlock.segments(this, videoId)
        }
        com.nuvio.app.features.youtube.YouTubeHost.sponsorAutoSkip = {
            !com.nuvio.app.youtube.YouTubeSettings.sponsorAsk(this)
        }
        com.nuvio.app.features.youtube.YouTubeHost.playerOverlay = { videoId, position, duration, ended, playNext ->
            com.nuvio.app.youtube.YouTubePlayerOverlay(videoId, position, duration, ended, playNext)
        }
        com.nuvio.app.features.youtube.YouTubeHost.moreVideos = { videoId, buttonVisible, bottomInset, onPlay ->
            com.nuvio.app.youtube.YouTubeMoreVideos(videoId, buttonVisible, bottomInset, onPlay)
        }
        com.nuvio.app.features.youtube.YouTubeHost.settingsContent = { com.nuvio.app.youtube.YouTubeSettingsContent() }
        com.nuvio.app.features.youtube.YouTubeHost.recordProgress = { contentId, position, duration ->
            com.nuvio.app.youtube.YouTubeProgress.record(this, contentId, position, duration)
        }
        com.nuvio.app.features.youtube.YouTubeHost.playerControls = { videoId, openPanel ->
            com.nuvio.app.youtube.rememberYouTubePlayerAction(videoId, openPanel)
        }
        com.nuvio.app.features.youtube.YouTubeHost.screen = { modifier, onPlay ->
            com.nuvio.app.youtube.YouTubeScreen(modifier, onPlay)
        }
        // Sports replays and pre/post-event videos use the same YouTube engine as the tabs.
        com.nuvio.app.youtube.YouTubeSports.init(this)
        com.nuvio.app.features.youtube.YouTubeHost.searchVideos = { query, limit ->
            com.nuvio.app.youtube.YouTubeSports.search(query, limit)
        }
        com.nuvio.app.features.youtube.YouTubeHost.playVideo = { video, queue ->
            com.nuvio.app.youtube.YouTubeSports.play(video, queue)
        }
        com.nuvio.app.features.youtube.YouTubeHost.resolveVideo = { videoId, title, poster, startPositionMs ->
            com.nuvio.app.youtube.YouTubeSports.resolve(videoId, title, poster, startPositionMs)
        }
    }

    override fun newImageLoader(context: android.content.Context): ImageLoader {
        return ImageLoader.Builder(context)
            .crossfade(true)
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .components {
                add(CustomPosterFallbackInterceptor())
                add(SvgDecoder.Factory())
                add(
                    KtorNetworkFetcherFactory(
                        cacheStrategy = { CacheControlCacheStrategy() },
                    ),
                )
                if (Build.VERSION.SDK_INT >= 28) {
                    add(AnimatedImageDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .build()
    }
}
