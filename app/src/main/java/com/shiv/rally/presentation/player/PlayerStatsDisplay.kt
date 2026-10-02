package com.shiv.rally.presentation.player

import com.shiv.rally.domain.model.PlayerStatTable
import com.shiv.rally.domain.model.SportEvent

/**
 * Produces a small, stable box-score model for TV rendering.
 * ESPN can return several categories per team, uneven labels, duplicate athletes,
 * and occasionally very large tables. Keeping that raw shape out of Compose avoids
 * layout churn and protects the player overlay from malformed provider payloads.
 */
internal fun SportEvent.playerTablesForDisplay(
    teamLimit: Int = 2,
    rowLimit: Int = 6
): List<PlayerStatTable> {
    if (teamLimit <= 0 || rowLimit <= 0) return emptyList()

    val tablesByTeam = linkedMapOf<String, MutableList<PlayerStatTable>>()
    playerStatTables.forEach { table ->
        if (table.rows.isEmpty()) return@forEach
        val key = table.teamId?.takeIf { it.isNotBlank() }
            ?: table.teamAbbreviation.trim().uppercase().takeIf { it.isNotBlank() }
            ?: table.teamName.trim().uppercase().takeIf { it.isNotBlank() }
            ?: return@forEach
        tablesByTeam.getOrPut(key) { mutableListOf() } += table
    }

    return tablesByTeam.values.mapNotNull { teamTables ->
        val table = teamTables.maxWithOrNull(
            compareBy<PlayerStatTable> { it.rows.count { row -> row.stats.any(String::isNotBlank) } }
                .thenBy { it.labels.count(String::isNotBlank) }
        ) ?: return@mapNotNull null
        val labels = table.labels.map(String::trim).filter(String::isNotBlank).take(8)
        val statLimit = labels.size.coerceIn(3, 8)
        val rows = table.rows.asSequence()
            .filter { it.displayName.isNotBlank() }
            .distinctBy { it.athleteId?.takeIf(String::isNotBlank) ?: it.displayName.trim().lowercase() }
            .map { row ->
                row.copy(
                    displayName = row.displayName.trim(),
                    shortName = row.shortName?.trim()?.takeIf(String::isNotBlank),
                    stats = row.stats.map(String::trim).take(statLimit)
                )
            }
            .take(rowLimit)
            .toList()
        table.copy(
            teamName = table.teamName.trim().ifBlank { table.teamAbbreviation.trim().ifBlank { "Team" } },
            teamAbbreviation = table.teamAbbreviation.trim().ifBlank { "TEAM" },
            labels = labels,
            rows = rows
        ).takeIf { it.rows.isNotEmpty() }
    }.take(teamLimit)
}

internal data class GamePlayerStats(
    val id: String,
    val player: com.shiv.rally.domain.model.PlayerStatRow,
    val categories: List<Pair<String, List<Pair<String, String>>>>
)

internal data class GameTeamPlayers(
    val id: String,
    val name: String,
    val abbreviation: String,
    val logoUrl: String?,
    val players: List<GamePlayerStats>
)

/** Every published participant, with all categories merged once per athlete and team. */
internal fun SportEvent.allGamePlayers(): List<GameTeamPlayers> {
    val teams = playerStatTables.groupBy {
        it.teamId?.takeIf(String::isNotBlank) ?: it.teamAbbreviation.ifBlank { it.teamName }
    }
    return teams.map { (teamId, tables) ->
        val first = tables.first()
        val athletes = linkedMapOf<String, MutableList<Pair<PlayerStatTable, com.shiv.rally.domain.model.PlayerStatRow>>>()
        tables.forEach { table ->
            table.rows.filter { it.displayName.isNotBlank() }.forEach { row ->
                val key = row.athleteId?.takeIf(String::isNotBlank) ?: row.displayName.trim().lowercase()
                athletes.getOrPut(key) { mutableListOf() }.add(table to row)
            }
        }
        GameTeamPlayers(teamId, first.teamName.ifBlank { first.teamAbbreviation }, first.teamAbbreviation, first.teamLogoUrl,
            athletes.map { (id, appearances) ->
                val row = appearances.first().second
                val categories = appearances.groupBy { statCategoryLabel(it.first.category) }.map { (category, entries) ->
                    category to entries.flatMap { (table, player) -> playerStatPairs(table, player) }.distinct()
                }
                GamePlayerStats(id, row, categories)
            })
    }.filter { it.players.isNotEmpty() }
}
