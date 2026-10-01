package com.shiv.rally.data.remote.sports

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Url

interface EspnApi {
    @GET
    suspend fun getJson(@Url url: String): JsonElement

    @GET("sports/{sport}/{league}/scoreboard")
    suspend fun getScoreboard(
        @Path("sport") sport: String,
        @Path("league") league: String,
        @Query("dates") dates: String?,
        @Query("limit") limit: Int
    ): EspnScoreboardResponse

    @GET("sports/{sport}/{league}/summary")
    suspend fun getSummary(
        @Path("sport") sport: String,
        @Path("league") league: String,
        @Query("event") eventId: String
    ): EspnSummaryResponse
}

data class EspnScoreboardResponse(
    val events: List<EspnEvent>?
)

data class EspnEvent(
    val id: String,
    val date: String?,
    val name: String?,
    val shortName: String?,
    val season: EspnSeason? = null,
    val week: EspnWeek? = null,
    val competitions: List<EspnCompetition>?
)

data class EspnSeason(
    val year: Int?,
    val type: Int?,
    val slug: String?
)

data class EspnWeek(
    val number: Int?,
    val text: String? = null
)

data class EspnCompetition(
    val status: EspnStatus?,
    val competitors: List<EspnCompetitor>?,
    val broadcasts: List<EspnBroadcast>?,
    val venue: EspnVenue? = null,
    val weather: EspnWeather? = null,
    val notes: List<EspnNote>? = null,
    val headlines: List<EspnHeadline>? = null,
    val situation: EspnSituation? = null,
    val date: String? = null
)

data class EspnSituation(
    val down: Int? = null,
    val distance: Int? = null,
    val shortDownDistanceText: String? = null,
    val downDistanceText: String? = null,
    val possession: String? = null,
    val yardLine: Int? = null,
    val isRedZone: Boolean? = null
)

data class EspnNote(
    val type: String?,
    val headline: String?
)

data class EspnHeadline(
    val description: String?,
    val shortLinkText: String?
)

data class EspnVenue(
    val fullName: String?,
    val address: EspnVenueAddress? = null,
    val images: List<EspnVenueImage>? = null
)

data class EspnVenueImage(val href: String? = null)
data class EspnGameInfo(val venue: EspnVenue? = null)

data class EspnVenueAddress(val city: String? = null, val state: String? = null)
data class EspnWeather(val displayValue: String? = null, val temperature: Int? = null)

data class EspnStatus(
    val type: EspnStatusType?
)

data class EspnStatusType(
    val name: String?,
    val state: String?,
    val completed: Boolean?,
    val description: String?,
    val detail: String? = null,
    val shortDetail: String? = null
)

data class EspnCompetitor(
    val homeAway: String?,
    val team: EspnTeam?,
    val score: String?,
    val hits: Int? = null,
    val errors: Int? = null,
    @SerializedName(value = "records", alternate = ["record"])
    val records: List<EspnRecord>? = null
)

data class EspnRecord(
    @SerializedName(value = "name", alternate = ["type"])
    val name: String?,
    val summary: String?
)

data class EspnTeam(
    val id: String,
    val name: String?,
    val displayName: String?,
    val abbreviation: String?,
    val logo: String?,
    val color: String? = null,
    val alternateColor: String? = null,
    val logos: List<EspnTeamLogo>? = null
) {
    // Scoreboards use `logo`; historical game summaries use `logos`.
    val logoImage: String?
        get() = logo?.takeIf(String::isNotBlank)
            ?: logos?.firstNotNullOfOrNull { it.href?.takeIf(String::isNotBlank) }
}

data class EspnTeamLogo(val href: String? = null)

data class EspnBroadcast(
    val names: List<String>?
)

data class EspnSummaryResponse(
    val gameInfo: EspnGameInfo? = null,
    val boxscore: EspnBoxscore? = null,
    val leaders: List<EspnLeaderGroup>? = null,
    val header: EspnSummaryHeader? = null,
    val pickcenter: List<EspnPickcenterItem>? = null,
    val predictor: EspnPredictor? = null,
    val videos: List<EspnVideo>? = null,
    val plays: List<EspnPlay>? = null,
    val winprobability: List<EspnWinProbability>? = null
)

data class EspnVideo(
    val id: Long? = null,
    val headline: String? = null,
    val description: String? = null,
    val duration: Int? = null,
    val thumbnail: String? = null,
    val links: EspnVideoLinks? = null
)

data class EspnVideoLinks(
    val web: EspnHref? = null,
    val source: EspnVideoSources? = null,
    val mobile: EspnVideoMobile? = null
)

data class EspnVideoMobile(val source: EspnHref? = null)

data class EspnVideoSources(
    val href: String? = null,
    val HD: EspnHref? = null,
    val HLS: EspnHlsSource? = null
)

data class EspnHlsSource(
    val href: String? = null,
    val HD: EspnHref? = null
)

data class EspnHref(val href: String? = null)

data class EspnPlay(
    val id: String? = null,
    val sequenceNumber: String? = null,
    val text: String? = null,
    val awayScore: Int? = null,
    val homeScore: Int? = null,
    val period: EspnPlayPeriod? = null,
    val clock: EspnPlayClock? = null,
    val scoringPlay: Boolean? = null,
    val wallclock: String? = null
)

data class EspnPlayPeriod(val number: Int? = null, val displayValue: String? = null)
data class EspnPlayClock(val displayValue: String? = null)

data class EspnWinProbability(
    val homeWinPercentage: Double? = null,
    val tiePercentage: Double? = null,
    val playId: String? = null
)

data class EspnPickcenterItem(
    val spread: Double? = null,
    val overUnder: Double? = null,
    val details: String? = null,
    val awayTeamOdds: EspnTeamOdds? = null,
    val homeTeamOdds: EspnTeamOdds? = null
)

data class EspnTeamOdds(
    val moneyLine: Int? = null,
    val spreadOdds: Double? = null
)

data class EspnPredictor(
    val header: String? = null,
    val homeTeam: EspnPredictorTeam? = null,
    val awayTeam: EspnPredictorTeam? = null
)

data class EspnPredictorTeam(
    val gameProjection: String? = null,
    val teamChanceLoss: String? = null
)

data class EspnSummaryHeader(
    val id: String? = null,
    val season: EspnSeason? = null,
    val competitions: List<EspnCompetition>? = null
)

data class EspnBoxscore(
    val teams: List<EspnBoxscoreTeam>? = null,
    val players: List<EspnBoxscorePlayerGroup>? = null
)

data class EspnBoxscorePlayerGroup(
    val team: EspnTeam? = null,
    val statistics: List<EspnBoxscorePlayerCategory>? = null
)

data class EspnBoxscorePlayerCategory(
    val name: String? = null,
    val labels: List<String>? = null,
    val descriptions: List<String>? = null,
    val athletes: List<EspnBoxscoreAthleteItem>? = null
)

data class EspnBoxscoreAthleteItem(
    val athlete: EspnAthlete? = null,
    val stats: List<String>? = null
)

data class EspnBoxscoreTeam(
    val team: EspnTeam? = null,
    val statistics: List<EspnStatistic>? = null
)

data class EspnStatistic(
    val name: String? = null,
    val displayValue: String? = null,
    val label: String? = null
)

data class EspnLeaderGroup(
    val team: EspnTeam? = null,
    val leaders: List<EspnLeaderCategory>? = null
)

data class EspnLeaderCategory(
    val name: String? = null,
    val displayName: String? = null,
    val leaders: List<EspnLeaderItem>? = null
)

data class EspnLeaderItem(
    val displayValue: String? = null,
    val value: Double? = null,
    val athlete: EspnAthlete? = null
)

data class EspnAthlete(
    val id: String? = null,
    val fullName: String? = null,
    val displayName: String? = null,
    val shortName: String? = null,
    val headshot: EspnHeadshot? = null,
    val position: EspnPosition? = null,
    val jersey: String? = null
)

data class EspnHeadshot(
    val href: String? = null
)

data class EspnPosition(
    val name: String? = null,
    val displayName: String? = null,
    val abbreviation: String? = null
)
