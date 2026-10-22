package com.rgbcontroller.feature.modules

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rgbcontroller.core.ui.CompiBadge
import com.rgbcontroller.core.ui.CompiBadgeType
import com.rgbcontroller.core.ui.CompiTokens
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Advanced RGB Lighting Control Panel.
 * Includes interactive color wheel, brightness slider, preset palette,
 * mode toggles, and confirmed physical hardware output status.
 */
@Composable
fun RgbLightingPanel(
    currentColor: Color,
    brightness: Float,
    isEnvironmentalMode: Boolean,
    isAuthorized: Boolean,
    onColorChanged: (Color) -> Unit,
    onBrightnessChanged: (Float) -> Unit,
    onEnvironmentalModeToggled: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedColor by remember(currentColor) { mutableStateOf(currentColor) }
    var currentBrightness by remember(brightness) { mutableFloatStateOf(brightness) }

    val presets = listOf(
        Color(0xFFA88BFF) to "Violeta",
        Color(0xFF27D987) to "Esmeralda",
        Color(0xFF46C6EF) to "Cian",
        Color(0xFFFFB84D) to "Cálido",
        Color(0xFFFF5252) to "Rojo",
        Color(0xFFFFFFFF) to "Blanco"
    )

    Card(
        shape = CompiTokens.Shapes.Large,
        colors = CardDefaults.cardColors(containerColor = CompiTokens.Colors.SurfaceCard),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.ColorLens,
                        contentDescription = null,
                        tint = CompiTokens.Colors.PrimaryViolet,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Iluminación RGB",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = if (isEnvironmentalMode) "Modo Automático Ambiental" else "Modo Manual",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }

                CompiBadge(
                    label = if (isAuthorized) "ESP32 Confirmado" else "Sin Enlace",
                    type = if (isAuthorized) CompiBadgeType.CONFIRMED else CompiBadgeType.OFFLINE
                )
            }

            // Mode Switch Card
            Surface(
                shape = CompiTokens.Shapes.Medium,
                color = CompiTokens.Colors.SurfaceElevated,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Modo Ambiental Automático",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        Text(
                            text = "El ESP32 ajusta el color según temperatura y humedad",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF94A3B8)
                        )
                    }
                    Switch(
                        checked = isEnvironmentalMode,
                        onCheckedChange = onEnvironmentalModeToggled,
                        enabled = isAuthorized
                    )
                }
            }

            // Interactive Color Wheel (Visible in Manual Mode)
            if (!isEnvironmentalMode) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier.size(180.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(
                            modifier = Modifier
                                .size(180.dp)
                                .pointerInput(Unit) {
                                    detectDragGestures { change, _ ->
                                        change.consume()
                                        val center = Offset(size.width / 2f, size.height / 2f)
                                        val touch = change.position - center
                                        val angle = atan2(touch.y, touch.x)
                                        val hue = ((angle * 180f / Math.PI.toFloat()) + 360f) % 360f
                                        val newColor = Color.hsv(hue, 0.85f, currentBrightness)
                                        selectedColor = newColor
                                        onColorChanged(newColor)
                                    }
                                }
                        ) {
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val radius = size.minDimension / 2f

                            val rainbow = listOf(
                                Color.Red, Color.Yellow, Color.Green,
                                Color.Cyan, Color.Blue, Color.Magenta, Color.Red
                            )
                            drawCircle(
                                brush = Brush.sweepGradient(rainbow, center),
                                radius = radius
                            )
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(Color.White.copy(alpha = 0.85f), Color.Transparent),
                                    center = center,
                                    radius = radius
                                )
                            )
                        }

                        // Selected Color Circle Preview in Center
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(selectedColor)
                                .border(3.dp, Color.White, CircleShape)
                        )
                    }

                    Spacer(Modifier.height(14.dp))

                    // Brightness Slider
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Brillo",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White
                            )
                            Text(
                                text = "%.0f%%".format(currentBrightness * 100f),
                                style = MaterialTheme.typography.labelMedium,
                                color = CompiTokens.Colors.PrimaryViolet
                            )
                        }
                        Slider(
                            value = currentBrightness,
                            onValueChange = {
                                currentBrightness = it
                                onBrightnessChanged(it)
                            },
                            enabled = isAuthorized
                        )
                    }

                    // Presets
                    Text(
                        text = "Presets rápidos",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        modifier = Modifier.align(Alignment.Start)
                    )
                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        presets.forEach { (color, label) ->
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .clickable {
                                        selectedColor = color
                                        onColorChanged(color)
                                    }
                                    .border(2.dp, if (selectedColor == color) Color.White else Color.Transparent, CircleShape)
                            )
                        }
                    }
                }
            }
        }
    }
}
