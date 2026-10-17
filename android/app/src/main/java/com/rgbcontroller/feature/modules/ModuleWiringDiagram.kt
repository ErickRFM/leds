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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rgbcontroller.core.components.registry.ModuleWiringProfile

/**
 * Reusable, generated wiring PREVIEW (a logical diagram, not a photo of
 * verified wiring). Signal order matches the on-screen per-signal editor.
 * No electrical measurements or actual attachment are implied by this image.
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
        Color(0xFFEC637B), Color(0xFF56DDA6), Color(0xFF6B9FF8),
        Color(0xFFFFCA66), Color(0xFFBC92FF)
    )
    val boardColor = MaterialTheme.colorScheme.primaryContainer
    val moduleColor = MaterialTheme.colorScheme.surfaceVariant
    Card(modifier.fillMaxWidth().testTag("module_wiring_diagram"),
        shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Vista de conexiones", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("ESP32", style = MaterialTheme.typography.labelMedium)
                Text(profile.componentId.replace('_', ' ').uppercase(),
                    style = MaterialTheme.typography.labelMedium)
            }
            Canvas(Modifier.fillMaxWidth().height(128.dp)) {
                val w = size.width
                val h = size.height
                val corner = CornerRadius(16.dp.toPx())
                drawRoundRect(boardColor,
                    topLeft = Offset(0f, 0f), size = Size(w * .31f, h),
                    cornerRadius = corner)
                drawRoundRect(moduleColor,
                    topLeft = Offset(w * .69f, 0f), size = Size(w * .31f, h),
                    cornerRadius = corner)
                // Each wire shows a logical signal-to-pin assignment, not the
                // physical coordinates of a board's pins or a wiring guarantee.
                profile.signals.forEachIndexed { index, _ ->
                    val y = h * (index + 1f) / (profile.signals.size + 1f)
                    val color = wireColors[index % wireColors.size]
                    drawLine(color,
                        start = Offset(w * .31f, y),
                        end = Offset(w * .69f, y),
                        strokeWidth = 4.dp.toPx())
                    drawCircle(color, 5.dp.toPx(), Offset(w * .31f, y))
                    drawCircle(color, 5.dp.toPx(), Offset(w * .69f, y))
                }
            }
            profile.signals.forEachIndexed { index, signal ->
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(Modifier.size(9.dp),
                        shape = RoundedCornerShape(50),
                        color = wireColors[index % wireColors.size]) {}
                    Text(signal.label, modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall)
                    Text("GPIO ${pins[index]}", style = MaterialTheme.typography.labelMedium)
                }
            }
            Text(
                if (firmwareConfirmed) "Pines leídos del firmware · verifica físicamente el cableado."
                else "Borrador local: no se ha aplicado ni verificado en el ESP32.",
                style = MaterialTheme.typography.bodySmall,
                color = if (firmwareConfirmed) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Esquema lógico orientativo. Comprueba VCC, GND y voltajes según el módulo.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    } // Compose both valid and incomplete profiles without returning early.
}
