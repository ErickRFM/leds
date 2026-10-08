package com.rgbcontroller.feature.control

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import com.rgbcontroller.core.ble.BleConnectionState
import com.rgbcontroller.core.ble.BleManager
import com.rgbcontroller.core.model.ColorUtils
import kotlinx.coroutines.delay
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.PI

@Composable
fun ControlScreen(bleManager: BleManager) {
    val connection by bleManager.connectionState.collectAsState()
    val actual by bleManager.rgbState.collectAsState()
    val ready = connection == BleConnectionState.Ready
    var red by remember { mutableFloatStateOf(255f) }
    var green by remember { mutableFloatStateOf(120f) }
    var blue by remember { mutableFloatStateOf(0f) }
    var brightness by remember { mutableFloatStateOf(255f) }
    var dirty by remember { mutableStateOf(false) }

    LaunchedEffect(actual) {
        if (!dirty && actual != null) {
            val state = actual ?: return@LaunchedEffect
            red = state.red.toFloat()
            green = state.green.toFloat()
            blue = state.blue.toFloat()
            brightness = state.brightness.toFloat()
        }
    }

    // Debounce UI changes; BleManager also serializes/coalesces SET commands.
    LaunchedEffect(red, green, blue, brightness, dirty, ready) {
        if (dirty && ready) {
            delay(60)
            bleManager.writeCommand(ColorUtils.serializeSetCommand(
                red.toInt(), green.toInt(), blue.toInt(), brightness.toInt()
            ))
            dirty = false
        }
    }

    val r = red.toInt().coerceIn(0, 255)
    val g = green.toInt().coerceIn(0, 255)
    val b = blue.toInt().coerceIn(0, 255)
    val light = if (actual?.power == true || dirty) brightness / 255f else 0f
    val preview = Color((r / 255f) * light, (g / 255f) * light, (b / 255f) * light)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Mezclador RGB", style = MaterialTheme.typography.headlineMedium)
        Text(
            if (ready) "ESP32 conectado" else "Conecta el ESP32 en Dispositivos",
            color = if (ready) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        )
        Spacer(Modifier.height(12.dp))

        var diameter by remember { mutableFloatStateOf(1f) }
        fun selectWheel(point: Offset) {
            val center = diameter / 2f
            val dx = point.x - center
            val dy = point.y - center
            val hue = ((atan2(dy, dx) * 180f / PI.toFloat()) + 360f) % 360f
            val sat = (hypot(dx, dy) / center).coerceIn(0f, 1f)
            val color = ColorUtils.hsvToRgb(hue, sat, 1f)
            red = color.r.toFloat()
            green = color.g.toFloat()
            blue = color.b.toFloat()
            dirty = true
        }
        Canvas(
            modifier = Modifier
                .size(210.dp)
                .onSizeChanged { diameter = it.width.toFloat() }
                .pointerInput(ready) {
                    if (ready) detectTapGestures(onTap = { selectWheel(it) })
                }
                .pointerInput(ready) {
                    if (ready) detectDragGestures(onDrag = { change, _ ->
                        selectWheel(change.position)
                        change.consume()
                    })
                }
        ) {
            drawCircle(
                brush = Brush.sweepGradient(
                    listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan,
                           Color.Blue, Color.Magenta, Color.Red)
                )
            )
            drawCircle(
                brush = Brush.radialGradient(listOf(Color.White, Color.Transparent))
            )
        }
        Spacer(Modifier.height(14.dp))
        Box(
            modifier = Modifier.size(82.dp).background(preview, CircleShape)
        )
        Spacer(Modifier.height(8.dp))
        Text(ColorUtils.rgbToHex(r, g, b), style = MaterialTheme.typography.titleLarge)
        Text(
            "R: $r   G: $g   B: $b",
            style = MaterialTheme.typography.labelLarge
        )
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Encender / apagar")
            Switch(
                checked = actual?.power == true,
                enabled = ready,
                onCheckedChange = { bleManager.writeCommand(if (it) "ON" else "OFF") }
            )
        }
        Spacer(Modifier.height(8.dp))
        ColorSlider("Rojo", red, Color.Red, ready) { red = it; dirty = true }
        ColorSlider("Verde", green, Color.Green, ready) { green = it; dirty = true }
        ColorSlider("Azul", blue, Color.Blue, ready) { blue = it; dirty = true }
        ColorSlider("Brillo", brightness, MaterialTheme.colorScheme.primary, ready) {
            brightness = it
            dirty = true
        }
        if (actual?.effect != null && actual?.effect != "NONE") {
            Text("Efecto activo: ${actual?.effect}")
        }
    }
}

@Composable
private fun ColorSlider(
    label: String, value: Float, color: Color, enabled: Boolean,
    onChange: (Float) -> Unit
) {
    Text("$label: ${value.toInt()}")
    Slider(
        value = value,
        onValueChange = onChange,
        valueRange = 0f..255f,
        enabled = enabled,
        colors = SliderDefaults.colors(thumbColor = color, activeTrackColor = color)
    )
}
