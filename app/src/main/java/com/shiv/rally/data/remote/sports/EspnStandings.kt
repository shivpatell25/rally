package com.shiv.rally.data.remote.sports

import com.google.gson.JsonArray
import com.google.gson.JsonObject

/** Order meaningful record fields explicitly; ESPN's source array starts with GB,
 * not wins. Playoff seeds must come from the feed, never from flattened row order. */
internal fun espnStandingSummary(stats: JsonArray?): String {
    val values = stats?.toList().orEmpty().filter { it.isJsonObject }.associate { stat ->
        val obj = stat.asJsonObject
        obj.get("name")?.asString.orEmpty().lowercase() to obj.get("displayValue")?.takeUnless { it.isJsonNull }?.asString.orEmpty()
    }
    return buildList {
        val overall = values["overall"]?.takeIf(String::isNotBlank)
        if (overall != null) add(overall)
        else {
            val wins = values["wins"]
            val losses = values["losses"]
            if (!wins.isNullOrBlank() && !losses.isNullOrBlank()) add(listOfNotNull(wins, losses, values["ties"]?.takeIf { it != "0" }).joinToString("–"))
        }
        values["points"]?.takeIf(String::isNotBlank)?.let { add("PTS $it") }
        values["winpercent"]?.takeIf(String::isNotBlank)?.let { add("PCT $it") }
        values["gamesbehind"]?.takeIf { it.isNotBlank() && it != "-" && it != "–" }?.let { add("GB $it") }
    }.take(3).joinToString(" · ")
}

internal fun espnStandings(root: JsonObject, league: String): Pair<List<Pair<String, String>>, List<Pair<String, String>>> {
    val standings = mutableListOf<Pair<String, String>>()
    val seeds = mutableListOf<Triple<String, Int, Pair<String, String>>>()
    fun walk(node: JsonObject, group: String) {
        val entries = node.getAsJsonObject("standings")?.getAsJsonArray("entries")
        entries?.toList().orEmpty().filter { it.isJsonObject }.forEach { element ->
            val entry = element.asJsonObject
            val team = entry.getAsJsonObject("team") ?: return@forEach
            val name = team.get("displayName")?.asString ?: return@forEach
            val stats = entry.getAsJsonArray("stats")
            val record = espnStandingSummary(stats)
            standings.add(name to record)
            val seed = stats?.toList().orEmpty().firstOrNull { it.isJsonObject && it.asJsonObject.get("name")?.asString == "playoffSeed" }
                ?.asJsonObject?.get("displayValue")?.asString?.toIntOrNull()
            val cutoff = when (league.uppercase()) { "NFL" -> 7; "MLB" -> 6; "NBA", "NHL" -> 8; else -> 0 }
            if (seed != null && seed in 1..cutoff) seeds.add(Triple(group, seed,
                name to listOf(group.takeIf(String::isNotBlank), "Seed $seed", record.takeIf(String::isNotBlank)).filterNotNull().joinToString(" · ")))
        }
        node.getAsJsonArray("children")?.toList().orEmpty().filter { it.isJsonObject }.forEach { child ->
            val obj = child.asJsonObject
            val title = obj.get("abbreviation")?.asString ?: obj.get("name")?.asString ?: group
            // Keep the conference rather than a nested division label.
            walk(obj, if (group.isBlank()) title else group)
        }
    }
    walk(root, "")
    return standings.distinctBy { it.first } to seeds.sortedWith(compareBy({ it.first }, { it.second })).map { it.third }.distinctBy { it.first }
}
