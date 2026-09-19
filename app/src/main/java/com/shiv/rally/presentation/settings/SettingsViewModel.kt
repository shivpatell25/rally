package com.shiv.rally.presentation.settings

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shiv.rally.data.local.PreferencesManager
import com.shiv.rally.data.local.PortalUrlNormalizer
import com.shiv.rally.BuildConfig
import com.shiv.rally.data.update.RallyUpdateManager
import com.shiv.rally.data.update.RallyUpdateState
import com.shiv.rally.domain.model.IptvProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesManager: PreferencesManager,
    private val iptvRepository: com.shiv.rally.domain.repository.IptvRepository,
    private val preflightProbe: com.shiv.rally.data.remote.network.StreamPreflightProbe,
    private val diagnosticsStore: com.shiv.rally.data.local.RallyDiagnostics,
    private val updateManager: RallyUpdateManager
) : ViewModel() {

    private val _iptvProvider = MutableStateFlow(preferencesManager.iptvProvider)
    val iptvProvider: StateFlow<IptvProvider> = _iptvProvider.asStateFlow()

    private val _portalUrl = MutableStateFlow(preferencesManager.portalUrl)
    val portalUrl: StateFlow<String> = _portalUrl.asStateFlow()

    private val _macAddress = MutableStateFlow(preferencesManager.macAddress)
    val macAddress: StateFlow<String> = _macAddress.asStateFlow()

    private val _xtreamServerUrl = MutableStateFlow(preferencesManager.xtreamServerUrl)
    val xtreamServerUrl: StateFlow<String> = _xtreamServerUrl.asStateFlow()

    private val _xtreamUsername = MutableStateFlow(preferencesManager.xtreamUsername)
    val xtreamUsername: StateFlow<String> = _xtreamUsername.asStateFlow()

    private val _xtreamPassword = MutableStateFlow(preferencesManager.xtreamPassword)
    val xtreamPassword: StateFlow<String> = _xtreamPassword.asStateFlow()

    private val _stremioAddonUrls = MutableStateFlow(preferencesManager.stremioAddonUrls)
    val stremioAddonUrls: StateFlow<List<String>> = _stremioAddonUrls.asStateFlow()

    private val _newAddonUrl = MutableStateFlow("")
    val newAddonUrl: StateFlow<String> = _newAddonUrl.asStateFlow()

    private val _serialNumber = MutableStateFlow(preferencesManager.serialNumber)
    val serialNumber: StateFlow<String> = _serialNumber.asStateFlow()

    private val _deviceId = MutableStateFlow(preferencesManager.deviceId)
    val deviceId: StateFlow<String> = _deviceId.asStateFlow()

    private val _enabledLeagues = MutableStateFlow(preferencesManager.enabledLeagues)
    val enabledLeagues: StateFlow<Set<String>> = _enabledLeagues.asStateFlow()

    private val _favoriteSports = MutableStateFlow(preferencesManager.favoriteSports)
    val favoriteSports: StateFlow<Set<String>> = _favoriteSports.asStateFlow()

    private val _favoriteTeams = MutableStateFlow(preferencesManager.favoriteTeams)
    val favoriteTeams: StateFlow<Set<String>> = _favoriteTeams.asStateFlow()

    private val _sportsOrder = MutableStateFlow(preferencesManager.sportsOrder)
    val sportsOrder: StateFlow<List<String>> = _sportsOrder.asStateFlow()

    private val _liveGameAlertsEnabled = MutableStateFlow(preferencesManager.liveGameAlertsEnabled)
    val liveGameAlertsEnabled: StateFlow<Boolean> = _liveGameAlertsEnabled.asStateFlow()

    private val _redZoneAlertsEnabled = MutableStateFlow(preferencesManager.redZoneAlertsEnabled)
    val redZoneAlertsEnabled: StateFlow<Boolean> = _redZoneAlertsEnabled.asStateFlow()

    private val _lowLatencyMode = MutableStateFlow(preferencesManager.lowLatencyMode)
    val lowLatencyMode: StateFlow<Boolean> = _lowLatencyMode.asStateFlow()
    private val _reducedMotion = MutableStateFlow(preferencesManager.reducedMotion)
    val reducedMotion: StateFlow<Boolean> = _reducedMotion.asStateFlow()
    private val _highContrastFocus = MutableStateFlow(preferencesManager.highContrastFocus)
    val highContrastFocus: StateFlow<Boolean> = _highContrastFocus.asStateFlow()
    private val _largeText = MutableStateFlow(preferencesManager.largeText)
    val largeText: StateFlow<Boolean> = _largeText.asStateFlow()
    private val _spokenScoreSummaries = MutableStateFlow(preferencesManager.spokenScoreSummaries)
    val spokenScoreSummaries: StateFlow<Boolean> = _spokenScoreSummaries.asStateFlow()
    private val _scoreSaverEnabled = MutableStateFlow(preferencesManager.scoreSaverEnabled)
    val scoreSaverEnabled: StateFlow<Boolean> = _scoreSaverEnabled.asStateFlow()
    private val _audioNormalizationEnabled = MutableStateFlow(preferencesManager.audioNormalizationEnabled)
    val audioNormalizationEnabled: StateFlow<Boolean> = _audioNormalizationEnabled.asStateFlow()
    private val _adaptiveQualityEnabled = MutableStateFlow(preferencesManager.adaptiveQualityEnabled)
    val adaptiveQualityEnabled: StateFlow<Boolean> = _adaptiveQualityEnabled.asStateFlow()

    private val _providerDiagnostics = MutableStateFlow(ProviderDiagnosticsState())
    val providerDiagnostics: StateFlow<ProviderDiagnosticsState> = _providerDiagnostics.asStateFlow()

    private val _configurationError = MutableStateFlow<String?>(null)
    val configurationError: StateFlow<String?> = _configurationError.asStateFlow()

    private val _supportMessage = MutableStateFlow<String?>(null)
    val supportMessage: StateFlow<String?> = _supportMessage.asStateFlow()

    private val _updateState = MutableStateFlow<RallyUpdateState>(RallyUpdateState.Idle)
    val updateState: StateFlow<RallyUpdateState> = _updateState.asStateFlow()
    
    fun updatePortalUrl(url: String) {
        _portalUrl.value = url
        _configurationError.value = null
    }

    fun updateIptvProvider(provider: IptvProvider) {
        _iptvProvider.value = provider
        _configurationError.value = null
    }
    
    fun updateMacAddress(mac: String) {
        _macAddress.value = mac
    }

    fun updateXtreamServerUrl(url: String) {
        _xtreamServerUrl.value = url
        _configurationError.value = null
    }

    fun updateXtreamUsername(username: String) {
        _xtreamUsername.value = username
        _configurationError.value = null
    }

    fun updateXtreamPassword(password: String) {
        _xtreamPassword.value = password
        _configurationError.value = null
    }

    fun updateNewAddonUrl(url: String) {
        _newAddonUrl.value = url
    }

    fun addStremioAddon(url: String? = null) {
        val target = (url ?: _newAddonUrl.value).trim()
        if (target.isNotEmpty()) {
            val current = _stremioAddonUrls.value.toMutableList()
            if (target !in current) {
                current.add(target)
                _stremioAddonUrls.value = current
            }
            _newAddonUrl.value = ""
        }
    }

    fun removeStremioAddon(url: String) {
        val current = _stremioAddonUrls.value.toMutableList()
        current.remove(url.trim())
        _stremioAddonUrls.value = current
    }

    fun resetStremioAddons() {
        _stremioAddonUrls.value = emptyList()
    }

    fun updateSerialNumber(sn: String) {
        _serialNumber.value = sn
    }

    fun updateDeviceId(id: String) {
        _deviceId.value = id
    }

    fun toggleLeague(league: String) {
        // An empty persisted set means "all leagues". Materialize that state before
        // disabling one item so the first toggle does not accidentally hide every
        // other league.
        val current = if (_enabledLeagues.value.isEmpty()) {
            _sportsOrder.value.toMutableSet()
        } else {
            _enabledLeagues.value.toMutableSet()
        }
        if (current.contains(league)) {
            current.remove(league)
        } else {
            current.add(league)
        }
        _enabledLeagues.value = current
    }

    fun toggleFavoriteSport(sport: String) {
        val current = _favoriteSports.value.toMutableSet()
        if (current.contains(sport)) current.remove(sport) else current.add(sport)
        _favoriteSports.value = current
    }

    fun toggleFavoriteTeam(team: String) {
        val current = _favoriteTeams.value.toMutableSet()
        if (current.contains(team)) current.remove(team) else current.add(team)
        _favoriteTeams.value = current
    }

    fun addFavoriteTeam(teamName: String) {
        val trimmed = teamName.trim()
        if (trimmed.isNotEmpty()) {
            val current = _favoriteTeams.value.toMutableSet()
            current.add(trimmed)
            _favoriteTeams.value = current
        }
    }

    fun moveSportUp(sport: String) {
        val list = _sportsOrder.value.toMutableList()
        val idx = list.indexOf(sport)
        if (idx > 0) {
            val item = list.removeAt(idx)
            list.add(idx - 1, item)
            _sportsOrder.value = list
            // Home reads this preference directly, so persist reordering as soon as
            // it happens instead of waiting for the rest of Settings to be saved.
            preferencesManager.sportsOrder = list
        }
    }

    fun moveSportDown(sport: String) {
        val list = _sportsOrder.value.toMutableList()
        val idx = list.indexOf(sport)
        if (idx in 0 until list.size - 1) {
            val item = list.removeAt(idx)
            list.add(idx + 1, item)
            _sportsOrder.value = list
            preferencesManager.sportsOrder = list
        }
    }

    fun toggleLiveGameAlerts() {
        _liveGameAlertsEnabled.value = !_liveGameAlertsEnabled.value
    }

    fun toggleRedZoneAlerts() {
        _redZoneAlertsEnabled.value = !_redZoneAlertsEnabled.value
    }

    fun toggleLowLatencyMode() { _lowLatencyMode.value = !_lowLatencyMode.value }
    fun toggleReducedMotion() { _reducedMotion.value = !_reducedMotion.value }
    fun toggleHighContrastFocus() { _highContrastFocus.value = !_highContrastFocus.value }
    fun toggleLargeText() { _largeText.value = !_largeText.value }
    fun toggleSpokenScoreSummaries() { _spokenScoreSummaries.value = !_spokenScoreSummaries.value }
    fun toggleScoreSaver() { _scoreSaverEnabled.value = !_scoreSaverEnabled.value }
    fun toggleAudioNormalization() { _audioNormalizationEnabled.value = !_audioNormalizationEnabled.value }
    fun toggleAdaptiveQuality() { _adaptiveQualityEnabled.value = !_adaptiveQualityEnabled.value }

    fun runProviderDiagnostics() {
        if (_providerDiagnostics.value.running) return
        _providerDiagnostics.value = ProviderDiagnosticsState(running = true, lastLocalIssue = diagnosticsStore.read().lastOrNull()?.message)
        viewModelScope.launch(Dispatchers.IO) {
            val portal = async {
                if (_portalUrl.value.isBlank()) "Not configured" else runCatching {
                    if (!iptvRepository.authenticate()) "Sign-in failed"
                    else {
                        val count = iptvRepository.getChannels().size
                        if (count > 0) "Ready · $count channels" else "Connected · no channels returned"
                    }
                }.getOrElse { "Unavailable · ${it.javaClass.simpleName}" }
            }
            val addons = _stremioAddonUrls.value.map { url ->
                async {
                    val result = preflightProbe.probe(url)
                    addonDisplayNameForDiagnostics(url) to if (result.passed) "Ready · ${result.latencyMs} ms" else result.detail
                }
            }
            _providerDiagnostics.value = ProviderDiagnosticsState(
                running = false,
                portalResult = portal.await(),
                addonResults = addons.map { it.await() },
                lastLocalIssue = diagnosticsStore.read().lastOrNull()?.message
            )
        }
    }

    fun clearLocalDiagnostics() {
        diagnosticsStore.clear()
        _providerDiagnostics.value = _providerDiagnostics.value.copy(lastLocalIssue = null)
        _supportMessage.value = "Local diagnostics cleared."
    }

    fun exportDiagnostics(): String = diagnosticsStore.exportReport(
        appVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
        deviceSummary = "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} · API ${Build.VERSION.SDK_INT}"
    )

    fun exportPreferences(): String = preferencesManager.exportPersonalization()

    fun importPreferences(raw: String) {
        preferencesManager.importPersonalization(raw)
            .onSuccess {
                refreshPersonalization()
                _supportMessage.value = "Preferences restored. Provider sign-in was not changed."
            }
            .onFailure {
                _supportMessage.value = it.message ?: "This Rally backup could not be imported."
            }
    }

    fun reportExportResult(label: String, succeeded: Boolean) {
        _supportMessage.value = if (succeeded) "$label saved." else "$label could not be saved."
    }

    fun checkForUpdates() {
        if (_updateState.value is RallyUpdateState.Checking || _updateState.value is RallyUpdateState.Downloading) return
        _updateState.value = RallyUpdateState.Checking
        viewModelScope.launch(Dispatchers.IO) {
            _updateState.value = updateManager.check()
        }
    }

    fun downloadUpdate() {
        val release = (_updateState.value as? RallyUpdateState.Available)?.release ?: return
        _updateState.value = RallyUpdateState.Downloading(release, 0)
        viewModelScope.launch(Dispatchers.IO) {
            _updateState.value = updateManager.download(release) { progress ->
                _updateState.value = RallyUpdateState.Downloading(release, progress)
            }
        }
    }

    fun installUpdate() {
        val current = _updateState.value
        val ready = when (current) {
            is RallyUpdateState.Ready -> current.release to current.file
            is RallyUpdateState.PermissionRequired -> current.release to current.file
            else -> return
        }
        _updateState.value = updateManager.install(ready.first, ready.second)
    }

    private fun refreshPersonalization() {
        _enabledLeagues.value = preferencesManager.enabledLeagues
        _favoriteSports.value = preferencesManager.favoriteSports
        _favoriteTeams.value = preferencesManager.favoriteTeams
        _sportsOrder.value = preferencesManager.sportsOrder
        _liveGameAlertsEnabled.value = preferencesManager.liveGameAlertsEnabled
        _redZoneAlertsEnabled.value = preferencesManager.redZoneAlertsEnabled
        _lowLatencyMode.value = preferencesManager.lowLatencyMode
        _audioNormalizationEnabled.value = preferencesManager.audioNormalizationEnabled
        _adaptiveQualityEnabled.value = preferencesManager.adaptiveQualityEnabled
        _reducedMotion.value = preferencesManager.reducedMotion
        _highContrastFocus.value = preferencesManager.highContrastFocus
        _largeText.value = preferencesManager.largeText
        _spokenScoreSummaries.value = preferencesManager.spokenScoreSummaries
        _scoreSaverEnabled.value = preferencesManager.scoreSaverEnabled
    }

    fun saveConfiguration(): Boolean {
        val cleanUrl = PortalUrlNormalizer.normalizePortal(_portalUrl.value)
        val cleanXtreamUrl = PortalUrlNormalizer.normalizeXtreamServer(_xtreamServerUrl.value)
        if (_iptvProvider.value == IptvProvider.STALKER && _portalUrl.value.isNotBlank() && cleanUrl.isBlank()) {
            _configurationError.value = "Enter a valid IPTV portal URL."
            return false
        }
        val cleanMac = _macAddress.value.trim().uppercase()
        if (_iptvProvider.value == IptvProvider.STALKER && cleanUrl.isNotBlank() && !cleanMac.matches(Regex("^(?:[0-9A-F]{2}:){5}[0-9A-F]{2}$"))) {
            _configurationError.value = "Enter a valid MAC address using XX:XX:XX:XX:XX:XX."
            return false
        }
        if (_iptvProvider.value == IptvProvider.XTREAM) {
            if (_xtreamServerUrl.value.isNotBlank() && cleanXtreamUrl.isBlank()) {
                _configurationError.value = "Enter a valid Xtream server URL."
                return false
            }
            if (cleanXtreamUrl.isBlank() || _xtreamUsername.value.trim().isBlank() || _xtreamPassword.value.isBlank()) {
                _configurationError.value = "Enter the Xtream server URL, username, and password."
                return false
            }
        }
        if (_stremioAddonUrls.value.any { PortalUrlNormalizer.normalizeAddon(it) == null }) {
            _configurationError.value = "One or more Stremio addon URLs are invalid."
            return false
        }
        if (_newAddonUrl.value.isNotBlank()) {
            addStremioAddon()
        }
        preferencesManager.portalUrl = cleanUrl
        preferencesManager.macAddress = cleanMac
        preferencesManager.iptvProvider = _iptvProvider.value
        preferencesManager.xtreamServerUrl = cleanXtreamUrl
        preferencesManager.xtreamUsername = _xtreamUsername.value.trim()
        preferencesManager.xtreamPassword = _xtreamPassword.value
        preferencesManager.stremioAddonUrls = _stremioAddonUrls.value
        preferencesManager.serialNumber = _serialNumber.value.trim()
        preferencesManager.deviceId = _deviceId.value.trim()
        preferencesManager.enabledLeagues = _enabledLeagues.value
        preferencesManager.favoriteSports = _favoriteSports.value
        preferencesManager.favoriteTeams = _favoriteTeams.value
        preferencesManager.sportsOrder = _sportsOrder.value
        preferencesManager.liveGameAlertsEnabled = _liveGameAlertsEnabled.value
        preferencesManager.redZoneAlertsEnabled = _redZoneAlertsEnabled.value
        preferencesManager.lowLatencyMode = _lowLatencyMode.value
        preferencesManager.audioNormalizationEnabled = _audioNormalizationEnabled.value
        preferencesManager.adaptiveQualityEnabled = _adaptiveQualityEnabled.value
        preferencesManager.reducedMotion = _reducedMotion.value
        preferencesManager.highContrastFocus = _highContrastFocus.value
        preferencesManager.largeText = _largeText.value
        preferencesManager.spokenScoreSummaries = _spokenScoreSummaries.value
        preferencesManager.scoreSaverEnabled = _scoreSaverEnabled.value
        preferencesManager.authToken = "" // Invalidate cached token so new handshake is forced
        iptvRepository.clearMemoryCache()
        preferencesManager.setupComplete = true
        _configurationError.value = null
        return true
    }
}

data class ProviderDiagnosticsState(
    val running: Boolean = false,
    val portalResult: String? = null,
    val addonResults: List<Pair<String, String>> = emptyList(),
    val lastLocalIssue: String? = null
)

private fun addonDisplayNameForDiagnostics(url: String): String = runCatching {
    java.net.URI(url).host?.removePrefix("www.") ?: "Addon"
}.getOrDefault("Addon")
