package com.rgbcontroller.feature.modules

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
    Card(Modifier.fillMaxWidth().testTag("compi_environmental_voice")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Compi te informa", style = MaterialTheme.typography.titleMedium)
            Text(
                when {
                    !live -> "Conecta por Wi-Fi autorizado o Bluetooth de propietario para escuchar lecturas actuales."
                    !report.hasFreshData -> "No hay lecturas físicas actuales válidas para anunciar."
                    !voiceReady -> "Preparando voz en español; comprueba el motor TTS de Android."
                    else -> report.text
                },
                style = MaterialTheme.typography.bodySmall
            )
            Button(
                onClick = { speaker.speak(report.text) },
                enabled = live && voiceReady && report.hasFreshData,
                modifier = Modifier.fillMaxWidth().testTag("compi_environmental_speak")
            ) { Text("Anunciar mediciones") }
        }
    }
}
