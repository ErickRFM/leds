package com.rgbcontroller.core.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// Compi Aurora Matte Dark Palette
private val CompiDarkPalette = darkColorScheme(
    primary = CompiTokens.Colors.PrimaryViolet,
    onPrimary = CompiTokens.Colors.OnPrimaryViolet,
    primaryContainer = CompiTokens.Colors.PrimaryVioletContainer,
    onPrimaryContainer = Color(0xFFF2ECFF),
    secondary = CompiTokens.Colors.SecondaryAqua,
    onSecondary = Color(0xFF042825),
    secondaryContainer = CompiTokens.Colors.SecondaryAquaContainer,
    onSecondaryContainer = Color(0xFFC7F9F2),
    tertiary = CompiTokens.Colors.ActiveGreen,
    onTertiary = Color(0xFF052B19),
    tertiaryContainer = CompiTokens.Colors.ActiveGreenContainer,
    onTertiaryContainer = Color(0xFFAEFAD8),
    background = CompiTokens.Colors.MatteBackground,
    onBackground = Color(0xFFF1F5F9),
    surface = CompiTokens.Colors.SurfaceElevated,
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = CompiTokens.Colors.SurfaceCard,
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = CompiTokens.Colors.SurfaceOutline,
    outlineVariant = CompiTokens.Colors.SurfaceOutlineSubtle,
    surfaceContainerLow = CompiTokens.Colors.DeepBackground,
    surfaceContainer = CompiTokens.Colors.SurfaceElevated,
    surfaceContainerHigh = CompiTokens.Colors.SurfaceVariant,
    error = CompiTokens.Colors.ErrorRed,
    onError = Color(0xFF380809),
    errorContainer = CompiTokens.Colors.ErrorRedContainer,
    onErrorContainer = Color(0xFFFFD1D1)
)

// Deep Black Palette (Pure OLED Dark)
private val CompiDeepBlackPalette = darkColorScheme(
    primary = CompiTokens.Colors.PrimaryViolet,
    onPrimary = CompiTokens.Colors.OnPrimaryViolet,
    primaryContainer = Color(0xFF1E1538),
    onPrimaryContainer = Color(0xFFF2ECFF),
    secondary = CompiTokens.Colors.SecondaryAqua,
    background = Color(0xFF000000),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF0D0D11),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF14141A),
    onSurfaceVariant = Color(0xFFA1A1AA),
    outline = Color(0xFF27272A),
    outlineVariant = Color(0xFF18181B),
    surfaceContainerLow = Color(0xFF000000),
    surfaceContainer = Color(0xFF0D0D11),
    surfaceContainerHigh = Color(0xFF18181B),
    error = CompiTokens.Colors.ErrorRed
)

// Clean Light Palette
private val CompiLightPalette = lightColorScheme(
    primary = Color(0xFF6B42DC),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEDE7FF),
    onPrimaryContainer = Color(0xFF230066),
    secondary = Color(0xFF006A60),
    background = CompiTokens.Colors.LightBackground,
    onBackground = Color(0xFF1E293B),
    surface = Color.White,
    onSurface = Color(0xFF1E293B),
    surfaceVariant = Color(0xFFEEECF5),
    onSurfaceVariant = Color(0xFF64748B),
    outline = Color(0xFFCBD5E1),
    outlineVariant = Color(0xFFE2E8F0),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFF8FAFCE),
    surfaceContainerHigh = Color(0xFFF1F5F9)
)

/**
 * Compi Aurora Global Theme. Supports DARK, LIGHT, DEEP_BLACK, or SYSTEM.
 */
@Composable
fun RgbTheme(mode: String, content: @Composable () -> Unit) {
    val darkSystem = isSystemInDarkTheme()
    val scheme = when (mode) {
        "DARK" -> CompiDarkPalette
        "DEEP_BLACK" -> CompiDeepBlackPalette
        "LIGHT" -> CompiLightPalette
        else -> if (darkSystem) CompiDarkPalette else CompiLightPalette
    }

    CompositionLocalProvider(LocalCompiTokens provides CompiTokens) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

@Composable
fun SectionHeader(title: String, subtitle: String) {
    Column {
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
