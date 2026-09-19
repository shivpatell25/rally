package com.shiv.rally.domain.model

import androidx.compose.runtime.Immutable
import java.time.Instant

@Immutable
data class Team(
    val id: String,
    val name: String,
    val abbreviation: String,
    val logoUrl: String? = null,
    val colors: List<String> = emptyList()
)

@Immutable
data class SportEvent(
    val id: String,
    val name: String,
    val homeTeam: Team?,
    val awayTeam: Team?,
    val startTime: Instant,
    val status: EventStatus,
    val scoreHome: Int? = null,
    val scoreAway: Int? = null,
    val sport: String,
    val league: String,
    val bannerUrl: String? = null,
    val homeTeamBadge: String? = null,
    val awayTeamBadge: String? = null,
    val venue: String? = null,
    val eventContextTitle: String? = null,
    val liveStats: Map<String, String> = emptyMap(),
    val gameStatusDetail: String? = null,
    val teamStats: List<TeamStatComparison> = emptyList(),
    val playerLeaders: List<PlayerLeader> = emptyList(),
    val highlightClips: List<HighlightClip> = emptyList(),
    val winProbability: List<WinProbabilityPoint> = emptyList(),
    val playerStatTables: List<PlayerStatTable> = emptyList(),
    val plays: List<GamePlay> = emptyList()
)

@Immutable
data class GamePlay(
    val id: String,
    val sequence: Int,
    val text: String,
    val awayScore: Int? = null,
    val homeScore: Int? = null,
    val period: Int? = null,
    val clock: String? = null,
    val isScoringPlay: Boolean = false
)

@Immutable
data class HighlightClip(
    val id: String,
    val title: String,
    val description: String? = null,
    val durationSeconds: Int? = null,
    val thumbnailUrl: String? = null,
    val streamUrl: String? = null,
    val webUrl: String? = null
)

@Immutable
data class WinProbabilityPoint(
    val playId: String? = null,
    val homeWinPercentage: Double,
    val tiePercentage: Double = 0.0,
    val period: Int? = null,
    val clock: String? = null,
    val sequence: Int
)

@Immutable
data class PlayerStatTable(
    val teamId: String? = null,
    val teamName: String,
    val teamAbbreviation: String,
    val teamLogoUrl: String? = null,
    val category: String? = null,
    val labels: List<String> = emptyList(),
    val rows: List<PlayerStatRow> = emptyList()
)

@Immutable
data class PlayerStatRow(
    val athleteId: String? = null,
    val displayName: String,
    val shortName: String? = null,
    val headshotUrl: String? = null,
    val jersey: String? = null,
    val position: String? = null,
    val stats: List<String> = emptyList()
)

@Immutable
data class TeamStatComparison(
    val label: String,
    val awayValue: String,
    val homeValue: String
)

@Immutable
data class PlayerLeader(
    val category: String,
    val teamLogoUrl: String? = null,
    val teamAbbr: String? = null,
    val playerShortName: String,
    val statDisplay: String,
    val position: String? = null,
    val headshotUrl: String? = null
)

enum class EventStatus {
    NOT_STARTED,
    LIVE,
    HALFTIME,
    FINISHED,
    DELAYED,
    CANCELED
}

@Immutable
data class IptvChannel(
    val id: String,
    val number: String,
    val name: String,
    val category: String,
    val logoUrl: String? = null,
    val streamUrl: String? = null,
    val guide: ChannelGuide? = null,
    val supportsCatchUp: Boolean = false,
    val archiveDurationHours: Int? = null
)

@Immutable
data class EpgProgram(
    val title: String,
    val description: String? = null,
    val startTime: Instant? = null,
    val endTime: Instant? = null
)

@Immutable
data class ChannelGuide(
    val now: EpgProgram? = null,
    val next: EpgProgram? = null,
    val fetchedAtEpochMs: Long = System.currentTimeMillis()
)

@Immutable
data class FavoriteTeam(
    val id: String,
    val league: String,
    val name: String,
    val abbreviation: String,
    val logoUrl: String? = null,
    val colors: List<String> = emptyList()
)

@Immutable
data class TeamStanding(
    val summary: String,
    val rank: Int? = null,
    val wins: Int? = null,
    val losses: Int? = null,
    val ties: Int? = null
)

@Immutable
data class TeamPlayer(
    val id: String,
    val name: String,
    val position: String? = null,
    val jersey: String? = null,
    val headshotUrl: String? = null
)

@Immutable
data class TeamInjury(
    val playerName: String,
    val status: String,
    val detail: String? = null
)

@Immutable
data class TeamHub(
    val team: FavoriteTeam,
    val standing: TeamStanding? = null,
    val schedule: List<SportEvent> = emptyList(),
    val roster: List<TeamPlayer> = emptyList(),
    val injuries: List<TeamInjury> = emptyList()
)

@Immutable
data class LeagueHub(
    val league: String,
    val events: List<SportEvent> = emptyList(),
    val standings: List<Pair<String, String>> = emptyList(),
    val postseasonEvents: List<SportEvent> = emptyList(),
    val playoffPicture: List<Pair<String, String>> = emptyList()
)

enum class GameAlertType { KICKOFF, SCORE, CLOSE_GAME, OVERTIME, FINAL, RED_ZONE }

@Immutable
data class GameAlert(
    val id: String,
    val eventId: String?,
    val type: GameAlertType,
    val title: String,
    val message: String
)

@Immutable
data class MatchResult(
    val sportEvent: SportEvent,
    val iptvChannel: IptvChannel,
    val confidenceScore: Float // 0.0 to 1.0
)

@Immutable
data class StremioStreamOption(
    val title: String,
    val description: String? = null,
    val streamUrl: String,
    val quality: String? = null,
    val bitrate: String? = null,
    val addonName: String? = null,
    val headers: Map<String, String>? = null,
    /** False when an add-on supplied an HTML watch page rather than playable media. */
    val isDirectPlayable: Boolean = true
)

enum class StreamSourceKind { STREMIO, IPTV }

@Immutable
data class StreamCandidate(
    val id: String,
    val playbackTarget: String,
    val title: String,
    val sourceKind: StreamSourceKind,
    val quality: StreamQualityInfo,
    val qualityRank: Int,
    val exactGameMatch: Boolean,
    val matchConfidence: Float,
    val matchEvidence: String,
    val headers: Map<String, String>? = null,
    val channel: IptvChannel? = null,
    val stremioStream: StremioStreamOption? = null,
    val preflightPassed: Boolean? = null,
    val preflightLatencyMs: Long? = null,
    val preflightContentType: String? = null
)

@Immutable
data class StreamSelection(
    val primary: StreamCandidate?,
    val candidates: List<StreamCandidate>,
    val relevantChannels: List<RelevantChannel>,
    val stremioStreams: List<StremioStreamOption>
)

@Immutable
data class RelevantChannel(
    val channel: IptvChannel,
    val likelihoodScore: Float,
    val matchBadge: String? = null,
    val isOfficialBroadcast: Boolean = false
)

@Immutable
data class StreamQualityInfo(
    val resolution: String? = null,
    val fps: String? = null,
    val is4K: Boolean = false,
    val is60Fps: Boolean = false,
    val isHdr: Boolean = false
)

fun parseQualityFromChannelName(name: String): StreamQualityInfo {
    val upper = name.uppercase()
    val res = when {
        upper.contains("4K") || upper.contains("UHD") || upper.contains("2160P") -> "4K"
        upper.contains("1080P") || upper.contains("1080I") || upper.contains("FHD") -> "1080p"
        upper.contains("720P") -> "720p"
        upper.contains(" HD") || upper.endsWith("HD") || upper.contains("| HD") || upper.contains(": HD") -> "HD"
        else -> null
    }
    val fps = when {
        upper.contains("60FPS") || upper.contains("60 FPS") || upper.contains(" 60P") || upper.contains(" 60 ") || upper.endsWith(" 60") -> "60 fps"
        upper.contains("50FPS") || upper.contains("50 FPS") || upper.contains(" 50P") || upper.contains(" 50 ") || upper.endsWith(" 50") -> "50 fps"
        upper.contains("30FPS") || upper.contains("30 FPS") -> "30 fps"
        upper.contains("25FPS") || upper.contains("25 FPS") -> "25 fps"
        else -> null
    }
    val isHdr = upper.contains("HDR") || upper.contains("HLG") || upper.contains("DOLBY VISION") || upper.contains("DV")
    return StreamQualityInfo(
        resolution = res,
        fps = fps,
        is4K = res == "4K",
        is60Fps = fps == "60 fps",
        isHdr = isHdr
    )
}

@Immutable
data class BroadcastQualityInfo(
    val badgeText: String,
    val fullLabel: String,
    val network: String? = null,
    val is4K: Boolean = false,
    val isHdr: Boolean = false,
    val is1080p: Boolean = false,
    val evidenceSource: String = "Official broadcaster"
)

fun resolveMaxBroadcastQuality(
    event: SportEvent,
    broadcastStations: List<String> = emptyList(),
    relevantChannels: List<RelevantChannel> = emptyList(),
    stremioStreams: List<StremioStreamOption> = emptyList()
): BroadcastQualityInfo {
    val rawStations = (broadcastStations + listOfNotNull(event.liveStats["TV Broadcast"])).flatMap {
        it.split(",", "/", "&", "+").map { s -> s.trim() }.filter { s -> s.isNotEmpty() }
    }.distinct()

    val primaryNetwork = rawStations.firstOrNull()

    // IPTV channel names are deliberately excluded. Provider labels such as "4K" are often
    // aliases rather than verified production formats. Official event/broadcaster metadata and
    // direct Stremio stream metadata are the only trusted inputs for the maximum-quality badge.
    @Suppress("UNUSED_VARIABLE")
    val ignoredProviderChannels = relevantChannels
    val officialEvidence = buildList {
        addAll(rawStations)
        add(event.liveStats["TV Broadcast"].orEmpty())
        add(event.liveStats["Broadcast Quality"].orEmpty())
        add(event.liveStats["Video Format"].orEmpty())
        add(event.eventContextTitle.orEmpty())
    }.joinToString(" ").uppercase()
    val stremioEvidence = stremioStreams
        .filter { it.isDirectPlayable }
        .flatMap { listOf(it.title, it.description.orEmpty(), it.quality.orEmpty(), it.bitrate.orEmpty()) }
        .joinToString(" ").uppercase()

    val explicitOfficial4k = Regex("""\b(4K|UHD|2160P?)\b""").containsMatchIn(officialEvidence)
    val explicitOfficialHdr = Regex("""\b(HDR10\+?|HDR|HLG|DOLBY\s+VISION)\b""").containsMatchIn(officialEvidence)
    val explicitOfficial1080 = Regex("""\b(1080P?|FHD)\b""").containsMatchIn(officialEvidence)
    val stremio4k = Regex("""\b(4K|UHD|2160P?)\b""").containsMatchIn(stremioEvidence)
    val stremioHdr = Regex("""\b(HDR10\+?|HDR|HLG|DOLBY\s+VISION)\b""").containsMatchIn(stremioEvidence)
    val stremio1080 = Regex("""\b(1080P?|FHD)\b""").containsMatchIn(stremioEvidence)

    // ESPN and ESPN2 officially produce NFL, NBA and NHL telecasts in 4K HDR. Keep this rule
    // intentionally narrow; select-event promises from other networks require explicit metadata.
    val espn4kSport = rawStations.any { it.equals("ESPN", true) || it.equals("ESPN2", true) } &&
        (event.league.contains("NFL", true) || event.league.contains("NBA", true) || event.league.contains("NHL", true))
    val eventIdentity = "${event.name} ${event.eventContextTitle.orEmpty()}".uppercase()
    val nbcPublished4kEvent = rawStations.any { it.equals("NBC", true) || it.equals("PEACOCK", true) } &&
        (eventIdentity.contains("SUPER BOWL LX") || eventIdentity.contains("MILAN CORTINA") || eventIdentity.contains("WINTER OLYMPIC"))
    val official4k = explicitOfficial4k || espn4kSport || nbcPublished4kEvent
    val officialHdr = explicitOfficialHdr || espn4kSport || nbcPublished4kEvent
    val official1080 = explicitOfficial1080

    val is4k = official4k || stremio4k
    val isHdr = officialHdr || stremioHdr
    val is1080p = !is4k && (official1080 || stremio1080)
    val evidenceSource = when {
        stremio4k && !official4k -> "Verified stream metadata"
        stremioHdr && !officialHdr -> "Verified stream metadata"
        stremio1080 && !official1080 -> "Verified stream metadata"
        else -> "Official broadcaster"
    }
    val label = when {
        is4k && isHdr -> "4K HDR"
        is4k -> "4K UHD"
        is1080p && isHdr -> "1080p HDR"
        is1080p -> "1080p"
        else -> "HD"
    }
    return BroadcastQualityInfo(
        badgeText = label,
        fullLabel = if (primaryNetwork != null) "$label · $primaryNetwork · $evidenceSource" else "$label · $evidenceSource",
        network = primaryNetwork,
        is4K = is4k,
        isHdr = isHdr,
        is1080p = is1080p,
        evidenceSource = evidenceSource
    )
}

enum class MultiViewLayoutMode {
    AUTO,
    DUAL_SPLIT,
    DUAL_FOCUS,
    TRIPLE_FOCUS,
    TRIPLE_COLUMNS,
    QUAD_GRID,
    QUAD_FOCUS
}

@Immutable
data class MultiViewSlot(
    val slotId: String = java.util.UUID.randomUUID().toString(),
    val event: SportEvent? = null,
    val channel: IptvChannel? = null,
    val streamUrl: String = "",
    val streamHeaders: Map<String, String>? = null,
    val title: String = "",
    val subtitle: String? = null,
    val scoreText: String? = null,
    val statusText: String? = null,
    val resolution: String? = null,
    val fps: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)
