@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class, androidx.compose.ui.text.ExperimentalTextApi::class)

package com.shiv.rally.presentation.player

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.shiv.rally.domain.model.EventStatus
import com.shiv.rally.domain.model.SportEvent
import com.shiv.rally.presentation.common.RallyTvPalette
import com.shiv.rally.presentation.theme.RallyBodyFont
import com.shiv.rally.R
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val scoreHeaderFont = FontFamily(
    Font(R.font.inter_variable, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.inter_variable, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700)))
)

/** Mirrors the reference: badge / abbreviation / score — status — score / abbreviation / badge. */
@Composable
internal fun RallyGameScoreHeader(event: SportEvent, modifier: Modifier = Modifier) {
    val live = event.status == EventStatus.LIVE || event.status == EventStatus.HALFTIME
    val status = when (event.status) {
        EventStatus.LIVE, EventStatus.HALFTIME -> "LIVE"
        EventStatus.FINISHED -> "FINAL"
        EventStatus.NOT_STARTED -> event.league
        EventStatus.DELAYED -> "DELAYED"
        EventStatus.CANCELED -> "CANCELED"
    }
    val detail = if (event.status == EventStatus.NOT_STARTED)
        DateTimeFormatter.ofPattern("EEE · h:mm a").withZone(ZoneId.systemDefault()).format(event.startTime)
    else event.gameStatusDetail.orEmpty()
    Row(modifier.height(54.dp).widthIn(max = 480.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AsyncImage(event.awayTeamBadge ?: event.awayTeam?.logoUrl, null, Modifier.size(43.dp), contentScale = ContentScale.Fit)
            TeamAbbreviation(event.awayTeam?.abbreviation ?: "AWAY")
            Score(event.scoreAway)
        }
        Column(Modifier.width(112.dp).padding(horizontal = 5.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(status, color = if (live) RallyTvPalette.Live else RallyTvPalette.Muted,
                fontFamily = scoreHeaderFont, fontSize = 14.sp, lineHeight = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(detail, color = RallyTvPalette.Muted, fontFamily = RallyBodyFont, fontSize = 12.sp,
                lineHeight = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)) {
            Score(event.scoreHome)
            TeamAbbreviation(event.homeTeam?.abbreviation ?: "HOME")
            AsyncImage(event.homeTeamBadge ?: event.homeTeam?.logoUrl, null, Modifier.size(43.dp), contentScale = ContentScale.Fit)
        }
    }
}

@Composable
private fun TeamAbbreviation(value: String) {
    Text(value.uppercase(), color = RallyTvPalette.Text, fontFamily = scoreHeaderFont, fontSize = 17.sp,
        lineHeight = 21.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
        modifier = Modifier.widthIn(max = 60.dp))
}

@Composable
private fun Score(value: Int?) {
    Text(value?.toString() ?: "–", color = RallyTvPalette.Text, fontFamily = scoreHeaderFont, fontSize = 39.sp,
        lineHeight = 45.sp, fontWeight = FontWeight.Bold, maxLines = 1)
}
