package com.rgbcontroller.feature.effects

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rgbcontroller.core.ble.BleConnectionState
import com.rgbcontroller.core.ble.BleSecurityState
import com.rgbcontroller.core.ble.BleManager
import com.rgbcontroller.core.model.RgbDeviceCommand
import com.rgbcontroller.core.model.RgbControlLink
import com.rgbcontroller.core.ui.SectionHeader

private data class EffectItem(val id: String, val name: String, val description: String)

@Composable
fun EffectsScreen(bleManager: BleManager, link: RgbControlLink? = null, embedded: Boolean = false) {
    val connection by bleManager.connectionState.collectAsState()
    val security by bleManager.securityState.collectAsState()
    val bleState by bleManager.rgbState.collectAsState()
    val state = if (link != null) link.state else bleState
    val ready = link?.ready ?: (connection == BleConnectionState.Ready && security == BleSecurityState.Authorized)
    fun send(command: String) {
        if (link != null) link.sendCommand(command) else bleManager.writeCommand(command)
    }
    var speed by remember { mutableFloatStateOf(120f) }
    var speedEditing by remember { mutableStateOf(false) }
    LaunchedEffect(state?.speed, ready) {
        if (!speedEditing && ready) state?.let { speed = it.speed.toFloat() }
    }
    val effects = listOf(
        EffectItem("RAINBOW", "Arcoíris", "Transición continua entre colores"),
        EffectItem("BREATHE", "Respiración", "Luz que aumenta y disminuye suavemente"),
        EffectItem("FADE", "Fundido RGB", "Variación de color y luminosidad")
    )

    Column((if (embedded) Modifier.fillMaxWidth() else Modifier.fillMaxSize().verticalScroll(rememberScrollState()))
        .padding(horizontal = 18.dp, vertical = 20.dp)) {
        SectionHeader("Efectos", "Ambientes de luz controlados desde el ESP32")
        Spacer(Modifier.height(20.dp))
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(18.dp)) {
                Text("Reproducción", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(5.dp))
                Text(if (ready) "Activo: " + (state?.effect ?: "Sincronizando…")
                     else "Conecta el ESP32 para iniciar un efecto",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Text("Intervalo · ${speed.toInt()} ms", style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = speed, onValueChange = { speed = it; speedEditing = true },
                    valueRange = 10f..500f, enabled = ready
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        effects.forEach { effect ->
            val active = ready && state?.effect == effect.id
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor =
                    if (active) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surface)) {
                Row(Modifier.fillMaxWidth().padding(16.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(effect.name, style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        Text(effect.description, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.width(12.dp))
                    Button(
                        onClick = {
                            send(RgbDeviceCommand.effect(effect.id, speed.toInt()))
                            speedEditing = false
                        },
                        enabled = ready,
                        shape = RoundedCornerShape(14.dp)
                    ) { Text(if (active) "Activo" else "Usar") }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        OutlinedButton(
            onClick = { send("FX,STOP") },
            enabled = ready && state?.effect != null && state?.effect != "NONE",
            modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
            shape = RoundedCornerShape(16.dp)
        ) { Text("Detener efecto") }
        Spacer(Modifier.height(12.dp))
        Text("Los efectos se ejecutan en el ESP32 y continúan aunque cierres la app.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
