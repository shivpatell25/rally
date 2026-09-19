package com.shiv.rally.data.remote.sports

import com.shiv.rally.data.local.PreferencesManager
import com.shiv.rally.domain.model.EventStatus
import com.shiv.rally.domain.model.SportEvent
import com.shiv.rally.domain.repository.SportsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import android.util.Log
import com.shiv.rally.domain.model.Team
import com.shiv.rally.domain.model.FavoriteTeam
import com.shiv.rally.domain.model.LeagueHub
import com.shiv.rally.domain.model.TeamHub
import com.shiv.rally.domain.model.TeamInjury
import com.shiv.rally.domain.model.TeamPlayer
import com.shiv.rally.domain.model.TeamStanding
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import javax.inject.Inject
import javax.inject.Singleton
import com.shiv.rally.di.ApplicationScope
import java.util.concurrent.atomic.AtomicBoolean

@Singleton
class EspnRepositoryImpl @Inject constructor(
    private val api: EspnApi,
    private val preferencesManager: PreferencesManager,
    private val diskCache: SportsEventDiskCache,
    @ApplicationScope private val applicationScope: CoroutineScope
) : SportsRepository {

    // Maps our domain leagues to ESPN's (sport, league) paths
    private val espnLeagues = mapOf(
        "NFL" to Pair("football", "nfl"),
        "NCAAF" to Pair("football", "college-football"),
        "NBA" to Pair("basketball", "nba"),
        "NCAAB" to Pair("basketball", "mens-college-basketball"),
        "MLB" to Pair("baseball", "mlb"),
        "NHL" to Pair("hockey", "nhl"),
        "EPL" to Pair("soccer", "eng.1"),
        "La Liga" to Pair("soccer", "esp.1"),
        "Champions League" to Pair("soccer", "uefa.champions"),
        "Serie A" to Pair("soccer", "ita.1"),
        "MLS" to Pair("soccer", "usa.1")
    )

    private var cachedEvents: List<SportEvent> = emptyList()
    private var lastFetched: Instant = Instant.MIN
    private val eventCache = java.util.concurrent.ConcurrentHashMap<String, SportEvent>()
    private val summaryFetchedAt = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val scoreboardMutex = Mutex()
    private val backgroundRefreshScheduled = AtomicBoolean(false)

    override suspend fun getLiveEvents(): List<SportEvent> {
        return fetchScoreboards().filter { it.status == EventStatus.LIVE || it.status == EventStatus.HALFTIME }
    }

    override suspend fun getUpcomingEvents(): List<SportEvent> {
        val now = Instant.now()
        return fetchScoreboards().filter { 
            it.status == EventStatus.NOT_STARTED && it.startTime.isAfter(now.minusSeconds(3600)) 
        }
    }

    override suspend fun getEventsSnapshot(): List<SportEvent> {
        val now = Instant.now()
        return fetchScoreboards().filter {
            it.status == EventStatus.LIVE || it.status == EventStatus.HALFTIME ||
                    (it.status == EventStatus.NOT_STARTED && it.startTime.isAfter(now.minusSeconds(3600)))
        }
    }

    override suspend fun getRecentEvents(): List<SportEvent> {
        val now = Instant.now()
        return fetchScoreboards().filter {
            it.status == EventStatus.LIVE ||
                it.status == EventStatus.HALFTIME ||
                it.status == EventStatus.FINISHED ||
                (it.status == EventStatus.NOT_STARTED && it.startTime.isAfter(now.minusSeconds(3600)))
        }
    }

    override suspend fun getEventsByLeague(league: String): List<SportEvent> {
        return fetchScoreboards().filter { it.league == league }
    }

    override suspend fun getEventById(eventId: String): SportEvent? = withContext(Dispatchers.IO) {
        eventCache[eventId]?.let { return@withContext it }
        val found = fetchScoreboards().find { it.id == eventId }
        if (found != null) return@withContext found

        val priorityLeagues = listOf(
            Pair("football", "college-football"),
            Pair("football", "nfl"),
            Pair("baseball", "mlb"),
            Pair("basketball", "nba"),
            Pair("hockey", "nhl"),
            Pair("soccer", "eng.1")
        )
        for (pair in priorityLeagues) {
            try {
                val summary = api.getSummary(pair.first, pair.second, eventId)
                val comp = summary.header?.competitions?.firstOrNull()
                val homeComp = comp?.competitors?.find { it.homeAway == "home" }
                val awayComp = comp?.competitors?.find { it.homeAway == "away" }
                if (homeComp?.team != null && awayComp?.team != null) {
                    val homeName = homeComp.team.displayName ?: homeComp.team.name ?: "Home"
                    val awayName = awayComp.team.displayName ?: awayComp.team.name ?: "Away"
                    val homeAbbr = homeComp.team.abbreviation ?: homeName.take(3).uppercase()
                    val awayAbbr = awayComp.team.abbreviation ?: awayName.take(3).uppercase()
                    val leagueName = espnLeagues.entries.find { it.value == pair }?.key ?: pair.second.uppercase()
                    val ev = SportEvent(
                        id = eventId,
                        name = "$awayName at $homeName",
                        homeTeam = Team(homeComp.team.id, homeName, homeAbbr, homeComp.team.logo),
                        awayTeam = Team(awayComp.team.id, awayName, awayAbbr, awayComp.team.logo),
                        homeTeamBadge = homeComp.team.logo ?: "",
                        awayTeamBadge = awayComp.team.logo ?: "",
                        scoreHome = homeComp.score?.toIntOrNull(),
                        scoreAway = awayComp.score?.toIntOrNull(),
                        sport = pair.first,
                        league = leagueName,
                        status = when (comp.status?.type?.state) {
                            "in" -> if (comp.status.type.name?.contains("Half", ignoreCase = true) == true) EventStatus.HALFTIME else EventStatus.LIVE
                            "post" -> EventStatus.FINISHED
                            else -> EventStatus.NOT_STARTED
                        },
                        startTime = Instant.now(),
                        liveStats = mutableMapOf<String, String>().apply {
                            comp.status?.type?.detail?.let { put("Game Status", it) }
                        },
                        gameStatusDetail = comp.status?.type?.detail ?: comp.status?.type?.description
                    )
                    val fullEvent = getEventSummary(ev)
                    eventCache[eventId] = fullEvent
                    return@withContext fullEvent
                }
            } catch (_: Exception) {
                // Try next
            }
        }
        null
    }

    override suspend fun searchEvents(query: String): List<SportEvent> {
        return fetchScoreboards().filter { it.name.contains(query, ignoreCase = true) }
    }

    override fun observeLiveEvent(eventId: String): Flow<SportEvent> {
        return flow {
            var previous: SportEvent? = null
            while (currentCoroutineContext().isActive) {
                val updated = fetchScoreboards(forceRefresh = true).firstOrNull { it.id == eventId }
                    ?: eventCache[eventId]
                if (updated != null) {
                    val enriched = getEventSummary(updated)
                    if (enriched != previous) {
                        emit(enriched)
                        previous = enriched
                    }
                    if (enriched.status == EventStatus.FINISHED || enriched.status == EventStatus.CANCELED) break
                }
                delay(30_000L)
            }
        }
    }

    override suspend fun getTvStationsForEvent(eventId: String): List<String> {
        val event = eventCache[eventId] ?: getEventById(eventId)
        val tvBroadcast = event?.liveStats?.get("TV Broadcast") ?: return emptyList()
        return tvBroadcast.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }

    override suspend fun getEventSummary(event: SportEvent): SportEvent = withContext(Dispatchers.IO) {
        val cachedSummary = eventCache[event.id]
        val summaryAge = System.currentTimeMillis() - (summaryFetchedAt[event.id] ?: 0L)
        if (cachedSummary != null && summaryAge < 30_000L &&
            (cachedSummary.teamStats.isNotEmpty() || cachedSummary.playerLeaders.isNotEmpty() ||
                cachedSummary.highlightClips.isNotEmpty() || cachedSummary.winProbability.isNotEmpty() ||
                cachedSummary.playerStatTables.isNotEmpty())) {
            return@withContext cachedSummary
        }
        val pair = espnLeagues[event.league] ?: when {
            event.league.contains("NFL", ignoreCase = true) || event.league.contains("NCAAF", ignoreCase = true) || event.sport.contains("football", ignoreCase = true) ->
                if (event.league.contains("college", ignoreCase = true) || event.league.contains("ncaa", ignoreCase = true)) Pair("football", "college-football") else Pair("football", "nfl")
            event.league.contains("NBA", ignoreCase = true) || event.sport.contains("basket", ignoreCase = true) ->
                if (event.league.contains("college", ignoreCase = true) || event.league.contains("ncaa", ignoreCase = true)) Pair("basketball", "mens-college-basketball") else Pair("basketball", "nba")
            event.league.contains("MLB", ignoreCase = true) || event.sport.contains("base", ignoreCase = true) -> Pair("baseball", "mlb")
            event.league.contains("NHL", ignoreCase = true) || event.sport.contains("hock", ignoreCase = true) -> Pair("hockey", "nhl")
            else -> Pair("soccer", "eng.1")
        }

        try {
            val summary = api.getSummary(pair.first, pair.second, event.id)
            
            // 1. Team Stats Comparisons
            val boxTeams = summary.boxscore?.teams ?: emptyList()
            val awayBoxTeam = boxTeams.find {
                it.team?.id == event.awayTeam?.id ||
                        it.team?.abbreviation.equals(event.awayTeam?.abbreviation, ignoreCase = true) ||
                        it.team?.displayName?.contains(event.awayTeam?.name ?: "___", ignoreCase = true) == true
            } ?: boxTeams.firstOrNull()

            val homeBoxTeam = boxTeams.find {
                it.team?.id == event.homeTeam?.id ||
                        it.team?.abbreviation.equals(event.homeTeam?.abbreviation, ignoreCase = true) ||
                        it.team?.displayName?.contains(event.homeTeam?.name ?: "___", ignoreCase = true) == true
            } ?: boxTeams.lastOrNull()

            val sportLower = event.sport.lowercase()
            val leagueLower = event.league.lowercase()

            val preferredStatKeys = when {
                leagueLower.contains("nfl") || leagueLower.contains("ncaaf") || sportLower.contains("football") -> listOf(
                    "netPassingYards" to "Passing",
                    "rushingYards" to "Rushing",
                    "totalYards" to "Total Yds",
                    "turnovers" to "Turnovers",
                    "firstDowns" to "1st Downs"
                )
                leagueLower.contains("nba") || leagueLower.contains("ncaab") || sportLower.contains("basket") -> listOf(
                    "fieldGoals" to "FG%",
                    "threePointFieldGoals" to "3PT%",
                    "totalRebounds" to "Rebounds",
                    "turnovers" to "Turnovers",
                    "assists" to "Assists"
                )
                leagueLower.contains("mlb") || sportLower.contains("base") -> listOf(
                    "hits" to "Hits",
                    "errors" to "Errors",
                    "strikeouts" to "Strikeouts",
                    "walks" to "Walks"
                )
                leagueLower.contains("nhl") || sportLower.contains("hock") -> listOf(
                    "shots" to "SOG",
                    "powerPlayGoals" to "Power Play",
                    "blockedShots" to "Blocks",
                    "hits" to "Hits"
                )
                else -> listOf(
                    "shotsOnTarget" to "SOG",
                    "possession" to "Possession",
                    "fouls" to "Fouls",
                    "cornerKicks" to "Corners"
                )
            }

            val teamStats = mutableListOf<com.shiv.rally.domain.model.TeamStatComparison>()
            preferredStatKeys.forEach { (key, label) ->
                val awayVal = awayBoxTeam?.statistics?.find { 
                    it.name.equals(key, ignoreCase = true) || it.label.equals(label, ignoreCase = true) 
                }?.displayValue
                val homeVal = homeBoxTeam?.statistics?.find { 
                    it.name.equals(key, ignoreCase = true) || it.label.equals(label, ignoreCase = true) 
                }?.displayValue
                if (awayVal != null || homeVal != null) {
                    teamStats.add(
                        com.shiv.rally.domain.model.TeamStatComparison(
                            label = label,
                            awayValue = awayVal ?: "-",
                            homeValue = homeVal ?: "-"
                        )
                    )
                }
            }

            // Fallback for Baseball / Soccer / other sports if boxscore stats are null
            if (teamStats.isEmpty()) {
                val comp = summary.header?.competitions?.firstOrNull()
                val awayComp = comp?.competitors?.find { it.homeAway == "away" }
                val homeComp = comp?.competitors?.find { it.homeAway == "home" }
                if (awayComp?.score != null && homeComp?.score != null && (awayComp.score != "0" || homeComp.score != "0")) {
                    teamStats.add(com.shiv.rally.domain.model.TeamStatComparison("Score", awayComp.score, homeComp.score))
                }
                if (awayComp?.hits != null && homeComp?.hits != null) {
                    teamStats.add(com.shiv.rally.domain.model.TeamStatComparison("Hits", awayComp.hits.toString(), homeComp.hits.toString()))
                }
                if (awayComp?.errors != null && homeComp?.errors != null) {
                    teamStats.add(com.shiv.rally.domain.model.TeamStatComparison("Errors", awayComp.errors.toString(), homeComp.errors.toString()))
                }
            }

            // Keep matchup analytics alongside live box-score data instead of only using it as a fallback.
            val pick = summary.pickcenter?.firstOrNull()
            val predictor = summary.predictor
            val awayComp = summary.header?.competitions?.firstOrNull()?.competitors?.find { it.homeAway == "away" }
            val homeComp = summary.header?.competitions?.firstOrNull()?.competitors?.find { it.homeAway == "home" }
            val awayAbbr = awayComp?.team?.abbreviation ?: "AWAY"
            val homeAbbr = homeComp?.team?.abbreviation ?: "HOME"

            fun addAnalytics(label: String, awayValue: String, homeValue: String) {
                if (teamStats.none { it.label.equals(label, ignoreCase = true) }) {
                    teamStats.add(com.shiv.rally.domain.model.TeamStatComparison(label, awayValue, homeValue))
                }
            }

            if (pick?.spread != null) {
                val s = pick.spread
                val homeSpreadStr = if (s > 0) "+$s" else "$s"
                val awaySpreadStr = if (s > 0) "-$s" else "+${-s}"
                addAnalytics("Spread", awaySpreadStr, homeSpreadStr)
            } else if (pick?.details != null) {
                val details = pick.details.trim()
                val parts = details.split(" ")
                if (parts.size >= 2) {
                    val favoredTeam = parts[0]
                    val spreadVal = parts[1]
                    val num = spreadVal.toDoubleOrNull()
                    if (num != null) {
                        val isHomeFavored = favoredTeam.equals(homeAbbr, ignoreCase = true)
                        val isAwayFavored = favoredTeam.equals(awayAbbr, ignoreCase = true)
                        if (isHomeFavored) {
                            addAnalytics("Spread", "+${Math.abs(num)}", "-${Math.abs(num)}")
                        } else if (isAwayFavored) {
                            addAnalytics("Spread", "-${Math.abs(num)}", "+${Math.abs(num)}")
                        } else {
                            addAnalytics("Line", details, details)
                        }
                    } else {
                        addAnalytics("Line", details, details)
                    }
                } else {
                    addAnalytics("Line", details, details)
                }
            }
            if (pick?.overUnder != null) {
                addAnalytics("Over/Under", "O ${pick.overUnder}", "U ${pick.overUnder}")
            }
            if (pick?.awayTeamOdds?.moneyLine != null && pick.homeTeamOdds?.moneyLine != null) {
                val awayMl = pick.awayTeamOdds.moneyLine.let { if (it > 0) "+$it" else "$it" }
                val homeMl = pick.homeTeamOdds.moneyLine.let { if (it > 0) "+$it" else "$it" }
                addAnalytics("Moneyline", awayMl, homeMl)
            }
            if (predictor?.awayTeam?.gameProjection != null && predictor.homeTeam?.gameProjection != null) {
                addAnalytics("Win Prob", "${predictor.awayTeam.gameProjection}%", "${predictor.homeTeam.gameProjection}%")
            }

            // 2. Per-player Prominent Leaders
            val playerLeaders = mutableListOf<com.shiv.rally.domain.model.PlayerLeader>()
            summary.leaders?.forEach { group ->
                val teamLogo = group.team?.logo
                val teamAbbr = group.team?.abbreviation
                group.leaders?.forEach { cat ->
                    val topLeader = cat.leaders?.firstOrNull()
                    val athlete = topLeader?.athlete
                    if (athlete != null) {
                        val shortName = athlete.shortName ?: athlete.displayName ?: athlete.fullName ?: ""
                        val rawStat = topLeader.displayValue ?: "${topLeader.value?.toInt() ?: ""}".trim()
                        val statText = when {
                            cat.name?.contains("tackle", ignoreCase = true) == true || cat.displayName?.contains("tackle", ignoreCase = true) == true ->
                                if (rawStat.all { it.isDigit() }) "$rawStat TKL" else rawStat
                            cat.name?.contains("sack", ignoreCase = true) == true ->
                                if (rawStat.all { it.isDigit() }) "$rawStat SCK" else rawStat
                            cat.name?.contains("passing", ignoreCase = true) == true && rawStat.all { it.isDigit() } -> "$rawStat YDS"
                            cat.name?.contains("rushing", ignoreCase = true) == true && rawStat.all { it.isDigit() } -> "$rawStat YDS"
                            cat.name?.contains("receiving", ignoreCase = true) == true && rawStat.all { it.isDigit() } -> "$rawStat YDS"
                            cat.name?.contains("point", ignoreCase = true) == true && rawStat.all { it.isDigit() } -> "$rawStat PTS"
                            cat.name?.contains("rebound", ignoreCase = true) == true && rawStat.all { it.isDigit() } -> "$rawStat REB"
                            cat.name?.contains("assist", ignoreCase = true) == true && rawStat.all { it.isDigit() } -> "$rawStat AST"
                            cat.name?.contains("goal", ignoreCase = true) == true && rawStat.all { it.isDigit() } -> "$rawStat G"
                            cat.name?.contains("save", ignoreCase = true) == true && rawStat.all { it.isDigit() } -> "$rawStat SV"
                            else -> rawStat
                        }
                        if (shortName.isNotEmpty() && statText.isNotEmpty()) {
                            val pos = athlete.position?.abbreviation ?: when (cat.name?.lowercase()) {
                                "passingyards" -> "QB"
                                "rushingyards" -> "RB"
                                "receivingyards" -> "WR"
                                "totaltackles" -> "LB"
                                "sacks" -> "DE"
                                "points" -> "PTS"
                                "rebounds" -> "REB"
                                "assists" -> "AST"
                                "goals" -> "G"
                                "saves" -> "SV"
                                else -> null
                            }
                            playerLeaders.add(
                                com.shiv.rally.domain.model.PlayerLeader(
                                    category = cat.displayName ?: cat.name ?: "Leader",
                                    teamLogoUrl = teamLogo,
                                    teamAbbr = teamAbbr,
                                    playerShortName = shortName,
                                    statDisplay = statText,
                                    position = pos,
                                    headshotUrl = athlete.headshot?.href
                                )
                            )
                        }
                    }
                }
            }

            // Fallback for Baseball / other sports with boxscore.players
            if (playerLeaders.isEmpty() && summary.boxscore?.players != null) {
                summary.boxscore.players.forEach { playerGroup ->
                    val teamLogo = playerGroup.team?.logo
                    val teamAbbr = playerGroup.team?.abbreviation
                    val battingCat = playerGroup.statistics?.find { it.name?.contains("bat", ignoreCase = true) == true } ?: playerGroup.statistics?.firstOrNull()
                    val topBatter = battingCat?.athletes?.firstOrNull()
                    if (topBatter?.athlete != null) {
                        val shortName = topBatter.athlete.shortName ?: topBatter.athlete.displayName ?: ""
                        val stats = topBatter.stats ?: emptyList()
                        val hitAb = stats.getOrNull(0) ?: ""
                        val rbi = stats.getOrNull(4)?.let { "$it RBI" } ?: ""
                        val statText = listOf(hitAb, rbi).filter { it.isNotEmpty() }.joinToString(", ").ifEmpty { hitAb }
                        if (shortName.isNotEmpty() && statText.isNotEmpty()) {
                            playerLeaders.add(
                                com.shiv.rally.domain.model.PlayerLeader(
                                    category = "Batting",
                                    teamLogoUrl = teamLogo,
                                    teamAbbr = teamAbbr,
                                    playerShortName = shortName,
                                    statDisplay = statText,
                                    position = "BAT",
                                    headshotUrl = topBatter.athlete.headshot?.href
                                )
                            )
                        }
                    }
                    val pitchingCat = playerGroup.statistics?.find { it.name?.contains("pitch", ignoreCase = true) == true } ?: playerGroup.statistics?.getOrNull(1)
                    val topPitcher = pitchingCat?.athletes?.firstOrNull()
                    if (topPitcher?.athlete != null) {
                        val shortName = topPitcher.athlete.shortName ?: topPitcher.athlete.displayName ?: ""
                        val stats = topPitcher.stats ?: emptyList()
                        val ip = stats.getOrNull(0)?.let { "$it IP" } ?: ""
                        val k = stats.getOrNull(5)?.let { "$it K" } ?: ""
                        val statText = listOf(ip, k).filter { it.isNotEmpty() }.joinToString(", ").ifEmpty { ip }
                        if (shortName.isNotEmpty() && statText.isNotEmpty()) {
                            playerLeaders.add(
                                com.shiv.rally.domain.model.PlayerLeader(
                                    category = "Pitching",
                                    teamLogoUrl = teamLogo,
                                    teamAbbr = teamAbbr,
                                    playerShortName = shortName,
                                    statDisplay = statText,
                                    position = "P",
                                    headshotUrl = topPitcher.athlete.headshot?.href
                                )
                            )
                        }
                    }
                }
            }

            val highlightClips = summary.videos.orEmpty().mapNotNull { video ->
                val title = video.headline?.trim().orEmpty()
                if (title.isEmpty()) return@mapNotNull null
                val directUrl = video.links?.source?.HLS?.HD?.href
                    ?: video.links?.source?.HLS?.href
                    ?: video.links?.source?.HD?.href
                    ?: video.links?.source?.href
                    ?: video.links?.mobile?.source?.href
                com.shiv.rally.domain.model.HighlightClip(
                    id = video.id?.toString() ?: "${event.id}:${title.hashCode()}",
                    title = title,
                    description = video.description?.trim()?.takeIf { it.isNotEmpty() },
                    durationSeconds = video.duration,
                    thumbnailUrl = video.thumbnail,
                    streamUrl = directUrl,
                    webUrl = video.links?.web?.href
                )
            }.distinctBy { it.id }

            val playsById = summary.plays.orEmpty().mapNotNull { play ->
                play.id?.let { it to play }
            }.toMap()
            val gamePlays = summary.plays.orEmpty().mapIndexedNotNull { index, play ->
                val text = play.text?.trim().orEmpty()
                if (text.isEmpty()) return@mapIndexedNotNull null
                com.shiv.rally.domain.model.GamePlay(
                    id = play.id ?: "${event.id}:play:$index",
                    sequence = play.sequenceNumber?.toIntOrNull() ?: index,
                    text = text,
                    awayScore = play.awayScore,
                    homeScore = play.homeScore,
                    period = play.period?.number,
                    clock = play.clock?.displayValue,
                    isScoringPlay = play.scoringPlay == true
                )
            }.sortedByDescending { it.sequence }
            val winProbability = summary.winprobability.orEmpty().mapIndexedNotNull { index, point ->
                val value = point.homeWinPercentage ?: return@mapIndexedNotNull null
                val play = point.playId?.let(playsById::get)
                com.shiv.rally.domain.model.WinProbabilityPoint(
                    playId = point.playId,
                    homeWinPercentage = value.coerceIn(0.0, 1.0),
                    tiePercentage = (point.tiePercentage ?: 0.0).coerceIn(0.0, 1.0),
                    period = play?.period?.number,
                    clock = play?.clock?.displayValue,
                    sequence = index
                )
            }

            val playerStatTables = summary.boxscore?.players.orEmpty().flatMap { group ->
                val team = group.team
                group.statistics.orEmpty().mapNotNull tableMap@ { category ->
                    val rows = category.athletes.orEmpty().mapNotNull rowMap@ { item ->
                        val athlete = item.athlete ?: return@rowMap null
                        val name = athlete.displayName ?: athlete.fullName ?: athlete.shortName ?: return@rowMap null
                        com.shiv.rally.domain.model.PlayerStatRow(
                            athleteId = athlete.id,
                            displayName = name,
                            shortName = athlete.shortName,
                            headshotUrl = athlete.headshot?.href,
                            jersey = athlete.jersey,
                            position = athlete.position?.abbreviation ?: athlete.position?.displayName,
                            stats = item.stats.orEmpty()
                        )
                    }
                    if (rows.isEmpty()) return@tableMap null
                    com.shiv.rally.domain.model.PlayerStatTable(
                        teamId = team?.id,
                        teamName = team?.displayName ?: team?.name ?: "Team",
                        teamAbbreviation = team?.abbreviation ?: "TEAM",
                        teamLogoUrl = team?.logo,
                        category = category.name,
                        labels = category.labels ?: category.descriptions.orEmpty(),
                        rows = rows
                    )
                }
            }

            val summaryCompetition = summary.header?.competitions?.firstOrNull()
            val summaryAway = summaryCompetition?.competitors?.find { it.homeAway == "away" }
            val summaryHome = summaryCompetition?.competitors?.find { it.homeAway == "home" }
            val updatedEvent = event.copy(
                scoreAway = summaryAway?.score?.toIntOrNull() ?: event.scoreAway,
                scoreHome = summaryHome?.score?.toIntOrNull() ?: event.scoreHome,
                status = if (summaryCompetition?.status?.type?.state != null) {
                    parseStatus(summaryCompetition.status.type.state, summaryCompetition.status.type.name)
                } else event.status,
                gameStatusDetail = summaryCompetition?.status?.type?.detail
                    ?: summaryCompetition?.status?.type?.description
                    ?: event.gameStatusDetail,
                teamStats = teamStats.ifEmpty { event.teamStats },
                playerLeaders = playerLeaders.ifEmpty { event.playerLeaders },
                highlightClips = highlightClips.ifEmpty { event.highlightClips },
                winProbability = winProbability.ifEmpty { event.winProbability },
                playerStatTables = playerStatTables.ifEmpty { event.playerStatTables },
                plays = gamePlays.ifEmpty { event.plays }
            )
            eventCache[event.id] = updatedEvent
            summaryFetchedAt[event.id] = System.currentTimeMillis()
            updatedEvent
        } catch (e: Exception) {
            Log.e("EspnRepo", "Error fetching event summary for ${event.name}: ${e.message}")
            event
        }
    }

    override suspend fun getTeamsForLeague(league: String): List<FavoriteTeam> = withContext(Dispatchers.IO) {
        val path = espnLeagues[league] ?: return@withContext emptyList()
        runCatching {
            val root = api.getJson("https://site.api.espn.com/apis/site/v2/sports/${path.first}/${path.second}/teams")
            collectObjects(root) { obj ->
                val candidate = obj.getAsJsonObject("team") ?: obj
                candidate.has("id") && (candidate.has("displayName") || candidate.has("name"))
            }.mapNotNull { wrapper ->
                val team = wrapper.getAsJsonObject("team") ?: wrapper
                val id = team.string("id") ?: return@mapNotNull null
                val name = team.string("displayName", "name") ?: return@mapNotNull null
                val abbreviation = team.string("abbreviation") ?: name.take(3).uppercase()
                val logo = team.string("logo") ?: team.getAsJsonArray("logos")?.firstOrNull()
                    ?.takeIf { it.isJsonObject }?.asJsonObject?.string("href")
                FavoriteTeam(
                    id = id,
                    league = league,
                    name = name,
                    abbreviation = abbreviation,
                    logoUrl = logo,
                    colors = listOfNotNull(team.string("color"), team.string("alternateColor"))
                )
            }.distinctBy { it.id }.sortedBy { it.name }
        }.getOrElse {
            Log.d("EspnRepository", "Team catalog unavailable for $league: ${it.message}")
            emptyList()
        }
    }

    override suspend fun getTeamHub(team: FavoriteTeam): TeamHub = withContext(Dispatchers.IO) {
        val path = espnLeagues[team.league]
        val events = runCatching { fetchScoreboards().filter { event ->
            event.homeTeam?.id == team.id || event.awayTeam?.id == team.id
        }.sortedBy { it.startTime } }.getOrDefault(emptyList())
        if (path == null) return@withContext TeamHub(team, schedule = events)

        val roster = runCatching {
            val root = api.getJson("https://site.api.espn.com/apis/site/v2/sports/${path.first}/${path.second}/teams/${team.id}/roster")
            collectObjects(root) { obj ->
                obj.has("id") && (obj.has("fullName") || obj.has("displayName")) &&
                    (obj.has("position") || obj.has("jersey") || obj.has("headshot"))
            }.mapNotNull { athlete ->
                val id = athlete.string("id") ?: return@mapNotNull null
                val name = athlete.string("fullName", "displayName") ?: return@mapNotNull null
                TeamPlayer(
                    id = id,
                    name = name,
                    position = athlete.getAsJsonObject("position")?.string("abbreviation", "displayName"),
                    jersey = athlete.string("jersey"),
                    headshotUrl = athlete.getAsJsonObject("headshot")?.string("href")
                )
            }.distinctBy { it.id }.take(80)
        }.getOrDefault(emptyList())

        val detailRoot = runCatching {
            api.getJson("https://site.api.espn.com/apis/site/v2/sports/${path.first}/${path.second}/teams/${team.id}")
        }.getOrNull()
        val summary = detailRoot?.let { findFirstString(it, "summary") }
        val rank = detailRoot?.let { findFirstString(it, "rank") }?.toIntOrNull()
        val standing = summary?.let { TeamStanding(summary = it, rank = rank) }

        val injuries = runCatching {
            val root = api.getJson("https://site.api.espn.com/apis/site/v2/sports/${path.first}/${path.second}/teams/${team.id}/injuries")
            collectObjects(root) { it.has("athlete") && (it.has("status") || it.has("type")) }.mapNotNull { item ->
                val athlete = item.getAsJsonObject("athlete") ?: return@mapNotNull null
                val name = athlete.string("displayName", "fullName") ?: return@mapNotNull null
                val status = item.string("status")
                    ?: item.getAsJsonObject("type")?.string("description", "name")
                    ?: "Injury"
                TeamInjury(name, status, item.string("details", "detail", "description"))
            }.distinctBy { "${it.playerName}:${it.status}" }
        }.getOrDefault(emptyList())

        TeamHub(team = team, standing = standing, schedule = events, roster = roster, injuries = injuries)
    }

    override suspend fun getLeagueHub(league: String): LeagueHub = withContext(Dispatchers.IO) {
        val events = runCatching { getEventsByLeague(league) }.getOrDefault(emptyList())
        val path = espnLeagues[league] ?: return@withContext LeagueHub(league, events)
        val standings = runCatching {
            val root = api.getJson("https://site.api.espn.com/apis/v2/sports/${path.first}/${path.second}/standings")
            collectObjects(root) { it.has("team") && it.has("stats") }.mapNotNull { entry ->
                val teamObj = entry.getAsJsonObject("team") ?: return@mapNotNull null
                val name = teamObj.string("displayName", "name") ?: return@mapNotNull null
                val stats = entry.getAsJsonArray("stats")
                val summary = stats?.mapNotNull { stat ->
                    stat.takeIf { it.isJsonObject }?.asJsonObject?.let { obj ->
                        val label = obj.string("shortDisplayName", "name")
                        val value = obj.string("displayValue")
                        if (label != null && value != null && label.lowercase() in setOf("w", "l", "t", "pct", "gb")) "$label $value" else null
                    }
                }?.take(3)?.joinToString(" · ").orEmpty()
                name to summary
            }.distinctBy { it.first }
        }.getOrDefault(emptyList())
        val postseasonTerms = listOf("playoff", "wild card", "divisional", "conference", "championship", "final", "postseason")
        val postseasonEvents = events.filter { event ->
            val context = "${event.name} ${event.eventContextTitle.orEmpty()} ${event.gameStatusDetail.orEmpty()}".lowercase()
            postseasonTerms.any(context::contains)
        }
        val playoffCutoff = when (league.uppercase()) {
            "NFL" -> 14
            "NBA", "NHL" -> 16
            "MLB" -> 12
            else -> 8
        }
        val playoffPicture = standings.take(playoffCutoff).mapIndexed { index, standing ->
            standing.first to "Seed ${index + 1}${standing.second.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()}"
        }
        LeagueHub(league, events, standings, postseasonEvents, playoffPicture)
    }

    private suspend fun fetchScoreboards(forceRefresh: Boolean = false): List<SportEvent> = withContext(Dispatchers.IO) {
        scoreboardMutex.withLock {
        val now = Instant.now()
        if (cachedEvents.isEmpty()) {
            val restoredEvents = diskCache.read()
            cachedEvents = restoredEvents
            cachedEvents.forEach { eventCache[it.id] = it }
            // Render the last-known schedule immediately on cold start. The regular
            // refresh loop will replace it shortly; the first frame never waits on DNS.
            if (restoredEvents.isNotEmpty() && !forceRefresh) {
                lastFetched = now
                if (backgroundRefreshScheduled.compareAndSet(false, true)) {
                    applicationScope.launch {
                        try {
                            fetchScoreboards(forceRefresh = true)
                        } finally {
                            backgroundRefreshScheduled.set(false)
                        }
                    }
                }
                return@withLock restoredEvents
            }
        }
        // Cache successful and failed attempts for 60 seconds. Without this, every
        // screen could immediately repeat all league requests during a DNS outage.
        if (!forceRefresh && now.minusSeconds(60).isBefore(lastFetched)) {
            return@withLock cachedEvents
        }

        var enabledLeagues = preferencesManager.enabledLeagues
        
        // Clean up old TheSportsDb numerical IDs if they are still cached
        if (enabledLeagues.any { it.all { char -> char.isDigit() } }) {
            enabledLeagues = emptySet()
            preferencesManager.enabledLeagues = emptySet()
        }
        
        if (enabledLeagues.isEmpty()) {
            enabledLeagues = espnLeagues.keys
        }

        val semaphore = Semaphore(3)
        val deferredEvents: List<kotlinx.coroutines.Deferred<Triple<String, List<SportEvent>, Boolean>>> = enabledLeagues.mapNotNull { leagueKey ->
                val mapping = espnLeagues[leagueKey]
            if (mapping != null) {
                val sport = mapping.first
                val league = mapping.second
                async {
                    semaphore.withPermit {
                        try {
                            val dateQuery = scoreboardDateQuery(leagueKey)
                            val response = api.getScoreboard(
                                sport = sport,
                                league = league,
                                dates = dateQuery,
                                limit = 200
                            )
                            val mapped = response.events?.mapNotNull { it.toSportEvent(leagueKey) }.orEmpty().toMutableList()
                            // Daily scoreboards are intentionally small. If a league has
                            // nothing today, make one monthly schedule request so the UI can
                            // still show the next game instead of looking completely empty.
                            if (mapped.isEmpty() && dateQuery != null) {
                                scoreboardFallbackMonthQueries(leagueKey).forEach { month ->
                                    runCatching {
                                        api.getScoreboard(sport, league, month, 1_000)
                                            .events.orEmpty()
                                            .mapNotNull { it.toSportEvent(leagueKey) }
                                    }.onSuccess(mapped::addAll)
                                        .onFailure { Log.w("EspnRepo", "Upcoming schedule fallback failed for $leagueKey", it) }
                                }
                            }
                            Triple(leagueKey, mapped.distinctBy { it.id }, true)
                        } catch (e: Exception) {
                            Log.e("EspnRepo", "fetchScoreboards: Error fetching $leagueKey", e)
                            Triple(leagueKey, emptyList(), false)
                        }
                    }
                }
            } else {
                null
            }
        }

        val results = deferredEvents.awaitAll()
        val successfulLeagues = results.filter { it.third }.mapTo(mutableSetOf()) { it.first }
        lastFetched = now
        if (successfulLeagues.isEmpty()) return@withLock cachedEvents

        val refreshedEvents = results.filter { it.third }.flatMap { it.second }
        val retainedFromFailedLeagues = cachedEvents.filter { it.league !in successfulLeagues }
        val merged = (refreshedEvents + retainedFromFailedLeagues).distinctBy { it.id }
        cachedEvents = merged
        merged.forEach { eventCache[it.id] = it }
        diskCache.write(merged)
        merged
        }
    }

    private fun EspnEvent.toSportEvent(leagueName: String): SportEvent? {
        val competition = competitions?.firstOrNull() ?: run {
            return null
        }
        
        val homeCompetitor = competition.competitors?.find { it.homeAway == "home" }
        val awayCompetitor = competition.competitors?.find { it.homeAway == "away" }
        
        if (homeCompetitor?.team == null || awayCompetitor?.team == null) {
            return null
        }

        val status = parseStatus(competition.status?.type?.state, competition.status?.type?.name)
        val startTime = try {
            if (!date.isNullOrEmpty()) {
                val normalized = if (date.matches(Regex("""^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}Z$"""))) {
                    date.replace("Z", ":00Z")
                } else {
                    date
                }
                Instant.parse(normalized)
            } else {
                Instant.now()
            }
        } catch (e: Exception) {
            Log.e("EspnRepo", "Failed to parse date '$date'", e)
            Instant.now()
        }
        
        // Filter out extreme old data only for finished/non-live events
        if (status != EventStatus.LIVE && status != EventStatus.HALFTIME) {
            if (startTime.isBefore(Instant.now().minusSeconds(12 * 3600))) {
                return null
            }
        }

        // Get TV networks to improve stream matching
        val tvNetworks = competition.broadcasts?.flatMap { it.names ?: emptyList() } ?: emptyList()

        val homeName = homeCompetitor.team.displayName ?: homeCompetitor.team.name ?: "Unknown"
        val awayName = awayCompetitor.team.displayName ?: awayCompetitor.team.name ?: "Unknown"
        val homeAbbr = homeCompetitor.team.abbreviation ?: homeCompetitor.team.name?.take(3)?.uppercase() ?: ""
        val awayAbbr = awayCompetitor.team.abbreviation ?: awayCompetitor.team.name?.take(3)?.uppercase() ?: ""

        val homeTeamModel = Team(id = homeCompetitor.team.id, name = homeName, abbreviation = homeAbbr, logoUrl = homeCompetitor.team.logo)
        val awayTeamModel = Team(id = awayCompetitor.team.id, name = awayName, abbreviation = awayAbbr, logoUrl = awayCompetitor.team.logo)

        val stats = mutableMapOf<String, String>()
        competition.status?.type?.detail?.let { stats["Game Status"] = it }
        homeCompetitor.records?.firstOrNull()?.summary?.let { stats["$homeAbbr Record"] = it }
        awayCompetitor.records?.firstOrNull()?.summary?.let { stats["$awayAbbr Record"] = it }
        if (homeCompetitor.hits != null || awayCompetitor.hits != null) {
            stats["Hits"] = "$homeAbbr ${homeCompetitor.hits ?: 0}  ·  $awayAbbr ${awayCompetitor.hits ?: 0}"
        }
        if (homeCompetitor.errors != null || awayCompetitor.errors != null) {
            stats["Errors"] = "$homeAbbr ${homeCompetitor.errors ?: 0}  ·  $awayAbbr ${awayCompetitor.errors ?: 0}"
        }
        if (tvNetworks.isNotEmpty()) {
            stats["TV Broadcast"] = tvNetworks.joinToString(", ")
        }

        // Smart Contextual Event Title (e.g. "NFL KICKOFF", "AFC CHAMPIONSHIP", "SUPER BOWL", "COLLEGE FOOTBALL KICKOFF")
        val noteHeadline = competition.notes?.firstOrNull()?.headline
            ?: competition.headlines?.firstOrNull()?.shortLinkText
        val contextTitle = when {
            !noteHeadline.isNullOrEmpty() -> noteHeadline.uppercase()
            leagueName == "NFL" -> when {
                season?.type == 3 -> when (week?.number) {
                    1 -> "NFL WILD CARD ROUND"
                    2 -> "NFL DIVISIONAL ROUND"
                    3 -> "AFC / NFC CHAMPIONSHIP"
                    4, 5 -> "SUPER BOWL"
                    else -> "NFL PLAYOFFS"
                }
                season?.type == 2 && week?.number == 1 -> "NFL KICKOFF"
                season?.type == 2 && (week?.number ?: 0) > 1 -> "NFL WEEK ${week?.number}"
                season?.type == 1 -> "NFL PRESEASON"
                else -> "NFL SHOWDOWN"
            }
            leagueName == "NCAAF" -> when {
                season?.type == 3 -> "COLLEGE FOOTBALL PLAYOFF"
                week?.number == 1 -> "COLLEGE FOOTBALL KICKOFF"
                (week?.number ?: 0) > 1 -> "COLLEGE FOOTBALL WEEK ${week?.number}"
                else -> "NCAA FOOTBALL GAMEDAY"
            }
            leagueName == "NCAAB" -> when {
                season?.type == 3 -> "MARCH MADNESS"
                else -> "COLLEGE BASKETBALL GAMEDAY"
            }
            leagueName == "NBA" -> when {
                season?.type == 3 -> "NBA PLAYOFFS"
                else -> "NBA SHOWCASE"
            }
            leagueName == "MLB" -> when {
                season?.type == 3 -> "MLB POSTSEASON"
                else -> "MLB GAMEDAY"
            }
            leagueName == "NHL" -> when {
                season?.type == 3 -> "STANLEY CUP PLAYOFFS"
                else -> "NHL FACEOFF"
            }
            leagueName == "EPL" -> "PREMIER LEAGUE MATCHDAY"
            leagueName == "Champions League" -> "CHAMPIONS LEAGUE MATCHDAY"
            leagueName == "La Liga" -> "LA LIGA SHOWCASE"
            leagueName == "Serie A" -> "SERIE A MATCHDAY"
            leagueName == "MLS" -> "MLS GAMEDAY"
            else -> "${leagueName.uppercase()} SHOWCASE"
        }

        // Game Status Detail (e.g. "3rd Quarter - 5:42", "Top 7th", "2nd Period - 12:30")
        val gameStatusDetail = competition.status?.type?.detail

        val event = SportEvent(
            id = this.id,
            name = this.name ?: "${homeTeamModel.name} vs ${awayTeamModel.name}",
            homeTeam = homeTeamModel,
            awayTeam = awayTeamModel,
            homeTeamBadge = homeCompetitor.team.logo ?: "",
            awayTeamBadge = awayCompetitor.team.logo ?: "",
            scoreHome = homeCompetitor.score?.toIntOrNull(),
            scoreAway = awayCompetitor.score?.toIntOrNull(),
            sport = espnLeagues[leagueName]?.first ?: "unknown",
            league = leagueName,
            status = status,
            venue = competition.venue?.fullName,
            eventContextTitle = contextTitle,
            startTime = startTime,
            liveStats = stats,
            gameStatusDetail = gameStatusDetail
        )
        eventCache[event.id] = event
        return event
    }

    private fun parseStatus(state: String?, name: String?): EventStatus {
        if (state == "post") return EventStatus.FINISHED
        if (state == "in") {
            if (name?.contains("Half", ignoreCase = true) == true) return EventStatus.HALFTIME
            return EventStatus.LIVE
        }
        if (state == "pre") return EventStatus.NOT_STARTED
        return EventStatus.NOT_STARTED
    }

    private fun collectObjects(element: JsonElement, predicate: (JsonObject) -> Boolean): List<JsonObject> {
        val result = mutableListOf<JsonObject>()
        fun visit(value: JsonElement?) {
            when {
                value == null || value.isJsonNull -> Unit
                value.isJsonArray -> value.asJsonArray.forEach(::visit)
                value.isJsonObject -> {
                    val obj = value.asJsonObject
                    if (predicate(obj)) result += obj
                    obj.entrySet().forEach { visit(it.value) }
                }
            }
        }
        visit(element)
        return result
    }

    private fun findFirstString(element: JsonElement, key: String): String? {
        if (element.isJsonObject) {
            val obj = element.asJsonObject
            obj.string(key)?.let { return it }
            obj.entrySet().forEach { (_, value) -> findFirstString(value, key)?.let { return it } }
        } else if (element.isJsonArray) {
            element.asJsonArray.forEach { findFirstString(it, key)?.let { found -> return found } }
        }
        return null
    }

    private fun JsonObject.string(vararg keys: String): String? = keys.firstNotNullOfOrNull { key ->
        runCatching { get(key)?.takeUnless { it.isJsonNull }?.asString?.trim() }
            .getOrNull()?.takeIf { it.isNotEmpty() }
    }
}

private val espnDateFormatter: DateTimeFormatter = DateTimeFormatter.BASIC_ISO_DATE

internal fun scoreboardDateQuery(league: String, today: LocalDate = LocalDate.now()): String? =
    if (league == "NFL" || league == "NCAAF") null else today.format(espnDateFormatter)

private val espnMonthFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMM")

internal fun scoreboardFallbackMonthQueries(league: String, today: LocalDate = LocalDate.now()): List<String> {
    if (league == "NFL" || league == "NCAAF") return emptyList()
    val currentMonth = today.format(espnMonthFormatter)
    val endOfWindowMonth = today.plusDays(7).format(espnMonthFormatter)
    return if (currentMonth == endOfWindowMonth) listOf(currentMonth) else listOf(currentMonth, endOfWindowMonth)
}
