@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.shiv.rally.presentation.common

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.shiv.rally.presentation.theme.AppleTvTheme
import com.shiv.rally.presentation.theme.LocalRallyAccessibility

private val skeletonShape = RoundedCornerShape(10.dp)

@Composable
fun RallyDashboardSkeleton(modifier: Modifier = Modifier) {
    val reducedMotion = LocalRallyAccessibility.current.reducedMotion
    val transition = rememberInfiniteTransition(label = "skeleton")
    val animatedAlpha by transition.animateFloat(
        initialValue = .35f,
        targetValue = .7f,
        animationSpec = infiniteRepeatable(tween(850), RepeatMode.Reverse),
        label = "skeleton-alpha"
    )
    val alpha = if (reducedMotion) .5f else animatedAlpha
    Column(modifier.fillMaxSize().padding(horizontal = 30.dp, vertical = 18.dp).alpha(alpha)) {
        Box(Modifier.fillMaxWidth().height(235.dp).clip(skeletonShape).background(Color(0xFF15202C)).border(1.dp, AppleTvTheme.GlassBorder, skeletonShape))
        Spacer(Modifier.height(16.dp))
        Box(Modifier.width(130.dp).height(14.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFF273442)))
        Spacer(Modifier.height(9.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(4) { Box(Modifier.weight(1f).height(105.dp).clip(skeletonShape).background(Color(0xFF121C27)).border(1.dp, AppleTvTheme.GlassBorder, skeletonShape)) }
        }
        Spacer(Modifier.height(16.dp))
        Box(Modifier.width(105.dp).height(14.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFF273442)))
        Spacer(Modifier.height(9.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(5) { Box(Modifier.weight(1f).height(98.dp).clip(skeletonShape).background(Color(0xFF121C27)).border(1.dp, AppleTvTheme.GlassBorder, skeletonShape)) }
        }
    }
}

data class RallyErrorCopy(val title: String, val guidance: String)

fun classifyRallyError(message: String): RallyErrorCopy {
    val value = message.lowercase()
    return when {
        listOf("401", "403", "auth", "token", "credential", "mac").any(value::contains) ->
            RallyErrorCopy("Source sign-in needs attention", "Check the provider address and credentials in Settings, then try again.")
        listOf("network", "host", "dns", "timeout", "connect", "internet").any(value::contains) ->
            RallyErrorCopy("Rally can’t reach the sports feeds", "Check the TV’s connection. Your last known schedule remains safe and Rally will retry cleanly.")
        listOf("stream", "play", "decoder", "source", "broadcast").any(value::contains) ->
            RallyErrorCopy("This broadcast is unavailable", "Try another verified source or return to the game page while Rally refreshes its source health.")
        else -> RallyErrorCopy("Sports data is temporarily unavailable", "The feed did not return usable data. Try again in a moment.")
    }
}

@Composable
fun RallyActionableError(
    message: String,
    onRetry: (() -> Unit)? = null,
    onSettings: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null
) {
    val copy = classifyRallyError(message)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.width(520.dp).clip(skeletonShape).background(AppleTvTheme.GlassPanelGradient)
                .border(1.dp, AppleTvTheme.GlassBorder, skeletonShape).padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(copy.title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(copy.guidance, color = AppleTvTheme.TextSecondary, fontSize = 13.sp, lineHeight = 19.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                onRetry?.let { RallyControlButton("Try Again", it, primary = true) }
                onSettings?.let { RallyControlButton("Open Settings", it) }
                onBack?.let { RallyControlButton("Back", it) }
            }
        }
    }
}
