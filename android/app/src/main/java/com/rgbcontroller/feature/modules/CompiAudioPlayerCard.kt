package com.rgbcontroller.feature.modules

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.rgbcontroller.core.ble.AudioHardwareState

/** Uses real queried UART state; MP3 filenames cannot be enumerated via UART. */
@Composable
fun CompiAudioPlayerCard(
    authorized: Boolean,
    hardware: AudioHardwareState?,
    busy: Boolean,
    feedback: String?,
    onCommand: (String, String?) -> Unit
) {
    var trackText by remember { mutableStateOf("1") }
    var volume by remember(hardware?.volume) { mutableFloatStateOf((hardware?.volume ?: 1).coerceAtLeast(0).toFloat()) }
    val available = authorized && hardware?.canPlay == true && !busy
    Card(Modifier.fillMaxWidth().testTag("dfplayer_physical_controls")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("DFPlayer · bocina externa", style = MaterialTheme.typography.titleMedium)
            Text(when {
                !authorized -> "Conecta y autoriza Bluetooth o Wi-Fi."
                hardware == null -> "Esperando estado del firmware…"
                !hardware.modulePresent -> "Módulo sin detectar. Revisa alimentación y UART."
                hardware.sdPresent == 0 -> "MicroSD ausente."
                hardware.sdPresent != 1 -> "MicroSD sin confirmar."
                !hardware.fresh -> "Módulo detectado · esperando estado completo."
                else -> "MicroSD detectada · ${hardware.tracks.takeIf { it >= 0 }?.toString() ?: "—"} archivos reportados"
            }, modifier = Modifier.testTag("dfplayer_status"))
            Text(when {
                hardware == null || !hardware.fresh -> "Reproducción sin confirmar"
                hardware.playing -> "Módulo reporta reproducción · índice SD ${hardware.track.takeIf { it >= 0 } ?: "—"}"
                hardware.playback == "PAUSED" -> "Módulo reporta pausa"
                hardware.playback == "STOPPED" -> "Módulo reporta detenido"
                else -> "Reproducción sin confirmar"
            })
            OutlinedTextField(value = trackText,
                onValueChange = { if (it.length <= 4 && it.all { c -> c in '0'..'9' }) trackText = it },
                label = { Text("Número de archivo MP3 (1–2999)") }, singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("dfplayer_track"))
            Text("Requiere /MP3/${trackText.padStart(4, '0')}.mp3. El total de archivos y el índice SD no identifican nombres MP3.",
                style = MaterialTheme.typography.bodySmall)
            Button(onClick = { onCommand("PLAY", trackText) },
                enabled = available && trackText.toIntOrNull() in 1..2999,
                modifier = Modifier.fillMaxWidth().testTag("dfplayer_play")) { Text("Reproducir archivo") }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = { onCommand("PAUSE", null) }, enabled = available && hardware?.playing == true,
                    modifier = Modifier.weight(1f)) { Text("Pausa") }
                OutlinedButton(onClick = { onCommand("RESUME", null) }, enabled = available && hardware?.playback == "PAUSED",
                    modifier = Modifier.weight(1f)) { Text("Seguir") }
                OutlinedButton(onClick = { onCommand("STOP", null) }, enabled = authorized && hardware?.fresh == true && !busy,
                    modifier = Modifier.weight(1f)) { Text("Parar") }
            }
            Text("Volumen DFPlayer: ${hardware?.volume?.takeIf { it >= 0 } ?: "—"}/30")
            Slider(value = volume, onValueChange = { volume = it }, valueRange = 0f..30f,
                enabled = available, modifier = Modifier.testTag("dfplayer_volume"))
            OutlinedButton(onClick = { onCommand("VOLUME", volume.toInt().toString()) }, enabled = available) {
                Text("Aplicar volumen ${volume.toInt()}")
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Avisos ambientales")
                Switch(checked = hardware?.voiceEnabled == true,
                    onCheckedChange = { onCommand("VOICE", if (it) "1" else "0") },
                    enabled = authorized && hardware != null && !busy,
                    modifier = Modifier.testTag("dfplayer_voice"))
            }
            Text("La opción se guarda en el ESP32. UART no mide sonido en la bocina.", style = MaterialTheme.typography.bodySmall)
            feedback?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            OutlinedButton(onClick = { onCommand("GET", null) }, enabled = authorized && !busy) { Text("Consultar módulo") }
        }
    }
    LocalAudioControls()
}
