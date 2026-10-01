@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.shiv.rally.presentation.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.shiv.rally.BuildConfig
import com.shiv.rally.data.update.RallyUpdateState
import com.shiv.rally.domain.model.IptvProvider
import com.shiv.rally.presentation.common.RallyTvPalette
import com.shiv.rally.presentation.common.RallyTvRule
import com.shiv.rally.presentation.common.rallyTvFocus
import com.shiv.rally.presentation.home.formatLeagueDisplayName
import com.shiv.rally.presentation.theme.RallyBodyFont
import com.shiv.rally.presentation.theme.RallyDisplayFont
import kotlinx.coroutines.delay

@Composable
private fun settingsFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = RallyTvPalette.Text,
    unfocusedTextColor = RallyTvPalette.Text,
    focusedBorderColor = RallyTvPalette.Accent,
    unfocusedBorderColor = RallyTvPalette.Divider,
    focusedLabelColor = RallyTvPalette.Text,
    unfocusedLabelColor = RallyTvPalette.Muted,
    focusedContainerColor = RallyTvPalette.FocusSurface,
    unfocusedContainerColor = RallyTvPalette.BackgroundSoft
)

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onSaved: () -> Unit,
    initialFocusRequester: FocusRequester? = null
) {
    val iptvProvider by viewModel.iptvProvider.collectAsStateWithLifecycle()
    val portalUrl by viewModel.portalUrl.collectAsStateWithLifecycle()
    val macAddress by viewModel.macAddress.collectAsStateWithLifecycle()
    val xtreamServerUrl by viewModel.xtreamServerUrl.collectAsStateWithLifecycle()
    val xtreamUsername by viewModel.xtreamUsername.collectAsStateWithLifecycle()
    val xtreamPassword by viewModel.xtreamPassword.collectAsStateWithLifecycle()
    val serialNumber by viewModel.serialNumber.collectAsStateWithLifecycle()
    val deviceId by viewModel.deviceId.collectAsStateWithLifecycle()
    val addonUrls by viewModel.stremioAddonUrls.collectAsStateWithLifecycle()
    val newAddonUrl by viewModel.newAddonUrl.collectAsStateWithLifecycle()
    val enabledLeagues by viewModel.enabledLeagues.collectAsStateWithLifecycle()
    val favoriteSports by viewModel.favoriteSports.collectAsStateWithLifecycle()
    val favoriteTeams by viewModel.favoriteTeams.collectAsStateWithLifecycle()
    val sportsOrder by viewModel.sportsOrder.collectAsStateWithLifecycle()
    val liveGameAlertsEnabled by viewModel.liveGameAlertsEnabled.collectAsStateWithLifecycle()
    val redZoneAlertsEnabled by viewModel.redZoneAlertsEnabled.collectAsStateWithLifecycle()
    val lowLatencyMode by viewModel.lowLatencyMode.collectAsStateWithLifecycle()
    val reducedMotion by viewModel.reducedMotion.collectAsStateWithLifecycle()
    val highContrastFocus by viewModel.highContrastFocus.collectAsStateWithLifecycle()
    val largeText by viewModel.largeText.collectAsStateWithLifecycle()
    val spokenScoreSummaries by viewModel.spokenScoreSummaries.collectAsStateWithLifecycle()
    val scoreSaverEnabled by viewModel.scoreSaverEnabled.collectAsStateWithLifecycle()
    val audioNormalizationEnabled by viewModel.audioNormalizationEnabled.collectAsStateWithLifecycle()
    val adaptiveQualityEnabled by viewModel.adaptiveQualityEnabled.collectAsStateWithLifecycle()
    val providerDiagnostics by viewModel.providerDiagnostics.collectAsStateWithLifecycle()
    val error by viewModel.configurationError.collectAsStateWithLifecycle()
    val supportMessage by viewModel.supportMessage.collectAsStateWithLifecycle()
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val diagnosticsExport = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        val saved = uri != null && writeTextDocument(context, uri, viewModel.exportDiagnostics())
        viewModel.reportExportResult("Support report", saved)
    }
    val preferencesExport = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val saved = uri != null && writeTextDocument(context, uri, viewModel.exportPreferences())
        viewModel.reportExportResult("Preferences backup", saved)
    }
    val preferencesImport = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val raw = readTextDocument(context, uri)
            if (raw != null) viewModel.importPreferences(raw)
            else viewModel.reportExportResult("Preferences backup", false)
        }
    }

    val fallbackFocus = remember { FocusRequester() }
    val firstFocus = initialFocusRequester ?: fallbackFocus
    LaunchedEffect(firstFocus) {
        delay(120)
        runCatching { firstFocus.requestFocus() }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 66.dp, vertical = 30.dp)
        ) {
            Column(Modifier.widthIn(max = 1120.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("SETTINGS", color = RallyTvPalette.Accent, fontFamily = RallyBodyFont, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                        Text("Make Rally yours", color = RallyTvPalette.Text, fontFamily = RallyDisplayFont, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                    }
                    SettingsButton(
                        label = "Save and Apply",
                        onClick = { if (viewModel.saveConfiguration()) onSaved() },
                        primary = true,
                        modifier = Modifier.focusRequester(firstFocus)
                    )
                }
                error?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(it, color = RallyTvPalette.Live, fontFamily = RallyBodyFont, fontSize = 13.sp)
                }
                Spacer(Modifier.height(30.dp))
                SourcesSettings(
                    iptvProvider = iptvProvider,
                    portalUrl = portalUrl,
                    macAddress = macAddress,
                    xtreamServerUrl = xtreamServerUrl,
                    xtreamUsername = xtreamUsername,
                    xtreamPassword = xtreamPassword,
                    serialNumber = serialNumber,
                    deviceId = deviceId,
                    addonUrls = addonUrls,
                    newAddonUrl = newAddonUrl,
                    onPortalChange = viewModel::updatePortalUrl,
                    onMacChange = viewModel::updateMacAddress,
                    onProviderChange = viewModel::updateIptvProvider,
                    onXtreamServerChange = viewModel::updateXtreamServerUrl,
                    onXtreamUsernameChange = viewModel::updateXtreamUsername,
                    onXtreamPasswordChange = viewModel::updateXtreamPassword,
                    onSerialChange = viewModel::updateSerialNumber,
                    onDeviceChange = viewModel::updateDeviceId,
                    onNewAddonChange = viewModel::updateNewAddonUrl,
                    onAddAddon = viewModel::addStremioAddon,
                    onRemoveAddon = viewModel::removeStremioAddon,
                    onResetAddons = viewModel::resetStremioAddons,
                    diagnostics = providerDiagnostics,
                    onRunDiagnostics = viewModel::runProviderDiagnostics,
                    onClearDiagnostics = viewModel::clearLocalDiagnostics
                )
                SportsSettings(
                    sportsOrder = sportsOrder,
                    enabledLeagues = enabledLeagues,
                    favoriteSports = favoriteSports,
                    onToggleEnabled = viewModel::toggleLeague,
                    onToggleFavorite = viewModel::toggleFavoriteSport,
                    onMoveUp = viewModel::moveSportUp,
                    onMoveDown = viewModel::moveSportDown
                )
                TeamsSettings(
                    favoriteTeams = favoriteTeams,
                    onToggleTeam = viewModel::toggleFavoriteTeam,
                    onAddTeam = viewModel::addFavoriteTeam
                )
                AlertsSettings(
                    liveGameAlertsEnabled = liveGameAlertsEnabled,
                    redZoneAlertsEnabled = redZoneAlertsEnabled,
                    onToggleLiveGameAlerts = viewModel::toggleLiveGameAlerts,
                    onToggleRedZoneAlerts = viewModel::toggleRedZoneAlerts
                )
                ViewingSettings(
                    lowLatencyMode = lowLatencyMode,
                    reducedMotion = reducedMotion,
                    highContrastFocus = highContrastFocus,
                    largeText = largeText,
                    spokenScoreSummaries = spokenScoreSummaries,
                    scoreSaverEnabled = scoreSaverEnabled,
                    audioNormalizationEnabled = audioNormalizationEnabled,
                    adaptiveQualityEnabled = adaptiveQualityEnabled,
                    onToggleLowLatency = viewModel::toggleLowLatencyMode,
                    onToggleReducedMotion = viewModel::toggleReducedMotion,
                    onToggleHighContrast = viewModel::toggleHighContrastFocus,
                    onToggleLargeText = viewModel::toggleLargeText,
                    onToggleSpokenScores = viewModel::toggleSpokenScoreSummaries,
                    onToggleScoreSaver = viewModel::toggleScoreSaver,
                    onToggleAudioNormalization = viewModel::toggleAudioNormalization,
                    onToggleAdaptiveQuality = viewModel::toggleAdaptiveQuality
                )
                SupportSettings(
                    diagnostics = providerDiagnostics,
                    message = supportMessage,
                    updateState = updateState,
                    onRunDiagnostics = viewModel::runProviderDiagnostics,
                    onExportDiagnostics = { diagnosticsExport.launch("rally-support-${BuildConfig.VERSION_NAME}.txt") },
                    onClearDiagnostics = viewModel::clearLocalDiagnostics,
                    onExportPreferences = { preferencesExport.launch("rally-preferences.json") },
                    onImportPreferences = { preferencesImport.launch(arrayOf("application/json", "text/plain")) },
                    onCheckForUpdates = viewModel::checkForUpdates,
                    onDownloadUpdate = viewModel::downloadUpdate,
                    onInstallUpdate = viewModel::installUpdate,
                    onOpenPrivacy = { openExternalPage(context, "https://github.com/shivpatell25/rally/blob/main/PRIVACY.md") },
                    onOpenReleases = { openExternalPage(context, "https://github.com/shivpatell25/rally/releases") }
                )
                Spacer(Modifier.height(6.dp))
                SettingsButton(
                    label = "Save and Apply",
                    onClick = { if (viewModel.saveConfiguration()) onSaved() },
                    primary = true
                )
                Spacer(Modifier.height(60.dp))
            }
        }
    }
}

@Composable
private fun ViewingSettings(
    lowLatencyMode: Boolean,
    reducedMotion: Boolean,
    highContrastFocus: Boolean,
    largeText: Boolean,
    spokenScoreSummaries: Boolean,
    scoreSaverEnabled: Boolean,
    audioNormalizationEnabled: Boolean,
    adaptiveQualityEnabled: Boolean,
    onToggleLowLatency: () -> Unit,
    onToggleReducedMotion: () -> Unit,
    onToggleHighContrast: () -> Unit,
    onToggleLargeText: () -> Unit,
    onToggleSpokenScores: () -> Unit,
    onToggleScoreSaver: () -> Unit,
    onToggleAudioNormalization: () -> Unit,
    onToggleAdaptiveQuality: () -> Unit
) {
    SettingsPage("Viewing", "Tune playback, accessibility, and the idle TV experience.") {
        SettingsPanel("Playback", "Designed for live sports on TV hardware") {
            ViewingToggle("Low-latency live playback", "Keeps live streams closer to the broadcast while retaining a safe buffer.", lowLatencyMode, onToggleLowLatency)
            ViewingToggle("Adaptive stream quality", "Steps down before a high-bitrate stream can stall, then restores quality after the connection stabilizes.", adaptiveQualityEnabled, onToggleAdaptiveQuality)
            ViewingToggle("Normalize broadcast audio", "Reduces abrupt volume changes between broadcasts while preserving crowd and commentary detail.", audioNormalizationEnabled, onToggleAudioNormalization)
        }
        Spacer(Modifier.height(16.dp))
        SettingsPanel("Accessibility", "Comfortable navigation from across the room") {
            ViewingToggle("Reduce motion", "Removes nonessential focus and background animation.", reducedMotion, onToggleReducedMotion)
            ViewingToggle("High-contrast focus", "Uses a brighter, thicker outline on the selected control.", highContrastFocus, onToggleHighContrast)
            ViewingToggle("Larger interface text", "Increases text size throughout Rally.", largeText, onToggleLargeText)
            ViewingToggle("Spoken score summaries", "Adds complete screen-reader descriptions to live game cards.", spokenScoreSummaries, onToggleSpokenScores)
        }
        Spacer(Modifier.height(16.dp))
        SettingsPanel("Idle display", "A quiet, TV-safe score view after five minutes") {
            ViewingToggle("Score saver", "Shows current scores and upcoming games instead of a static screen.", scoreSaverEnabled, onToggleScoreSaver)
        }
    }
}

@Composable
private fun ViewingToggle(title: String, description: String, enabled: Boolean, onToggle: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }
            .rallyTvFocus(focused).clip(RoundedCornerShape(4.dp))
            .background(if (focused) RallyTvPalette.FocusSurface else Color.Transparent)
            .clickable(onClick = onToggle).focusable()
            .padding(horizontal = 12.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = RallyTvPalette.Text, fontFamily = RallyBodyFont, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(description, color = RallyTvPalette.Muted, fontFamily = RallyBodyFont, fontSize = 12.sp, lineHeight = 17.sp)
        }
        Spacer(Modifier.width(18.dp))
        Text(if (enabled) "ON" else "OFF", color = if (enabled) RallyTvPalette.Accent else RallyTvPalette.Subtle, fontFamily = RallyBodyFont, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun AlertsSettings(
    liveGameAlertsEnabled: Boolean,
    redZoneAlertsEnabled: Boolean,
    onToggleLiveGameAlerts: () -> Unit,
    onToggleRedZoneAlerts: () -> Unit
) {
    SettingsPage("Live Alerts", "Choose which sports moments can interrupt your TV experience.") {
        SettingsPanel("Favorite team alerts", "Kickoff, scores, close games, overtime and finals") {
            ViewingToggle("Game updates", "Notify when a favorite team is playing.", liveGameAlertsEnabled, onToggleLiveGameAlerts)
        }
        SettingsPanel("NFL RedZone", "Notify when the dedicated RedZone feed goes live") {
            ViewingToggle("RedZone alerts", "Follow the dedicated live feed.", redZoneAlertsEnabled, onToggleRedZoneAlerts)
        }
    }
}

@Composable
private fun SupportSettings(
    diagnostics: ProviderDiagnosticsState,
    message: String?,
    updateState: RallyUpdateState,
    onRunDiagnostics: () -> Unit,
    onExportDiagnostics: () -> Unit,
    onClearDiagnostics: () -> Unit,
    onExportPreferences: () -> Unit,
    onImportPreferences: () -> Unit,
    onCheckForUpdates: () -> Unit,
    onDownloadUpdate: () -> Unit,
    onInstallUpdate: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenReleases: () -> Unit
) {
    SettingsPage("Support", "Private diagnostics, portable preferences, and release information.") {
        SettingsPanel("Rally for Android TV", "Version ${BuildConfig.VERSION_NAME} · Build ${BuildConfig.VERSION_CODE}") {
            Text(
                "Sports, kept simple. Rally combines public sports data with sources you configure and control.",
                color = RallyTvPalette.Muted,
                fontFamily = RallyBodyFont,
                fontSize = 13.sp,
                lineHeight = 19.sp
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                SettingsButton("Release notes", onOpenReleases)
                SettingsButton("Privacy policy", onOpenPrivacy)
            }
        }

        Spacer(Modifier.height(16.dp))
        SettingsPanel("Software updates", "Signed releases from Rally's official GitHub repository") {
            val updateCopy = when (updateState) {
                RallyUpdateState.Idle -> "Check GitHub Releases for a newer signed build."
                RallyUpdateState.Checking -> "Checking GitHub Releases…"
                is RallyUpdateState.UpToDate -> "Rally ${updateState.version} is up to date."
                is RallyUpdateState.Available -> "${updateState.release.title} is available · ${formatFileSize(updateState.release.assetSize)}"
                is RallyUpdateState.Downloading -> "Downloading ${updateState.release.tag} · ${updateState.progress}%"
                is RallyUpdateState.Ready -> "${updateState.release.tag} is downloaded and its Rally signature is verified."
                is RallyUpdateState.PermissionRequired -> "Allow Rally to install unknown apps, then select Install update again."
                is RallyUpdateState.Error -> updateState.message
            }
            Text(updateCopy, color = RallyTvPalette.Muted, fontFamily = RallyBodyFont, fontSize = 12.sp, lineHeight = 17.sp)
            if (updateState is RallyUpdateState.Available && updateState.release.notes.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    updateState.release.notes.lineSequence().take(3).joinToString(" "),
                    color = RallyTvPalette.Muted,
                    fontFamily = RallyBodyFont,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(12.dp))
            when (updateState) {
                is RallyUpdateState.Available -> SettingsButton("Download update", onDownloadUpdate, primary = true)
                is RallyUpdateState.Ready, is RallyUpdateState.PermissionRequired -> SettingsButton("Install update", onInstallUpdate, primary = true)
                is RallyUpdateState.Downloading -> SettingsButton("Downloading…", {}, enabled = false)
                RallyUpdateState.Checking -> SettingsButton("Checking…", {}, enabled = false)
                else -> SettingsButton("Check for updates", onCheckForUpdates, primary = true)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Rally never installs silently. Android's system installer always asks for confirmation.",
                color = RallyTvPalette.Muted,
                fontFamily = RallyBodyFont,
                fontSize = 12.sp
            )
        }

        Spacer(Modifier.height(16.dp))
        SettingsPanel("Support report", "Stored locally and scrubbed before export") {
            Text(
                "The report includes app, device, playback recovery, and source-ranking events. Stream URLs, credentials, tokens, MAC addresses, and device IDs are redacted.",
                color = RallyTvPalette.Muted,
                fontFamily = RallyBodyFont,
                fontSize = 12.sp,
                lineHeight = 18.sp
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                SettingsButton(if (diagnostics.running) "Checking…" else "Run checks", onRunDiagnostics, enabled = !diagnostics.running)
                SettingsButton("Export report", onExportDiagnostics, primary = true)
                SettingsButton("Clear report", onClearDiagnostics, danger = true)
            }
            diagnostics.lastLocalIssue?.let {
                Spacer(Modifier.height(10.dp))
                DiagnosticSettingRow("Most recent issue", it)
            }
        }

        Spacer(Modifier.height(16.dp))
        SettingsPanel("Preferences backup", "Moves personalization without copying provider credentials") {
            Text(
                "Backups include sports order, favorites, alerts, playback preferences, and accessibility settings. IPTV credentials and addon addresses stay on this TV.",
                color = RallyTvPalette.Muted,
                fontFamily = RallyBodyFont,
                fontSize = 12.sp,
                lineHeight = 18.sp
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                SettingsButton("Export preferences", onExportPreferences, primary = true)
                SettingsButton("Import preferences", onImportPreferences)
            }
        }

        Spacer(Modifier.height(16.dp))
        SettingsPanel("Content and providers", "Rally does not include or sell television service") {
            Text(
                "Schedules and statistics come from public sports feeds. IPTV portals and Stremio addons are optional user-configured services. Use only sources and subscriptions you are authorized to access.",
                color = RallyTvPalette.Muted,
                fontFamily = RallyBodyFont,
                fontSize = 12.sp,
                lineHeight = 18.sp
            )
        }

        message?.let {
            Spacer(Modifier.height(14.dp))
            Text(it, color = RallyTvPalette.Accent, fontFamily = RallyBodyFont, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun SettingsPage(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        RallyTvRule()
        Spacer(Modifier.height(22.dp))
        Text(title.uppercase(), color = RallyTvPalette.Accent, fontFamily = RallyBodyFont, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
        Spacer(Modifier.height(5.dp))
        Text(subtitle, color = RallyTvPalette.Muted, fontFamily = RallyBodyFont, fontSize = 13.sp)
        Spacer(Modifier.height(22.dp))
        content()
        Spacer(Modifier.height(36.dp))
    }
}

@Composable
private fun SourcesSettings(
    iptvProvider: IptvProvider,
    portalUrl: String,
    macAddress: String,
    xtreamServerUrl: String,
    xtreamUsername: String,
    xtreamPassword: String,
    serialNumber: String,
    deviceId: String,
    addonUrls: List<String>,
    newAddonUrl: String,
    onPortalChange: (String) -> Unit,
    onMacChange: (String) -> Unit,
    onProviderChange: (IptvProvider) -> Unit,
    onXtreamServerChange: (String) -> Unit,
    onXtreamUsernameChange: (String) -> Unit,
    onXtreamPasswordChange: (String) -> Unit,
    onSerialChange: (String) -> Unit,
    onDeviceChange: (String) -> Unit,
    onNewAddonChange: (String) -> Unit,
    onAddAddon: (String?) -> Unit,
    onRemoveAddon: (String) -> Unit,
    onResetAddons: () -> Unit,
    diagnostics: ProviderDiagnosticsState,
    onRunDiagnostics: () -> Unit,
    onClearDiagnostics: () -> Unit
) {
    SettingsPage("Sources", "Connect the services you are authorized to use.") {
        SettingsPanel("IPTV provider", "Choose the middleware used by your authorized subscription") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SettingsButton("Stalker / Ministra", { onProviderChange(IptvProvider.STALKER) }, selected = iptvProvider == IptvProvider.STALKER, modifier = Modifier.weight(1f))
                SettingsButton("Xtream Codes", { onProviderChange(IptvProvider.XTREAM) }, selected = iptvProvider == IptvProvider.XTREAM, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(16.dp))
            if (iptvProvider == IptvProvider.XTREAM) {
                SettingsField(xtreamServerUrl, onXtreamServerChange, "Server URL", KeyboardType.Uri)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SettingsField(xtreamUsername, onXtreamUsernameChange, "Username", modifier = Modifier.weight(1f))
                    SettingsField(xtreamPassword, onXtreamPasswordChange, "Password", modifier = Modifier.weight(1f), password = true)
                }
                Spacer(Modifier.height(9.dp))
                Text("Use the provider's server address only, for example https://provider.example:8080.", color = RallyTvPalette.Muted, fontFamily = RallyBodyFont, fontSize = 12.sp)
            } else {
                SettingsField(portalUrl, onPortalChange, "Portal URL", KeyboardType.Uri)
                if (portalUrl.startsWith("http://", true)) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "This provider uses an unencrypted connection. Prefer HTTPS when available.",
                        color = Color(0xFFFFC783),
                        fontFamily = RallyBodyFont,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                Spacer(Modifier.height(12.dp))
                SettingsField(macAddress, onMacChange, "MAC address")
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SettingsField(serialNumber, onSerialChange, "Serial number (optional)", modifier = Modifier.weight(1f))
                    SettingsField(deviceId, onDeviceChange, "Device ID (optional)", modifier = Modifier.weight(1f))
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        SettingsPanel("Stremio Addons", "Manifest URLs used to discover event streams") {
            addonUrls.forEach { url ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(addonDisplayName(url), color = RallyTvPalette.Text, fontFamily = RallyBodyFont, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(2.dp))
                        Text(url, color = RallyTvPalette.Muted, fontFamily = RallyBodyFont, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    SettingsButton("Remove", { onRemoveAddon(url) }, danger = true)
                }
                RallyTvRule()
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SettingsField(newAddonUrl, onNewAddonChange, "Addon manifest URL", KeyboardType.Uri, Modifier.weight(1f))
                SettingsButton("Add", { onAddAddon(null) })
            }
            Spacer(Modifier.height(10.dp))
            SettingsButton("Clear Addons", onResetAddons)
        }

        Spacer(Modifier.height(18.dp))
        SettingsPanel("Connection diagnostics", "Private checks run locally on this TV") {
            SettingsButton(if (diagnostics.running) "Checking…" else "Run checks", onRunDiagnostics, enabled = !diagnostics.running)
            diagnostics.portalResult?.let { result ->
                Spacer(Modifier.height(10.dp))
                DiagnosticSettingRow("TV provider", result)
            }
            diagnostics.addonResults.forEach { (name, result) -> DiagnosticSettingRow(name, result) }
            diagnostics.lastLocalIssue?.let { issue ->
                Spacer(Modifier.height(8.dp))
                DiagnosticSettingRow("Last app issue", issue)
                Spacer(Modifier.height(8.dp))
                SettingsButton("Clear local report", onClearDiagnostics)
            }
        }
    }
}

@Composable
private fun DiagnosticSettingRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = RallyTvPalette.Muted, fontFamily = RallyBodyFont, fontSize = 12.sp)
        Spacer(Modifier.width(16.dp))
        Text(value, color = RallyTvPalette.Text, fontFamily = RallyBodyFont, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SportsSettings(
    sportsOrder: List<String>,
    enabledLeagues: Set<String>,
    favoriteSports: Set<String>,
    onToggleEnabled: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onMoveUp: (String) -> Unit,
    onMoveDown: (String) -> Unit
) {
    SettingsPage("Sports", "Choose what appears on Home and arrange shelf priority. Keep at least one sport enabled.") {
        sportsOrder.forEachIndexed { index, sport ->
            val allEnabled = enabledLeagues.isEmpty()
            val enabled = allEnabled || sport in enabledLeagues
            val favorite = sport in favoriteSports
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("${index + 1}", color = RallyTvPalette.Subtle, fontFamily = RallyBodyFont, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(30.dp))
                Column(Modifier.weight(1f)) {
                    Text(formatLeagueDisplayName(sport), color = RallyTvPalette.Text, fontFamily = RallyBodyFont, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(if (enabled) "Shown on Home" else "Hidden from Home", color = RallyTvPalette.Muted, fontFamily = RallyBodyFont, fontSize = 12.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    SettingsButton(if (favorite) "Favorited" else "Favorite", { onToggleFavorite(sport) }, selected = favorite)
                    SettingsButton(if (enabled) "Enabled" else "Hidden", { onToggleEnabled(sport) }, selected = enabled)
                    SettingsButton("↑", { onMoveUp(sport) }, enabled = index > 0)
                    SettingsButton("↓", { onMoveDown(sport) }, enabled = index < sportsOrder.lastIndex)
                }
            }
            RallyTvRule()
        }
        if (enabledLeagues.isEmpty()) {
            Text("All leagues are currently enabled. Toggling a league starts a custom selection.", color = RallyTvPalette.Muted, fontFamily = RallyBodyFont, fontSize = 12.sp)
        }
    }
}

@Composable
private fun TeamsSettings(
    favoriteTeams: Set<String>,
    onToggleTeam: (String) -> Unit,
    onAddTeam: (String) -> Unit
) {
    var customTeam by remember { mutableStateOf("") }
    var expandedLeague by remember { mutableStateOf<String?>(null) }
    val catalogs = remember { teamCatalogs() }

    SettingsPage("My Rally", "Follow teams to personalize your game center.") {
        SettingsPanel("Following", "${favoriteTeams.size} selected") {
            if (favoriteTeams.isEmpty()) {
                Text("You are not following any teams yet.", color = RallyTvPalette.Muted, fontFamily = RallyBodyFont, fontSize = 13.sp)
            } else {
                favoriteTeams.toList().sorted().chunked(4).forEach { rowTeams ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rowTeams.forEach { team ->
                            SettingsButton("$team  ×", { onToggleTeam(team) }, selected = true, modifier = Modifier.weight(1f))
                        }
                        repeat(4 - rowTeams.size) { Spacer(Modifier.weight(1f)) }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SettingsField(customTeam, { customTeam = it }, "Add any team", modifier = Modifier.weight(1f))
                SettingsButton("Add", {
                    if (customTeam.isNotBlank()) {
                        onAddTeam(customTeam)
                        customTeam = ""
                    }
                })
            }
        }

        Spacer(Modifier.height(18.dp))
        catalogs.forEach { (league, teams) ->
            val expanded = expandedLeague == league
            val selectedCount = teams.count { it in favoriteTeams }
            Column(Modifier.fillMaxWidth()) {
                var focused by remember(league) { mutableStateOf(false) }
                Row(
                    Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }
                        .rallyTvFocus(focused).clip(RoundedCornerShape(4.dp))
                        .background(if (focused) RallyTvPalette.FocusSurface else Color.Transparent)
                        .clickable { expandedLeague = if (expanded) null else league }.focusable()
                        .padding(horizontal = 12.dp, vertical = 13.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(league, color = RallyTvPalette.Text, fontFamily = RallyBodyFont, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(if (expanded) "CLOSE" else "$selectedCount SELECTED  ›", color = if (focused) RallyTvPalette.Accent else RallyTvPalette.Muted, fontFamily = RallyBodyFont, fontSize = 12.sp)
                }
                if (expanded) {
                    Spacer(Modifier.height(10.dp))
                    teams.chunked(4).forEach { rowTeams ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            rowTeams.forEach { team ->
                                SettingsButton(
                                    if (team in favoriteTeams) "✓ $team" else team,
                                    { onToggleTeam(team) },
                                    selected = team in favoriteTeams,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            repeat(4 - rowTeams.size) { Spacer(Modifier.weight(1f)) }
                        }
                        Spacer(Modifier.height(7.dp))
                    }
                }
            }
            RallyTvRule()
        }
    }
}

@Composable
private fun SettingsPanel(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = 22.dp)) {
        Text(title, color = RallyTvPalette.Text, fontFamily = RallyBodyFont, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Text(subtitle, color = RallyTvPalette.Muted, fontFamily = RallyBodyFont, fontSize = 12.sp, lineHeight = 17.sp)
        Spacer(Modifier.height(11.dp))
        content()
    }
}

@Composable
private fun SettingsField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    modifier: Modifier = Modifier.fillMaxWidth(),
    password: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { androidx.compose.material3.Text(label) },
        singleLine = true,
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = settingsFieldColors(),
        shape = RoundedCornerShape(4.dp),
        modifier = modifier.height(58.dp)
    )
}

@Composable
private fun SettingsButton(
    label: String,
    onClick: () -> Unit,
    primary: Boolean = false,
    selected: Boolean = false,
    danger: Boolean = false,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier.onFocusChanged { focused = it.isFocused }
            .rallyTvFocus(focused)
            .clip(RoundedCornerShape(4.dp))
            .background(
                when {
                    !enabled -> Color.Transparent
                    primary -> RallyTvPalette.Accent
                    focused -> RallyTvPalette.FocusSurface
                    selected -> RallyTvPalette.BackgroundSoft
                    else -> Color.Transparent
                }
            )
            .clickable(enabled = enabled, onClick = onClick)
            .focusable(enabled = enabled)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            color = when {
                !enabled -> RallyTvPalette.Subtle
                primary -> RallyTvPalette.Background
                danger -> RallyTvPalette.Live
                selected || focused -> RallyTvPalette.Accent
                else -> RallyTvPalette.Text
            },
            fontFamily = RallyBodyFont,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun addonDisplayName(url: String): String = when {
    url.contains("highfly", true) -> "Highfly Sports"
    url.contains("nuvio", true) -> "Nuvio Live Sports"
    else -> "Custom Addon"
}

private fun formatFileSize(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> "${bytes / (1024L * 1024L)} MB"
    bytes >= 1024L -> "${bytes / 1024L} KB"
    else -> "$bytes bytes"
}

private fun writeTextDocument(context: Context, uri: Uri, value: String): Boolean = runCatching {
    val stream = context.contentResolver.openOutputStream(uri, "wt")
        ?: error("Unable to open the selected file")
    stream.use { output -> output.bufferedWriter().use { it.write(value) } }
}.isSuccess

private fun readTextDocument(context: Context, uri: Uri): String? = runCatching {
    val stream = context.contentResolver.openInputStream(uri)
        ?: error("Unable to open the selected file")
    stream.use { input -> input.bufferedReader().use { it.readText() } }
}.getOrNull()

private fun openExternalPage(context: Context, url: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

private fun teamCatalogs(): List<Pair<String, List<String>>> = listOf(
    "NFL" to listOf("Chiefs", "Eagles", "49ers", "Cowboys", "Packers", "Bills", "Lions", "Ravens", "Dolphins", "Bengals", "Jets", "Seahawks", "Steelers", "Patriots", "Bears", "Vikings"),
    "College Football" to listOf("Alabama", "Georgia", "Ohio State", "Texas", "Michigan", "Notre Dame", "USC", "Oregon", "LSU", "Penn State", "Oklahoma", "Florida State", "Tennessee", "Clemson", "Utah", "Miami"),
    "NBA" to listOf("Lakers", "Warriors", "Celtics", "Heat", "Knicks", "Bulls", "Bucks", "Nuggets", "76ers", "Mavericks", "Suns", "Cavaliers", "Timberwolves", "Thunder", "Spurs", "Clippers"),
    "College Basketball" to listOf("Duke", "North Carolina", "Kentucky", "Kansas", "UConn", "Gonzaga", "Houston", "Arizona", "Purdue", "Michigan State", "Villanova", "Auburn"),
    "MLB" to listOf("Cubs", "Tigers", "Yankees", "Dodgers", "Red Sox", "Braves", "Phillies", "Astros", "Mets", "Padres", "Giants", "Rangers", "Blue Jays", "Mariners", "Orioles", "Cardinals"),
    "Soccer" to listOf("Real Madrid", "Barcelona", "Liverpool", "Arsenal", "Man City", "Man United", "Chelsea", "Tottenham", "Bayern Munich", "PSG", "Inter Milan", "AC Milan", "Juventus", "Atletico Madrid", "Inter Miami", "LAFC"),
    "NHL" to listOf("Rangers", "Bruins", "Maple Leafs", "Oilers", "Blackhawks", "Avalanche", "Panthers", "Golden Knights", "Lightning", "Canadiens", "Red Wings", "Canucks")
)
