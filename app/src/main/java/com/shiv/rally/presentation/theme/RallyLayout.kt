package com.shiv.rally.presentation.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** Shared TV-safe geometry. Keeps every surface aligned to the same 16:9 rhythm. */
object RallyLayout {
    val SafeHorizontal = 46.dp
    val SafeVertical = 30.dp
    val SectionSpacing = 16.dp
    val ControlSpacing = 9.dp
    val PanelPadding = 18.dp
    val CardCorner = RoundedCornerShape(10.dp)
    val ControlCorner = RoundedCornerShape(8.dp)
}
