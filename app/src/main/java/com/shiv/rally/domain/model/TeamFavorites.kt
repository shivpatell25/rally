package com.shiv.rally.domain.model

/** ESPN team IDs are only unique within a league (NFL 7 and NBA 7 are different teams). */
fun favoriteTeamKey(league: String, teamId: String): String = "${league.uppercase()}:$teamId"
fun SportEvent.matchesFavoriteTeamKeys(keys: Set<String>): Boolean =
    listOfNotNull(homeTeam?.id, awayTeam?.id).any { favoriteTeamKey(league, it) in keys }
fun SportEvent.matchesFavoriteTeams(teams: Collection<FavoriteTeam>): Boolean =
    teams.any { it.league.equals(league, true) && (homeTeam?.id == it.id || awayTeam?.id == it.id) }
