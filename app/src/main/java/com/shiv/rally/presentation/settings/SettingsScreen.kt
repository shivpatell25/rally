@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.shiv.rally.presentation.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Text
import com.shiv.rally.R
import com.shiv.rally.presentation.home.formatLeagueDisplayName
import com.shiv.rally.presentation.theme.AppleTvTheme
import kotlinx.coroutines.delay

private enum class SettingsSection(val title: String, val subtitle: String) {
    SOURCES("Sources", "IPTV and addons"),
    SPORTS("Sports", "Leagues and order"),
    TEAMS("Teams", "Favorite clubs"),
    ALERTS("Alerts", "Live notifications"),
    VIEWING("Viewing", "Playback and access")
}

private val sectionShape = RoundedCornerShape(8.dp)
private val panelShape = RoundedCornerShape(10.dp)
private val pillShape = RoundedCornerShape(8.dp)

@Composable
private fun settingsFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White.copy(alpha = .86f),
    focusedBorderColor = AppleTvTheme.RallyCyan,
    unfocusedBorderColor = Color(0x2EFFFFFF),
    focusedLabelColor = Color.White,
    unfocusedLabelColor = AppleTvTheme.TextSecondary,
    focusedContainerColor = AppleTvTheme.Graphite,
    unfocusedContainerColor = AppleTvTheme.Slate
)

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onSaved: () -> Unit,
    initialFocusRequester: FocusRequester? = null
) {
    val portalUrl by viewModel.portalUrl.collectAsStateWithLifecycle()
    val macAddress by viewModel.macAddress.collectAsStateWithLifecycle()
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

    var section by remember { mutableStateOf(SettingsSection.SOURCES) }
    val fallbackFocus = remember { FocusRequester() }
    val firstFocus = initialFocusRequester ?: fallbackFocus
    LaunchedEffect(Unit) {
        delay(120)
        runCatching { firstFocus.requestFocus() }
    }

    Row(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.width(285.dp).fillMaxHeight().background(Color(0xB80A101B)).border(1.dp, Color(0x385A7894)).padding(horizontal = 30.dp, vertical = 22.dp)
        ) {
            Text("SETTINGS", color = AppleTvTheme.TextTertiary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            Spacer(Modifier.height(10.dp))
            SettingsSection.entries.forEachIndexed { index, item ->
                val selected = section == item
                Button(
                    onClick = { section = item },
                    modifier = Modifier.fillMaxWidth().then(if (index == 0) Modifier.focusRequester(firstFocus) else Modifier),
                    shape = ButtonDefaults.shape(sectionShape),
                    scale = ButtonDefaults.scale(scale = 1f, focusedScale = AppleTvTheme.ButtonFocusScale),
                    colors = ButtonDefaults.colors(
                        containerColor = if (selected) AppleTvTheme.Graphite else Color.Transparent,
                        focusedContainerColor = AppleTvTheme.Graphite,
                        contentColor = if (selected) AppleTvTheme.RallyCyan else Color(0xA6FFFFFF),
                        focusedContentColor = AppleTvTheme.RallyCyan
                    ),
                    border = ButtonDefaults.border(
                        border = Border(border = BorderStroke(1.dp, if (selected) Color(0x24FFFFFF) else Color.Transparent), shape = sectionShape),
                        focusedBorder = Border(border = BorderStroke(2.dp, AppleTvTheme.RallyCyan), shape = sectionShape)
                    )
                ) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
                        Text(item.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text(item.subtitle, fontSize = 10.sp, color = if (selected) Color.White.copy(alpha = .56f) else Color.White.copy(alpha = .4f))
                    }
                }
                Spacer(Modifier.height(6.dp))
            }

            Spacer(Modifier.weight(1f))
            error?.let {
                Text(it, color = Color(0xFFFF6961), fontSize = 11.sp, lineHeight = 15.sp, modifier = Modifier.padding(bottom = 10.dp))
            }
            SettingsButton(
                label = "Save and Apply",
                onClick = { if (viewModel.saveConfiguration()) onSaved() },
                primary = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Box(Modifier.weight(1f).fillMaxHeight()) {
            when (section) {
                SettingsSection.SOURCES -> SourcesSettings(
                    portalUrl = portalUrl,
                    macAddress = macAddress,
                    serialNumber = serialNumber,
                    deviceId = deviceId,
                    addonUrls = addonUrls,
                    newAddonUrl = newAddonUrl,
                    onPortalChange = viewModel::updatePortalUrl,
                    onMacChange = viewModel::updateMacAddress,
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
                SettingsSection.SPORTS -> SportsSettings(
                    sportsOrder = sportsOrder,
                    enabledLeagues = enabledLeagues,
                    favoriteSports = favoriteSports,
                    onToggleEnabled = viewModel::toggleLeague,
                    onToggleFavorite = viewModel::toggleFavoriteSport,
                    onMoveUp = viewModel::moveSportUp,
                    onMoveDown = viewModel::moveSportDown
                )
                SettingsSection.TEAMS -> TeamsSettings(
                    favoriteTeams = favoriteTeams,
                    onToggleTeam = viewModel::toggleFavoriteTeam,
                    onAddTeam = viewModel::addFavoriteTeam
                )
                SettingsSection.ALERTS -> AlertsSettings(
                    liveGameAlertsEnabled = liveGameAlertsEnabled,
                    redZoneAlertsEnabled = redZoneAlertsEnabled,
                    onToggleLiveGameAlerts = viewModel::toggleLiveGameAlerts,
                    onToggleRedZoneAlerts = viewModel::toggleRedZoneAlerts
                )
                SettingsSection.VIEWING -> ViewingSettings(
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
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(description, color = AppleTvTheme.TextSecondary, fontSize = 11.sp, lineHeight = 15.sp)
        }
        Spacer(Modifier.width(18.dp))
        SettingsButton(if (enabled) "On" else "Off", onToggle, selected = enabled)
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
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Game updates", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                SettingsButton(
                    if (liveGameAlertsEnabled) "On" else "Off",
                    onToggleLiveGameAlerts,
                    selected = liveGameAlertsEnabled
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        SettingsPanel("NFL RedZone", "Notify when the dedicated RedZone feed goes live") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("RedZone alerts", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                SettingsButton(
                    if (redZoneAlertsEnabled) "On" else "Off",
                    onToggleRedZoneAlerts,
                    selected = redZoneAlertsEnabled
                )
            }
        }
    }
}

@Composable
private fun SettingsPage(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 46.dp, vertical = 30.dp).verticalScroll(rememberScrollState())
    ) {
        Text(title, color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-.7).sp)
        Spacer(Modifier.height(4.dp))
        Text(subtitle, color = AppleTvTheme.TextSecondary, fontSize = 14.sp)
        Spacer(Modifier.height(28.dp))
        content()
        Spacer(Modifier.height(60.dp))
    }
}

@Composable
private fun SourcesSettings(
    portalUrl: String,
    macAddress: String,
    serialNumber: String,
    deviceId: String,
    addonUrls: List<String>,
    newAddonUrl: String,
    onPortalChange: (String) -> Unit,
    onMacChange: (String) -> Unit,
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
        SettingsPanel("IPTV Portal", "Stalker or Ministra middleware") {
            SettingsField(portalUrl, onPortalChange, "Portal URL", KeyboardType.Uri)
            if (portalUrl.startsWith("http://", true)) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "This provider uses an unencrypted connection. Prefer HTTPS when available.",
                    color = Color(0xFFFFB340),
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFF24180A)).padding(11.dp)
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

        Spacer(Modifier.height(18.dp))
        SettingsPanel("Stremio Addons", "Manifest URLs used to discover event streams") {
            addonUrls.forEach { url ->
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(AppleTvTheme.Graphite).padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(addonDisplayName(url), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(2.dp))
                        Text(url, color = AppleTvTheme.TextTertiary, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    SettingsButton("Remove", { onRemoveAddon(url) }, danger = true)
                }
                Spacer(Modifier.height(8.dp))
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
            SettingsButton(if (diagnostics.running) "Checking…" else "Run checks", onRunDiagnostics)
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
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = AppleTvTheme.TextSecondary, fontSize = 11.sp)
        Spacer(Modifier.width(16.dp))
        Text(value, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
    SettingsPage("Sports", "Choose what appears on Home and arrange shelf priority.") {
        sportsOrder.forEachIndexed { index, sport ->
            val allEnabled = enabledLeagues.isEmpty()
            val enabled = allEnabled || sport in enabledLeagues
            val favorite = sport in favoriteSports
            Row(
                modifier = Modifier.fillMaxWidth().clip(panelShape).background(AppleTvTheme.Slate).border(1.dp, Color(0x20FFFFFF), panelShape).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("${index + 1}", color = AppleTvTheme.TextTertiary, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(30.dp))
                Column(Modifier.weight(1f)) {
                    Text(formatLeagueDisplayName(sport), color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Text(if (enabled) "Shown on Home" else "Hidden from Home", color = AppleTvTheme.TextSecondary, fontSize = 11.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    SettingsButton(if (favorite) "Favorited" else "Favorite", { onToggleFavorite(sport) }, selected = favorite)
                    SettingsButton(if (enabled) "Enabled" else "Hidden", { onToggleEnabled(sport) }, selected = enabled)
                    SettingsButton("↑", { onMoveUp(sport) }, enabled = index > 0)
                    SettingsButton("↓", { onMoveDown(sport) }, enabled = index < sportsOrder.lastIndex)
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        if (enabledLeagues.isEmpty()) {
            Text("All leagues are currently enabled. Toggling a league starts a custom selection.", color = AppleTvTheme.TextTertiary, fontSize = 11.sp)
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

    SettingsPage("Favorite Teams", "Favorite matchups are promoted in the Home spotlight.") {
        SettingsPanel("Your Teams", "${favoriteTeams.size} selected") {
            if (favoriteTeams.isEmpty()) {
                Text("No favorite teams yet.", color = AppleTvTheme.TextSecondary, fontSize = 13.sp)
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
            Column(Modifier.fillMaxWidth().clip(panelShape).background(AppleTvTheme.Slate).border(1.dp, Color(0x20FFFFFF), panelShape).padding(13.dp)) {
                Button(
                    onClick = { expandedLeague = if (expanded) null else league },
                    modifier = Modifier.fillMaxWidth(),
                    shape = ButtonDefaults.shape(sectionShape),
                    colors = ButtonDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = AppleTvTheme.Graphite, contentColor = Color.White, focusedContentColor = AppleTvTheme.RallyCyan)
                ) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 5.dp, vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(league, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(if (expanded) "Close" else "$selectedCount selected  ›", fontSize = 12.sp)
                    }
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
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun SettingsPanel(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(panelShape).background(AppleTvTheme.Slate).border(1.dp, Color(0x20F5F7FA), panelShape).padding(18.dp)) {
        Text(title, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
        Text(subtitle, color = AppleTvTheme.TextSecondary, fontSize = 11.sp)
        Spacer(Modifier.height(15.dp))
        content()
    }
}

@Composable
private fun SettingsField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    modifier: Modifier = Modifier.fillMaxWidth()
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { androidx.compose.material3.Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = settingsFieldColors(),
        shape = panelShape,
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
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.onFocusChanged { focused = it.isFocused },
        shape = ButtonDefaults.shape(pillShape),
        scale = ButtonDefaults.scale(scale = 1f, focusedScale = AppleTvTheme.ButtonFocusScale),
        colors = ButtonDefaults.colors(
            containerColor = when {
                primary -> AppleTvTheme.OffWhite
                danger -> Color(0x33FF453A)
                selected -> Color(0x30202834)
                else -> AppleTvTheme.SurfaceRaised
            },
            focusedContainerColor = if (primary) Color.White else AppleTvTheme.SurfaceFocused,
            contentColor = when {
                primary -> AppleTvTheme.DeepNavy
                danger -> Color(0xFFFF6961)
                else -> Color.White
            },
            focusedContentColor = if (primary) AppleTvTheme.DeepNavy else AppleTvTheme.RallyCyan
        )
    ) {
        Text(
            label,
            color = when {
                primary -> AppleTvTheme.DeepNavy
                focused -> AppleTvTheme.RallyCyan
                danger -> Color(0xFFFF6961)
                else -> Color.White
            },
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
        )
    }
}

private fun addonDisplayName(url: String): String = when {
    url.contains("highfly", true) -> "Highfly Sports"
    url.contains("nuvio", true) -> "Nuvio Live Sports"
    else -> "Custom Addon"
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
