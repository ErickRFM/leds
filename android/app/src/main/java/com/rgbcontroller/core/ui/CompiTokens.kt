package com.rgbcontroller.core.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Compi Aurora Centralized Design Tokens
 * Premium matte dark mode with depth, glassmorphism, violet accents, and climate gradients.
 */
object CompiTokens {
    object Colors {
        // Backgrounds
        val DeepBackground = Color(0xFF080B12)
        val MatteBackground = Color(0xFF0C101B)
        val LightBackground = Color(0xFFF4F3F8)

        // Surfaces
        val SurfaceElevated = Color(0xFF141926)
        val SurfaceCard = Color(0xFF1B2132)
        val SurfaceCardTranslucent = Color(0xD81B2132)
        val SurfaceVariant = Color(0xFF232A3E)
        val SurfaceOutline = Color(0xFF333D56)
        val SurfaceOutlineSubtle = Color(0xFF262E42)

        // Primary Accent (Violet / Aurora)
        val PrimaryViolet = Color(0xFFA88BFF)
        val PrimaryVioletDark = Color(0xFF7A52F4)
        val PrimaryVioletContainer = Color(0xFF2A1F4C)
        val OnPrimaryViolet = Color(0xFF150B30)

        // Secondary Accent (Cyan / Aqua)
        val SecondaryAqua = Color(0xFF42E3D0)
        val SecondaryAquaContainer = Color(0xFF143B38)

        // Status Colors
        val ActiveGreen = Color(0xFF27D987)
        val ActiveGreenContainer = Color(0xFF123827)
        val WarningAmber = Color(0xFFFFB84D)
        val WarningAmberContainer = Color(0xFF3D2A10)
        val ErrorRed = Color(0xFFFF5252)
        val ErrorRedContainer = Color(0xFF3B1214)
        val ColdBlue = Color(0xFF46C6EF)

        // Climate Gradient Brushes
        val ColdGradient = Brush.horizontalGradient(listOf(Color(0xFF2B5B84), Color(0xFF38A1DB)))
        val ComfortGradient = Brush.horizontalGradient(listOf(Color(0xFF1B5E3C), Color(0xFF27D987)))
        val HotGradient = Brush.horizontalGradient(listOf(Color(0xFF8A3B1B), Color(0xFFFF6B4A)))
        val AuroraGradient = Brush.horizontalGradient(listOf(Color(0xFF4A3185), Color(0xFFA88BFF), Color(0xFF27D987)))
        val GlassOverlay = Brush.verticalGradient(listOf(Color(0x28FFFFFF), Color(0x05FFFFFF)))
    }

    object Shapes {
        val Small = RoundedCornerShape(12.dp)
        val Medium = RoundedCornerShape(18.dp)
        val Large = RoundedCornerShape(24.dp)
        val ExtraLarge = RoundedCornerShape(28.dp)
        val Full = RoundedCornerShape(100.dp)
    }

    object Spacing {
        val xxs: Dp = 4.dp
        val xs: Dp = 8.dp
        val s: Dp = 12.dp
        val m: Dp = 16.dp
        val l: Dp = 20.dp
        val xl: Dp = 24.dp
        val xxl: Dp = 32.dp
    }

    object Elevations {
        val flat: Dp = 0.dp
        val card: Dp = 4.dp
        val modal: Dp = 12.dp
    }

    object Motion {
        val FastSpring = spring<Float>(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioLowBouncy)
        val SmoothSpring = spring<Float>(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioNoBouncy)
    }

    object Typography {
        val DisplayHero = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Bold,
            fontSize = 36.sp,
            lineHeight = 42.sp,
            letterSpacing = (-0.5).sp
        )
        val HeadlineTitle = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.SemiBold,
            fontSize = 22.sp,
            lineHeight = 28.sp
        )
        val SectionHeader = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.SemiBold,
            fontSize = 17.sp,
            lineHeight = 22.sp
        )
        val MetricValue = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Bold,
            fontSize = 28.sp,
            lineHeight = 34.sp
        )
        val BodyClean = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            lineHeight = 20.sp
        )
        val BadgeLabel = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.2.sp
        )
    }
}

val LocalCompiTokens = staticCompositionLocalOf { CompiTokens }
