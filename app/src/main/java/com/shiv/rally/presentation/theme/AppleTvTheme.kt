package com.shiv.rally.presentation.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api

/** Rally's TV design system: cinematic, editorial, and deliberately inexpensive to render. */
object AppleTvTheme {
    // Alpha-layered "glass" keeps the visual depth of the mockups without realtime blur.
    val RallyLime = Color(0xFFEAFB78)
    val RallyMint = Color(0xFFB8F3C7)
    val RallyCyan = Color(0xFF6FCFF6)
    val DeepNavy = Color(0xFF050507)
    val Slate = Color(0xFF111418)
    val Graphite = Color(0xFF24282C)
    val OffWhite = Color(0xFFF5F7FA)

    val DarkBackground = DeepNavy
    val BackgroundDark = DarkBackground
    val DarkBackgroundElevated = Slate
    val GlassSurface = Color(0xA611161C)
    val GlassSurfaceSubtle = Color(0x860E1318)
    val GlassSurfaceHover = Color(0xD2293038)
    val GlassSurfaceFocused = Color(0xE0293038)
    val GlassSurfaceDefault = Color(0x9411161C)
    val GlassSurfaceHeavy = Color(0xC805080F)
    val GlassBorder = Color(0x59858D96)
    val Divider = Color(0x1FF5F7FA)
    val SurfaceBase = Color(0xFF11161C)
    val SurfaceRaised = Color(0xFF1B2229)
    val SurfaceFocused = Color(0xFF293038)
    val GlassBorderFocused = Color(0xFFD1D7DD)

    // Apple System Accent Tints
    val AccentBlue = Color(0xFFD1D7DD)
    val AccentGreen = Color(0xFFD1D7DD)
    val AccentRed = Color(0xFFFF3B30)
    val LiveRed = Color(0xFFFF453A)
    val AccentOrange = Color(0xFFFF9500)
    val AccentIndigo = Color(0xFF5E5CE6)
    val AccentPurple = Color(0xFFBF5AF2)
    val GoldBadge = Color(0xFFFFD60A)

    // Text Hierarchy
    val TextPrimary = OffWhite
    val TextSecondary = Color(0xB8F5F7FA)
    val TextTertiary = Color(0x73F5F7FA)
    val TextInverse = DeepNavy

    // 2. Shapes (tvOS Curvature)
    val HeroShape = RoundedCornerShape(12.dp)
    val CardShape = RoundedCornerShape(10.dp)
    val ButtonShape = RoundedCornerShape(8.dp)
    val PillShape = RoundedCornerShape(6.dp)
    val TagShape = RoundedCornerShape(6.dp)
    val DialogShape = RoundedCornerShape(12.dp)

    // 3. Borders
    val CardBorderUnfocused = BorderStroke(1.dp, GlassBorder)
    val CardBorderFocused = BorderStroke(1.dp, GlassBorderFocused)
    val ButtonBorderUnfocused = BorderStroke(0.dp, Color.Transparent)
    val ButtonBorderFocused = BorderStroke(1.dp, GlassBorderFocused)

    // 4. Gradients (Lightweight, GPU-friendly scrims)
    val HeroGradient = Brush.verticalGradient(
        0.0f to Color.Transparent,
        0.60f to Color(0x88000000),
        1.0f to DeepNavy
    )

    val ScreenGradient = Brush.verticalGradient(
        0.0f to Color(0xFF08090B),
        0.42f to Slate,
        1.0f to DeepNavy
    )

    val CardVignetteGradient = Brush.verticalGradient(
        0.0f to Color.Transparent,
        0.50f to Color.Transparent,
        1.0f to Color(0xFF000000)
    )

    val GlassPanelGradient = Brush.verticalGradient(
        0.0f to Color(0xB8242A31),
        0.18f to Color(0xA4171D23),
        1.0f to Color(0x900E1318)
    )

    val GlassPanelFocusedGradient = Brush.verticalGradient(
        0.0f to Color(0xD0343B43),
        0.24f to Color(0xBC262D35),
        1.0f to Color(0xA8161D24)
    )

    // 5. Typography Tokens (Optimized for 10-foot TV viewing)
    object Typography {
        val HeroTitle = TextStyle(
            fontFamily = RallyBodyFont,
            fontSize = 42.sp,
            fontWeight = FontWeight.Black,
            color = TextPrimary,
            letterSpacing = (-1).sp,
            lineHeight = 46.sp
        )
        val SectionHeader = TextStyle(
            fontFamily = RallyBodyFont,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            letterSpacing = (-0.5).sp
        )
        val CardTitle = TextStyle(
            fontFamily = RallyBodyFont,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            lineHeight = 22.sp
        )
        val CardScore = TextStyle(
            fontFamily = RallyBodyFont,
            fontSize = 26.sp,
            fontWeight = FontWeight.Black,
            color = TextPrimary,
            letterSpacing = (-0.5).sp
        )
        val Subhead = TextStyle(
            fontFamily = RallyBodyFont,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = TextSecondary
        )
        val Badge = TextStyle(
            fontFamily = RallyBodyFont,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
        val ButtonLabel = TextStyle(
            fontFamily = RallyBodyFont,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.2.sp
        )
    }

    val ButtonLabel = Typography.ButtonLabel
    val SectionHeader = Typography.SectionHeader

    // 6. Focus Scale & Animation
    // Keep the tvOS lift without forcing large layers and overdraw on low-end TV GPUs.
    const val CardFocusScale = 1.025f
    const val ButtonFocusScale = 1.02f
    val FocusAnimationSpec = tween<Float>(durationMillis = 150, easing = FastOutSlowInEasing)

    // tvOS Material Card & Button Defaults
    @OptIn(ExperimentalTvMaterial3Api::class)
    val cardBorder = Border(
        border = CardBorderUnfocused,
        shape = CardShape
    )

    @OptIn(ExperimentalTvMaterial3Api::class)
    val cardFocusedBorder = Border(
        border = CardBorderFocused,
        shape = CardShape
    )
}
