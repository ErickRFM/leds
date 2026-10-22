package com.rgbcontroller.core.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val DarkPalette = darkColorScheme(
    primary = Color(0xFFAE94FF),
    onPrimary = Color(0xFF181027),
    primaryContainer = Color(0xFF332454),
    onPrimaryContainer = Color(0xFFF7F0FF),
    secondary = Color(0xFF6FE2D7),
    background = Color(0xFF0C0E15),
    onBackground = Color(0xFFF3F0F7),
    surface = Color(0xFF1A1E2B),
    onSurface = Color(0xFFF3F0F7),
    surfaceVariant = Color(0xFF222638),
    onSurfaceVariant = Color(0xFFB1B5CF),
    outline = Color(0xFF444862),
    outlineVariant = Color(0xFF35394F),
    surfaceContainerLow = Color(0xFF171B27),
    surfaceContainer = Color(0xFF1D2230),
    surfaceContainerHigh = Color(0xFF252A3A),
    error = Color(0xFFFFB4AB)
)
private val LightPalette = lightColorScheme(
    primary = Color(0xFF61428E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEADCFF),
    onPrimaryContainer = Color(0xFF29104D),
    background = Color(0xFFF8F7FB),
    onBackground = Color(0xFF201F26),
    surface = Color.White,
    onSurface = Color(0xFF201F26),
    surfaceVariant = Color(0xFFF0EDF5),
    onSurfaceVariant = Color(0xFF55515F),
    outline = Color(0xFF817A89),
    outlineVariant = Color(0xFFD9D3DF),
    surfaceContainerLow = Color(0xFFFCFAFF),
    surfaceContainer = Color(0xFFF3F0F7),
    surfaceContainerHigh = Color(0xFFEBE6F1)
)

/** Shared minimal matte theme; respects the system unless overridden in Settings. */
@Composable
fun RgbTheme(mode: String, content: @Composable () -> Unit) {
    val dark = when (mode) {
        "DARK" -> true
        "LIGHT" -> false
        else -> isSystemInDarkTheme()
    }
    MaterialTheme(colorScheme = if (dark) DarkPalette else LightPalette, content = content)
}

@Composable
fun SectionHeader(title: String, subtitle: String) {
    Column {
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
