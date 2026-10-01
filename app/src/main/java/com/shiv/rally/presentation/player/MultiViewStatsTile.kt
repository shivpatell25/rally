@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.shiv.rally.presentation.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.items
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.shiv.rally.domain.model.PlayerStatRow
import com.shiv.rally.domain.model.PlayerStatTable
import com.shiv.rally.domain.model.SportEvent
import com.shiv.rally.presentation.common.RallyControlButton
import com.shiv.rally.presentation.common.RallyTvPalette
import com.shiv.rally.presentation.common.rallyReadableFocus
import com.shiv.rally.presentation.theme.RallyBodyFont
import kotlinx.coroutines.delay

@Composable
internal fun MultiViewStatsTile(
    games: List<SportEvent>, loading: Boolean, error: String?, focused: Boolean,
    requester: FocusRequester, onFocus: () -> Unit, onRefresh: () -> Unit, onRemove: () -> Unit,
    leftExit: FocusRequester?, upExit: FocusRequester?
) {
    var detail by remember { mutableStateOf<Pair<PlayerStatTable, PlayerStatRow>?>(null) }
    val shape = RoundedCornerShape(8.dp)
    Column(Modifier.fillMaxSize().onFocusChanged { if (it.hasFocus) onFocus() }.focusGroup()
        .clip(shape).background(Color(0xFF0B0F13))
        .border(1.dp, if (focused) RallyTvPalette.FocusEdge else RallyTvPalette.Divider, shape)) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Column(Modifier.weight(1f)) {
                Text("PLAYER STATS", fontSize = 10.sp, lineHeight = 13.sp, letterSpacing = .7.sp, color = RallyTvPalette.Text, fontWeight = FontWeight.Bold)
                Text(if (loading) "Updating…" else "${games.size} games", fontSize = 8.sp, lineHeight = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = RallyTvPalette.Muted)
            }
            StatsToolbarButton(if (loading) "Updating" else "Refresh", onRefresh, modifier = Modifier.focusRequester(requester).focusProperties {
                if (leftExit != null) left = leftExit
                if (upExit != null) up = upExit
            })
            StatsToolbarButton("Remove", onRemove)
        }
        TvLazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)) {
            if (error != null) item { Text(error, color = RallyTvPalette.Muted, fontSize = 10.sp) }
            if (games.isEmpty()) item {
                Text(if (loading) "Loading player box scores…" else "Choose a game stream to see its player stats. RedZone adds today's afternoon NFL slate.",
                    color = RallyTvPalette.Muted, fontSize = 11.sp, lineHeight = 15.sp)
            }
            games.forEach { game ->
                item(key = "game:${game.id}") {
                    Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp)) {
                        Text(multiViewTitle(game), color = RallyTvPalette.Text, fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold)
                        Text(listOfNotNull(game.league, game.gameStatusDetail,
                            if (game.scoreAway != null && game.scoreHome != null) "${game.scoreAway} – ${game.scoreHome}" else null).joinToString(" · "),
                            color = RallyTvPalette.Muted, fontSize = 9.sp)
                    }
                }
                if (game.playerStatTables.isEmpty()) item(key = "empty:${game.id}") {
                    Text("Player stats have not been published for this game yet.", color = RallyTvPalette.Muted, fontSize = 10.sp)
                }
                game.playerStatTables.forEachIndexed { tableIndex, table ->
                    item(key = "table:${game.id}:$tableIndex") {
                        Row(Modifier.fillMaxWidth().padding(top = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            AsyncImage(table.teamLogoUrl, null, Modifier.size(18.dp), contentScale = ContentScale.Fit)
                            Spacer(Modifier.width(5.dp))
                            Text("${table.teamAbbreviation} · ${statCategoryLabel(table.category)}", color = RallyTvPalette.Muted,
                                fontSize = 10.sp, lineHeight = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    items(table.rows.distinctBy { it.athleteId ?: it.displayName }, key = { "${game.id}:$tableIndex:${it.athleteId ?: it.displayName}" }) { player ->
                        var rowFocused by remember { mutableStateOf(false) }
                        Row(Modifier.fillMaxWidth().onFocusChanged { rowFocused = it.isFocused }
                            .focusProperties { if (leftExit != null) left = leftExit }
                            .clip(RoundedCornerShape(4.dp)).background(if (rowFocused) RallyTvPalette.FocusSurface else Color.Transparent)
                            .clickable { detail = table to player }.padding(5.dp), verticalAlignment = Alignment.CenterVertically) {
                            AsyncImage(player.headshotUrl, null, Modifier.size(23.dp), contentScale = ContentScale.Fit)
                            Spacer(Modifier.width(5.dp))
                            Column(Modifier.weight(1f)) {
                                Text(player.displayName, color = RallyTvPalette.Text, fontFamily = RallyBodyFont, fontSize = 10.sp, lineHeight = 13.sp,
                                    fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(playerStatPairs(table, player).joinToString(" · ") { "${it.first} ${it.second}" },
                                    color = RallyTvPalette.Muted, fontSize = 9.sp, lineHeight = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
    detail?.let { (table, player) ->
        val close = remember { FocusRequester() }
        Dialog(onDismissRequest = { detail = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Column(Modifier.width(500.dp).heightIn(max = 450.dp).clip(shape).background(RallyTvPalette.BackgroundSoft)
                .border(1.dp, RallyTvPalette.Divider, shape).padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(player.headshotUrl, null, Modifier.size(46.dp), contentScale = ContentScale.Fit)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(player.displayName, color = RallyTvPalette.Text, fontSize = 22.sp, lineHeight = 25.sp, fontWeight = FontWeight.Bold)
                        Text(listOfNotNull(table.teamName, player.position, player.jersey?.let { "#$it" }, statCategoryLabel(table.category)).joinToString(" · "),
                            color = RallyTvPalette.Muted, fontSize = 11.sp)
                    }
                }
                Spacer(Modifier.height(16.dp))
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                playerStatPairs(table, player).chunked(2).forEach { values ->
                    Row(Modifier.fillMaxWidth().rallyReadableFocus().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        values.forEach { (label, value) -> Column(Modifier.weight(1f)) {
                            Text(label, color = RallyTvPalette.Muted, fontSize = 11.sp)
                            Text(value, color = RallyTvPalette.Text, fontSize = 17.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold)
                        } }
                    }
                }
                }
                Spacer(Modifier.height(14.dp))
                RallyControlButton("Done", { detail = null }, modifier = Modifier.focusRequester(close), primary = true)
            }
            LaunchedEffect(Unit) { delay(100); close.requestFocus() }
        }
    }
}

internal fun statCategoryLabel(category: String?): String = category.orEmpty()
    .replace(Regex("([a-z])([A-Z])"), "$1 $2").replaceFirstChar { it.uppercase() }.ifBlank { "Players" }

internal fun playerStatPairs(table: PlayerStatTable, player: PlayerStatRow): List<Pair<String, String>> =
    player.stats.mapIndexed { index, value -> (table.labels.getOrNull(index)?.takeIf(String::isNotBlank) ?: "Stat ${index + 1}") to value }

@Composable
private fun StatsToolbarButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(5.dp)
    Box(modifier.height(26.dp).onFocusChanged { focused = it.isFocused }.clip(shape)
        .background(if (focused) RallyTvPalette.FocusSurface else Color.Transparent)
        .border(1.dp, if (focused) RallyTvPalette.FocusEdge else RallyTvPalette.Divider, shape)
        .clickable(onClick = onClick).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
        Text(label, color = RallyTvPalette.Text, fontSize = 10.sp, lineHeight = 13.sp, maxLines = 1)
    }
}
