package com.rgbcontroller.feature.modules

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rgbcontroller.core.components.registry.ModuleWiringProfile

/**
 * Compact, device-shaped logical preview, never a photograph, a wiring
 * guarantee or a claim that an attached module has been detected. Actual
 * pin authority always belongs to the firmware.
 */
@Composable
fun ModuleWiringDiagram(
    profile: ModuleWiringProfile,
    pins: List<Int>,
    firmwareConfirmed: Boolean,
    modifier: Modifier = Modifier
) {
    if (pins.size != profile.signals.size) {
        Text(
            "Asigna ${profile.signals.size} conexiones para mostrar el esquema.",
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            modifier = modifier.testTag("module_wiring_incomplete")
        )
    } else {

    val wireColors = listOf(
        Color(0xFF54C9F0), Color(0xFF61D69A), Color(0xFFFFC96D),
        Color(0xFFA78BFA), Color(0xFFF28497)
    )
    val board = Color(0xFF142B3C)
    val outline = Color(0xFF4A6074)
    val componentId = profile.componentId
    val componentName = when (componentId) {
        "dht22" -> "DHT22"
        "soil_moisture" -> "Sensor de suelo"
        "led_rgb" -> "LED RGB"
        "ssd1306_oled" -> "Pantalla OLED"
        else -> componentId.replace('_', ' ').uppercase()
    }

    Card(modifier.fillMaxWidth().testTag("module_wiring_diagram"),
        shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Vista previa de conexiones", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("ESP32", style = MaterialTheme.typography.labelMedium)
                Text(componentName, style = MaterialTheme.typography.labelMedium)
            }
            Canvas(Modifier.fillMaxWidth().height(116.dp)) {
                val w = size.width
                val h = size.height
                val boardLeft = w * .06f
                val boardWidth = w * .31f
                val moduleLeft = w * .77f
                val moduleWidth = w * .17f
                val corner = CornerRadius(9.dp.toPx())
                drawRoundRect(board, Offset(boardLeft, 0f),
                    Size(boardWidth, h), corner)
                drawRoundRect(outline, Offset(boardLeft, 0f),
                    Size(boardWidth, h), corner, style = Stroke(1.dp.toPx()))
                // A simplified ESP32 board: pin headers, radio module, and USB connector.
                val pinRadius = 1.8.dp.toPx()
                repeat(8) { index ->
                    val y = h * (index + .75f) / 9f
                    drawCircle(Color(0xFFDCB878), pinRadius, Offset(boardLeft + w * .026f, y))
                    drawCircle(Color(0xFFDCB878), pinRadius,
                        Offset(boardLeft + boardWidth - w * .026f, y))
                }
                drawRoundRect(Color(0xFF6B7C88),
                    Offset(boardLeft + boardWidth * .24f, h * .12f),
                    Size(boardWidth * .52f, h * .46f),
                    CornerRadius(5.dp.toPx()))
                drawRoundRect(Color(0xFF263542),
                    Offset(boardLeft + boardWidth * .32f, h * .22f),
                    Size(boardWidth * .36f, h * .22f),
                    CornerRadius(3.dp.toPx()))
                drawRoundRect(Color(0xFFD0D2D5),
                    Offset(boardLeft + boardWidth * .35f, h * .88f),
                    Size(boardWidth * .30f, h * .12f),
                    CornerRadius(3.dp.toPx()))
                // A recognizable schematic shape for each supported component.
                val moduleFill = if (componentId == "dht22") Color(0xFFE9E7E1)
                    else if (componentId == "soil_moisture") Color(0xFF2E966F)
                    else Color(0xFF3F526B)
                drawRoundRect(moduleFill, Offset(moduleLeft, h * .16f),
                    Size(moduleWidth, h * .65f), corner)
                if (componentId == "dht22") {
                    repeat(3) { col ->
                        repeat(4) { row ->
                            drawCircle(Color(0xFF9CA3A6), 1.5.dp.toPx(),
                                Offset(moduleLeft + moduleWidth * (col + 1f) / 4f,
                                    h * (.26f + row * .13f)))
                        }
                    }
                } else if (componentId == "soil_moisture") {
                    repeat(2) { column ->
                        drawLine(Color(0xFFE0BA75),
                            Offset(moduleLeft + moduleWidth * (.35f + column * .3f), h * .67f),
                            Offset(moduleLeft + moduleWidth * (.35f + column * .3f), h * .96f),
                            4.dp.toPx())
                    }
                } else {
                    drawCircle(Color(0xFFA78BFA), moduleWidth * .16f,
                        Offset(moduleLeft + moduleWidth * .5f, h * .47f))
                }
                profile.signals.forEachIndexed { index, _ ->
                    val y = h * (index + 1f) / (profile.signals.size + 1f)
                    val c = wireColors[index % wireColors.size]
                    val start = Offset(boardLeft + boardWidth, y)
                    val end = Offset(moduleLeft, y)
                    drawLine(c, start, end, strokeWidth = 2.5.dp.toPx())
                    drawCircle(c, 4.dp.toPx(), start)
                    drawCircle(c, 4.dp.toPx(), end)
                }
            }
            profile.signals.forEachIndexed { index, signal ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(Modifier.size(9.dp), shape = RoundedCornerShape(50),
                        color = wireColors[index % wireColors.size]) {}
                    Text(signal.label, modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall)
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp)) {
                        Text("GPIO ${pins[index]}", modifier = Modifier.padding(
                            horizontal = 9.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            Text(
                if (firmwareConfirmed)
                    "GPIO confirmados por el ESP32 · comprueba el cableado físico."
                else "Vista de un borrador local · aún no aplicado al ESP32.",
                style = MaterialTheme.typography.bodySmall,
                color = if (firmwareConfirmed) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text("Esquema orientativo: verifica VCC, GND y voltaje según tu componente.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    } // Maintain balanced Compose groups across incomplete-to-valid wiring transitions.
}
