package com.rgbcontroller.feature.control

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.rgbcontroller.core.ble.BleConnectionState
import com.rgbcontroller.core.ble.BleManager
import com.rgbcontroller.core.model.RgbControlLink
import com.rgbcontroller.core.model.ColorUtils
import com.rgbcontroller.core.ui.SectionHeader
import kotlinx.coroutines.delay
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.cos

@Composable
fun ControlScreen(bleManager: BleManager, link: RgbControlLink? = null) {
    val connection by bleManager.connectionState.collectAsState()
    val bleActual by bleManager.rgbState.collectAsState()
    val actual = if (link != null) link.state else bleActual
    val ready = link?.ready ?: (connection == BleConnectionState.Ready)
    fun send(command: String) {
        if (link != null) link.sendCommand(command) else bleManager.writeCommand(command)
    }
    var red by remember { mutableFloatStateOf(255f) }
    var green by remember { mutableFloatStateOf(120f) }
    var blue by remember { mutableFloatStateOf(0f) }
    var brightness by remember { mutableFloatStateOf(255f) }
    var dirty by remember { mutableStateOf(false) }
    var awaitingState by remember { mutableStateOf<ColorUtils.RgbColor?>(null) }
    var confirmationError by remember { mutableStateOf<String?>(null) }

    // Never replace an in-progress local edit with an older BLE notification.
    // Only accept the device state once the last submitted RGB command is acknowledged.
    LaunchedEffect(actual, awaitingState, dirty) {
        val state = actual ?: return@LaunchedEffect
        val expected = awaitingState
        if (expected != null &&
            (state.red != expected.r || state.green != expected.g ||
                state.blue != expected.b || state.brightness != expected.brightness)) {
            return@LaunchedEffect
        }
        // A late exact readback may clear a timeout; unrelated old states may not.
        if (!dirty && (expected != null || confirmationError == null)) {
            red = state.red.toFloat()
            green = state.green.toFloat()
            blue = state.blue.toFloat()
            brightness = state.brightness.toFloat()
            if (expected != null) {
                awaitingState = null
                confirmationError = null
            }
        }
    }

    // Sample the latest input every 60 ms, including during continuous drags.
    // A debounce-only effect would wait until the user stopped dragging.
    // BleManager serializes GATT writes and coalesces superseded SET commands.
    LaunchedEffect(ready, link?.transport) {
        while (ready) {
            delay(if (link?.transport == "WIFI_LAB") 250 else 60)
            if (dirty) {
                val sent = ColorUtils.RgbColor(
                    red.toInt(), green.toInt(), blue.toInt(), brightness.toInt()
                )
                awaitingState = sent
                confirmationError = null
                send(
                    ColorUtils.serializeSetCommand(sent.r, sent.g, sent.b, sent.brightness)
                )
                dirty = false
            }
        }
    }

    // A dropped acknowledgement must not lock the display to stale optimistic data.
    LaunchedEffect(awaitingState) {
        val pending = awaitingState
        if (pending != null) {
            delay(3000)
            if (awaitingState == pending) {
                // Do not turn a timeout into a fake successful synchronization.
                // The request remains pending until a matching readback or disconnect.
                confirmationError = "El ESP32 no confirmó este color. Comprueba la conexión."
            }
        }
    }
    LaunchedEffect(ready, link?.transport) {
        if (!ready) {
            awaitingState = null
            confirmationError = null
            dirty = false
        }
    }

    val r = red.toInt().coerceIn(0, 255)
    val g = green.toInt().coerceIn(0, 255)
    val b = blue.toInt().coerceIn(0, 255)
    // The swatch represents the selected color, never an unverified physical LED state.
    val light = brightness / 255f
    val targetPreview = Color((r / 255f) * light, (g / 255f) * light, (b / 255f) * light)
    val preview by animateColorAsState(targetPreview, label = "RGB preview")

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Color y brillo", style = MaterialTheme.typography.titleMedium)
        Text(
            when {
                !ready -> "Conecta el ESP32 para habilitar la rueda y los deslizadores."
                confirmationError != null -> confirmationError!!
                awaitingState != null -> "Aplicando ajustes, esperando confirmación del ESP32…"
                dirty -> "Preparando nuevo color…"
                actual == null -> "Esperando un estado RGB confirmado del ESP32…"
                actual?.power == false -> "LED apagado · muestra del color seleccionado"
                else -> "Ajustes sincronizados con el ESP32"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                .semantics {
                    contentDescription = "Selector circular de color. " +
                        "Para un ajuste accesible usa los deslizadores Rojo, Verde y Azul."
                }
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
            drawCircle(brush = Brush.radialGradient(listOf(Color.White, Color.Transparent)))
            // Mark the currently selected hue/saturation without changing the BLE protocol.
            val hsv = FloatArray(3)
            android.graphics.Color.RGBToHSV(r, g, b, hsv)
            val angle = hsv[0] * PI.toFloat() / 180f
            val distance = size.minDimension * 0.38f * hsv[1]
            val marker = Offset(
                center.x + cos(angle) * distance,
                center.y + sin(angle) * distance
            )
            drawCircle(color = Color(0xFF1B1920), radius = 13.dp.toPx(), center = marker)
            drawCircle(color = Color.White, radius = 10.dp.toPx(), center = marker,
                style = Stroke(width = 3.dp.toPx()))
        }
        Spacer(Modifier.height(12.dp))
        Box(modifier = Modifier.size(64.dp).background(preview, CircleShape))
        Spacer(Modifier.height(8.dp))
        Text(ColorUtils.rgbToHex(r, g, b), style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold)
        Text(
            "R: $r   G: $g   B: $b",
            style = MaterialTheme.typography.labelLarge
        )
        Spacer(Modifier.height(16.dp))
        Card(Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Encender / apagar")
            Switch(
                checked = actual?.power == true,
                enabled = ready,
                onCheckedChange = { send(if (it) "ON" else "OFF") }
            )
        }
        }
        Spacer(Modifier.height(18.dp))
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
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value.toInt().toString(), style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Slider(
        modifier = Modifier.semantics { contentDescription = "Ajustar $label" },
        value = value,
        onValueChange = onChange,
        valueRange = 0f..255f,
        enabled = enabled,
        colors = SliderDefaults.colors(thumbColor = color, activeTrackColor = color)
    )
}
