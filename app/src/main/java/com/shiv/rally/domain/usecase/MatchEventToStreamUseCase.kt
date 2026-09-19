package com.shiv.rally.domain.usecase

import com.shiv.rally.domain.model.IptvChannel
import com.shiv.rally.domain.model.MatchResult
import com.shiv.rally.domain.model.RelevantChannel
import com.shiv.rally.domain.model.SportEvent
import com.shiv.rally.domain.repository.SportsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class MatchEventToStreamUseCase @Inject constructor(
    private val sportsRepository: SportsRepository
) : MatcherService {

    companion object {
        // Pre-compiled regex cache for matchesWord — avoids re-creating on every call
        private val wordRegexCache = java.util.concurrent.ConcurrentHashMap<String, Regex>()
        // Pre-compiled regex cache for broadcast station matching
        private val stationRegexCache = java.util.concurrent.ConcurrentHashMap<String, Regex>()
        // Station alias cache
        private val stationAliasCache = java.util.concurrent.ConcurrentHashMap<String, List<String>>()
    }

    override suspend fun matchEventToChannels(
        event: SportEvent,
        channels: List<IptvChannel>
    ): List<MatchResult> = withContext(Dispatchers.Default) {
        val results = mutableListOf<MatchResult>()
        
        // 1. Fetch TV stations for this event
        val tvStations = sportsRepository.getTvStationsForEvent(event.id)
            .map { it.lowercase() }
            // Some clean up on common TV station names to match IPTV names better
            .map { it.replace(" network", "").replace(" channel", "").trim() }
        
        // 2. Base keywords
        val keywords = mutableListOf<String>()
        event.homeTeam?.let {
            keywords.add(it.name)
            keywords.add(it.abbreviation)
            keywords.addAll(it.name.split(" "))
        }
        event.awayTeam?.let {
            keywords.add(it.name)
            keywords.add(it.abbreviation)
            keywords.addAll(it.name.split(" "))
        }
        keywords.add(event.league)
        keywords.add(event.sport)
        
        val distinctKeywords = keywords.map { it.lowercase() }.filter { it.length > 2 }.distinct()
        
        for (channel in channels) {
            val channelNameLower = channel.name.lowercase()
            var score: Float
            
            // Check if this channel matches one of the broadcast TV stations
            val hasTvStationMatch = tvStations.any { station -> 
                station.isNotEmpty() && station.length > 2 && channelNameLower.contains(station) 
            }
            
            var keywordMatches = 0
            for (keyword in distinctKeywords) {
                if (channelNameLower.contains(keyword)) {
                    keywordMatches++
                }
            }
            
            // Normalize score to 0.0 - 1.0 based on matches
            if (keywordMatches > 0 || hasTvStationMatch) {
                val hasLeague = event.league.isNotEmpty() && channelNameLower.contains(event.league.lowercase())
                
                val homeKeywords = event.homeTeam?.let { team ->
                    (listOf(team.name, team.abbreviation) + team.name.split(" "))
                        .map { kw -> kw.lowercase() }
                        .filter { kw -> kw.length > 2 }
                } ?: emptyList()

                val awayKeywords = event.awayTeam?.let { team ->
                    (listOf(team.name, team.abbreviation) + team.name.split(" "))
                        .map { kw -> kw.lowercase() }
                        .filter { kw -> kw.length > 2 }
                } ?: emptyList()

                val hasHomeTeam = homeKeywords.any { kw -> channelNameLower.contains(kw) }
                val hasAwayTeam = awayKeywords.any { kw -> channelNameLower.contains(kw) }
                                  
                if (hasTvStationMatch && (hasHomeTeam || hasAwayTeam || hasLeague)) {
                    // Highest priority: The broadcast station matches AND mentions the team or league
                    score = 0.98f
                } else if (hasTvStationMatch) {
                    // Very high priority: It matches the official broadcast network exactly
                    score = 0.92f
                } else if (hasLeague && (hasHomeTeam || hasAwayTeam)) {
                    score = 0.9f
                } else if (hasHomeTeam && hasAwayTeam) {
                    score = 0.85f
                } else if (hasHomeTeam || hasAwayTeam) {
                    score = 0.7f
                } else if (hasLeague) {
                    score = 0.4f
                } else {
                    score = 0.1f * keywordMatches
                }
                
                // Cap at 1.0
                score = score.coerceAtMost(1.0f)
                
                results.add(MatchResult(event, channel, score))
            }
        }
        
        results.sortedByDescending { it.confidenceScore }
    }

    override suspend fun getRelevantChannelsForEvent(
        event: SportEvent,
        channels: List<IptvChannel>
    ): List<RelevantChannel> = withContext(Dispatchers.Default) {
        val rawStations = sportsRepository.getTvStationsForEvent(event.id)
        val tvStations = rawStations
            .flatMap { raw ->
                raw.split(",", "/", "&", "+", "|").map { it.trim() }
            }
            .filter { it.isNotEmpty() && it.length >= 2 }
            .distinct()

        val homeTeam = event.homeTeam
        val awayTeam = event.awayTeam
        val homeName = homeTeam?.name?.lowercase() ?: ""
        val awayName = awayTeam?.name?.lowercase() ?: ""
        val homeAbbr = homeTeam?.abbreviation?.lowercase() ?: ""
        val awayAbbr = awayTeam?.abbreviation?.lowercase() ?: ""
        val homeParts = homeName.split(" ").filter { it.isNotBlank() }
        val awayParts = awayName.split(" ").filter { it.isNotBlank() }
        val homeCity = homeParts.dropLast(1).joinToString(" ")
        val awayCity = awayParts.dropLast(1).joinToString(" ")
        val homeNickname = homeParts.lastOrNull() ?: ""
        val awayNickname = awayParts.lastOrNull() ?: ""

        val leagueLower = event.league.lowercase()
        val sportLower = event.sport.lowercase()

        val results = mutableListOf<RelevantChannel>()

        for (channel in channels) {
            val nameLower = channel.name.lowercase()
            val categoryLower = channel.category.lowercase()

            // Filter out dead, test, or replay/vault channels
            val isReplayOrDead = nameLower.contains("replay") ||
                    nameLower.contains("classic") ||
                    nameLower.contains("rewind") ||
                    nameLower.contains("vault") ||
                    nameLower.contains("offline") ||
                    nameLower.contains("test")
            if (isReplayOrDead) continue

            val isConflicting = isConflictingSport(nameLower, categoryLower, leagueLower, sportLower)

            // 1. Determine if this channel matches the official ESPN broadcast (with full alias resolution)
            var matchedStationName: String? = null
            var isOfficialBroadcast = false
            for (station in tvStations) {
                val aliases = getStationAliases(station)
                if (aliases.any { matchesBroadcastStation(nameLower, it.lowercase()) }) {
                    matchedStationName = station
                    isOfficialBroadcast = true
                    break
                }
            }

            // 2. Check team matchup matches
            val isCollege = leagueLower.startsWith("ncaa") || leagueLower.contains("college")
            val hasLeagueOrSport = nameLower.contains(leagueLower) || categoryLower.contains(leagueLower) ||
                    nameLower.contains(sportLower) || categoryLower.contains(sportLower) ||
                    (isCollege && (nameLower.contains("college") || categoryLower.contains("college") ||
                                   nameLower.contains("sec") || nameLower.contains("acc") ||
                                   nameLower.contains("big ten") || nameLower.contains("btn") ||
                                   nameLower.contains("ncaa") || categoryLower.contains("ncaa")))

            val homeAbbrMatch = homeAbbr.length in 2..6 && matchesWord(nameLower, homeAbbr) &&
                    (hasLeagueOrSport || nameLower.contains("vs") || nameLower.contains("@"))
            val awayAbbrMatch = awayAbbr.length in 2..6 && matchesWord(nameLower, awayAbbr) &&
                    (hasLeagueOrSport || nameLower.contains("vs") || nameLower.contains("@"))

            val homeFullNameMatch = homeName.length > 3 && nameLower.contains(homeName)
            val awayFullNameMatch = awayName.length > 3 && nameLower.contains(awayName)

            val homeCityMatch = homeCity.length > 3 && matchesWord(nameLower, homeCity)
            val awayCityMatch = awayCity.length > 3 && matchesWord(nameLower, awayCity)

            val homeNicknameMatch = homeNickname.length > 2 && matchesWord(nameLower, homeNickname)
            val awayNicknameMatch = awayNickname.length > 2 && matchesWord(nameLower, awayNickname)

            val bothNicknamesMatch = homeNickname.length > 2 && awayNickname.length > 2 &&
                    matchesWord(nameLower, homeNickname) && matchesWord(nameLower, awayNickname)
            val bothNamesMatch = (homeFullNameMatch && awayFullNameMatch) ||
                    (homeFullNameMatch && awayNicknameMatch) ||
                    (awayFullNameMatch && homeNicknameMatch)

            // Regional Sports Networks (RSN) detection
            val isHomeRsn = isTeamRsn(nameLower, homeCity, homeNickname)
            val isAwayRsn = isTeamRsn(nameLower, awayCity, awayNickname)

            // Nickname or city alone must have league/sport context to avoid generic collisions (e.g. "Tigers" or "Lions" in other sports)
            val hasHomeTeam = !isConflicting && (
                homeFullNameMatch ||
                bothNicknamesMatch ||
                isHomeRsn ||
                (homeCityMatch && homeNicknameMatch) ||
                (homeCityMatch && hasLeagueOrSport) ||
                (homeNicknameMatch && hasLeagueOrSport) ||
                homeAbbrMatch
            )
            val hasAwayTeam = !isConflicting && (
                awayFullNameMatch ||
                bothNicknamesMatch ||
                isAwayRsn ||
                (awayCityMatch && awayNicknameMatch) ||
                (awayCityMatch && hasLeagueOrSport) ||
                (awayNicknameMatch && hasLeagueOrSport) ||
                awayAbbrMatch
            )

            val isBothTeamsMatch = !isConflicting && (bothNicknamesMatch || bothNamesMatch || (hasHomeTeam && hasAwayTeam))

            // League package matching (MLB Extra Innings, NBA League Pass, NFL Sunday Ticket, NHL Center Ice)
            val isLeaguePackageMatch = when (leagueLower) {
                "mlb" -> (nameLower.contains("mlb extra") || nameLower.contains("mlb ei") || (nameLower.contains("mlb") && (nameLower.contains("vs") || nameLower.contains("@"))))
                "nba" -> (nameLower.contains("nba league pass") || nameLower.contains("nba lp") || (nameLower.contains("nba") && (nameLower.contains("vs") || nameLower.contains("@"))))
                "nfl" -> (nameLower.contains("sunday ticket") || nameLower.contains("nfl st") || (nameLower.contains("nfl") && (nameLower.contains("vs") || nameLower.contains("@"))))
                "nhl" -> (nameLower.contains("center ice") || nameLower.contains("nhl ci") || (nameLower.contains("nhl") && (nameLower.contains("vs") || nameLower.contains("@"))))
                else -> false
            } && (hasHomeTeam || hasAwayTeam)

            val isSingleTeamMatch = (hasHomeTeam || hasAwayTeam) &&
                    (nameLower.contains("vs") || nameLower.contains("@") || hasLeagueOrSport || isHomeRsn || isAwayRsn)
            val isDirectGameMatch = isBothTeamsMatch || isLeaguePackageMatch || isSingleTeamMatch

            // 3. Filter: Only sports-related channels
            val isSports = isDirectGameMatch || isOfficialBroadcast || isSportsChannel(channel)
            if (!isSports) continue

            // 4. Calculate Likelihood Score based on ESPN data
            var score = 0.0f
            val badge: String?

            if (isBothTeamsMatch) {
                // Tier 1: Dedicated game feed matching both teams
                score += 100.0f
                badge = "Game Matchup"
            } else if (isLeaguePackageMatch) {
                // Dedicated out-of-market league game feed
                score += 96.0f
                val teamName = if (hasHomeTeam) homeTeam?.name else awayTeam?.name
                badge = if (teamName != null) "$teamName Live Feed" else "Live Game Feed"
            } else if (isOfficialBroadcast && matchedStationName != null) {
                // Tier 2: Official ESPN broadcast network (FOX, NBC, CBS, ABC, ESPN, etc.)
                val isNicheNonEventSport = isConflicting ||
                        (sportLower != "golf" && nameLower.contains("golf")) ||
                        (sportLower != "tennis" && nameLower.contains("tennis")) ||
                        (sportLower != "racing" && (nameLower.contains("racing") || nameLower.contains("f1") || nameLower.contains("nascar")))

                if (isNicheNonEventSport) {
                    score += 55.0f
                    badge = "Official Broadcast: $matchedStationName"
                } else {
                    score += 90.0f
                    badge = "Official Broadcast: $matchedStationName"

                    // Bonus if sports-specific feed of the network (e.g., NBC Sports, FOX Sports)
                    if (nameLower.contains("sports") || nameLower.contains("sport") || hasLeagueOrSport) {
                        score += 3.0f
                    }
                    // Bonus for team's home market feed
                    if ((homeCity.length > 3 && nameLower.contains(homeCity)) ||
                        (awayCity.length > 3 && nameLower.contains(awayCity))) {
                        score += 2.0f
                    }
                }
            } else if (isSingleTeamMatch) {
                // Dedicated single team channel / RSN in current league
                score += 82.0f
                val teamName = if (hasHomeTeam) homeTeam?.name else awayTeam?.name
                badge = if (isHomeRsn || isAwayRsn) {
                    if (teamName != null) "$teamName (RSN)" else "Regional Sports"
                } else {
                    if (teamName != null) "$teamName Feed" else "Team Feed"
                }
            } else {
                // Tier 3: Dedicated League Channels
                val isDedicatedLeague = isDedicatedLeagueChannel(nameLower, categoryLower, leagueLower)
                if (isDedicatedLeague != null) {
                    score += 35.0f
                    badge = isDedicatedLeague
                } else if (nameLower.contains(leagueLower) || categoryLower.contains(leagueLower)) {
                    score += 25.0f
                    badge = "${event.league} Channel"
                } else if (isMajorSportsNetwork(nameLower)) {
                    // Tier 4: Major Tier-1 Sports Networks
                    score += 15.0f
                    badge = "Sports Network"
                } else {
                    // Tier 5: General Sports Channels
                    score += 5.0f
                    badge = channel.category.ifEmpty { "Sports" }
                }
            }

            // Quality Bonuses (4K / 60fps / 1080p)
            val quality = com.shiv.rally.domain.model.parseQualityFromChannelName(channel.name)
            if (quality.is4K) {
                score += 4.0f
            } else if (quality.resolution?.contains("1080") == true) {
                score += 1.5f
            }
            if (quality.fps?.contains("60") == true || quality.fps?.contains("50") == true) {
                score += 2.0f
            }

            // Region preference: Prioritize English US/CA/UK feeds for North American / European leagues
            if (nameLower.startsWith("us") || nameLower.contains("us |") || nameLower.contains("us:") ||
                nameLower.startsWith("ca") || nameLower.contains("ca |") ||
                nameLower.startsWith("uk") || nameLower.contains("uk |")) {
                score += 1.0f
            }

            results.add(
                RelevantChannel(
                    channel = channel,
                    likelihoodScore = score.coerceIn(0f, 100f),
                    matchBadge = badge,
                    isOfficialBroadcast = isOfficialBroadcast
                )
            )
        }

        // Sort with highest likelihood first, then channel number / alphabetically
        results.sortedWith(
            compareByDescending<RelevantChannel> { it.likelihoodScore }
                .thenBy { it.channel.number.toIntOrNull() ?: 99999 }
                .thenBy { it.channel.name }
        )
    }

    /**
     * Checks if a channel or category is dedicated to a conflicting sport/league.
     */
    private fun isConflictingSport(
        nameLower: String,
        catLower: String,
        leagueLower: String,
        sportLower: String
    ): Boolean {
        val nflConflicts = listOf("mlb", "milb", "baseball", "nhl", "hockey", "nba", "wnba", "basketball", "cfl", "cricket", "golf", "tennis", "rugby", "afl", "motorsport", "f1", "nascar")
        val nbaConflicts = listOf("mlb", "milb", "baseball", "nhl", "hockey", "nfl", "football", "cfl", "cricket", "golf", "tennis", "rugby", "motorsport")
        val mlbConflicts = listOf("nfl", "football", "nhl", "hockey", "nba", "wnba", "basketball", "cricket", "golf", "tennis", "rugby", "motorsport")
        val nhlConflicts = listOf("nfl", "football", "mlb", "milb", "baseball", "nba", "wnba", "basketball", "cricket", "golf", "tennis", "rugby")

        val conflicts = when (leagueLower) {
            "nfl" -> nflConflicts
            "nba" -> nbaConflicts
            "mlb" -> mlbConflicts
            "nhl" -> nhlConflicts
            else -> when (sportLower) {
                "football" -> nflConflicts
                "basketball" -> nbaConflicts
                "baseball" -> mlbConflicts
                "hockey" -> nhlConflicts
                else -> emptyList()
            }
        }

        for (conflict in conflicts) {
            if (catLower.contains(conflict) || matchesWord(nameLower, conflict)) {
                return true
            }
        }
        return false
    }

    /**
     * Checks whether a channel is sports-related based on its category and name.
     */
    private fun isSportsChannel(channel: IptvChannel): Boolean {
        val cat = channel.category.lowercase()
        val name = channel.name.lowercase()

        // Definite blacklist categories
        val blacklistedCategories = listOf(
            "news", "kids", "children", "cartoon", "movie", "movies", "cinema",
            "series", "vod", "music", "radio", "religious", "islam", "christian",
            "xxx", "adult", "for adult", "docu", "documentary", "shopping"
        )
        if (blacklistedCategories.any { cat.contains(it) } && !isMajorSportsNetwork(name)) {
            return false
        }

        // Sports category keywords
        val sportsCategoryKeywords = listOf(
            "sport", "espn", "football", "soccer", "futbol", "nfl", "nba", "mlb",
            "nhl", "basketball", "baseball", "hockey", "tennis", "golf", "racing",
            "f1", "formula", "nascar", "motogp", "motorsport", "fight", "mma",
            "ufc", "boxing", "wwe", "cricket", "rugby", "afl", "athletics",
            "olympic", "ppv", "live event", "events", "stadium"
        )
        if (sportsCategoryKeywords.any { cat.contains(it) }) {
            return true
        }

        // Sports channel name keywords
        return isMajorSportsNetwork(name)
    }

    /**
     * Resolves common broadcast station aliases (e.g. SECN -> SEC Network, ACCN -> ACC Network, FS1 -> Fox Sports 1).
     */
    private fun getStationAliases(station: String): List<String> {
        val s = station.lowercase().trim()
        return stationAliasCache.getOrPut(s) {
            val aliases = mutableListOf(s)
            when {
                s.contains("sec network") || s == "secn" || s == "sec" -> {
                    aliases.addAll(listOf("sec network", "secn", "sec net", "sec", "sec+"))
                }
                s.contains("acc network") || s == "accn" || s == "acc" -> {
                    aliases.addAll(listOf("acc network", "accn", "acc net", "acc", "accnx"))
                }
                s.contains("big ten") || s.contains("btn") -> {
                    aliases.addAll(listOf("big ten network", "big ten", "btn"))
                }
                s.contains("cbs sports network") || s == "cbssn" || s == "cbs sports" -> {
                    aliases.addAll(listOf("cbs sports network", "cbssn", "cbs sports net", "cbs sports"))
                }
                s == "fs1" || s.contains("fox sports 1") -> {
                    aliases.addAll(listOf("fox sports 1", "fs1", "fs 1"))
                }
                s == "fs2" || s.contains("fox sports 2") -> {
                    aliases.addAll(listOf("fox sports 2", "fs2", "fs 2"))
                }
                s.contains("espnu") -> {
                    aliases.addAll(listOf("espnu", "espn u"))
                }
                s.contains("espn2") || s.contains("espn 2") -> {
                    aliases.addAll(listOf("espn2", "espn 2"))
                }
                s.contains("espn+") || s.contains("espn plus") -> {
                    aliases.addAll(listOf("espn+", "espn plus", "espnplus"))
                }
                s.contains("tnt") -> {
                    aliases.addAll(listOf("tnt", "tnt sports"))
                }
                s.contains("tbs") -> {
                    aliases.addAll(listOf("tbs"))
                }
                s.contains("nbcsn") || s.contains("nbc sports") -> {
                    aliases.addAll(listOf("nbc sports", "nbcsn", "nbc sports net"))
                }
                s.contains("fanduel") || s.contains("bally") -> {
                    aliases.addAll(listOf("fanduel sports", "fanduel", "bally sports", "bally"))
                }
            }
            aliases.distinct()
        }
    }

    /**
     * Identifies major sports networks even if in general categories.
     */
    private fun isMajorSportsNetwork(nameLower: String): Boolean {
        val majorSportsKeywords = listOf(
            "sports", "sport", "espn", "fox sports", "fs1", "fs2", "nbcsn", "nbc sports",
            "cbs sports", "cbssn", "tnt sports", "sky sports", "bally", "fanduel", "yes network",
            "nesn", "masn", "marquee", "altitude", "sportsnet", "tsn", "bein", "dazn",
            "super sport", "optus", "nfl network", "nfl redzone", "nba tv", "mlb network",
            "nhl network", "sec network", "secn", "acc network", "accn", "big ten", "btn", "pac-12",
            "golf channel", "tennis channel", "fight network", "ufc", "boxnation", "wwe",
            "willow", "star sports", "sony ten", "abc", "cbs", "nbc", "fox", "tnt", "tbs",
            "trutv", "usa network", "peacock"
        )
        return majorSportsKeywords.any { nameLower.contains(it) }
    }

    /**
     * Checks if channel is a dedicated league feed channel.
     */
    private fun isDedicatedLeagueChannel(nameLower: String, catLower: String, leagueLower: String): String? {
        return when {
            leagueLower == "nfl" && (nameLower.contains("nfl network") || nameLower.contains("nfl redzone") || nameLower.contains("nfl sunday") || nameLower.contains("nfl game pass") || catLower.contains("nfl")) -> "NFL Network"
            leagueLower == "nba" && (nameLower.contains("nba tv") || nameLower.contains("nba league pass") || catLower.contains("nba")) -> "NBA TV"
            leagueLower == "mlb" && (nameLower.contains("mlb network") || nameLower.contains("mlb extra") || catLower.contains("mlb")) -> "MLB Network"
            leagueLower == "nhl" && (nameLower.contains("nhl network") || nameLower.contains("nhl center") || catLower.contains("nhl")) -> "NHL Network"
            (leagueLower == "epl" || leagueLower.contains("premier")) && (nameLower.contains("premier league") || nameLower.contains("sky sports pl") || nameLower.contains("tnt sports") || nameLower.contains("usa network")) -> "Premier League"
            (leagueLower == "ncaaf" || leagueLower == "ncaab") && (nameLower.contains("sec network") || nameLower.contains("acc network") || nameLower.contains("big ten") || nameLower.contains("btn")) -> "College Sports"
            else -> null
        }
    }

    /**
     * Accurately matches ESPN broadcast stations (like FOX, CBS, NBC, ABC, ESPN, TNT, etc.)
     * avoiding false positives in words like "BABCOCK" or "FOXO".
     */
    private fun matchesBroadcastStation(channelNameLower: String, stationLower: String): Boolean {
        if (stationLower.isEmpty()) return false
        val cleanStation = stationLower
            .replace(" network", "")
            .replace(" channel", "")
            .replace(" hd", "")
            .replace(" tv", "")
            .trim()

        if (cleanStation.length < 2) return false

        // Normalize dots for stations like MLB.TV -> mlbtv / mlb
        val cleanNoDot = cleanStation.replace(".", "")

        // 1. Substring check with word boundaries or separators (cached regex)
        val regex = stationRegexCache.getOrPut("station_$cleanStation") {
            Regex("""(?i)(?:^|[\s|:_\-\[/])""" + Regex.escape(cleanStation) + """(?:$|[\s|:_\-\]\d/])""")
        }
        if (regex.containsMatchIn(channelNameLower)) {
            return true
        }

        if (cleanNoDot != cleanStation) {
            val regexNoDot = stationRegexCache.getOrPut("station_nodot_$cleanNoDot") {
                Regex("""(?i)(?:^|[\s|:_\-\[/])""" + Regex.escape(cleanNoDot) + """(?:$|[\s|:_\-\]\d/])""")
            }
            if (regexNoDot.containsMatchIn(channelNameLower)) {
                return true
            }
        }

        // 2. Exact word match
        return channelNameLower.split(" ", "|", ":", "-", "_", "/").any { 
            val token = it.trim()
            token.equals(cleanStation, ignoreCase = true) || (cleanNoDot != cleanStation && token.equals(cleanNoDot, ignoreCase = true))
        }
    }

    /**
     * Checks if a channel is the designated Regional Sports Network (RSN) for a team.
     */
    private fun isTeamRsn(channelNameLower: String, city: String, nickname: String): Boolean {
        if (city.length < 3 && nickname.length < 3) return false

        // Boston: NESN
        if ((city == "boston" || nickname.contains("sox") || nickname == "bruins") && channelNameLower.contains("nesn")) return true
        // Baltimore / Washington: MASN
        if ((city == "baltimore" || city == "washington" || nickname == "orioles" || nickname == "nationals") && channelNameLower.contains("masn")) return true
        // NY Yankees / Brooklyn: YES Network
        if ((city == "new york" || nickname == "yankees" || nickname == "nets") && (channelNameLower.contains("yes network") || channelNameLower.contains("yes hd"))) return true
        // NY Mets: SNY
        if ((city == "new york" || nickname == "mets") && channelNameLower.contains("sny")) return true
        // Chicago Cubs: Marquee
        if ((city == "chicago" || nickname == "cubs") && channelNameLower.contains("marquee")) return true
        // LA Dodgers / Lakers: SportsNet LA / Spectrum
        if ((city == "los angeles" || nickname == "dodgers" || nickname == "lakers") && (channelNameLower.contains("sportsnet la") || channelNameLower.contains("spectrum sports"))) return true
        // Philadelphia: NBC Sports Philadelphia
        if ((city == "philadelphia" || nickname == "phillies" || nickname == "flyers" || nickname == "sixers") && channelNameLower.contains("nbc") && channelNameLower.contains("phil")) return true
        // Detroit: Bally Sports Detroit / FanDuel
        if ((city == "detroit" || nickname == "tigers" || nickname == "pistons") && (channelNameLower.contains("bally") || channelNameLower.contains("fanduel")) && channelNameLower.contains("det")) return true
        // Denver: Altitude
        if ((city == "denver" || city == "colorado" || nickname == "nuggets" || nickname == "avalanche") && channelNameLower.contains("altitude")) return true

        return false
    }

    private fun matchesWord(text: String, word: String): Boolean {
        val regex = wordRegexCache.getOrPut(word) {
            Regex("""(?i)\b""" + Regex.escape(word) + """\b""")
        }
        return regex.containsMatchIn(text)
    }
}
