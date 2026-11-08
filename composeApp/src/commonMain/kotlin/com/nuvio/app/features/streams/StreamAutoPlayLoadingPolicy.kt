package com.nuvio.app.features.streams

import com.nuvio.app.features.player.PlayerSettingsUiState

internal fun StreamsUiState.shouldShowAutoPlayLoading(
    expectedRequestToken: String,
    settings: PlayerSettingsUiState,
    manualSelection: Boolean,
    forceAutoPlay: Boolean = false,
): Boolean =
    if (requestToken == expectedRequestToken && autoPlayDecided) {
        showDirectAutoPlayOverlay
    } else {
        (forceAutoPlay || !manualSelection) &&
            (forceAutoPlay || StreamAutoPlayPolicy.isEffectivelyEnabled(settings))
    }

internal fun StreamsUiState.shouldUseLandscapeAutoPlayLoading(
    expectedRequestToken: String,
    settings: PlayerSettingsUiState,
    manualSelection: Boolean,
    forceAutoPlay: Boolean = false,
): Boolean =
    !manualSelection &&
        (forceAutoPlay || settings.streamAutoPlayMode != StreamAutoPlayMode.MANUAL) &&
        requestToken == expectedRequestToken &&
        autoPlayDecided &&
        isDirectAutoPlayFlow &&
        showDirectAutoPlayOverlay

internal fun List<AddonStreamGroup>.areAutoPlaySourcesLoaded(
    source: StreamAutoPlaySource,
    installedAddonIds: Set<String>,
): Boolean = none { group ->
    group.isLoading && when (source) {
        StreamAutoPlaySource.ALL_SOURCES -> true
        StreamAutoPlaySource.INSTALLED_ADDONS_ONLY -> group.addonId in installedAddonIds
        StreamAutoPlaySource.ENABLED_PLUGINS_ONLY -> group.addonId !in installedAddonIds
    }
}
