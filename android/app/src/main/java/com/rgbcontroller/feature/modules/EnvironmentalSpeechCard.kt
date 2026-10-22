package com.rgbcontroller.feature.modules

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.rgbcontroller.core.audio.AndroidEnvironmentalSpeaker
import com.rgbcontroller.core.audio.EnvironmentalSpeechFormatter
import com.rgbcontroller.core.components.status.ComponentDashboardState
import com.rgbcontroller.core.components.status.ComponentStatusFreshness

/** On-device speech from live current measurements over authorized Wi-Fi or owner BLE. */
@Composable
fun EnvironmentalSpeechCard(state: ComponentDashboardState) {
    val context = LocalContext.current
    val speaker = remember(context) { AndroidEnvironmentalSpeaker(context.applicationContext) }
    DisposableEffect(speaker) { onDispose { speaker.close() } }
    val voiceReady by speaker.ready.collectAsState()
    val live = state.connected && state.authorityError == null &&
        state.telemetryFreshness == ComponentStatusFreshness.LIVE
    val activeIds = if (live) state.authoritative.orEmpty()
        .filter { it.active }.map { it.id }.toSet() else emptySet()
    val report = EnvironmentalSpeechFormatter.report(
        if (live) state.authoritativeTelemetry.orEmpty() else emptyList(),
        includeDht = "sensor.dht11" in activeIds || "sensor.dht22" in activeIds,
        includeLight = "sensor.ldr_light" in activeIds
    )
    Card(Modifier.fillMaxWidth().animateContentSize(animationSpec = tween(220)).testTag("compi_environmental_voice"), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Default.VolumeUp, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f)) {
                    Text("Voz ambiental", style = MaterialTheme.typography.titleMedium)
                    Text("Reproduce en el teléfono · TTS Android",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(
                when {
                    !live -> "Conecta por Wi-Fi autorizado o Bluetooth de propietario para escuchar lecturas actuales."
                    !report.hasFreshData -> "No hay lecturas físicas actuales válidas para anunciar."
                    !voiceReady -> "Preparando voz en español; comprueba el motor TTS de Android."
                    else -> report.text
                },
                style = MaterialTheme.typography.bodySmall
            )
            Text("Los avisos de la bocina DFPlayer son independientes y requieren microSD.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(
                onClick = { speaker.speak(report.text) },
                enabled = live && voiceReady && report.hasFreshData,
                modifier = Modifier.fillMaxWidth().testTag("compi_environmental_speak")
            ) { Text("Escuchar ambiente") }
        }
    }
}
