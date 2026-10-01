@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.shiv.rally.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.shiv.rally.domain.model.SportEvent
import com.shiv.rally.presentation.common.RallyTvPalette
import com.shiv.rally.presentation.theme.RallyBodyFont

/** The same four events move from a horizontal preview into full-width guide rows.
 * Logos and text are measured before movement starts; no second screen is composed on Down.
 */
@Composable
internal fun RallyUpcomingMorph(
    events: List<SportEvent>,
    guide: Boolean,
    progress: State<Float>,
    title: String,
    firstFocus: FocusRequester,
    liveFocus: FocusRequester,
    sportFocus: FocusRequester,
    seeFullFocus: FocusRequester,
    savedEventIds: Set<String>,
    onToggleAlert: (String) -> Unit,
    onEvent: (SportEvent) -> Unit,
    onSchedule: () -> Unit,
    onExpand: () -> Unit
) {
    val rowFocus = remember(events.map { it.id }, firstFocus) {
        events.mapIndexed { index, _ -> if (index == 0) firstFocus else FocusRequester() }
    }
    val bellFocus = remember(events.map { it.id }) { events.map { FocusRequester() } }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 60.dp), verticalAlignment = Alignment.CenterVertically) {
            RallySectionHeader(if (guide) title else "Starting Soon", Modifier.weight(1f).padding(start = 10.dp), 0.dp)
            HeaderAction("See Full Schedule  ›", onSchedule,
                modifier = Modifier.graphicsLayer { alpha = progress.value }
                    .focusProperties { canFocus = guide },
                focusRequester = seeFullFocus, upFocus = liveFocus, downFocus = firstFocus)
        }
        Spacer(Modifier.height(8.dp))
        if (events.isEmpty()) {
            Text("NO GAMES SCHEDULED YET", color = RallyTvPalette.Muted, fontFamily = RallyBodyFont, fontSize = 10.sp,
                modifier = Modifier.padding(horizontal = 70.dp).focusRequester(firstFocus)
                    .focusProperties { up = liveFocus; down = sportFocus }
                    .onPreviewKeyEvent { if (!guide && it.type == KeyEventType.KeyDown && it.key == Key.DirectionDown) { onExpand(); true } else false }
                    .clickable(onClick = onSchedule).padding(vertical = 16.dp))
        } else BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 60.dp)) {
            val fullWidth = maxWidth
            val compactWidth = (fullWidth - 36.dp) / 4
            Layout(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)),
                content = {
                    events.forEachIndexed { index, event ->
                        MorphEvent(event, guide, progress, compactWidth, fullWidth,
                            saved = event.id in savedEventIds,
                            onToggleAlert = { onToggleAlert(event.id) },
                            bellModifier = Modifier.focusRequester(bellFocus[index]).focusProperties {
                                canFocus = guide
                                left = rowFocus[index]; right = FocusRequester.Cancel
                                up = if (index == 0) seeFullFocus else bellFocus[index - 1]
                                down = if (index == events.lastIndex) sportFocus else bellFocus[index + 1]
                            },
                            onClick = { onEvent(event) },
                            modifier = Modifier.focusRequester(rowFocus[index]).focusProperties {
                                up = if (!guide) liveFocus else if (index == 0) seeFullFocus else rowFocus[index - 1]
                                down = if (guide && index < events.lastIndex) rowFocus[index + 1] else sportFocus
                                left = if (!guide && index > 0) rowFocus[index - 1] else FocusRequester.Cancel
                                right = if (guide) bellFocus[index] else if (index < events.lastIndex) rowFocus[index + 1] else FocusRequester.Cancel
                            }.onPreviewKeyEvent {
                                if (!guide && it.type == KeyEventType.KeyDown && it.key == Key.DirectionDown) { onExpand(); true } else false
                            })
                    }
                }
            ) { measurables, constraints ->
                val width = if (guide) constraints.maxWidth else compactWidth.roundToPx()
                val rowHeight = if (guide) 34.dp.roundToPx() else 49.dp.roundToPx()
                val rows = measurables.map { it.measure(Constraints.fixed(width, rowHeight)) }
                val totalHeight = maxOf(49.dp.roundToPx(), (34.dp * events.size + (events.size - 1).dp).roundToPx())
                layout(constraints.maxWidth, totalHeight) {
                    val t = progress.value
                    rows.forEachIndexed { index, row ->
                        row.placeRelative(
                            blend((compactWidth + 12.dp).roundToPx() * index, 0, t),
                            blend(0, 35.dp.roundToPx() * index, t)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MorphEvent(
    event: SportEvent, guide: Boolean, progress: State<Float>, compactWidth: Dp, fullWidth: Dp,
    saved: Boolean, onToggleAlert: () -> Unit, bellModifier: Modifier, onClick: () -> Unit, modifier: Modifier
) {
    var focused by remember(event.id) { mutableStateOf(false) }
    Layout(
        modifier = modifier.onFocusChanged { focused = it.hasFocus }
            .drawBehind {
                val t = progress.value
                val width = compactWidth.toPx() + (fullWidth - compactWidth).toPx() * t
                val height = 49.dp.toPx() + (34.dp - 49.dp).toPx() * t
                drawRoundRect(if (focused) RallyTvPalette.FocusSurface else Color(0x7310161C),
                    size = Size(width, height), cornerRadius = CornerRadius(6.dp.toPx()))
            }
            .clickable(onClick = onClick),
        content = {
            Text(homePreviewTime(event).uppercase(), color = RallyTvPalette.Muted, fontFamily = RallyBodyFont,
                fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = .45.sp, maxLines = 1)
            AsyncImage(event.awayTeamBadge?.takeIf(String::isNotBlank) ?: event.awayTeam?.logoUrl, null, Modifier.size(28.dp), contentScale = ContentScale.Fit)
            AsyncImage(event.homeTeamBadge?.takeIf(String::isNotBlank) ?: event.homeTeam?.logoUrl, null, Modifier.size(28.dp), contentScale = ContentScale.Fit)
            val matchup = "${matchupTeamName(event.awayTeam?.name, event.league)} vs ${matchupTeamName(event.homeTeam?.name, event.league)}"
            Text(matchup, color = RallyTvPalette.Text, fontFamily = RallyBodyFont, fontSize = 9.5.sp, lineHeight = 11.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.graphicsLayer { alpha = 1f - progress.value })
            Text(matchup, color = RallyTvPalette.Text, fontFamily = RallyBodyFont, fontSize = 11.sp, lineHeight = 13.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.graphicsLayer { alpha = progress.value })
            Text(event.league.uppercase(), color = RallyTvPalette.Muted, fontFamily = RallyBodyFont, fontSize = 8.sp,
                lineHeight = 9.sp, letterSpacing = .5.sp,
                modifier = Modifier.graphicsLayer { alpha = 1f - progress.value })
            Text(event.league.uppercase(), color = RallyTvPalette.Muted, fontFamily = RallyBodyFont, fontSize = 9.sp,
                lineHeight = 11.sp, letterSpacing = .6.sp,
                modifier = Modifier.graphicsLayer { alpha = progress.value })
            var bellFocused by remember { mutableStateOf(false) }
            Box(bellModifier.size(27.dp).graphicsLayer { alpha = progress.value }
                .onFocusChanged { bellFocused = it.isFocused }
                .clip(RoundedCornerShape(5.dp))
                .background(if (bellFocused) Color.White.copy(alpha = .09f) else Color.Transparent)
                .clickable(enabled = guide, onClick = onToggleAlert)
                .semantics { contentDescription = if (saved) "Remove reminder for ${event.name}" else "Set reminder for ${event.name}" },
                contentAlignment = Alignment.Center) {
                ScheduleBell(bellFocused || saved)
                if (saved) Box(Modifier.size(4.dp).align(Alignment.TopEnd).background(Color.White, RoundedCornerShape(50)))
            }
        }
    ) { nodes, constraints ->
        // Each endpoint's text keeps the same measuring width throughout the movement.
        val time = nodes[0].measure(Constraints.fixedWidth(112.dp.roundToPx()))
        val away = nodes[1].measure(Constraints.fixed(28.dp.roundToPx(), 28.dp.roundToPx()))
        val home = nodes[2].measure(Constraints.fixed(28.dp.roundToPx(), 28.dp.roundToPx()))
        val compactTitle = nodes[3].measure(Constraints.fixedWidth((compactWidth - 83.dp).roundToPx()))
        val guideTitle = nodes[4].measure(Constraints.fixedWidth((fullWidth - 373.dp).roundToPx()))
        val compactLeague = nodes[5].measure(Constraints.fixedWidth(70.dp.roundToPx()))
        val guideLeague = nodes[6].measure(Constraints.fixedWidth(88.dp.roundToPx()))
        val bell = nodes[7].measure(Constraints.fixed(27.dp.roundToPx(), 27.dp.roundToPx()))
        layout(constraints.maxWidth, constraints.maxHeight) {
            val t = progress.value
            fun x(start: Dp, end: Dp) = blend(start.roundToPx(), end.roundToPx(), t)
            time.placeRelative(x(8.dp, 16.dp), x(3.dp, 10.dp))
            away.placeRelative(x(8.dp, 128.dp), x(17.dp, 3.dp))
            home.placeRelative(x(41.dp, 171.dp), x(17.dp, 3.dp))
            compactTitle.placeRelative(x(76.dp, 229.dp), x(17.dp, 10.dp))
            guideTitle.placeRelative(x(76.dp, 229.dp), x(17.dp, 10.dp))
            compactLeague.placeRelative(x(76.dp, fullWidth - 120.dp), x(38.dp, 12.dp))
            guideLeague.placeRelative(x(76.dp, fullWidth - 120.dp), x(38.dp, 12.dp))
            bell.placeRelative((fullWidth - 41.dp).roundToPx(), 3.dp.roundToPx())
        }
    }
}

private fun blend(start: Int, end: Int, amount: Float): Int = kotlin.math.round(start + (end - start) * amount).toInt()
