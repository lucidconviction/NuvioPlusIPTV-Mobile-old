package com.nuvio.app.features.web

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.nuvio.app.core.ui.nuvio
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private class IosWebBrowserController : WebBrowserController {
    private val _state = MutableStateFlow(
        WebBrowserUiState(error = "Web-Apps is available on Android in this build. iOS support is coming next.")
    )
    override val state: StateFlow<WebBrowserUiState> = _state.asStateFlow()
    override fun load(url: String) = Unit
    override fun goBack() = Unit
    override fun goForward() = Unit
    override fun reload() = Unit
    override fun stopLoading() = Unit
    override fun destroy() = Unit
    override fun applySettings(settings: WebAppsSettings, scripts: List<WebUserscript>) = Unit
}

@Composable
actual fun rememberWebBrowserController(): WebBrowserController = remember { IosWebBrowserController() }

@Composable
actual fun WebBrowserHost(controller: WebBrowserController, modifier: Modifier) {
    val tokens = MaterialTheme.nuvio
    Box(modifier.fillMaxSize().background(tokens.colors.background), contentAlignment = Alignment.Center) {
        Text("Web-Apps is available on Android in this build.", color = tokens.colors.textMuted)
    }
}
