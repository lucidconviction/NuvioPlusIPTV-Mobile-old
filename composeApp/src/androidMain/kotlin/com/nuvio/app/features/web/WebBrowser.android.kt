package com.nuvio.app.features.web

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.SystemClock
import android.view.MotionEvent
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Collections
import java.util.zip.GZIPInputStream

/**
 * The Android Web-Apps browser. The WebView setup, media probe, HLS polyfill and popup rules are
 * ported from Lucid Hikari's WebViewActivity (GPL-3.0), adapted to Nuvio's controller and player.
 */
@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
internal class AndroidWebBrowserController(
    private val appContext: Context,
) : WebBrowserController {

    private val _state = MutableStateFlow(WebBrowserUiState())
    override val state: StateFlow<WebBrowserUiState> = _state.asStateFlow()

    private val mainHandler = Handler(Looper.getMainLooper())
    private var webView: WebView? = null
    private var popupChild: WebView? = null
    private var pendingUrl: String? = null
    private var settings = WebAppsSettings()
    private var scripts: List<WebUserscript> = emptyList()
    private var hlsInjectedFor: String? = null
    private var lastLoadedUrl: String = ""
    private var pendingThumbnailSiteId: String? = null
    private var thumbnailCaptureRunnable: Runnable? = null
    private var thumbnailListener: ((siteId: String, path: String) -> Unit)? = null
    private var mediaScanRunnable: Runnable? = null
    private var rendererRestarts = 0
    private var pendingMainFrameUrl: String? = null
    private var allowNextMainFrame = false
    private var lastChoiceAt = 0L
    private var lastChoiceUrl: String? = null
    @Volatile private var videoPlaying = false
    @Volatile private var lastUserInteractionMs: Long = 0L
    @Volatile private var currentUserAgent: String = ""
    @Volatile private var pageUrl: String = ""
    @Volatile private var pageTitle: String = ""
    private val detected = Collections.synchronizedMap(LinkedHashMap<String, WebMediaSource>())
    private val probing = Collections.synchronizedSet(HashSet<String>())

    private val desktopUa =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    /** Login/payment/Cloudflare hosts that are always allowed through redirect protection. */
    private val builtInAllowedHosts = setOf(
        "accounts.google.com",
        "myaccount.google.com",
        "paypal.com",
        "stripe.com",
        "challenges.cloudflare.com",
        "cloudflare.com",
    )

    private val hlsLib: String by lazy {
        runCatching {
            appContext.assets.open("web/hls.min.js").bufferedReader().use { it.readText() }
        }.getOrDefault("")
    }

    fun attach(view: WebView) {
        if (webView === view) return
        webView = view
        Thread { WebAdBlocker.load(appContext) }.start()
        configure(view)
        val url = pendingUrl ?: lastLoadedUrl.takeIf { it.isNotBlank() }
        pendingUrl = null
        if (!url.isNullOrBlank()) view.loadUrl(url)
    }

    override fun load(url: String) {
        val normalized = normalizeWebUrl(url) ?: url.trim()
        lastLoadedUrl = normalized
        pendingMainFrameUrl = normalized
        val view = webView
        if (view == null) {
            pendingUrl = normalized
        } else {
            view.loadUrl(normalized)
        }
    }

    override fun goBack() {
        webView?.let {
            if (it.canGoBack()) {
                allowNextMainFrame = true
                it.goBack()
            } else {
                updateNavigation()
            }
        }
    }

    override fun goForward() {
        webView?.let {
            if (it.canGoForward()) {
                allowNextMainFrame = true
                it.goForward()
            } else {
                updateNavigation()
            }
        }
    }

    override fun reload() {
        val view = webView
        if (view == null) {
            // The renderer was lost (or the host has not attached yet). Let the host create a
            // fresh WebView and load the last page.
            rendererRestarts = 0
            pendingUrl = lastLoadedUrl.takeIf { it.isNotBlank() }
            _state.value = _state.value.copy(
                error = null,
                rendererGeneration = _state.value.rendererGeneration + 1,
            )
            return
        }
        allowNextMainFrame = true
        view.reload()
    }

    override fun stopLoading() {
        webView?.stopLoading()
    }

    override fun pause() {
        webView?.onPause()
        webView?.evaluateJavascript(
            "(function(){try{document.querySelectorAll('video,audio').forEach(function(m){try{m.pause()}catch(e){}})}catch(e){}})()",
            null,
        )
    }

    override fun resume() {
        webView?.onResume()
    }

    override fun destroy() {
        mediaScanRunnable?.let { mainHandler.removeCallbacks(it) }
        mediaScanRunnable = null
        mainHandler.removeCallbacksAndMessages(null)
        runCatching { CookieManager.getInstance().flush() }
        runCatching { popupChild?.destroy() }
        popupChild = null
        webView?.let { view ->
            runCatching {
                view.loadUrl("about:blank")
                view.stopLoading()
                view.destroy()
            }
        }
        webView = null
    }

    override fun updateAdblockLists() {
        Thread { runCatching { WebAdBlocker.update(appContext) } }.start()
    }

    override fun scanForMedia() {
        val view = webView ?: return
        view.evaluateJavascript(VIDEO_SCAN_JS, null)
        view.evaluateJavascript(PERFORMANCE_SCAN_JS, null)
    }

    override fun playInBrowser(source: WebMediaSource) {
        val view = webView ?: return
        // Do NOT call onPause() here: a paused WebView can ignore the data-page load and keep
        // showing the previous frame. Just pause the page's media and keep the view running.
        view.onResume()
        view.evaluateJavascript(
            "(function(){try{document.querySelectorAll('video,audio').forEach(function(m){try{m.pause()}catch(e){}})}catch(e){}})()",
            null,
        )
        val headers = source.headers.filterKeys { key ->
            key.startsWith("X-", ignoreCase = true) ||
                key.equals("Authorization", ignoreCase = true)
        }
        val headersJson = org.json.JSONObject().apply {
            headers.forEach { (key, value) -> put(key, value) }
        }.toString()
        val urlJs = org.json.JSONObject.quote(source.url)
        val html = if (source.isHls || source.streamType == "hls") {
            hlsPlayerHtml(urlJs, headersJson)
        } else {
            directPlayerHtml(urlJs)
        }
        val base = source.pageUrl.ifBlank { source.url }
        view.stopLoading()
        view.loadDataWithBaseURL(base, html, "text/html", "utf-8", base)
    }

    private fun playerShell(script: String, headScript: String = ""): String {
        fun safe(code: String) = code.replace("</script", "<\\/script")
        val library = headScript.takeIf { it.isNotBlank() }
            ?.let { "<script>${safe(it)}</script>" }
            .orEmpty()
        return """
        <!doctype html>
        <html>
        <head>
          <meta charset="utf-8">
          <meta name="viewport" content="width=device-width,initial-scale=1">
          <style>
            html,body{margin:0;height:100%;background:#000;color:#ddd;font:14px system-ui,sans-serif;overflow:hidden}
            video{position:fixed;inset:0;width:100%;height:100%;object-fit:contain;background:#000;z-index:1}
            #back{position:fixed;top:10px;left:10px;z-index:3;background:rgba(0,0,0,.62);
              border:1px solid rgba(255,255,255,.3);color:#fff;border-radius:999px;
              padding:7px 14px;text-decoration:none;font:14px system-ui,sans-serif}
            #err{position:fixed;inset:0;display:none;align-items:center;justify-content:center;
              text-align:center;padding:24px;color:#bbb}
          </style>
          $library
        </head>
        <body>
          <a id="back" href="javascript:history.back()">‹ Back</a>
          <video id="v" controls autoplay playsinline></video>
          <div id="err">This video could not play in the browser. Try “Play in Nuvio”.</div>
          <script>${safe(script)}</script>
        </body>
        </html>
        """.trimIndent()
    }

    private fun directPlayerHtml(urlJs: String): String = playerShell(
        """
        (function(){
          var v=document.getElementById('v');
          var failed=false;
          function fail(){if(failed)return;failed=true;document.getElementById('err').style.display='flex';}
          v.addEventListener('error',fail);
          v.src=$urlJs;
          v.play().catch(function(){});
          setTimeout(function(){if(v.readyState===0)fail();},12000);
        })();
        """.trimIndent()
    )

    private fun hlsPlayerHtml(urlJs: String, headersJson: String): String = playerShell(
        """
        (function(){
          var url=$urlJs;
          var headers=$headersJson;
          var v=document.getElementById('v');
          v.setAttribute('data-nuvio-browser','1');
          var started=false;
          var failed=false;
          var began=Date.now();
          function fail(){if(failed)return;failed=true;document.getElementById('err').style.display='flex';}
          v.addEventListener('error',function(){if(started)fail();});
          function attachHls(){
            if(!(window.Hls && window.Hls.isSupported()))return false;
            var hls=new window.Hls({enableWorker:true,lowLatencyMode:true,
              xhrSetup:function(xhr){
                for(var k in headers){
                  try{xhr.setRequestHeader(k,headers[k]);}catch(e){}
                }
              }});
            hls.loadSource(url);
            hls.attachMedia(v);
            hls.on(window.Hls.Events.MANIFEST_PARSED,function(){v.play().catch(function(){});});
            hls.on(window.Hls.Events.ERROR,function(e,d){if(d&&d.fatal)fail();});
            v.__nuvioBrowserHls=hls;
            v.__nuvioHlsAttached=hls;
            started=true;
            return true;
          }
          function start(){
            if(attachHls())return;
            if(Date.now()-began>10000 && v.canPlayType('application/vnd.apple.mpegurl')){
              v.src=url;
              v.play().catch(function(){});
              started=true;
              return;
            }
            setTimeout(start,250);
          }
          start();
          setTimeout(function(){if(!started)fail();},15000);
        })();
        """.trimIndent(),
        // loadDataWithBaseURL creates a fresh document; the hls.js injected into the page we
        // came from is gone. Put the bundled library in the player document itself.
        headScript = hlsLib,
    )

    override fun beginThumbnailCapture(siteId: String) {
        thumbnailCaptureRunnable?.let { mainHandler.removeCallbacks(it) }
        thumbnailCaptureRunnable = null
        pendingThumbnailSiteId = siteId
    }

    override fun setThumbnailCaptureListener(listener: ((siteId: String, path: String) -> Unit)?) {
        thumbnailListener = listener
    }

    override fun applySettings(settings: WebAppsSettings, scripts: List<WebUserscript>) {
        val desktopChanged = this.settings.desktopMode != settings.desktopMode
        this.settings = settings
        this.scripts = scripts
        webView?.let { view ->
            view.settings.userAgentString = if (settings.desktopMode) desktopUa else null
            currentUserAgent = view.settings.userAgentString ?: desktopUa
            if (desktopChanged && lastLoadedUrl.isNotBlank()) view.reload()
        }
    }

    private fun configure(view: WebView) {
        val settings = view.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.loadsImagesAutomatically = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.javaScriptCanOpenWindowsAutomatically = true
        settings.setSupportMultipleWindows(true)
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.setSupportZoom(true)
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.offscreenPreRaster = true
        settings.userAgentString = if (this.settings.desktopMode) desktopUa else null
        currentUserAgent = settings.userAgentString ?: desktopUa
        view.setBackgroundColor(0xFF070909.toInt())
        view.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                lastUserInteractionMs = SystemClock.uptimeMillis()
            }
            false
        }
        if (appContext.applicationInfo.flags and
            android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
        ) {
            WebView.setWebContentsDebuggingEnabled(true)
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(view, true)
        }
        view.addJavascriptInterface(Bridge(), "NuvioWebBridge")
        view.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false
                if (handleUrl(view, url)) return true
                if (request?.isForMainFrame == true && shouldBlockMainFrame(url, request.hasGesture())) {
                    return true
                }
                return false
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                val newUrl = url.orEmpty()
                pendingMainFrameUrl?.let { pending ->
                    if (sameUrl(pending, newUrl)) pendingMainFrameUrl = null
                }
                videoPlaying = false
                mediaScanRunnable?.let { mainHandler.removeCallbacks(it) }
                mediaScanRunnable = null
                val samePage = newUrl.isNotBlank() &&
                    pageUrl.isNotBlank() &&
                    newUrl.substringBefore('#') == pageUrl.substringBefore('#')
                pageUrl = newUrl
                if (newUrl.isNotBlank()) lastLoadedUrl = newUrl
                hlsInjectedFor = null
                if (!samePage) detected.clear()
                _state.value = _state.value.copy(
                    url = pageUrl,
                    loading = true,
                    progress = 0f,
                    detectedMedia = if (samePage) detected.values.toList() else emptyList(),
                    error = null,
                )
                injectUserscripts(atStart = true)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                pageUrl = url.orEmpty()
                _state.value = _state.value.copy(
                    url = pageUrl,
                    loading = false,
                    progress = 1f,
                    error = null,
                )
                updateNavigation()
                injectHlsAndScan()
                injectUserscripts(atStart = false)
                injectAdClean()
                view?.evaluateJavascript(VIDEO_SCAN_JS, null)
                view?.evaluateJavascript(PERFORMANCE_SCAN_JS, null)
                view?.evaluateJavascript(STUCK_MONITOR_JS, null)
                view?.evaluateJavascript(PLAYING_MONITOR_JS, null)
                // Single-page players create their <video> long after onPageFinished. Keep
                // scanning the DOM and performance entries, like Hikari does.
                mediaScanRunnable?.let { mainHandler.removeCallbacks(it) }
                val scan = object : Runnable {
                    override fun run() {
                        val v = webView ?: return
                        v.evaluateJavascript(VIDEO_SCAN_JS, null)
                        v.evaluateJavascript(PERFORMANCE_SCAN_JS, null)
                        v.evaluateJavascript(STUCK_MONITOR_JS, null)
                        v.evaluateJavascript(PLAYING_MONITOR_JS, null)
                        mainHandler.postDelayed(this, 2_500L)
                    }
                }
                mediaScanRunnable = scan
                mainHandler.postDelayed(scan, 1_500L)
                pendingThumbnailSiteId?.let { siteId ->
                    // A redirect or challenge often finishes more than once; keep resetting the
                    // timer so the screenshot shows the final landing page, not the interstitial.
                    thumbnailCaptureRunnable?.let { mainHandler.removeCallbacks(it) }
                    val capture = Runnable {
                        thumbnailCaptureRunnable = null
                        pendingThumbnailSiteId = null
                        captureThumbnail(siteId)
                    }
                    thumbnailCaptureRunnable = capture
                    mainHandler.postDelayed(capture, 2_500L)
                }
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: android.webkit.WebResourceError?,
            ) {
                if (request?.isForMainFrame == true) {
                    _state.value = _state.value.copy(
                        loading = false,
                        error = error?.description?.toString()?.takeIf { it.isNotBlank() }
                            ?: "This page couldn't load.",
                    )
                }
            }

            override fun onReceivedHttpError(
                view: WebView?,
                request: WebResourceRequest?,
                errorResponse: WebResourceResponse?,
            ) {
                // Do NOT cover the page for HTTP errors: Cloudflare and many players return 403
                // on the first request and then render the challenge/player anyway. The page must
                // stay interactive.
            }

            override fun onRenderProcessGone(
                view: WebView?,
                detail: android.webkit.RenderProcessGoneDetail?,
            ): Boolean {
                // Heavy player pages can exhaust the WebView renderer. Return true so Android
                // does not kill the app, and let WebBrowserHost build a fresh WebView.
                mediaScanRunnable?.let { mainHandler.removeCallbacks(it) }
                mediaScanRunnable = null
                webView = null
                rendererRestarts++
                _state.value = _state.value.copy(
                    loading = false,
                    error = if (rendererRestarts <= MAX_RENDER_RESTARTS) {
                        "The page stopped responding. Reloading it."
                    } else {
                        "This page keeps stopping. Try another site or open it in an external browser."
                    },
                    rendererGeneration = if (rendererRestarts <= MAX_RENDER_RESTARTS) {
                        _state.value.rendererGeneration + 1
                    } else {
                        _state.value.rendererGeneration
                    },
                )
                return true
            }

            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                if (request == null) return null
                val url = request.url.toString()

                // Cloudflare's challenge bootstrap is never ad traffic.
                if (WebAdBlocker.isCloudflareInfra(url)) return null

                if (request.isForMainFrame) {
                    if (pendingMainFrameUrl?.let { sameUrl(it, url) } == true) {
                        pendingMainFrameUrl = null
                        return null
                    }
                    if (shouldBlockMainFrame(url, request.hasGesture())) {
                        return blockedResponse()
                    }
                    return null
                }

                if (isMediaRequest(request)) {
                    if (VIDEO_URL_RE.containsMatchIn(url)) {
                        maybeAddMedia(url, request.requestHeaders)
                    }
                    return null
                }

                val host = hostOf(url).orEmpty()
                val refererHost = request.requestHeaders?.entries
                    ?.firstOrNull { it.key.equals("Referer", ignoreCase = true) }
                    ?.value
                    ?.let(::hostOf)
                val pageHost = refererHost ?: currentPageHost()
                val sameSite = host.isNotBlank() && pageHost != null && isSameSite(host, pageHost)
                val type = WebAdBlocker.requestType(request)
                val isSubframeDocument = type == "other" &&
                    request.requestHeaders?.entries?.any { header ->
                        header.key.equals("Accept", ignoreCase = true) &&
                            header.value.contains("text/html", ignoreCase = true)
                    } == true

                if (this@AndroidWebBrowserController.settings.adBlockEnabled &&
                    !isAdblockWhitelisted(host)
                ) {
                    // Never strip the page's own layout CSS/fonts. Ads in CSS are rare; a
                    // missing stylesheet makes the whole site look broken.
                    if (type == "stylesheet" || type == "font" || type == "media") return null

                    // Downloaded hosts lists occasionally contain CDN hosts. Built-in rules
                    // still apply to same-site requests; the big external lists do not. Also
                    // keep scripts and XHR on the curated built-ins: a broad EasyList rule that
                    // happens to match the site's own app bundle makes a page look half-loaded.
                    val allowBroadRules = type != "script" && type != "xmlhttprequest"
                    val includeExtra = allowBroadRules && !sameSite && !isSubframeDocument
                    if (WebAdBlocker.isBlocked(url, includeExtra = includeExtra)) {
                        return blockedResponse()
                    }
                    // uBlock/EasyList rules: full strength on third-party images, frames and
                    // other resources. Same-site requests, player iframes, scripts and XHR only
                    // get the curated built-ins, so a broad filter-list rule cannot strip the
                    // site's own code or a player frame.
                    if (WebAdBlocker.matchesPattern(
                            request,
                            pageHost,
                            includeFilterRules = includeExtra,
                        )
                    ) {
                        return blockedResponse()
                    }
                }
                return null
            }
        }
        view.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                _state.value = _state.value.copy(
                    progress = newProgress / 100f,
                    loading = newProgress < 100,
                )
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                pageTitle = title.orEmpty()
                _state.value = _state.value.copy(title = pageTitle)
            }

            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message?,
            ): Boolean {
                // Some sites open the player from an async click handler, so the WebView does
                // not always credit a gesture. A tap in the last 2.5 s still counts as the user
                // asking for the window; popunders that fire on page load do not.
                val recentGesture = isUserGesture ||
                    (SystemClock.uptimeMillis() - lastUserInteractionMs <= 2_500L)
                if (this@AndroidWebBrowserController.settings.popupProtection && !recentGesture) {
                    return false
                }
                val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
                val child = WebView(appContext).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    settings.userAgentString = currentUserAgent
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    webViewClient = object : WebViewClient() {
                        private var relayed = false

                        private fun relay(rawUrl: String?) {
                            if (relayed) return
                            val url = rawUrl?.trim().orEmpty()
                            if (!url.startsWith("http://") && !url.startsWith("https://")) return
                            val host = hostOf(url) ?: return
                            val current = currentPageHost()
                            val sameSite = current != null && isSameSite(host, current)
                            relayed = true
                            if (this@AndroidWebBrowserController.settings.popupProtection && !sameSite) {
                                // A foreign popup while a video is playing is an ad overlay.
                                if (videoPlaying || isBlockedPopupHost(host)) {
                                    runCatching { destroy() }
                                    return
                                }
                                if (isAllowedHost(host)) {
                                    loadMainFrame(url)
                                    runCatching { destroy() }
                                    return
                                }
                                askPopupPermission(url, host) { allowed ->
                                    if (allowed) loadMainFrame(url)
                                    runCatching { destroy() }
                                }
                                return
                            }
                            loadMainFrame(url)
                            runCatching { destroy() }
                        }

                        override fun shouldOverrideUrlLoading(v: WebView?, request: WebResourceRequest?): Boolean {
                            relay(request?.url?.toString())
                            return true
                        }

                        override fun onPageStarted(v: WebView?, url: String?, favicon: Bitmap?) {
                            super.onPageStarted(v, url, favicon)
                            // Some players set window.location instead of navigating through
                            // shouldOverrideUrlLoading; catch the first real URL here too.
                            relay(url)
                        }
                    }
                }
                popupChild = child
                transport.webView = child
                resultMsg.sendToTarget()
                return true
            }

            override fun onCloseWindow(window: WebView?) {
                if (window === popupChild) {
                    runCatching { popupChild?.destroy() }
                    popupChild = null
                }
            }

            override fun onPermissionRequest(request: android.webkit.PermissionRequest?) {
                request?.deny()
            }
        }
    }

    private fun handleUrl(view: WebView?, url: String): Boolean {
        return when {
            url.startsWith("http://") || url.startsWith("https://") ||
                url.startsWith("data:") || url.startsWith("blob:") ||
                url == "about:blank" -> false
            url.startsWith("intent:") -> {
                runCatching {
                    val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
                    appContext.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                true
            }
            else -> {
                runCatching {
                    appContext.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
                true
            }
        }
    }

    private fun hostOf(url: String?): String? = runCatching {
        if (url.isNullOrBlank()) null else URL(url).host
    }.getOrNull()?.trim()?.trimEnd('.')?.lowercase()?.takeIf { it.isNotBlank() }

    private fun currentPageHost(): String? =
        hostOf(pageUrl) ?: hostOf(webView?.url)

    private fun sameUrl(a: String, b: String): Boolean =
        a.trimEnd('/') == b.trimEnd('/')

    private fun isSameSite(host: String, current: String): Boolean {
        val a = host.lowercase().trimEnd('.')
        val b = current.lowercase().trimEnd('.')
        return a == b || a.endsWith(".$b") || b.endsWith(".$a")
    }

    private fun isBlockedHost(host: String, hosts: Set<String>): Boolean {
        val h = host.lowercase().trimEnd('.')
        return hosts.any { listed ->
            val entry = listed.lowercase().trimEnd('.')
            h == entry || h.endsWith(".$entry")
        }
    }

    private fun isAllowedHost(host: String): Boolean =
        isBlockedHost(host, builtInAllowedHosts) || isBlockedHost(host, settings.allowedHosts)

    private fun isBlockedRedirectHost(host: String): Boolean =
        isBlockedHost(host, settings.blockedRedirectHosts)

    private fun isBlockedPopupHost(host: String): Boolean =
        isBlockedHost(host, settings.blockedPopupHosts)

    private fun isAdblockWhitelisted(host: String): Boolean =
        host.isNotBlank() && isBlockedHost(host, settings.adBlockWhitelist)

    private fun normalizeHost(raw: String): String? {
        val value = raw.trim()
            .removePrefix("http://")
            .removePrefix("https://")
            .substringBefore('/')
            .substringBefore('?')
            .substringBefore('#')
            .trim()
            .trimEnd('.')
            .lowercase()
        return value.takeIf {
            it.isNotBlank() && it.length <= 253 && !it.contains(' ') &&
                (it == "localhost" || it.contains('.'))
        }
    }

    private fun saveSettings(newSettings: WebAppsSettings) {
        settings = newSettings
        WebAppsRepository.updateSettings(newSettings)
    }

    private fun allowHost(host: String) {
        val h = normalizeHost(host) ?: return
        saveSettings(
            settings.copy(
                allowedHosts = settings.allowedHosts + h,
                blockedRedirectHosts = settings.blockedRedirectHosts - h,
                blockedPopupHosts = settings.blockedPopupHosts - h,
            )
        )
    }

    private fun blockRedirectHost(host: String) {
        val h = normalizeHost(host) ?: return
        saveSettings(
            settings.copy(
                blockedRedirectHosts = settings.blockedRedirectHosts + h,
                allowedHosts = settings.allowedHosts - h,
            )
        )
    }

    private fun blockPopupHost(host: String) {
        val h = normalizeHost(host) ?: return
        saveSettings(
            settings.copy(
                blockedPopupHosts = settings.blockedPopupHosts + h,
                allowedHosts = settings.allowedHosts - h,
            )
        )
    }

    private fun loadMainFrame(url: String) {
        pendingMainFrameUrl = url
        webView?.loadUrl(url)
    }

    private fun blockedResponse(): WebResourceResponse =
        WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))

    private fun choiceDue(url: String): Boolean {
        val now = SystemClock.uptimeMillis()
        if (url == lastChoiceUrl && now - lastChoiceAt < 5_000L) return false
        lastChoiceUrl = url
        lastChoiceAt = now
        return true
    }

    @Volatile
    private var choiceShowing = false

    private fun showChoice(
        title: String,
        message: String,
        options: List<Pair<String, () -> Unit>>,
        onCancel: () -> Unit = {},
    ) {
        if (choiceShowing) {
            onCancel()
            return
        }
        val activity = appContext as? Activity
        if (activity == null) {
            onCancel()
            return
        }
        mainHandler.post {
            if (activity.isFinishing || activity.isDestroyed) {
                onCancel()
                return@post
            }
            choiceShowing = true
            runCatching {
                val density = activity.resources.displayMetrics.density
                val pad = (16 * density).toInt()
                var dialog: AlertDialog? = null
                val box = android.widget.LinearLayout(activity).apply {
                    orientation = android.widget.LinearLayout.VERTICAL
                    setPadding(pad, pad / 4, pad, pad / 2)
                }
                for ((label, action) in options) {
                    box.addView(
                        android.widget.Button(activity).apply {
                            text = label
                            setOnClickListener {
                                dialog?.dismiss()
                                action()
                            }
                        },
                        android.widget.LinearLayout.LayoutParams(
                            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                        ),
                    )
                }
                dialog = AlertDialog.Builder(activity)
                    .setTitle(title)
                    .setMessage(message)
                    .setView(box)
                    .setNegativeButton("Cancel") { _, _ -> onCancel() }
                    .setOnCancelListener { onCancel() }
                    .setOnDismissListener { choiceShowing = false }
                    .create()
                dialog?.show()
            }.onFailure {
                choiceShowing = false
                onCancel()
            }
        }
    }

    private fun askRedirectPermission(url: String, host: String) {
        if (!choiceDue(url)) return
        showChoice(
            title = "Open this link?",
            message = "This page tried to open $host. Redirects to other sites are blocked by default.",
            options = listOf(
                "Allow once" to { loadMainFrame(url) },
                "Always allow $host" to {
                    allowHost(host)
                    loadMainFrame(url)
                },
                "Block always $host" to { blockRedirectHost(host) },
            ),
        )
    }

    private fun askPopupPermission(url: String, host: String, onAllow: (Boolean) -> Unit) {
        if (!choiceDue(url)) {
            onAllow(false)
            return
        }
        showChoice(
            title = "Open this popup?",
            message = "This page tried to open a popup from $host. Popups from other sites are blocked by default.",
            options = listOf(
                "Allow once" to { onAllow(true) },
                "Always allow $host" to {
                    allowHost(host)
                    onAllow(true)
                },
                "Block always $host" to {
                    blockPopupHost(host)
                    onAllow(false)
                },
            ),
            onCancel = { onAllow(false) },
        )
    }

    private fun shouldBlockMainFrame(url: String, hasGesture: Boolean): Boolean {
        if (allowNextMainFrame) {
            allowNextMainFrame = false
            return false
        }
        val pending = pendingMainFrameUrl
        if (pending != null && sameUrl(pending, url)) {
            pendingMainFrameUrl = null
            return false
        }
        if (url == "about:blank") return false
        if (!settings.redirectProtection) return false
        if (WebAdBlocker.isCloudflareInfra(url)) return false
        if (VIDEO_URL_RE.containsMatchIn(url)) return false
        val host = hostOf(url) ?: return false
        if (isAllowedHost(host)) return false
        val current = currentPageHost() ?: return false
        if (isSameSite(host, current)) return false
        if (isBlockedRedirectHost(host)) return true
        // Ask for automatic redirects too, not only taps. Some players redirect through a
        // separate embed/CDN host without a gesture; silently cancelling left a blank page.
        askRedirectPermission(url, host)
        return true
    }

    private fun updateNavigation() {
        val view = webView ?: return
        _state.value = _state.value.copy(
            canGoBack = view.canGoBack(),
            canGoForward = view.canGoForward(),
            url = view.url ?: _state.value.url,
        )
    }

    private fun injectUserscripts(atStart: Boolean) {
        val view = webView ?: return
        val url = pageUrl.ifBlank { view.url.orEmpty() }
        if (url.isBlank()) return
        WebUserscriptManager.scriptsFor(url, scripts, atStart).forEach { code ->
            view.evaluateJavascript(code, null)
        }
    }

    private fun injectHlsAndScan() {
        val view = webView ?: return
        val url = view.url ?: return
        if (hlsInjectedFor == url) return
        hlsInjectedFor = url
        if (hlsLib.isNotBlank()) {
            // Do not replace a site's own hls.js: overwriting window.Hls can break its player
            // (and shows up as a blinking/reloading video).
            val guarded = "if(typeof window.Hls==='undefined'){\n" + hlsLib + "\n}"
            view.evaluateJavascript(guarded, null)
        }
        view.evaluateJavascript(HLS_ATTACH_JS, null)
    }

    private fun injectAdClean() {
        if (!settings.adBlockEnabled) return
        webView?.evaluateJavascript(AD_CLEAN_JS, null)
    }

    private fun isMediaRequest(request: WebResourceRequest): Boolean {
        val headers = request.requestHeaders
        if (headers?.keys?.any { it.equals("Range", ignoreCase = true) } == true) return true
        val accept = headers?.entries?.firstOrNull { it.key.equals("Accept", ignoreCase = true) }?.value
            ?.lowercase().orEmpty()
        if (accept.contains("video/") || accept.contains("application/octet-stream") ||
            accept.contains("mpegurl")
        ) {
            return true
        }
        val u = request.url.toString().lowercase()
        return u.contains(".mp4") || u.contains(".m3u8") || u.contains(".mpd") ||
            u.contains(".webm") || u.contains(".mkv") || u.contains(".mov") || u.contains(".m4v") ||
            u.contains(".ts?") || u.contains("videoplayback") || u.contains("/videos/") ||
            u.contains("/media/") || u.contains("/master") || u.contains("/manifest") ||
            u.contains("/playlist")
    }

    private data class MediaProbe(
        val streamType: String?,
        val durationMs: Long? = null,
        val width: Int? = null,
        val height: Int? = null,
        val bandwidth: Long? = null,
        val isMaster: Boolean = false,
    )

    private fun maybeAddMedia(
        rawUrl: String,
        requestHeaders: Map<String, String>? = null,
        infoWidth: Int = 0,
        infoHeight: Int = 0,
        infoDurationMs: Long = 0L,
    ) {
        val url = rawUrl.trim()
        if (!url.startsWith("http://", true) && !url.startsWith("https://", true)) return

        // A DOM video often arrives after the request interceptor added the bare URL. Merge its
        // real duration/resolution so the picker can rank the actual video above 10-second ads.
        val existing = detected[url]
        if (existing != null) {
            if (infoWidth > 0 || infoHeight > 0 || infoDurationMs > 0) {
                val updated = existing.copy(
                    width = infoWidth.takeIf { it > 0 } ?: existing.width,
                    height = infoHeight.takeIf { it > 0 } ?: existing.height,
                    durationMs = infoDurationMs.takeIf { it > 0 } ?: existing.durationMs,
                ).let {
                    it.copy(likelyAd = looksLikeAd(url, it.durationMs, it.width, it.height))
                }
                detected[url] = updated
                mainHandler.post {
                    _state.value = _state.value.copy(detectedMedia = detected.values.toList())
                }
            }
            return
        }
        if (!probing.add(url)) return
        Thread {
            val headers = mediaHeaders(url, requestHeaders)
            val probe = runCatching { probeMedia(url, headers) }.getOrNull()
            probing.remove(url)
            if (probe == null) return@Thread
            val width = infoWidth.takeIf { it > 0 } ?: probe.width
            val height = infoHeight.takeIf { it > 0 } ?: probe.height
            val duration = infoDurationMs.takeIf { it > 0 } ?: probe.durationMs
            val source = WebMediaSource(
                url = url,
                pageUrl = pageUrl,
                title = pageTitle.ifBlank { webHost(url) },
                headers = headers,
                streamType = probe.streamType,
                isHls = probe.streamType == "hls",
                isDash = probe.streamType == "dash",
                durationMs = duration,
                width = width,
                height = height,
                bandwidth = probe.bandwidth,
                isMaster = probe.isMaster,
                likelyAd = looksLikeAd(url, duration, width, height),
            )
            detected[url] = source
            mainHandler.post {
                _state.value = _state.value.copy(detectedMedia = detected.values.toList())
            }
        }.start()
    }

    private fun looksLikeAd(url: String, durationMs: Long?, width: Int?, height: Int?): Boolean {
        val lower = url.lowercase()
        val adPath = listOf(
            "/ad/", "/ads/", "/advert", "preroll", "midroll", "pre-roll",
            "popunder", "banner",
        ).any { lower.contains(it) }
        val shortClip = durationMs != null && durationMs in 1..20_000L
        val smallVideo = (width ?: 0) in 1..640 || (height ?: 0) in 1..360
        return adPath || (shortClip && smallVideo)
    }

    private fun captureThumbnail(siteId: String) {
        val view = webView ?: return
        val width = view.width
        val height = view.height
        if (width <= 0 || height <= 0) return

        // Draw the top of the visible page into a wide 16:10 preview. That is the part of a home
        // page that identifies the site (header, hero, first row of content) and it matches the
        // card shape, so nothing important is cropped away.
        val targetWidth = 800
        val targetHeight = 500
        val aspect = targetWidth.toFloat() / targetHeight.toFloat()
        val sourceWidth = width.toFloat()
        val sourceHeight = (width / aspect).coerceAtMost(height.toFloat())
        val bitmap = runCatching {
            val bmp = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            val scale = targetWidth.toFloat() / sourceWidth
            canvas.scale(scale, scale)
            canvas.clipRect(0f, 0f, sourceWidth, sourceHeight)
            view.draw(canvas)
            bmp
        }.getOrNull() ?: return

        Thread {
            val dir = File(appContext.filesDir, "web_thumbs").apply { mkdirs() }
            val file = File(dir, "$siteId-${System.currentTimeMillis()}.jpg")
            val saved = runCatching {
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                }
            }.getOrDefault(false)
            bitmap.recycle()
            if (saved) {
                // The path changes on every capture so Coil cannot serve a stale cached image.
                dir.listFiles()
                    ?.filter {
                        (it.name.startsWith("$siteId-") || it.name == "$siteId.jpg") && it != file
                    }
                    ?.forEach { it.delete() }
                mainHandler.post { thumbnailListener?.invoke(siteId, file.absolutePath) }
            }
        }.start()
    }

    private fun mediaHeaders(
        url: String,
        requestHeaders: Map<String, String>? = null,
    ): Map<String, String> {
        fun requestHeader(name: String): String? =
            requestHeaders?.entries
                ?.firstOrNull { it.key.equals(name, ignoreCase = true) }
                ?.value
                ?.takeIf { it.isNotBlank() }

        val referer = requestHeader("Referer")
            ?: pageUrl.takeIf { it.isNotBlank() }
        val origin = requestHeader("Origin")
            ?: referer?.let(::originOf)
        return buildMap {
            if (!referer.isNullOrBlank()) put("Referer", referer)
            if (!origin.isNullOrBlank()) put("Origin", origin)
            put("User-Agent", requestHeader("User-Agent") ?: currentUserAgent)
            val cookie = runCatching { CookieManager.getInstance().getCookie(url) }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?: requestHeader("Cookie")
            if (!cookie.isNullOrBlank()) put("Cookie", cookie)
            // Some players sign each request with a custom header (session id, token). Carry
            // those through the probe and the handoff so the app player can fetch the same URL.
            requestHeaders?.forEach { (key, value) ->
                if (key.isNotBlank() && value.isNotBlank() &&
                    (key.startsWith("X-", ignoreCase = true) ||
                        key.equals("Authorization", ignoreCase = true))
                ) {
                    put(key, value)
                }
            }
        }
    }

    private fun originOf(url: String): String? {
        val scheme = url.substringBefore("://").lowercase()
        if (scheme != "http" && scheme != "https") return null
        val host = url.substringAfter("://").substringBefore('/')
        return if (host.isNotBlank()) "$scheme://$host" else null
    }

    private fun probeMedia(url: String, headers: Map<String, String>): MediaProbe? {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 6_000
            readTimeout = 8_000
            instanceFollowRedirects = true
            // No Range header here: manifests can be gzip-compressed, and a truncated gzip
            // stream fails to decompress. We only read the first 64 KB and close.
            headers.forEach { (key, value) -> setRequestProperty(key, value) }
        }
        return try {
            val code = connection.responseCode
            if (code !in 200..299) return null
            val contentType = connection.contentType?.lowercase().orEmpty()
            val bytes = connection.inputStream.use { input ->
                val stream = if (connection.contentEncoding?.equals("gzip", ignoreCase = true) == true) {
                    GZIPInputStream(input)
                } else {
                    input
                }
                stream.use { readUpTo(it, 64 * 1024) }
            }
            val head = String(bytes, 0, minOf(bytes.size, 2048), Charsets.ISO_8859_1)
            val text = String(bytes, Charsets.ISO_8859_1)
            when {
                head.startsWith("#EXTM3U") || contentType.contains("mpegurl") -> parseHlsManifest(text)
                contentType.contains("application/dash+xml") || head.contains("<mpd") -> parseDashManifest(text)
                contentType.contains("video/") -> MediaProbe(null)
                contentType.contains("application/octet-stream") && looksLikeMp4(bytes) -> MediaProbe(null)
                else -> null
            }
        } catch (_: Throwable) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun readUpTo(stream: java.io.InputStream, limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (out.size() < limit) {
            val read = stream.read(buffer)
            if (read <= 0) break
            out.write(buffer, 0, minOf(read, limit - out.size()))
        }
        return out.toByteArray()
    }

    private fun parseHlsManifest(text: String): MediaProbe {
        var durationMs = 0L
        var master = false
        var bestWidth = 0
        var bestHeight = 0
        var bestBandwidth = 0L
        for (line in text.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.startsWith("#EXT-X-STREAM-INF:")) {
                master = true
                val attrs = trimmed.substringAfter(':')
                Regex("""RESOLUTION=(\d+)x(\d+)""").find(attrs)?.let { match ->
                    val w = match.groupValues[1].toIntOrNull() ?: 0
                    val h = match.groupValues[2].toIntOrNull() ?: 0
                    if (w.toLong() * h > bestWidth.toLong() * bestHeight) {
                        bestWidth = w
                        bestHeight = h
                    }
                }
                Regex("""BANDWIDTH=(\d+)""").find(attrs)?.let { match ->
                    bestBandwidth = maxOf(bestBandwidth, match.groupValues[1].toLongOrNull() ?: 0L)
                }
            } else if (trimmed.startsWith("#EXTINF:")) {
                val seconds = trimmed.substringAfter(':').substringBefore(',').toDoubleOrNull() ?: 0.0
                durationMs += (seconds * 1000).toLong()
            }
        }
        return MediaProbe(
            streamType = "hls",
            durationMs = durationMs.takeIf { it > 0L },
            width = bestWidth.takeIf { it > 0 },
            height = bestHeight.takeIf { it > 0 },
            bandwidth = bestBandwidth.takeIf { it > 0L },
            isMaster = master,
        )
    }

    private fun parseDashManifest(text: String): MediaProbe {
        var bestWidth = 0
        var bestHeight = 0
        var bestBandwidth = 0L
        Regex("""<Representation\b[^>]*>""", RegexOption.IGNORE_CASE).findAll(text).forEach { match ->
            val tag = match.value
            val w = Regex("""width="(\d+)"""").find(tag)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val h = Regex("""height="(\d+)"""").find(tag)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val bw = Regex("""bandwidth="(\d+)"""").find(tag)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
            if (w.toLong() * h > bestWidth.toLong() * bestHeight) {
                bestWidth = w
                bestHeight = h
            }
            bestBandwidth = maxOf(bestBandwidth, bw)
        }
        return MediaProbe(
            streamType = "dash",
            width = bestWidth.takeIf { it > 0 },
            height = bestHeight.takeIf { it > 0 },
            bandwidth = bestBandwidth.takeIf { it > 0L },
        )
    }

    private fun looksLikeMp4(body: ByteArray): Boolean {
        if (body.size < 12) return false
        val tag = String(body, 4, 4, Charsets.ISO_8859_1)
        return tag == "ftyp" || tag == "moov" || tag == "mdat" || tag == "styp"
    }

    private inner class Bridge {
        @JavascriptInterface
        fun media(url: String) {
            runCatching { maybeAddMedia(url) }
        }

        @JavascriptInterface
        fun mediaInfo(url: String, width: Int, height: Int, durationMs: Double) {
            runCatching {
                maybeAddMedia(
                    rawUrl = url,
                    infoWidth = width,
                    infoHeight = height,
                    infoDurationMs = durationMs.toLong(),
                )
            }
        }

        @JavascriptInterface
        fun stuck(url: String) {
            runCatching { maybeAddMedia(url) }
        }

        @JavascriptInterface
        fun playing(value: Boolean) {
            videoPlaying = value
        }

        @JavascriptInterface
        fun userscriptGet(scriptId: String, key: String): String? =
            runCatching { WebAppsRepository.gmGet(scriptId, key) }.getOrNull()

        @JavascriptInterface
        fun userscriptSet(scriptId: String, key: String, valueJson: String) {
            runCatching { WebAppsRepository.gmSet(scriptId, key, valueJson) }
        }

        @JavascriptInterface
        fun userscriptDelete(scriptId: String, key: String) {
            runCatching { WebAppsRepository.gmDelete(scriptId, key) }
        }

        @JavascriptInterface
        fun userscriptList(scriptId: String): String =
            runCatching { WebAppsRepository.gmList(scriptId) }.getOrDefault("")
    }

    private companion object {
        const val MAX_RENDER_RESTARTS = 2

        val VIDEO_URL_RE = Regex(
            """(\.mp4|\.webm|\.mkv|\.mov|\.m4v|\.m3u8|\.mpd|videoplayback|/master|/manifest|/playlist)""",
            RegexOption.IGNORE_CASE,
        )

        val HLS_ATTACH_JS = """
            (function(){
              if(window.__nuvioHlsAttach)return;
              window.__nuvioHlsAttach=true;
              function attach(v){
                try{
                  if(v.hasAttribute('data-nuvio-browser'))return;
                  v.setAttribute('playsinline','');
                  v.setAttribute('webkit-playsinline','');
                  if(v.__nuvioHlsAttached)return;
                  var src=v.currentSrc||v.src||(v.querySelector('source')&&v.querySelector('source').src)||(v.getAttribute('data-src')||'');
                  if(!src||src.indexOf('.m3u8')<0)return;
                  if(src.indexOf('blob:')===0)return;
                  if(v.canPlayType('application/vnd.apple.mpegurl'))return;
                  if(!window.Hls||!window.Hls.isSupported())return;
                  var hls=new window.Hls({enableWorker:true,lowLatencyMode:true});
                  hls.loadSource(src);
                  hls.attachMedia(v);
                  v.__nuvioHlsAttached=hls;
                }catch(e){}
              }
              function scan(){
                try{
                  var vs=document.querySelectorAll('video');
                  for(var i=0;i<vs.length;i++)attach(vs[i]);
                  var ss=document.querySelectorAll('video source');
                  for(var i=0;i<ss.length;i++){
                    var s=ss[i];
                    if(s.src&&s.src.indexOf('.m3u8')>=0&&s.parentElement)attach(s.parentElement);
                  }
                }catch(e){}
              }
              scan();
              setInterval(scan,2000);
              try{
                new MutationObserver(scan).observe(document.body||document.documentElement,{childList:true,subtree:true});
              }catch(e){}
            })();
        """.trimIndent()

        val VIDEO_SCAN_JS = """
            (function(){
              var out=[];
              function add(url,v){
                if(!url||url.indexOf('blob:')===0)return;
                var w=v&&v.videoWidth?v.videoWidth:0;
                var h=v&&v.videoHeight?v.videoHeight:0;
                var d=v&&isFinite(v.duration)?Math.round(v.duration*1000):0;
                out.push([url,w,h,d]);
              }
              try{
                document.querySelectorAll('video').forEach(function(v){
                  var s=v.currentSrc||v.src||(v.querySelector('source')&&v.querySelector('source').src)||(v.getAttribute('data-src')||'');
                  add(s,v);
                });
                document.querySelectorAll('video source').forEach(function(s){
                  if(s.src&&s.src.indexOf('blob:')!==0)add(s.src,s.parentElement);
                });
              }catch(e){}
              out.forEach(function(x){
                try{NuvioWebBridge.mediaInfo(x[0],x[1],x[2],x[3])}catch(e){}
              });
            })();
        """.trimIndent()

        val PERFORMANCE_SCAN_JS = """
            (function(){
              try{
                performance.getEntriesByType('resource').forEach(function(e){
                  var u=e&&e.name?e.name:'';
                  if(!u||u.indexOf('http')!==0)return;
                  if(/\.(m3u8|mpd|mp4|webm|mkv|mov|m4v)(\?|$)/i.test(u) ||
                     /(master|manifest|playlist)/i.test(u)) {
                    try{NuvioWebBridge.media(u)}catch(e){}
                  }
                });
              }catch(e){}
            })();
        """.trimIndent()

        val STUCK_MONITOR_JS = """
            (function(){
              if(window.__nuvioStuckMon)return;
              window.__nuvioStuckMon=true;
              var state=new Map();
              var FIRE_AFTER=9000;
              function fire(v,src){
                if(v.__nuvioStuckFired)return;
                v.__nuvioStuckFired=true;
                try{NuvioWebBridge.stuck(src);}catch(e){}
              }
              function tick(){
                try{
                  document.querySelectorAll('video').forEach(function(v){
                    try{
                      var src=v.currentSrc||v.src||(v.querySelector('source')&&v.querySelector('source').src)||'';
                      if(!src||src.indexOf('http')!==0){state.delete(v);return;}
                      var prev=v.__nuvioSrc;
                      if(prev!==src){v.__nuvioSrc=src;v.__nuvioStuckFired=false;state.delete(v);}
                      var stuck=(v.error!==null)||(!v.paused&&v.readyState<2&&!v.videoWidth);
                      if(stuck){
                        var st=state.get(v);
                        if(!st){st={since:Date.now()};state.set(v,st);}
                        if(Date.now()-st.since>=FIRE_AFTER)fire(v,src);
                      }else{
                        state.delete(v);
                      }
                    }catch(e){}
                  });
                }catch(e){}
              }
              setInterval(tick,2000);
              tick();
            })();
        """.trimIndent()

        val PLAYING_MONITOR_JS = """
            (function(){
              if(window.__nuvioPlayingMon)return;
              window.__nuvioPlayingMon=true;
              var last=false;
              setInterval(function(){
                try{
                  var p=false;
                  document.querySelectorAll('video').forEach(function(v){
                    if(!v.paused&&!v.ended&&v.readyState>=2)p=true;
                  });
                  if(p!==last){last=p;try{NuvioWebBridge.playing(p)}catch(e){}}
                }catch(e){}
              },1500);
            })();
        """.trimIndent()

        val AD_CLEAN_JS = """
            (function(){
              if(window.__nuvioAdClean)return;
              window.__nuvioAdClean=true;
              var SEL=[
                '[id^="google_ads"]','.adsbygoogle','ins.adsbygoogle','[class*="adsense"]',
                '[class*="advert"]','[id*="advert"]','[class*="ad-banner"]','[id*="ad-banner"]',
                'div[data-ad]','[class*="ad-placeholder"]',
                'iframe[src*="doubleclick"]','iframe[src*="googlesyndication"]',
                'iframe[src*="googleads"]','iframe[src*="advertising"]',
                'iframe[src*="adserver"]','iframe[src*="2mdn"]',
                '[class^="ad_"]','[id^="ad_"]','[class*="ad-pop"]',
                '[class*="popunder"]','[id*="popunder"]',
                '[class*="interstitial"]','[class*="overlay-ad"]','[id*="overlay-ad"]',
                '[class*="ad-overlay"]','[class*="ad_container"]','[class*="ad-slot"]',
                '[id*="ad-slot"]','[class*="ads-container"]',
                'iframe[src*="popads"]','iframe[src*="popcash"]',
                'iframe[src*="propellerads"]','iframe[src*="adnxs"]',
                'iframe[src*="exoclick"]','iframe[src*="juicyads"]',
                'iframe[src*="trafficjunky"]'
              ];
              var st=document.createElement('style');
              st.textContent=SEL.join(',')+'{display:none!important;visibility:hidden!important}';
              (document.head||document.documentElement).appendChild(st);
            })();
        """.trimIndent()
    }
}

/** Built-in hosts list plus downloaded hosts/uBlock filter lists, ported from Lucid Hikari (GPL-3.0). */
internal object WebAdBlocker {
    private val builtIn = setOf(
        "doubleclick.net", "googleadservices.com", "googlesyndication.com", "googletagmanager.com",
        "googletagservices.com", "google-analytics.com", "adservice.google.com", "2mdn.net", "adf.ly",
        "taboola.com", "outbrain.com", "adnxs.com", "criteo.com", "criteo.net", "rubiconproject.com",
        "moatads.com", "quantserve.com", "scorecardresearch.com", "amazon-adsystem.com", "adform.net",
        "smartadserver.com", "openx.net", "pubmatic.com", "sovrn.com", "casalemedia.com", "lijit.com",
        "adsafeprotected.com", "popads.net", "popunder.ru", "adsterra.com", "propellerads.com",
        "adroll.com", "media.net", "yieldmo.com", "adcolony.com", "inmobi.com", "admarvel.com",
        "flurry.com", "startappservice.com", "smartyads.com", "admob.com", "applovin.com",
        "vungle.com", "chartboost.com", "mopub.com", "supersonicads.com", "revcontent.com",
        "adpushup.com", "snigelweb.com", "adthrive.com", "ezoic.net", "mediavine.com",
        "trafficjunky.net", "exoclick.com", "juicyads.com",
    )

    private val hostsListUrls = listOf(
        "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts",
        "https://adaway.org/hosts.txt",
        "https://pgl.yoyo.org/adservers/serverlist.php?hostformat=hosts&showintro=0&mimetype=plaintext",
    )

    private val filterListUrls = listOf(
        "https://easylist.to/easylist/easylist.txt",
        "https://easylist.to/easylist/easyprivacy.txt",
        "https://raw.githubusercontent.com/uBlockOrigin/uAssets/master/filters/filters.txt",
        "https://raw.githubusercontent.com/uBlockOrigin/uAssets/master/filters/privacy.txt",
        "https://raw.githubusercontent.com/uBlockOrigin/uAssets/master/filters/badware.txt",
    )

    /** Keep filter-list parsing and per-request checks bounded on a phone. */
    private const val MAX_FILTER_RULES = 15_000

    @Volatile
    private var extraHosts: Set<String> = emptySet()

    @Volatile
    private var filterRules: List<PatternRule> = emptyList()

    @Volatile
    private var combinedRules: List<PatternRule> = emptyList()

    @Volatile
    private var filtersLoaded = false

    private val builtInRules: List<PatternRule> by lazy {
        BUILTIN_PATTERNS.mapNotNull(::parsePattern)
    }

    private fun compiledPatterns(): List<PatternRule> =
        combinedRules.ifEmpty { builtInRules }

    private fun hostsFile(context: android.content.Context) =
        java.io.File(context.filesDir, "web_adblock_hosts.txt")

    private fun filterDir(context: android.content.Context) =
        java.io.File(context.filesDir, "web_adblock_filters").apply { mkdirs() }

    private fun filterCacheFile(context: android.content.Context, url: String) =
        java.io.File(filterDir(context), url.hashCode().toLong().toString(16) + ".txt")

    fun load(context: android.content.Context) {
        val file = hostsFile(context)
        if (file.exists()) {
            extraHosts = runCatching { file.readLines().filter { it.isNotBlank() }.toSet() }
                .getOrDefault(emptySet())
        }
        if (filtersLoaded) return
        filtersLoaded = true
        Thread {
            val rules = ArrayList<PatternRule>()
            filterDir(context).listFiles()?.forEach { cached ->
                if (!cached.name.endsWith(".txt")) return@forEach
                runCatching {
                    parseFilterList(cached.readText(), MAX_FILTER_RULES - rules.size)
                        .forEach { rules += it }
                }
            }
            filterRules = rules
            combinedRules = builtInRules + rules
        }.start()
    }

    fun update(context: android.content.Context) {
        val hosts = HashSet<String>()
        for (url in hostsListUrls) {
            runCatching {
                val connection = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
                    connectTimeout = 20_000
                    readTimeout = 30_000
                    setRequestProperty("User-Agent", "Nuvio")
                }
                connection.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        val clean = line.substringBefore('#').trim()
                        if (clean.isBlank()) return@forEach
                        val parts = clean.split(Regex("\\s+"))
                        val host = when {
                            parts.firstOrNull()?.let(::isIp) == true -> parts.getOrNull(1)
                            else -> parts.firstOrNull()
                        }?.trim()?.trimEnd('.')?.lowercase()
                        if (host != null && host.contains('.') &&
                            !host.startsWith("localhost") && !host.endsWith(".local")
                        ) {
                            hosts += host
                        }
                    }
                }
                connection.disconnect()
            }
        }
        if (hosts.isNotEmpty()) {
            extraHosts = hosts
            runCatching { hostsFile(context).writeText(hosts.joinToString("\n")) }
        }

        // EasyList/uBlock network rules: same uBO-style engine, cached for offline use.
        for (url in filterListUrls) {
            runCatching {
                val connection = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
                    connectTimeout = 20_000
                    readTimeout = 40_000
                    setRequestProperty("User-Agent", "Nuvio")
                }
                val text = connection.inputStream.bufferedReader().use { it.readText() }
                connection.disconnect()
                if (text.isNotBlank()) {
                    filterCacheFile(context, url).writeText(text)
                }
            }
        }

        val rules = ArrayList<PatternRule>()
        filterDir(context).listFiles()?.forEach { cached ->
            if (!cached.name.endsWith(".txt")) return@forEach
            runCatching {
                parseFilterList(cached.readText(), MAX_FILTER_RULES - rules.size)
                    .forEach { rules += it }
            }
        }
        filterRules = rules
        combinedRules = builtInRules + rules
    }

    private fun isIp(value: String): Boolean =
        value == "0.0.0.0" || value == "127.0.0.1" || value == "::1" || value.startsWith("::ffff:")

    /** Frameworks/CDNs that must never be blocked, even if a downloaded list names them. */
    private val essentialHosts = setOf(
        "cdn.jsdelivr.net", "unpkg.com", "cdnjs.cloudflare.com",
        "ajax.googleapis.com", "fonts.googleapis.com", "fonts.gstatic.com",
        "challenges.cloudflare.com", "cloudflare.com",
    )

    private fun isEssentialHost(host: String): Boolean =
        isBlockedHost(host, essentialHosts)

    fun isBlocked(url: String, includeExtra: Boolean = true): Boolean {
        val host = runCatching { java.net.URL(url).host?.lowercase() }.getOrNull() ?: return false
        if (isEssentialHost(host)) return false
        return isBlockedHost(host, builtIn) ||
            (includeExtra && isBlockedHost(host, extraHosts))
    }

    private fun isBlockedHost(host: String, list: Set<String>): Boolean {
        if (list.isEmpty()) return false
        var current = host
        while (true) {
            if (current in list) return true
            val dot = current.indexOf('.')
            if (dot < 0) return false
            current = current.substring(dot + 1)
        }
    }

    // ------------------------------------------------ uBO-style network rules
    //
    // The hosts files only match whole domains. These rules catch the ads served
    // from the site's own domain (`/ads/banner`, `/pop.js`), rotating popunder
    // hosts and player-ad scripts. Syntax is the practical EasyList/uBO subset:
    //   ||domain^path*   domain-anchored (any subdomain, any scheme)
    //   |http…|          start/end anchored
    //   plain substring  (with * wildcards)
    //   ^               separator (end of host, /, ?, &, =)
    //   @@…             exception — never block, wins over everything
    //   $third-party, $~third-party, $domain=a|~b, $script, $image, $media, …
    //   $match-case

    data class PatternRule(
        val raw: String,
        val isException: Boolean,
        val regex: Regex,
        val thirdParty: Boolean?,
        val domains: List<DomainOpt>?,
        val types: Set<String>?,
        val keyword: String?,
        val fromFilterList: Boolean = false,
    )

    data class DomainOpt(val domain: String, val negated: Boolean)

    /** Curated uBO-style rules for the ad networks video/streaming sites actually use. */
    private val BUILTIN_PATTERNS = listOf(
        "||popads.net^", "||popcash.net^", "||popunder.net^",
        "||adsterra.com^", "||propellerads.com^", "||propeller-tracking.com^",
        "||adcash.com^", "||exoclick.com^", "||trafficjunky.net^",
        "||juicyads.com^", "||adxpansion.com^", "||hilltopads.net^",
        "||clickadu.com^", "||mgid.com^", "||revcontent.com^",
        "||outbrain.com^", "||taboola.com^", "||criteo.com^",
        "||adnxs.com^", "||rubiconproject.com^", "||pubmatic.com^",
        "||doubleclick.net^", "||googlesyndication.com^",
        "||googleadservices.com^", "||amazon-adsystem.com^",
        "||imasdk.googleapis.com^",
        "/popunder", "/pop-under", "/popup-ad", "/popad.", "/pop.js",
        "/popunder.js", "/popup.js", "/popcash.js", "/pst.js",
        "/ads/banner", "/ads/pop", "/advertising/", "/adserver/",
        "/promo-redirect", "/redirect-ads", "/out.php?",
        "||vast.com^", "/vast.xml", "/vmap", "imasdk",
        "/jwplayer-ads", "/video-ads/", "/preroll",
        "||moatads.com^", "||scorecardresearch.com^", "||quantserve.com^",
        "@@||accounts.google.com^", "@@||paypal.com^", "@@||stripe.com^",
        "@@||cloudflare.com^", "@@||challenges.cloudflare.com^",
    )

    private val optionsRe = Regex("^[a-z~\\-]+(=[^,]*)?(,[a-z~\\-]+(=[^,]*)?)*$")

    fun parsePattern(raw: String): PatternRule? {
        var s = raw.trim()
        if (s.isEmpty() || s.startsWith("!") || s.startsWith("[")) return null
        if (s.contains("##") || s.contains("#@#") || s.contains("#?#") || s.contains("#$#")) return null
        if (s.startsWith("/") && s.endsWith("/") && s.length > 2) return null // regex rule
        val isException = s.startsWith("@@")
        if (isException) s = s.substringAfter("@@")
        var thirdParty: Boolean? = null
        var domains: List<DomainOpt>? = null
        var types: Set<String>? = null
        val optIdx = s.lastIndexOf('$')
        if (optIdx > 0) {
            val opts = s.substring(optIdx + 1)
            if (optionsRe.matches(opts)) {
                s = s.substring(0, optIdx)
                for (o in opts.split(',')) {
                    val name = o.substringBefore('=')
                    val value = o.substringAfter('=', "")
                    when (name) {
                        "third-party" -> thirdParty = true
                        "~third-party" -> thirdParty = false
                        "domain" -> domains = value.split('|')
                            .filter { it.isNotBlank() }
                            .map { DomainOpt(it.trimStart('~').lowercase(), it.startsWith("~")) }
                            .takeIf { it.isNotEmpty() }
                        "script", "stylesheet", "image", "font", "media",
                        "xmlhttprequest", "subdocument", "other", "object",
                        "ping", "websocket" -> types = (types ?: emptySet()) + name
                        "~script", "~stylesheet", "~image", "~font", "~media",
                        "~xmlhttprequest", "~subdocument", "~other" -> Unit
                        "match-case", "document", "elemhide", "generichide", "important",
                        "redirect", "csp", "removeparam", "cookie" -> Unit
                        else -> return null
                    }
                }
            }
        }
        if (s.isEmpty()) return null
        val startAnchor = s.startsWith("|") && !s.startsWith("||")
        val domainAnchor = s.startsWith("||")
        var endAnchor = false
        var core = when {
            domainAnchor -> s.substring(2)
            startAnchor -> s.substring(1)
            else -> s
        }
        if (core.endsWith("|") && !core.endsWith("\\|")) {
            endAnchor = true
            core = core.dropLast(1)
        }
        val keyword = Regex("[a-zA-Z0-9][a-zA-Z0-9._/-]{3,}")
            .find(core)
            ?.value
            ?.lowercase()
            ?.takeIf { it.length >= 4 }
        val body = buildString {
            for (c in core) {
                when (c) {
                    '*' -> append(".*")
                    '^' -> append("(?:[^a-zA-Z0-9_.%-]|$)")
                    else -> append(Regex.escape(c.toString()))
                }
            }
        }
        val pattern = when {
            domainAnchor -> "^[a-zA-Z][a-zA-Z0-9+.-]*://([^/]*\\.)?" + body
            startAnchor -> "^" + body
            else -> body
        } + if (endAnchor) "$" else ""
        val regex = runCatching { Regex(pattern, RegexOption.IGNORE_CASE) }.getOrNull() ?: return null
        return PatternRule(raw, isException, regex, thirdParty, domains, types, keyword)
    }

    private fun parseFilterList(text: String, limit: Int): List<PatternRule> {
        if (limit <= 0) return emptyList()
        val out = ArrayList<PatternRule>(minOf(limit, 2_000))
        for (line in text.lineSequence()) {
            if (out.size >= limit) break
            val raw = line.trim()
            if (raw.isEmpty() || raw.startsWith("!") || raw.startsWith("[")) continue
            if (raw.contains("##") || raw.contains("#@#") || raw.contains("#?#") || raw.contains("#$#")) continue
            parsePattern(raw)?.copy(fromFilterList = true)?.let { out += it }
        }
        return out
    }

    fun requestType(request: android.webkit.WebResourceRequest): String {
        val u = request.url.toString().lowercase()
        val path = u.substringBefore('?')
        return when {
            path.endsWith(".js") || path.endsWith(".mjs") -> "script"
            path.endsWith(".css") -> "stylesheet"
            path.endsWith(".png") || path.endsWith(".jpg") || path.endsWith(".jpeg") ||
                path.endsWith(".gif") || path.endsWith(".webp") || path.endsWith(".svg") ||
                path.endsWith(".ico") || path.endsWith(".avif") || path.endsWith(".bmp") -> "image"
            path.endsWith(".woff") || path.endsWith(".woff2") || path.endsWith(".ttf") ||
                path.endsWith(".otf") || path.endsWith(".eot") -> "font"
            path.endsWith(".mp4") || path.endsWith(".m3u8") || path.endsWith(".mpd") ||
                path.endsWith(".ts") || path.endsWith(".webm") || path.endsWith(".m4s") -> "media"
            else -> {
                val accept = request.requestHeaders?.get("Accept")?.lowercase().orEmpty()
                when {
                    accept.contains("javascript") || accept.contains("ecmascript") -> "script"
                    accept.contains("css") -> "stylesheet"
                    accept.startsWith("image/") -> "image"
                    accept.contains("font") -> "font"
                    accept.contains("video/") || accept.contains("audio/") ||
                        accept.contains("mpegurl") || accept.contains("mpd") -> "media"
                    request.method.equals("POST", ignoreCase = true) -> "xmlhttprequest"
                    else -> "other"
                }
            }
        }
    }

    private fun sameParty(reqHost: String, pageHost: String): Boolean {
        val r = reqHost.lowercase()
        val p = pageHost.trim().trimEnd('.').lowercase()
        return r == p || r.endsWith(".$p") || p.endsWith(".$r")
    }

    private fun ruleApplies(
        rule: PatternRule,
        url: String,
        reqHost: String,
        pageHost: String?,
        type: String,
    ): Boolean {
        if (rule.keyword != null && !url.contains(rule.keyword, ignoreCase = true)) return false
        if (!rule.regex.containsMatchIn(url)) return false
        if (rule.thirdParty != null && pageHost != null) {
            val third = !sameParty(reqHost, pageHost)
            if (rule.thirdParty != third) return false
        }
        rule.domains?.let { opts ->
            val ph = (pageHost ?: "").lowercase()
            var included = opts.none { !it.negated }
            for (o in opts) {
                if (ph == o.domain || ph.endsWith("." + o.domain)) {
                    if (o.negated) return false
                    included = true
                }
            }
            if (!included) return false
        }
        rule.types?.let { if (type !in it) return false }
        return true
    }

    fun matchesPatternUrl(
        url: String,
        pageHost: String?,
        type: String = "other",
        includeFilterRules: Boolean = true,
    ): Boolean {
        val reqHost = runCatching { java.net.URI(url).host }
            .getOrNull()?.trim()?.trimEnd('.')?.lowercase().orEmpty()
        if (reqHost.isEmpty() || isEssentialHost(reqHost)) return false
        val patterns = compiledPatterns()
        if (patterns.isEmpty()) return false
        var blocked = false
        for (rule in patterns) {
            if (!includeFilterRules && rule.fromFilterList) continue
            if (!ruleApplies(rule, url, reqHost, pageHost, type)) continue
            if (rule.isException) return false
            blocked = true
        }
        return blocked
    }

    fun matchesPattern(
        request: android.webkit.WebResourceRequest,
        pageHost: String?,
        includeFilterRules: Boolean = true,
    ): Boolean = matchesPatternUrl(
        request.url.toString(),
        pageHost,
        requestType(request),
        includeFilterRules,
    )

    /** Cloudflare's challenge bootstrap must never be treated as ad traffic. */
    fun isCloudflareInfra(url: String?): Boolean {
        if (url == null) return false
        if (url.contains("/cdn-cgi/")) return true
        val host = runCatching { java.net.URI(url).host?.lowercase() }.getOrNull() ?: return false
        return host == "challenges.cloudflare.com" || host.endsWith(".challenges.cloudflare.com") ||
            host == "cloudflare.com" || host.endsWith(".cloudflare.com") ||
            host == "cloudflareinsights.com" || host.endsWith(".cloudflareinsights.com")
    }
}

@Composable
actual fun rememberWebBrowserController(): WebBrowserController {
    // Use the Activity context so popup WebViews created from onCreateWindow can load pages
    // and share the main WebView's session. The controller is disposed with this screen.
    val context = LocalContext.current
    return remember { AndroidWebBrowserController(context) }
}

@Composable
actual fun WebBrowserHost(
    controller: WebBrowserController,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val android = controller as? AndroidWebBrowserController
    val state by controller.state.collectAsState()
    // A renderer crash leaves the old WebView unusable. The controller bumps this when that
    // happens; key() gives AndroidView a fresh factory and the page loads again.
    key(state.rendererGeneration) {
        AndroidView(
            factory = { WebView(context).also { view -> android?.attach(view) } },
            modifier = modifier,
            update = { view -> android?.attach(view) },
        )
    }
    DisposableEffect(controller) {
        onDispose {
            controller.pause()
            controller.destroy()
        }
    }
}
