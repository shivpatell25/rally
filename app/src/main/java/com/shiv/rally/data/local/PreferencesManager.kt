package com.shiv.rally.data.local

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import java.security.SecureRandom
import com.shiv.rally.domain.model.FavoriteTeam
import com.shiv.rally.domain.model.IptvProvider

@Singleton
class PreferencesManager @Inject constructor(@ApplicationContext context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences = EncryptedSharedPreferences.create(
        context,
        "rally_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    init {
        migrateLegacyPreferences(context)
        migrateSettingsSchema()
    }

    private fun migrateSettingsSchema() {
        val current = sharedPreferences.getInt("_rally_settings_schema", 0)
        if (current < 2) {
            // Versioned independently from the Room database so preference changes
            // remain safe across sideloaded beta upgrades.
            sharedPreferences.edit()
                .putInt("_rally_settings_schema", 2)
                // Existing installations remain on their working Stalker setup.
                .putString("iptv_provider", sharedPreferences.getString("iptv_provider", IptvProvider.STALKER.name))
                .apply()
        }
    }

    var iptvProvider: IptvProvider
        get() = runCatching {
            IptvProvider.valueOf(sharedPreferences.getString("iptv_provider", IptvProvider.STALKER.name).orEmpty())
        }.getOrDefault(IptvProvider.STALKER)
        set(value) = sharedPreferences.edit().putString("iptv_provider", value.name).apply()

    private fun migrateLegacyPreferences(context: Context) {
        if (sharedPreferences.getBoolean("_rally_migration_complete", false)) return
        runCatching {
            val legacy = EncryptedSharedPreferences.create(
                context,
                "spatelorts_secure_prefs",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            val editor = sharedPreferences.edit()
            legacy.all.forEach { (key, value) ->
                when (value) {
                    is String -> editor.putString(key, value)
                    is Boolean -> editor.putBoolean(key, value)
                    is Int -> editor.putInt(key, value)
                    is Long -> editor.putLong(key, value)
                    is Float -> editor.putFloat(key, value)
                    is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
                }
            }
            editor.putBoolean("_rally_migration_complete", true).apply()
        }
    }

    var portalUrl: String
        get() = PortalUrlNormalizer.normalizePortal(sharedPreferences.getString("portal_url", "").orEmpty())
        set(value) {
            sharedPreferences.edit()
                .putString("portal_url", PortalUrlNormalizer.normalizePortal(value))
                .apply()
        }

    var xtreamServerUrl: String
        get() = PortalUrlNormalizer.normalizeXtreamServer(sharedPreferences.getString("xtream_server_url", "").orEmpty())
        set(value) {
            sharedPreferences.edit()
                .putString("xtream_server_url", PortalUrlNormalizer.normalizeXtreamServer(value))
                .apply()
        }

    var xtreamUsername: String
        get() = sharedPreferences.getString("xtream_username", "") ?: ""
        set(value) = sharedPreferences.edit().putString("xtream_username", value.trim()).apply()

    var xtreamPassword: String
        get() = sharedPreferences.getString("xtream_password", "") ?: ""
        set(value) = sharedPreferences.edit().putString("xtream_password", value).apply()

    var macAddress: String
        get() {
            val saved = sharedPreferences.getString("mac_address", "").orEmpty().trim()
            if (saved.isNotEmpty()) return saved

            val random = SecureRandom()
            val randomMac = "00:1A:79:${String.format("%02X:%02X:%02X", random.nextInt(256), random.nextInt(256), random.nextInt(256))}"
            sharedPreferences.edit().putString("mac_address", randomMac).apply()
            return randomMac
        }
        set(value) {
            sharedPreferences.edit().putString("mac_address", value.trim().uppercase()).apply()
        }

    var authToken: String
        get() = sharedPreferences.getString("auth_token", "") ?: ""
        set(value) {
            sharedPreferences.edit().putString("auth_token", value).apply()
        }

    var enabledLeagues: Set<String>
        get() = sharedPreferences.getStringSet("enabled_leagues", emptySet()) ?: emptySet()
        set(value) {
            sharedPreferences.edit().putStringSet("enabled_leagues", value).apply()
        }

    companion object {
        // Providers are intentionally opt-in. A fresh install must not contact or
        // display any IPTV portal or Stremio addon until the user configures one.
        val DEFAULT_STREMIO_ADDONS = emptyList<String>()
    }

    var stremioAddonUrls: List<String>
        get() {
            val raw = sharedPreferences.getString("stremio_addon_urls_json", null)
            if (raw.isNullOrBlank()) {
                val legacy = sharedPreferences.getString("stremio_addon_url", null)?.trim()
                return if (!legacy.isNullOrBlank() && legacy !in DEFAULT_STREMIO_ADDONS) {
                    listOf(legacy)
                } else {
                    DEFAULT_STREMIO_ADDONS
                }
            }
            return try {
                val array = org.json.JSONArray(raw)
                val list = mutableListOf<String>()
                for (i in 0 until array.length()) {
                    PortalUrlNormalizer.normalizeAddon(array.getString(i))?.let(list::add)
                }
                list
            } catch (e: Exception) {
                DEFAULT_STREMIO_ADDONS
            }
        }
        set(value) {
            val array = org.json.JSONArray()
            value.mapNotNull(PortalUrlNormalizer::normalizeAddon).distinct().forEach { array.put(it) }
            sharedPreferences.edit().putString("stremio_addon_urls_json", array.toString()).apply()
        }

    var stremioAddonUrl: String
        get() = stremioAddonUrls.firstOrNull().orEmpty()
        set(value) {
            val current = stremioAddonUrls.toMutableList()
            val trimmed = value.trim()
            if (trimmed.isNotEmpty() && trimmed !in current) {
                current.add(trimmed)
            }
            stremioAddonUrls = current
        }

    fun addStremioAddonUrl(url: String) {
        val trimmed = url.trim()
        if (trimmed.isNotEmpty()) {
            val current = stremioAddonUrls.toMutableList()
            if (trimmed !in current) {
                current.add(trimmed)
                stremioAddonUrls = current
            }
        }
    }

    fun removeStremioAddonUrl(url: String) {
        val current = stremioAddonUrls.toMutableList()
        current.remove(url.trim())
        stremioAddonUrls = current
    }

    fun resetStremioAddonUrls() {
        stremioAddonUrls = emptyList()
    }

    var serialNumber: String
        get() = sharedPreferences.getString("serial_number", "") ?: ""
        set(value) {
            sharedPreferences.edit().putString("serial_number", value).apply()
        }

    var deviceId: String
        get() = sharedPreferences.getString("device_id", "") ?: ""
        set(value) {
            sharedPreferences.edit().putString("device_id", value).apply()
        }

    var favoriteSports: Set<String>
        get() = sharedPreferences.getStringSet("favorite_sports", emptySet()) ?: emptySet()
        set(value) {
            sharedPreferences.edit().putStringSet("favorite_sports", value).apply()
        }

    var favoriteTeams: Set<String>
        get() = sharedPreferences.getStringSet("favorite_teams", emptySet()) ?: emptySet()
        set(value) {
            sharedPreferences.edit().putStringSet("favorite_teams", value).apply()
        }

    var liveGameAlertsEnabled: Boolean
        get() = sharedPreferences.getBoolean("live_game_alerts_enabled", true)
        set(value) = sharedPreferences.edit().putBoolean("live_game_alerts_enabled", value).apply()

    var redZoneAlertsEnabled: Boolean
        get() = sharedPreferences.getBoolean("redzone_alerts_enabled", true)
        set(value) = sharedPreferences.edit().putBoolean("redzone_alerts_enabled", value).apply()

    var lowLatencyMode: Boolean
        get() = sharedPreferences.getBoolean("low_latency_mode", true)
        set(value) = sharedPreferences.edit().putBoolean("low_latency_mode", value).apply()

    var audioNormalizationEnabled: Boolean
        get() = sharedPreferences.getBoolean("audio_normalization_enabled", true)
        set(value) = sharedPreferences.edit().putBoolean("audio_normalization_enabled", value).apply()

    var adaptiveQualityEnabled: Boolean
        get() = sharedPreferences.getBoolean("adaptive_quality_enabled", true)
        set(value) = sharedPreferences.edit().putBoolean("adaptive_quality_enabled", value).apply()

    var favoritePlayerIds: Set<String>
        get() = sharedPreferences.getStringSet("favorite_player_ids", emptySet()) ?: emptySet()
        set(value) = sharedPreferences.edit().putStringSet("favorite_player_ids", value).apply()

    fun toggleFavoritePlayer(playerId: String): Boolean {
        val updated = favoritePlayerIds.toMutableSet()
        val nowFavorite = if (playerId in updated) { updated.remove(playerId); false } else { updated.add(playerId); true }
        favoritePlayerIds = updated
        return nowFavorite
    }

    var recentLeagues: List<String>
        get() = sharedPreferences.getString("recent_leagues", "").orEmpty().split('|').filter(String::isNotBlank)
        private set(value) = sharedPreferences.edit().putString("recent_leagues", value.distinct().take(8).joinToString("|")).apply()

    fun recordViewedLeague(league: String) {
        if (league.isBlank()) return
        recentLeagues = listOf(league) + recentLeagues.filterNot { it.equals(league, true) }
    }

    var reducedMotion: Boolean
        get() = sharedPreferences.getBoolean("reduced_motion", false)
        set(value) = sharedPreferences.edit().putBoolean("reduced_motion", value).apply()

    var highContrastFocus: Boolean
        get() = sharedPreferences.getBoolean("high_contrast_focus", false)
        set(value) = sharedPreferences.edit().putBoolean("high_contrast_focus", value).apply()

    var largeText: Boolean
        get() = sharedPreferences.getBoolean("large_text", false)
        set(value) = sharedPreferences.edit().putBoolean("large_text", value).apply()

    var spokenScoreSummaries: Boolean
        get() = sharedPreferences.getBoolean("spoken_score_summaries", false)
        set(value) = sharedPreferences.edit().putBoolean("spoken_score_summaries", value).apply()

    var scoreSaverEnabled: Boolean
        get() = sharedPreferences.getBoolean("score_saver_enabled", true)
        set(value) = sharedPreferences.edit().putBoolean("score_saver_enabled", value).apply()

    data class StreamHealth(
        val successes: Int = 0,
        val failures: Int = 0,
        val stalls: Int = 0,
        val averageStartupMs: Long = 0L,
        val lastUpdatedMs: Long = 0L
    ) {
        val score: Int
            get() = (successes * 24 - failures * 55 - stalls * 12 - (averageStartupMs / 750L).toInt())
                .coerceIn(-240, 120)
    }

    fun streamHealth(target: String): StreamHealth {
        val raw = sharedPreferences.getString("stream_health_${streamHealthKey(target)}", null) ?: return StreamHealth()
        return runCatching {
            val json = org.json.JSONObject(raw)
            StreamHealth(
                successes = json.optInt("successes"),
                failures = json.optInt("failures"),
                stalls = json.optInt("stalls"),
                averageStartupMs = json.optLong("startup"),
                lastUpdatedMs = json.optLong("updated")
            )
        }.getOrDefault(StreamHealth())
    }

    fun recordStreamSuccess(target: String, startupMs: Long) {
        val current = streamHealth(target)
        val successes = (current.successes + 1).coerceAtMost(100)
        val average = if (current.successes == 0) startupMs else
            ((current.averageStartupMs * current.successes) + startupMs) / successes
        saveStreamHealth(target, current.copy(successes = successes, averageStartupMs = average, lastUpdatedMs = System.currentTimeMillis()))
    }

    fun recordStreamFailure(target: String) {
        val current = streamHealth(target)
        saveStreamHealth(target, current.copy(failures = (current.failures + 1).coerceAtMost(100), lastUpdatedMs = System.currentTimeMillis()))
    }

    fun recordStreamStall(target: String) {
        val current = streamHealth(target)
        saveStreamHealth(target, current.copy(stalls = (current.stalls + 1).coerceAtMost(200), lastUpdatedMs = System.currentTimeMillis()))
    }

    private fun saveStreamHealth(target: String, health: StreamHealth) {
        val json = org.json.JSONObject().apply {
            put("successes", health.successes)
            put("failures", health.failures)
            put("stalls", health.stalls)
            put("startup", health.averageStartupMs)
            put("updated", health.lastUpdatedMs)
        }
        sharedPreferences.edit().putString("stream_health_${streamHealthKey(target)}", json.toString()).apply()
    }

    private fun streamHealthKey(target: String): String {
        val bytes = java.security.MessageDigest.getInstance("SHA-256").digest(target.toByteArray())
        return bytes.take(10).joinToString("") { "%02x".format(it) }
    }

    var favoriteTeamProfiles: List<FavoriteTeam>
        get() {
            val raw = sharedPreferences.getString("favorite_team_profiles_v2", null) ?: return emptyList()
            return runCatching {
                val array = org.json.JSONArray(raw)
                buildList {
                    for (index in 0 until array.length()) {
                        val item = array.getJSONObject(index)
                        add(
                            FavoriteTeam(
                                id = item.getString("id"),
                                league = item.optString("league"),
                                name = item.getString("name"),
                                abbreviation = item.optString("abbreviation"),
                                logoUrl = item.optString("logoUrl").takeIf { it.isNotBlank() },
                                colors = buildList {
                                    val colorsJson = item.optJSONArray("colors")
                                    if (colorsJson != null) {
                                        for (colorIndex in 0 until colorsJson.length()) add(colorsJson.getString(colorIndex))
                                    }
                                }
                            )
                        )
                    }
                }
            }.getOrDefault(emptyList())
        }
        set(value) {
            val array = org.json.JSONArray()
            value.distinctBy { "${it.league}:${it.id}" }.forEach { team ->
                array.put(org.json.JSONObject().apply {
                    put("id", team.id)
                    put("league", team.league)
                    put("name", team.name)
                    put("abbreviation", team.abbreviation)
                    put("logoUrl", team.logoUrl.orEmpty())
                    put("colors", org.json.JSONArray(team.colors))
                })
            }
            sharedPreferences.edit()
                .putString("favorite_team_profiles_v2", array.toString())
                .apply()
        }

    fun toggleFavoriteTeam(team: FavoriteTeam): Boolean {
        val key = "${team.league}:${team.id}"
        val current = favoriteTeamProfiles.toMutableList()
        val existing = current.indexOfFirst { "${it.league}:${it.id}" == key }
        val nowFavorite = existing < 0
        if (nowFavorite) {
            current += team
            favoriteTeams = favoriteTeams + team.name
        } else {
            current.removeAt(existing)
            favoriteTeams = favoriteTeams.filterNot {
                it.equals(team.name, true) || it.equals(team.abbreviation, true)
            }.toSet()
        }
        favoriteTeamProfiles = current
        return nowFavorite
    }

    fun isFavoriteTeam(teamId: String, league: String): Boolean =
        favoriteTeamProfiles.any { it.id == teamId && it.league.equals(league, true) }

    var sportsOrder: List<String>
        get() {
            val defaults = listOf("NFL", "NCAAF", "NBA", "NCAAB", "MLB", "NHL", "EPL", "La Liga", "Champions League", "Serie A")
            val raw = sharedPreferences.getString("sports_order", null)
            if (raw.isNullOrEmpty()) return defaults
            val saved = raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            return saved + defaults.filterNot { it in saved }
        }
        set(value) {
            sharedPreferences.edit().putString("sports_order", value.joinToString(",")).apply()
        }

    fun hasCredentials(): Boolean {
        return setupComplete || portalUrl.isNotEmpty() ||
                stremioAddonUrls.isNotEmpty()
    }

    var setupComplete: Boolean
        get() = sharedPreferences.getBoolean("setup_complete", false)
        set(value) = sharedPreferences.edit().putBoolean("setup_complete", value).apply()

    var channelCacheIdentity: String
        get() = sharedPreferences.getString("channel_cache_identity", "").orEmpty()
        set(value) = sharedPreferences.edit().putString("channel_cache_identity", value).apply()

    /**
     * Portable personalization backup. Provider addresses, MAC addresses, serials,
     * device IDs, auth tokens and stream-health history are deliberately excluded.
     */
    fun exportPersonalization(): String = org.json.JSONObject().apply {
        put("schema", 1)
        put("createdAt", System.currentTimeMillis())
        put("enabledLeagues", org.json.JSONArray(enabledLeagues.toList().sorted()))
        put("favoriteSports", org.json.JSONArray(favoriteSports.toList().sorted()))
        put("favoriteTeams", org.json.JSONArray(favoriteTeams.toList().sorted()))
        put("sportsOrder", org.json.JSONArray(sportsOrder))
        put("favoritePlayerIds", org.json.JSONArray(favoritePlayerIds.toList().sorted()))
        put("liveGameAlertsEnabled", liveGameAlertsEnabled)
        put("redZoneAlertsEnabled", redZoneAlertsEnabled)
        put("lowLatencyMode", lowLatencyMode)
        put("audioNormalizationEnabled", audioNormalizationEnabled)
        put("adaptiveQualityEnabled", adaptiveQualityEnabled)
        put("reducedMotion", reducedMotion)
        put("highContrastFocus", highContrastFocus)
        put("largeText", largeText)
        put("spokenScoreSummaries", spokenScoreSummaries)
        put("scoreSaverEnabled", scoreSaverEnabled)
    }.toString(2)

    fun importPersonalization(raw: String): Result<Unit> = runCatching {
        val json = org.json.JSONObject(raw)
        require(json.optInt("schema", -1) == 1) { "Unsupported Rally settings backup." }
        fun stringSet(key: String): Set<String> = buildSet {
            val array = json.optJSONArray(key) ?: return@buildSet
            for (index in 0 until array.length()) {
                array.optString(index).trim().takeIf(String::isNotEmpty)?.let(::add)
            }
        }
        fun stringList(key: String): List<String> = buildList {
            val array = json.optJSONArray(key) ?: return@buildList
            for (index in 0 until array.length()) {
                array.optString(index).trim().takeIf(String::isNotEmpty)?.let(::add)
            }
        }
        enabledLeagues = stringSet("enabledLeagues")
        favoriteSports = stringSet("favoriteSports")
        favoriteTeams = stringSet("favoriteTeams")
        stringList("sportsOrder").takeIf { it.isNotEmpty() }?.let { sportsOrder = it }
        favoritePlayerIds = stringSet("favoritePlayerIds")
        liveGameAlertsEnabled = json.optBoolean("liveGameAlertsEnabled", liveGameAlertsEnabled)
        redZoneAlertsEnabled = json.optBoolean("redZoneAlertsEnabled", redZoneAlertsEnabled)
        lowLatencyMode = json.optBoolean("lowLatencyMode", lowLatencyMode)
        audioNormalizationEnabled = json.optBoolean("audioNormalizationEnabled", audioNormalizationEnabled)
        adaptiveQualityEnabled = json.optBoolean("adaptiveQualityEnabled", adaptiveQualityEnabled)
        reducedMotion = json.optBoolean("reducedMotion", reducedMotion)
        highContrastFocus = json.optBoolean("highContrastFocus", highContrastFocus)
        largeText = json.optBoolean("largeText", largeText)
        spokenScoreSummaries = json.optBoolean("spokenScoreSummaries", spokenScoreSummaries)
        scoreSaverEnabled = json.optBoolean("scoreSaverEnabled", scoreSaverEnabled)
    }
    
    fun clearCredentials() {
        sharedPreferences.edit().clear().apply()
    }
}
