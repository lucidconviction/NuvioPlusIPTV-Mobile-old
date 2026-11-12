package com.nuvio.app.features.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.rounded.Policy
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.build.AppVersionConfig
import com.nuvio.app.features.updater.AppUpdaterStatus
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.about_licenses_attributions_subtitle
import nuvio.composeapp.generated.resources.about_supporters_contributors_subtitle
import nuvio.composeapp.generated.resources.compose_about_made_with
import nuvio.composeapp.generated.resources.compose_about_version_format
import nuvio.composeapp.generated.resources.compose_settings_page_licenses_attributions
import nuvio.composeapp.generated.resources.compose_settings_page_privacy_policy
import nuvio.composeapp.generated.resources.compose_settings_page_supporters_contributors
import nuvio.composeapp.generated.resources.compose_settings_root_about_section
import nuvio.composeapp.generated.resources.compose_settings_root_check_updates_description
import nuvio.composeapp.generated.resources.compose_settings_root_check_updates_title
import nuvio.composeapp.generated.resources.compose_settings_whats_new_description
import nuvio.composeapp.generated.resources.compose_settings_whats_new_title
import nuvio.composeapp.generated.resources.settings_updates_available
import nuvio.composeapp.generated.resources.settings_updates_checking
import nuvio.composeapp.generated.resources.settings_updates_failed
import nuvio.composeapp.generated.resources.settings_updates_latest
import nuvio.composeapp.generated.resources.compose_settings_root_privacy_policy_description
import nuvio.composeapp.generated.resources.updates_debug_test_description
import nuvio.composeapp.generated.resources.updates_debug_test_title
import org.jetbrains.compose.resources.stringResource

private const val PRIVACY_POLICY_URL = "https://nuvio.tv/privacy-policy"
private const val CHANGELOG_URL =
    "https://github.com/lucidconviction/lucid-nuvio-iptvplus-mobile/releases"

internal fun LazyListScope.aboutSettingsContent(
    isTablet: Boolean,
    onSupportersContributorsClick: () -> Unit,
    onLicensesAttributionsClick: () -> Unit,
    onCheckForUpdatesClick: (() -> Unit)?,
    onTestUpdateBannerClick: (() -> Unit)?,
    showSupportersContributorsPage: Boolean,
) {
    item {
        val uriHandler = LocalUriHandler.current
        val updaterState by AppUpdaterStatus.state.collectAsStateWithLifecycle()
        SettingsSection(
            title = stringResource(Res.string.compose_settings_root_about_section),
            isTablet = isTablet,
        ) {
            SettingsGroup(isTablet = isTablet) {
                if (showSupportersContributorsPage) {
                    SettingsNavigationRow(
                        title = stringResource(Res.string.compose_settings_page_supporters_contributors),
                        description = stringResource(Res.string.about_supporters_contributors_subtitle),
                        icon = SettingsPage.SupportersContributors.icon,
                        isTablet = isTablet,
                        onClick = onSupportersContributorsClick,
                    )
                    SettingsGroupDivider(isTablet = isTablet)
                }
                SettingsNavigationRow(
                    title = stringResource(Res.string.compose_settings_page_privacy_policy),
                    description = stringResource(Res.string.compose_settings_root_privacy_policy_description),
                    icon = Icons.Rounded.Policy,
                    isTablet = isTablet,
                    onClick = { uriHandler.openUri(PRIVACY_POLICY_URL) },
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsNavigationRow(
                    title = stringResource(Res.string.compose_settings_page_licenses_attributions),
                    description = stringResource(Res.string.about_licenses_attributions_subtitle),
                    icon = SettingsPage.LicensesAttributions.icon,
                    isTablet = isTablet,
                    onClick = onLicensesAttributionsClick,
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsNavigationRow(
                    title = stringResource(Res.string.compose_settings_whats_new_title),
                    description = stringResource(Res.string.compose_settings_whats_new_description),
                    icon = SettingsPage.LicensesAttributions.icon,
                    isTablet = isTablet,
                    onClick = { uriHandler.openUri(CHANGELOG_URL) },
                )
                if (onCheckForUpdatesClick != null) {
                    SettingsGroupDivider(isTablet = isTablet)
                    UpdateChannelSettingsRow(isTablet = isTablet)
                    SettingsGroupDivider(isTablet = isTablet)
                    SettingsNavigationRow(
                        title = stringResource(Res.string.compose_settings_root_check_updates_title),
                        description = stringResource(Res.string.compose_settings_root_check_updates_description),
                        icon = SettingsSearchTarget.CheckForUpdates.icon,
                        isTablet = isTablet,
                        onClick = onCheckForUpdatesClick,
                    )
                    val updateStatus = when {
                        updaterState.isChecking -> stringResource(Res.string.settings_updates_checking)
                        updaterState.isUpdateAvailable && updaterState.update != null ->
                            stringResource(Res.string.settings_updates_available, updaterState.update!!.title)
                        updaterState.errorMessage != null -> stringResource(Res.string.settings_updates_failed)
                        updaterState.hasChecked ->
                            stringResource(Res.string.settings_updates_latest, AppVersionConfig.VERSION_NAME)
                        else -> null
                    }
                    if (updateStatus != null) {
                        Text(
                            text = updateStatus,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (onTestUpdateBannerClick != null) {
                    SettingsGroupDivider(isTablet = isTablet)
                    SettingsNavigationRow(
                        title = stringResource(Res.string.updates_debug_test_title),
                        description = stringResource(Res.string.updates_debug_test_description),
                        icon = Icons.Default.BugReport,
                        isTablet = isTablet,
                        onClick = onTestUpdateBannerClick,
                    )
                }
            }
        }
    }
    settingsFooterContent(isTablet)
}

internal fun LazyListScope.settingsFooterContent(isTablet: Boolean) {
    item {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = if (isTablet) 20.dp else 16.dp),
        ) {
            MemberBrandWordmark(
                height = if (isTablet) 30.dp else 26.dp,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            androidx.compose.foundation.layout.Spacer(
                modifier = Modifier.height(if (isTablet) 10.dp else 8.dp),
            )
            Text(
                text = stringResource(Res.string.compose_about_made_with),
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(
                    Res.string.compose_about_version_format,
                    AppVersionConfig.VERSION_NAME,
                    AppVersionConfig.VERSION_CODE,
                ),
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
