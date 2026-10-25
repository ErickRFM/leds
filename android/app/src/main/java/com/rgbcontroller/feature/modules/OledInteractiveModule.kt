package com.rgbcontroller.feature.modules

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.InvertColors
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rgbcontroller.core.ui.CompiBadge
import com.rgbcontroller.core.ui.CompiBadgeType
import com.rgbcontroller.core.ui.CompiTokens
import com.rgbcontroller.core.ble.OledHardwareState

data class OledExpression(val id: String, val name: String, val symbol: String)

/**
 * Interactive SSD1306 OLED Module Screen.
 * Renders a faithful 128x64 monochrome preview,
 * firmware expression selectors, contrast & animation controls.
 */
@Composable
fun OledInteractiveModule(
    isAuthorized: Boolean,
    onSendExpression: (String) -> Unit,
    onSetContrast: (Int) -> Unit,
    confirmed: OledHardwareState?,
    onSetInverted: (Boolean) -> Unit,
    onSetPaused: (Boolean) -> Unit,
    feedback: String? = null,
    modifier: Modifier = Modifier
) {
    val selectedExpression = confirmed?.scene ?: "unknown"
    var contrastLevel by remember(confirmed?.ready) { mutableFloatStateOf((confirmed?.contrast ?: 128).toFloat()) }
    val isInverted = confirmed?.inverted == true
    val isAnimActive = confirmed?.paused == false
    val canControl = isAuthorized && confirmed?.ready == true

    val expressions = listOf(
        OledExpression("happy", "Sonrisa", "◕ ᴗ ◕"),
        OledExpression("pumpkin", "Calabaza", "🎃"),
        OledExpression("cat", "Gato", "🐱"),
        OledExpression("dance", "Gato Baile", "🐱‍👤"),
        OledExpression("sleepy", "Sueño", "－ ᴗ －"),
        OledExpression("surprised", "Sorpresa", "● ○"),
        OledExpression("angry", "Alerta", "⌁ ⌁")
    )

    Card(
        shape = CompiTokens.Shapes.Large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Keep status on its own line; do not squeeze the title on small screens.
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.DesktopWindows,
                        contentDescription = null,
                        tint = CompiTokens.Colors.PrimaryViolet,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Pantalla OLED SSD1306",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Monocromo 128×64 · I2C GPIO 21, 22",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    CompiBadge(
                        label = if (canControl) "Operativa" else "Sin confirmar",
                        type = if (canControl) CompiBadgeType.LIVE else CompiBadgeType.OFFLINE
                    )
                }
            }

            // Faithful 128x64 Monochrome Canvas Preview
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2.1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF070B14))
                    .border(3.dp, Color(0xFF2A344E), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                val bgPixelColor = if (isInverted) Color(0xFF9EE6FF) else Color(0xFF070B14)
                val fgPixelColor = if (isInverted) Color(0xFF070B14) else Color(0xFF9EE6FF)

                Canvas(modifier = Modifier.matchParentSize()) {
                    drawRect(bgPixelColor)

                    val w = size.width
                    val h = size.height
                    val cx = w / 2f
                    val cy = h / 2f

                    // Render Pixel Art Preview based on expression
                    when (selectedExpression) {
                        "unknown", "telemetry", "text" -> Unit
                        "pumpkin" -> {
                            // Pumpkin Outline
                            drawCircle(
                                color = fgPixelColor,
                                radius = h * 0.35f,
                                center = Offset(cx, cy),
                                style = Stroke(width = 4f)
                            )
                            // Stem
                            drawLine(fgPixelColor, Offset(cx, cy - h * 0.35f), Offset(cx, cy - h * 0.48f), strokeWidth = 5f)
                            // Eyes
                            drawPath(
                                Path().apply {
                                    moveTo(cx - 20f, cy - 10f)
                                    lineTo(cx - 10f, cy + 5f)
                                    lineTo(cx - 30f, cy + 5f)
                                    close()
                                },
                                fgPixelColor
                            )
                            drawPath(
                                Path().apply {
                                    moveTo(cx + 20f, cy - 10f)
                                    lineTo(cx + 30f, cy + 5f)
                                    lineTo(cx + 10f, cy + 5f)
                                    close()
                                },
                                fgPixelColor
                            )
                        }
                        "cat", "dance" -> {
                            // Cat Ears
                            drawPath(
                                Path().apply {
                                    moveTo(cx - 30f, cy - 20f)
                                    lineTo(cx - 15f, cy - 45f)
                                    lineTo(cx, cy - 20f)
                                    close()
                                },
                                fgPixelColor
                            )
                            drawPath(
                                Path().apply {
                                    moveTo(cx + 30f, cy - 20f)
                                    lineTo(cx + 15f, cy - 45f)
                                    lineTo(cx, cy - 20f)
                                    close()
                                },
                                fgPixelColor
                            )
                            // Cat Head
                            drawCircle(fgPixelColor, radius = h * 0.30f, center = Offset(cx, cy), style = Stroke(width = 4f))
                            // Eyes
                            drawCircle(fgPixelColor, radius = 6f, center = Offset(cx - 15f, cy - 5f))
                            drawCircle(fgPixelColor, radius = 6f, center = Offset(cx + 15f, cy - 5f))
                        }
                        else -> {
                            // Happy / Default Smile
                            drawCircle(fgPixelColor, radius = 8f, center = Offset(cx - 25f, cy - 10f))
                            drawCircle(fgPixelColor, radius = 8f, center = Offset(cx + 25f, cy - 10f))
                            drawPath(
                                Path().apply {
                                    moveTo(cx - 20f, cy + 10f)
                                    quadraticBezierTo(cx, cy + 28f, cx + 20f, cy + 10f)
                                },
                                fgPixelColor,
                                style = Stroke(width = 5f)
                            )
                        }
                    }
                }

                if (selectedExpression in setOf("unknown", "telemetry", "text")) {
                    Text(
                        text = when (selectedExpression) {
                            "telemetry" -> "COMPI  ·  Sensores"
                            "text" -> "Texto mostrado en OLED"
                            else -> "Esperando escena confirmada"
                        },
                        color = if (isInverted) Color(0xFF070B14) else Color(0xFF9EE6FF),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                Text(
                    text = "Estado: $selectedExpression · vista ilustrativa",
                    color = if (isInverted) Color.Black.copy(alpha = 0.6f) else Color(0xFF9EE6FF).copy(alpha = 0.6f),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp)
                )
            }

            // Expression Visual Selector Cards
            Text(
                text = "Expresiones soportadas por Firmware",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(expressions, key = { it.id }) { expr ->
                    Surface(
                        shape = CompiTokens.Shapes.Medium,
                        color = if (selectedExpression == expr.id) CompiTokens.Colors.PrimaryVioletContainer else CompiTokens.Colors.SurfaceElevated,
                        modifier = Modifier
                            .testTag("oled_expression_${expr.id}")
                            .clickable(enabled = canControl) {
                                onSendExpression(expr.id)
                            }
                            .border(
                                width = 2.dp,
                                color = if (selectedExpression == expr.id) CompiTokens.Colors.PrimaryViolet else Color.Transparent,
                                shape = CompiTokens.Shapes.Medium
                            )
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(text = expr.symbol, fontSize = 20.sp)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = expr.name,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // Screen Controls: Contrast, Invert, Sleep, Anim
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Contraste", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
                    Text(text = "%.0f%%".format(contrastLevel / 2.55f), style = MaterialTheme.typography.labelMedium, color = CompiTokens.Colors.PrimaryViolet)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Slider(
                        value = contrastLevel,
                        onValueChange = { contrastLevel = it },
                        valueRange = 8f..255f,
                        enabled = canControl,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(10.dp))
                    Button(
                        onClick = { onSetContrast(contrastLevel.toInt()) },
                        enabled = canControl
                    ) {
                        Text("Aplicar")
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { onSetInverted(!isInverted) },
                        enabled = canControl,
                        modifier = Modifier.weight(1f).testTag("oled_invert")
                    ) {
                        Icon(Icons.Default.InvertColors, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (isInverted) "Normal" else "Invertir")
                    }

                    OutlinedButton(
                        onClick = { onSetPaused(isAnimActive) },
                        enabled = canControl,
                        modifier = Modifier.weight(1f).testTag("oled_pause")
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (isAnimActive) "Pausar" else "Animación")
                    }
                }
            }
            feedback?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}
