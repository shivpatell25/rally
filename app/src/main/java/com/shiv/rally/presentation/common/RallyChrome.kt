@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.shiv.rally.presentation.common

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.RepeatMode
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.shiv.rally.R
import com.shiv.rally.presentation.theme.AppleTvTheme
import com.shiv.rally.presentation.theme.RallyBodyFont
import com.shiv.rally.presentation.theme.LocalRallyAccessibility

enum class RallyDestination { HOME, LIVE, LEAGUES, HIGHLIGHTS, MY_TEAMS }

class RallyChromeFocus {
    val home = FocusRequester()
    val live = FocusRequester()
    val leagues = FocusRequester()
    val highlights = FocusRequester()
    val myTeams = FocusRequester()
    val search = FocusRequester()
    val settings = FocusRequester()
}

private val chromeShape = RoundedCornerShape(10.dp)
private val navShape = RoundedCornerShape(8.dp)

@Composable
fun RallyAmbientSurface(
    modifier: Modifier = Modifier,
    contextKey: String = "",
    content: @Composable BoxScope.() -> Unit
) {
    val accessibility = LocalRallyAccessibility.current
    val targetTint = when {
        contextKey.contains("NFL", true) -> Color(0xFF215A86)
        contextKey.contains("NBA", true) -> Color(0xFF7E3B2D)
        contextKey.contains("NHL", true) -> Color(0xFF316779)
        contextKey.contains("MLB", true) -> Color(0xFF344E86)
        contextKey.contains("SOCCER", true) -> Color(0xFF246B58)
        else -> Color(0xFF1C5267)
    }
    val contextTint by animateColorAsState(
        targetValue = targetTint,
        animationSpec = if (accessibility.reducedMotion) snap() else tween(520),
        label = "context background"
    )
    Box(modifier.fillMaxSize().background(AppleTvTheme.DeepNavy)) {
        Image(
            painter = painterResource(R.drawable.rally_ambient_background_v5),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color(0x18000000),
                    .52f to Color(0x2405080F),
                    1f to Color(0xA805080F)
                )
            )
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    listOf(contextTint.copy(alpha = if (accessibility.reducedMotion) .055f else .075f), Color.Transparent, Color.Transparent)
                )
            )
        )
        content()
    }
}

@Composable
fun RallyTopBar(
    selected: RallyDestination?,
    onHome: () -> Unit,
    onLive: () -> Unit,
    onLeagues: () -> Unit,
    onHighlights: () -> Unit,
    onWatchlist: () -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
    compactLogo: Boolean = false,
    focus: RallyChromeFocus,
    contentFocusRequester: FocusRequester? = null
) {
    Row(
        modifier = modifier.fillMaxWidth().height(58.dp).padding(horizontal = 30.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(if (compactLogo) R.drawable.rally_mark_ui else R.drawable.rally_wordmark_color_ui),
            contentDescription = "Rally",
            modifier = if (compactLogo) Modifier.size(30.dp) else Modifier.width(92.dp).height(31.dp),
            contentScale = ContentScale.Fit
        )
        Spacer(Modifier.weight(1f))
        Row(
            modifier = Modifier
                .clip(chromeShape)
                .background(Color(0x5E0A101B))
                .border(1.dp, Color(0x425A7894), chromeShape)
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RallyTopNavItem("HOME", selected == RallyDestination.HOME, onHome, focusRequester = focus.home, downFocusRequester = contentFocusRequester)
            RallyTopNavItem("LIVE", selected == RallyDestination.LIVE, onLive, live = true, focusRequester = focus.live, downFocusRequester = contentFocusRequester)
            RallyTopNavItem("LEAGUES", selected == RallyDestination.LEAGUES, onLeagues, focusRequester = focus.leagues, downFocusRequester = contentFocusRequester)
            RallyTopNavItem("HIGHLIGHTS", selected == RallyDestination.HIGHLIGHTS, onHighlights, focusRequester = focus.highlights, downFocusRequester = contentFocusRequester)
            RallyTopNavItem("MY TEAMS", selected == RallyDestination.MY_TEAMS, onWatchlist, focusRequester = focus.myTeams, downFocusRequester = contentFocusRequester)
        }
        Spacer(Modifier.weight(1f))
        RallyChromeIcon(R.drawable.ic_rally_search, "Search", onSearch, focus.search, contentFocusRequester)
        Spacer(Modifier.width(9.dp))
        RallyChromeIcon(R.drawable.ic_rally_settings, "Settings", onSettings, focus.settings, contentFocusRequester)
    }
}

@Composable
private fun RallyTopNavItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    live: Boolean = false,
    focusRequester: FocusRequester,
    downFocusRequester: FocusRequester?
) {
    var focused by remember(label) { mutableStateOf(false) }
    val accessibility = LocalRallyAccessibility.current
    val active = selected || focused
    Row(
        Modifier
            .height(34.dp)
            .focusRequester(focusRequester)
            .focusProperties { downFocusRequester?.let { down = it } }
            .onFocusChanged { focused = it.isFocused }
            .rallyFocusScale(focused, AppleTvTheme.ButtonFocusScale)
            .clip(navShape)
            .background(
                when {
                    focused -> Color(0xB0233449)
                    selected -> Color(0x781A293C)
                    else -> Color.Transparent
                }
            )
            .border(
                width = if (focused && accessibility.highContrastFocus) 3.dp else if (focused) 1.5.dp else 1.dp,
                color = when {
                    focused -> Color(0xD6B9D8EA)
                    selected -> Color(0x3D7A94AF)
                    else -> Color.Transparent
                },
                shape = navShape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (live) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(AppleTvTheme.LiveRed))
            Spacer(Modifier.width(7.dp))
        }
        Text(
            text = label,
            color = if (active) AppleTvTheme.OffWhite else AppleTvTheme.TextSecondary,
            fontSize = 11.sp,
            fontFamily = RallyBodyFont,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            letterSpacing = 1.sp
        )
    }
}

@Composable
private fun RallyChromeIcon(
    icon: Int,
    label: String,
    onClick: () -> Unit,
    focusRequester: FocusRequester,
    downFocusRequester: FocusRequester?
) {
    var focused by remember(label) { mutableStateOf(false) }
    val accessibility = LocalRallyAccessibility.current
    Box(
        Modifier
            .size(36.dp)
            .focusRequester(focusRequester)
            .focusProperties { downFocusRequester?.let { down = it } }
            .onFocusChanged { focused = it.isFocused }
            .rallyFocusScale(focused, AppleTvTheme.ButtonFocusScale)
            .clip(navShape)
            .background(if (focused) Color(0xB0233449) else Color.Transparent)
            .border(if (focused && accessibility.highContrastFocus) 3.dp else if (focused) 1.5.dp else 1.dp, if (focused) Color(0xFFF2FAFF) else Color.Transparent, navShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(icon),
            contentDescription = label,
            modifier = Modifier.size(20.dp),
            colorFilter = ColorFilter.tint(AppleTvTheme.OffWhite)
        )
    }
}

@Composable
fun RallyPanel(
    modifier: Modifier = Modifier,
    focused: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier
            .clip(AppleTvTheme.CardShape)
            .background(if (focused) AppleTvTheme.GlassPanelFocusedGradient else AppleTvTheme.GlassPanelGradient)
            .border(
                if (focused) 2.dp else 1.dp,
                if (focused) AppleTvTheme.RallyCyan else Color(0x385A7894),
                AppleTvTheme.CardShape
            ),
        content = content
    )
}

/** Shared, low-cost TV action treatment used by hero, event, and playback controls. */
@Composable
fun RallyControlButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    iconRes: Int? = null,
    loading: Boolean = false,
    enabled: Boolean = true
) {
    var focused by remember(label) { mutableStateOf(false) }
    val accessibility = LocalRallyAccessibility.current
    val foreground = if (primary) AppleTvTheme.DeepNavy else AppleTvTheme.OffWhite
    val surface = when {
        primary -> Brush.verticalGradient(listOf(Color(0xFFF9FBFC), Color(0xFFE4EAF0)))
        focused -> AppleTvTheme.GlassPanelFocusedGradient
        else -> AppleTvTheme.GlassPanelGradient
    }
    Row(
        modifier = modifier
            .height(38.dp)
            .onFocusChanged { focused = it.isFocused }
            .rallyFocusScale(focused, AppleTvTheme.ButtonFocusScale)
            .clip(AppleTvTheme.ButtonShape)
            .background(surface)
            .border(
                width = if (focused && accessibility.highContrastFocus) 3.dp else if (focused) 1.5.dp else 1.dp,
                color = when {
                    focused -> Color(0xD6B9D8EA)
                    primary -> Color(0xD6FFFFFF)
                    else -> AppleTvTheme.GlassBorder
                },
                shape = AppleTvTheme.ButtonShape
            )
            .clickable(enabled = enabled && !loading, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        when {
            loading -> RallyLoadingRing(foreground)
            iconRes != null -> Image(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                colorFilter = ColorFilter.tint(foreground)
            )
        }
        if (loading || iconRes != null) Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            color = foreground.copy(alpha = if (enabled) 1f else .55f),
            fontSize = 11.sp,
            fontFamily = RallyBodyFont,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = .35.sp,
            maxLines = 1
        )
    }
}

@Composable
private fun RallyLoadingRing(color: Color) {
    val rotation by rememberInfiniteTransition(label = "rally-loading").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(850, easing = LinearEasing), RepeatMode.Restart),
        label = "rally-loading-rotation"
    )
    Canvas(Modifier.size(13.dp)) {
        drawArc(
            color = color.copy(alpha = .28f),
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            style = Stroke(width = 1.6.dp.toPx())
        )
        drawArc(
            color = color,
            startAngle = rotation,
            sweepAngle = 92f,
            useCenter = false,
            style = Stroke(width = 1.6.dp.toPx())
        )
    }
}
