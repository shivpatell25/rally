@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.shiv.rally.presentation.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

import androidx.tv.material3.Text
import com.shiv.rally.R
import com.shiv.rally.presentation.theme.RallyBodyFont
import com.shiv.rally.presentation.theme.LocalRallyAccessibility
import androidx.compose.ui.text.style.TextOverflow

object RallyTvPalette {
    val Background = Color(0xFF020203)
    val BackgroundSoft = Color(0xFF0A1015)
    val Text = Color(0xFFF5F7FA)
    val Muted = Color(0xFFB0B6C0)
    val Subtle = Color(0xFF737D8A)
    // Rally's gradient stays in the wordmark and background. Interactive
    // copy remains neutral so the interface does not turn into a field of green text.
    val Accent = Color(0xFFE8EDF2)
    val FocusEdge = Color(0xFFCBD2D8)
    val Live = Color(0xFFFF453A)
    val Divider = Color(0x554B535B)
    val FocusSurface = Color(0xFF1A2026)
}

enum class RallyTvDestination { HOME, LIVE, SCHEDULE, LEAGUES, HIGHLIGHTS, MY_RALLY, SEARCH, SETTINGS }

class RallyTvChromeFocus {
    val home = FocusRequester()
    val live = FocusRequester()
    val schedule = FocusRequester()
    val leagues = FocusRequester()
    val highlights = FocusRequester()
    val myRally = FocusRequester()
    val search = FocusRequester()
    val settings = FocusRequester()
}

@Composable
fun RallyTvBackdrop(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(RallyTvPalette.Background)) {
        // One static, display-sized texture shared by every screen and the nav shell.
        // The neutral-black body prevents a blue tint behind the content.
        val backdrop = painterResource(R.drawable.rally_tv_background_v8)
        Canvas(Modifier.fillMaxSize()) {
            // The asset has faded completely to black here. Clip the invisible
            // portion rather than blending a full-screen bitmap on every frame.
            clipRect(right = size.width * .55f) {
                with(backdrop) { draw(size = size, alpha = .42f) }
            }
        }
        content()
    }
}

@Composable
fun RallyTvHeroGlow(modifier: Modifier = Modifier) {
    Box(modifier.drawWithCache {
        val glow = Brush.radialGradient(
            0f to Color(0xBBDDE780),
            .28f to Color(0x992D827E),
            .7f to Color(0x550B4F68),
            1f to Color.Transparent,
            center = Offset.Zero,
            radius = size.width * .31f
        )
        onDrawBehind { drawRect(glow) }
    })
}

@Composable
fun RallyTvTopBar(
    selected: RallyTvDestination?,
    onHome: () -> Unit,
    onLive: () -> Unit,
    onSchedule: () -> Unit,
    onLeagues: () -> Unit,
    onHighlights: () -> Unit,
    onMyRally: () -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    focus: RallyTvChromeFocus,
    contentFocusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(70.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(70.dp)
                .padding(horizontal = 60.dp)
                .padding(top = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
        Image(
            painter = painterResource(R.drawable.rally_wordmark_color_ui),
            contentDescription = "Rally",
            modifier = Modifier.width(94.dp).height(40.dp)
        )
        Spacer(Modifier.weight(1f))
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RallyTvNavItem("Home", selected == RallyTvDestination.HOME, focus.home, contentFocusRequester, FocusRequester.Cancel, focus.live, onHome)
            RallyTvNavItem("Live", selected == RallyTvDestination.LIVE, focus.live, contentFocusRequester, focus.home, focus.schedule, onLive)
            RallyTvNavItem("Schedule", selected == RallyTvDestination.SCHEDULE, focus.schedule, contentFocusRequester, focus.live, focus.leagues, onSchedule)
            RallyTvNavItem("Leagues", selected == RallyTvDestination.LEAGUES, focus.leagues, contentFocusRequester, focus.schedule, focus.highlights, onLeagues)
            RallyTvNavItem("Highlights", selected == RallyTvDestination.HIGHLIGHTS, focus.highlights, contentFocusRequester, focus.leagues, focus.myRally, onHighlights)
            RallyTvNavItem("My Rally", selected == RallyTvDestination.MY_RALLY, focus.myRally, contentFocusRequester, focus.highlights, focus.search, onMyRally)
        }
        Spacer(Modifier.weight(1f))
        RallyTvNavIcon("Search", selected == RallyTvDestination.SEARCH, focus.search, contentFocusRequester, focus.myRally, focus.settings, onSearch, search = true)
        Spacer(Modifier.width(5.dp))
        RallyTvNavIcon("Settings", selected == RallyTvDestination.SETTINGS, focus.settings, contentFocusRequester, focus.search, FocusRequester.Cancel, onSettings, search = false)
        }
    }
}

@Composable
private fun RallyTvNavIcon(
    label: String,
    selected: Boolean,
    requester: FocusRequester,
    downFocusRequester: FocusRequester?,
    leftFocus: FocusRequester,
    rightFocus: FocusRequester,
    onClick: () -> Unit,
    search: Boolean
) {
    var focused by remember(label) { mutableStateOf(false) }
    Box(
        Modifier
            .size(34.dp)
            .focusRequester(requester)
            .focusProperties {
                if (downFocusRequester != null) down = downFocusRequester
                left = leftFocus; right = rightFocus; up = FocusRequester.Cancel
            }
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected || focused) Color.White.copy(alpha = .1f) else Color.Transparent)
            .clickable(onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        if (!search) {
            Image(painterResource(R.drawable.ic_rally_settings), null, Modifier.size(19.dp))
        } else Canvas(Modifier.size(17.dp)) {
            val color = if (selected || focused) RallyTvPalette.Text else RallyTvPalette.Muted
            val strokeWidth = 1.7.dp.toPx()
            drawCircle(color, radius = size.minDimension * .31f, center = Offset(size.width * .42f, size.height * .42f), style = Stroke(strokeWidth))
            drawLine(color, Offset(size.width * .64f, size.height * .64f), Offset(size.width * .92f, size.height * .92f), strokeWidth)
        }
    }
}

@Composable
private fun RallyTvNavItem(
    label: String,
    selected: Boolean,
    requester: FocusRequester,
    downFocusRequester: FocusRequester?,
    leftFocus: FocusRequester,
    rightFocus: FocusRequester,
    onClick: () -> Unit
) {
    var focused by remember(label) { mutableStateOf(false) }
    Box(
        Modifier
            .onFocusChanged { focused = it.isFocused }
            .focusRequester(requester)
            .focusProperties {
                if (downFocusRequester != null) down = downFocusRequester
                left = leftFocus; right = rightFocus; up = FocusRequester.Cancel
            }
            .rallyTvFocus(focused, scaleWhenFocused = 1.02f)
            .then(
                if (selected || focused) Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.White.copy(alpha = if (focused) .16f else .12f), Color.White.copy(alpha = .055f))
                        )
                    )
                    .border(
                        1.dp,
                        if (focused) Color.White.copy(alpha = .34f) else Color.White.copy(alpha = .16f),
                        RoundedCornerShape(8.dp)
                    )
                else Modifier
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (focused || selected) RallyTvPalette.Text else RallyTvPalette.Muted,
            fontFamily = RallyBodyFont,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            letterSpacing = .15.sp
        )
    }
}


@Composable
fun RallyTvActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true,
    focusRequester: FocusRequester? = null,
    onFocused: () -> Unit = {}
) {
    var focused by remember(label) { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .rallyTvFocus(focused, scaleWhenFocused = 1.02f)
            .clip(shape)
            .background(
                when {
                    primary -> Color(0xFFF8FAFC)
                    focused -> RallyTvPalette.FocusSurface
                    else -> Color(0xCC171C22)
                }
            )
            .border(
                width = if (focused && !primary) 1.dp else 0.dp,
                color = if (focused && !primary) Color(0x99D1D7DD) else Color.Transparent,
                shape = shape
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            label.uppercase(),
            color = when {
                !enabled -> RallyTvPalette.Subtle
                primary -> Color.Black
                else -> RallyTvPalette.Text
            },
            fontFamily = RallyBodyFont,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.05.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
    }
}

@Composable
fun Modifier.rallyTvFocus(focused: Boolean, scaleWhenFocused: Float = 1.04f): Modifier {
    val reducedMotion = LocalRallyAccessibility.current.reducedMotion
    val scale = animateFloatAsState(
        if (focused) scaleWhenFocused else 1f,
        if (reducedMotion) snap() else spring(dampingRatio = 1f, stiffness = 750f), label = "rally-focus-lift"
    )
    // Read the animation in the layer, so focus motion never remeasures a rail.
    return graphicsLayer { scaleX = scale.value; scaleY = scale.value }
}

/** Read-only rows still need TV focus so long tables can be explored with a remote. */
@Composable
fun Modifier.rallyReadableFocus(): Modifier {
    var focused by remember { mutableStateOf(false) }
    return onFocusChanged { focused = it.isFocused }
        .clip(RoundedCornerShape(4.dp))
        .background(if (focused) RallyTvPalette.FocusSurface else Color.Transparent)
        .focusable()
}

@Composable
fun RallyTvRule(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(RallyTvPalette.Divider))
}
