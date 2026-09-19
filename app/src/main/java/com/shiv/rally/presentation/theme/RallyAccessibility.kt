package com.shiv.rally.presentation.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

@Immutable
data class RallyAccessibilitySettings(
    val reducedMotion: Boolean = false,
    val highContrastFocus: Boolean = false,
    val largeText: Boolean = false,
    val spokenScoreSummaries: Boolean = false
)

val LocalRallyAccessibility = staticCompositionLocalOf { RallyAccessibilitySettings() }
