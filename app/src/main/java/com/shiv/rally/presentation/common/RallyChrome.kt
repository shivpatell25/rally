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
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.shiv.rally.R
import com.shiv.rally.presentation.theme.AppleTvTheme
import com.shiv.rally.presentation.theme.RallyBodyFont
import com.shiv.rally.presentation.theme.LocalRallyAccessibility








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
                if (focused) AppleTvTheme.GlassBorderFocused else Color(0x385A7894),
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
    val currentOnClick by rememberUpdatedState(onClick)
    val actionGate = remember { TvActionGate(450L) }
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
                    focused -> AppleTvTheme.GlassBorderFocused
                    primary -> Color(0xD6FFFFFF)
                    else -> AppleTvTheme.GlassBorder
                },
                shape = AppleTvTheme.ButtonShape
            )
            .clickable(enabled = enabled && !loading) {
                if (actionGate.tryAcquire(label)) currentOnClick()
            }
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
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
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
