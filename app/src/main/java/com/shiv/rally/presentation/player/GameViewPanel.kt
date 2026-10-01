@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.shiv.rally.presentation.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.shiv.rally.domain.model.SportEvent
import com.shiv.rally.domain.model.Team
import com.shiv.rally.presentation.common.RallyTvPalette

private val panelShape = RoundedCornerShape(9.dp)

@Composable
fun RallyGameInformationPanel(
    event: SportEvent?,
    sourceLabel: String,
    sourceLabels: List<String>,
    onChooseSource: () -> Unit,
    modifier: Modifier = Modifier,
    initialTabFocus: FocusRequester? = null,
    leftExitFocus: FocusRequester? = null
) {
    var tab by remember(event?.id) { mutableStateOf("Stats") }
    val fallbackTabFocus = remember { FocusRequester() }
    val firstTabFocus = initialTabFocus ?: fallbackTabFocus
    val tabFocus = remember(firstTabFocus) { listOf(firstTabFocus, FocusRequester(), FocusRequester(), FocusRequester()) }
    val contentFocus = remember { FocusRequester() }
    val selectedTabFocus = tabFocus[listOf("Stats", "Plays", "Lineups", "Sources").indexOf(tab)]
    val firstContentModifier = Modifier.focusRequester(contentFocus).focusProperties {
        up = selectedTabFocus
        leftExitFocus?.let { left = it }
        right = FocusRequester.Cancel
    }
    Column(modifier.clip(panelShape).background(Color(0xE6080D11)).border(1.dp, RallyTvPalette.Divider, panelShape)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 7.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            listOf("Stats", "Plays", "Lineups", "Sources").forEachIndexed { index, label ->
                var focused by remember(label) { mutableStateOf(false) }
                Column(Modifier.weight(1f)
                    .focusRequester(tabFocus[index])
                    .focusProperties {
                        if (index > 0) left = tabFocus[index - 1] else leftExitFocus?.let { left = it }
                        if (index < tabFocus.lastIndex) right = tabFocus[index + 1]
                        else right = FocusRequester.Cancel
                        leftExitFocus?.let { up = it }
                        down = contentFocus
                    }
                    .onFocusChangedCompat { focused = it }
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (focused || tab == label) Brush.verticalGradient(
                            listOf(Color.White.copy(alpha = if (focused) .16f else .11f), Color.White.copy(alpha = .045f))
                        ) else Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent))
                    )
                    .then(if (focused || tab == label) Modifier.border(
                        1.dp, Color.White.copy(alpha = if (focused) .34f else .14f), RoundedCornerShape(8.dp)
                    ) else Modifier)
                    .clickable { tab = label }.padding(vertical = 7.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(label, color = if (tab == label || focused) RallyTvPalette.Text else RallyTvPalette.Muted,
                        fontSize = 10.sp, fontWeight = if (tab == label) FontWeight.Bold else FontWeight.Normal,
                        letterSpacing = .35.sp)
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(RallyTvPalette.Divider))
        if (event == null) {
            Text("Open a game to see live stats and plays.", color = RallyTvPalette.Muted, fontSize = 12.sp, modifier = Modifier.padding(16.dp))
        } else {
            if (tab == "Stats") {
                CompactGameOverview(event, firstContentModifier, selectedTabFocus, leftExitFocus)
            } else TvLazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 11.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                when (tab) {
                    "Plays" -> {
                        if (event.plays.isEmpty()) item { PanelCard("Play-by-Play", firstContentModifier) { PanelEmpty("Live play updates are not available yet.") } }
                        event.plays.sortedByDescending { it.sequence }.distinctBy { it.id }.forEachIndexed { index, play ->
                            item(key = "play:${play.id}") {
                                PanelCard(listOfNotNull(play.period?.let { "P$it" }, play.clock).joinToString(" · ").ifBlank { "Play" },
                                    if (index == 0) firstContentModifier else Modifier.focusProperties { leftExitFocus?.let { left = it } }) {
                                    Text(play.text, color = RallyTvPalette.Text, fontSize = 10.sp)
                                }
                            }
                        }
                    }
                    "Lineups" -> {
                        if (event.playerStatTables.isEmpty()) item { PanelCard("Lineups & Players", firstContentModifier) { PanelEmpty("Player stats are not available yet.") } }
                        var firstPlayer = true
                        event.playerStatTables.forEachIndexed { tableIndex, table ->
                            item(key = "table:$tableIndex") {
                                Text("${table.teamAbbreviation} · ${statCategoryLabel(table.category)}", color = RallyTvPalette.Muted,
                                    fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(6.dp))
                            }
                            table.rows.distinctBy { it.athleteId ?: it.displayName }.forEach { player ->
                                val first = firstPlayer
                                firstPlayer = false
                                item(key = "player:$tableIndex:${player.athleteId ?: player.displayName}") {
                                    PanelCard(player.displayName, if (first) firstContentModifier else Modifier.focusProperties { leftExitFocus?.let { left = it } }) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            AsyncImage(player.headshotUrl ?: table.teamLogoUrl, null, Modifier.size(22.dp), contentScale = ContentScale.Fit)
                                            Spacer(Modifier.width(5.dp))
                                            Text(playerStatPairs(table, player).joinToString(" · ") { "${it.first} ${it.second}" }, color = RallyTvPalette.Muted, fontSize = 9.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    "Sources" -> item { PanelCard("Available Sources", firstContentModifier) {
                        Text("Playing: $sourceLabel", color = RallyTvPalette.Muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(7.dp))
                        if (sourceLabels.isEmpty()) PanelEmpty("No alternate sources are available.")
                        sourceLabels.take(5).forEach { label ->
                            Text(label, color = RallyTvPalette.Text, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth().clip(panelShape).clickable(onClick = onChooseSource).padding(vertical = 5.dp))
                        }
                        Text("Pick Source  ›", color = RallyTvPalette.Text, fontSize = 11.sp, modifier = Modifier.fillMaxWidth().clip(panelShape)
                            .background(RallyTvPalette.FocusSurface).clickable(onClick = onChooseSource).padding(10.dp))
                    } }
                }
                if (tab == "Sources") item { PanelCard("Broadcast Details") {
                    event.liveStats["TV Broadcast"]?.let { Text(it, color = RallyTvPalette.Text, fontSize = 10.sp) }
                    Text(listOfNotNull(event.league, event.venue, event.gameStatusDetail).joinToString(" · "), color = RallyTvPalette.Muted, fontSize = 10.sp)
                    PanelEmpty("Use the quality button above the video for signal, buffering, codec, and stream health details.")
                } }
                if (tab == "Lineups") item { PanelCard("Team Leaders") {
                    event.playerLeaders.distinctBy { "${it.teamAbbr}:${it.playerShortName}" }.take(6).forEach { leader ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            AsyncImage(leader.headshotUrl ?: leader.teamLogoUrl, null, Modifier.size(22.dp), contentScale = ContentScale.Fit)
                            Spacer(Modifier.width(6.dp))
                            Column(Modifier.weight(1f)) {
                                Text(leader.playerShortName, color = RallyTvPalette.Text, fontSize = 10.sp)
                                Text(listOfNotNull(leader.position, leader.category).joinToString(" · "), color = RallyTvPalette.Muted, fontSize = 8.sp)
                            }
                            Text(leader.statDisplay, color = RallyTvPalette.Text, fontSize = 10.sp)
                        }
                    }
                } }
                if (tab == "Plays") item { PanelCard("Scoring Summary") {
                    val scoring = event.plays.filter { it.isScoringPlay }.sortedByDescending { it.sequence }.take(8)
                    if (scoring.isEmpty()) PanelEmpty("No scoring plays have been published.")
                    scoring.forEach { Text(it.text, color = RallyTvPalette.Text, fontSize = 10.sp, modifier = Modifier.padding(vertical = 4.dp)) }
                } }
            }
        }
    }
}

/** All four overview cards fit the player column; focusing a card never scrolls it. */
@Composable
private fun CompactGameOverview(
    event: SportEvent, firstModifier: Modifier, tabFocus: FocusRequester, leftExit: FocusRequester?
) {
    val stats = remember(event.teamStats) {
        event.teamStats.filterNot { stat ->
            listOf("spread", "moneyline", "over/under", "odds", "prediction").any { stat.label.contains(it, true) }
        }.take(8)
    }
    val cardFocus = remember { List(4) { FocusRequester() } }
    val football = event.league in listOf("NFL", "NCAAF")
    val hasDrive = football && event.liveStats["Current Drive"].isNullOrBlank().not()
    fun navigation(index: Int) = Modifier.focusRequester(cardFocus[index]).focusProperties {
        up = if (index == 0) tabFocus else cardFocus[index - 1]
        down = if (index == 3) FocusRequester.Cancel else cardFocus[index + 1]
        right = FocusRequester.Cancel
        leftExit?.let { left = it }
    }
    BoxWithConstraints(Modifier.fillMaxSize().padding(6.dp)) {
        val leadersHeight = 136.dp
        val statsHeight = 28.dp + 14.dp * stats.size.coerceAtLeast(1)
        val situationHeight = if (hasDrive) 64.dp else 48.dp
        val playsHeight = maxHeight - leadersHeight - statsHeight - situationHeight - 15.dp
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            CompactPanelCard("Team Leaders", firstModifier.then(navigation(0)).height(leadersHeight)) {
                if (event.playerLeaders.isEmpty()) PanelEmpty("Leaders will appear when published.")
                else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(event.awayTeam to event.awayTeamBadge, event.homeTeam to event.homeTeamBadge).forEach { (team, badge) ->
                        Column(Modifier.weight(1f)) {
                            Row(Modifier.height(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                AsyncImage(badge ?: team?.logoUrl, null, Modifier.size(16.dp), contentScale = ContentScale.Fit)
                                Spacer(Modifier.width(4.dp))
                                Text(team?.abbreviation.orEmpty(), color = RallyTvPalette.Text, fontSize = 8.sp, lineHeight = 10.sp, fontWeight = FontWeight.Bold)
                            }
                            event.playerLeaders.filter { it.teamAbbr.equals(team?.abbreviation, true) }.take(3).forEach { leader ->
                                Row(Modifier.fillMaxWidth().height(30.dp), verticalAlignment = Alignment.CenterVertically) {
                                    AsyncImage(leader.headshotUrl ?: leader.teamLogoUrl, null, Modifier.size(21.dp).clip(RoundedCornerShape(50)), contentScale = ContentScale.Crop)
                                    Spacer(Modifier.width(4.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(leader.playerShortName, color = RallyTvPalette.Text, fontSize = 8.5.sp, lineHeight = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(listOfNotNull(leader.position, leader.category).joinToString(" · "), color = RallyTvPalette.Muted, fontSize = 7.sp, lineHeight = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(leader.statDisplay, color = RallyTvPalette.Text, fontSize = 8.5.sp, lineHeight = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            CompactPanelCard("Team Stats", navigation(1).height(statsHeight), event) {
                if (stats.isEmpty()) PanelEmpty("Team stats will appear during the game.")
                stats.forEach { stat ->
                    val awayFraction = statBarFraction(stat.awayValue, stat.homeValue)
                    Row(Modifier.fillMaxWidth().height(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stat.awayValue, color = RallyTvPalette.Text, fontSize = 8.5.sp, lineHeight = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(35.dp))
                        Box(Modifier.weight(1f).height(3.dp).clip(RoundedCornerShape(50)).background(Color(0xFF20252A))) {
                            Box(Modifier.fillMaxWidth(awayFraction).height(3.dp).background(gamePanelTeamColor(event.awayTeam, Color.Gray)))
                        }
                        Text(stat.label, color = RallyTvPalette.Muted, fontSize = 8.sp, lineHeight = 10.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Box(Modifier.weight(1f).height(3.dp).clip(RoundedCornerShape(50)).background(Color(0xFF20252A))) {
                            Box(Modifier.fillMaxWidth(1f - awayFraction).height(3.dp).background(gamePanelTeamColor(event.homeTeam, Color.Gray)))
                        }
                        Text(stat.homeValue, color = RallyTvPalette.Text, fontSize = 8.5.sp, lineHeight = 10.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.width(35.dp))
                    }
                }
            }
            CompactPanelCard(if (hasDrive) "Current Drive" else if (event.status == com.shiv.rally.domain.model.EventStatus.FINISHED) "Game Summary" else "Live Situation", navigation(2).height(situationHeight)) {
                Text(event.liveStats["Current Drive"] ?: event.gameStatusDetail ?: event.league,
                    color = RallyTvPalette.Text, fontSize = 8.5.sp, lineHeight = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (hasDrive) Canvas(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFF171C20))) {
                    repeat(11) { index ->
                        val x = size.width * index / 10f
                        drawLine(Color(0x556E7881), Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
                    }
                    event.liveStats["Drive Yard Line"]?.toFloatOrNull()?.let { yard ->
                        drawCircle(Color.White, 3.dp.toPx(), Offset(size.width * (yard / 100f).coerceIn(.02f, .98f), size.height / 2f))
                    }
                }
                Text(event.plays.maxByOrNull { it.sequence }?.text ?: event.venue.orEmpty(),
                    color = RallyTvPalette.Muted, fontSize = 8.sp, lineHeight = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            CompactPanelCard("Latest Play-by-Play", navigation(3).height(playsHeight.coerceAtLeast(46.dp))) {
                val playCount = ((playsHeight - 28.dp) / 22.dp).toInt().coerceIn(1, 6)
                val plays = event.plays.sortedByDescending { it.sequence }.take(playCount)
                if (plays.isEmpty()) PanelEmpty("Live play updates will appear here.")
                else plays.forEach { play ->
                    Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Text(listOfNotNull(play.period?.let { "P$it" }, play.clock).joinToString(" · "), color = RallyTvPalette.Muted,
                            fontSize = 7.5.sp, lineHeight = 10.sp, modifier = Modifier.width(43.dp), maxLines = 1)
                        Text(play.text, color = RallyTvPalette.Text, fontSize = 8.5.sp, lineHeight = 11.sp,
                            maxLines = if (playsHeight < 72.dp) 1 else 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactPanelCard(
    title: String, modifier: Modifier = Modifier, teamMarks: SportEvent? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth().onFocusChanged { focused = it.hasFocus }.focusable()
        .clip(panelShape).background(if (focused) RallyTvPalette.FocusSurface else Color(0xB00D1217))
        .border(1.dp, if (focused) RallyTvPalette.FocusEdge.copy(alpha = .55f) else RallyTvPalette.Divider, panelShape)
        .padding(horizontal = 8.dp, vertical = 6.dp)) {
        Row(Modifier.fillMaxWidth().height(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = RallyTvPalette.Text, fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            teamMarks?.let { event ->
                AsyncImage(event.awayTeamBadge ?: event.awayTeam?.logoUrl, null, Modifier.size(14.dp), contentScale = ContentScale.Fit)
                Spacer(Modifier.width(7.dp))
                AsyncImage(event.homeTeamBadge ?: event.homeTeam?.logoUrl, null, Modifier.size(14.dp), contentScale = ContentScale.Fit)
            }
        }
        Spacer(Modifier.height(4.dp))
        content()
    }
}

@Composable
private fun PanelCard(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth().onFocusChanged { focused = it.hasFocus }.focusable()
        .clip(panelShape).background(if (focused) RallyTvPalette.FocusSurface else Color(0xB00D1217))
        .border(1.dp, if (focused) RallyTvPalette.FocusEdge.copy(alpha = .55f) else RallyTvPalette.Divider, panelShape).padding(9.dp)) {
        Text(title, color = RallyTvPalette.Text, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun PanelEmpty(message: String) {
    Text(message, color = RallyTvPalette.Muted, fontSize = 10.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
}

private fun Modifier.onFocusChangedCompat(onChanged: (Boolean) -> Unit): Modifier =
    this.then(Modifier.onFocusChanged { onChanged(it.isFocused) })

private fun statBarFraction(away: String, home: String): Float {
    fun amount(value: String): Float? = Regex("-?\\d+(?:\\.\\d+)?").find(value)?.value?.toFloatOrNull()?.let { kotlin.math.abs(it) }
    val awayValue = amount(away) ?: return .5f
    val homeValue = amount(home) ?: return .5f
    val total = awayValue + homeValue
    return if (total <= 0f) .5f else (awayValue / total).coerceIn(.08f, .92f)
}

private fun gamePanelTeamColor(team: Team?, fallback: Color): Color {
    val raw = team?.colors?.firstOrNull()?.trim().orEmpty()
    if (raw.isNotBlank()) {
        val normalized = if (raw.startsWith("#")) raw else "#$raw"
        runCatching { return Color(android.graphics.Color.parseColor(normalized)) }
    }
    if (team == null) return fallback
    val palette = listOf(Color(0xFF285C78), Color(0xFF7A3044), Color(0xFF5A3A86), Color(0xFF8A5A1E), Color(0xFF24634F), Color(0xFF7C4025))
    return palette[(team.id.hashCode() and Int.MAX_VALUE) % palette.size]
}
