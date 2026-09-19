package com.shiv.rally.presentation.common

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.snap
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import com.shiv.rally.presentation.theme.LocalRallyAccessibility

/**
 * Rally's inexpensive TV focus motion. This only animates a render-layer scale,
 * avoiding layout, blur, and image work on every frame.
 */
fun Modifier.rallyFocusScale(
    focused: Boolean,
    focusedScale: Float = 1.018f
): Modifier = composed {
    val accessibility = LocalRallyAccessibility.current
    val scale by animateFloatAsState(
        targetValue = if (focused && !accessibility.reducedMotion) focusedScale else 1f,
        animationSpec = if (accessibility.reducedMotion) snap() else tween(durationMillis = 120, easing = FastOutSlowInEasing),
        label = "Rally focus scale"
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
