package com.nuvio.app.features.web

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Creates the platform browser controller (Android WebView today). */
@Composable
expect fun rememberWebBrowserController(): WebBrowserController

/** Hosts the native browser view. */
@Composable
expect fun WebBrowserHost(
    controller: WebBrowserController,
    modifier: Modifier = Modifier,
)
