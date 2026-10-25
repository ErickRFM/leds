package com.rgbcontroller.feature.modules

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
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
    var volume by remember(hardware?.modulePresent, hardware?.volume) { mutableFloatStateOf((hardware?.volume?.takeIf { it >= 0 } ?: 15).toFloat()) }
    val available = authorized && hardware?.modulePresent == true && hardware?.sdPresent == 1 && !busy
    val sdCount = hardware?.tracks ?: -1
    Card(Modifier.fillMaxWidth().testTag("dfplayer_physical_controls"), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Reproductor Compi", style = MaterialTheme.typography.titleLarge)
                Text(if (hardware?.modulePresent == true) "● Conectado" else "○ Sin respuesta",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (hardware?.modulePresent == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("DFPlayer Mini · microSD", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(when {
                !authorized -> "Conecta y autoriza Bluetooth o Wi-Fi."
                hardware == null -> "Esperando estado del firmware…"
                !hardware.modulePresent -> "Sin respuesta UART del DFPlayer. Revisa 5 V, GND, ESP32 TX14 → RX y RX13 ← TX."
                hardware.sdPresent == 0 -> "MicroSD ausente."
                hardware.sdPresent != 1 -> "UART respondió, pero microSD sin confirmar. Comprueba FAT32 y reinicia el módulo."
                sdCount == 0 -> "MicroSD reconocida · 0 archivos reportados. Revisa FAT32 y /MP3/0001.mp3."
                !hardware.fresh -> "Módulo y microSD detectados · lectura parcial."
                else -> "MicroSD detectada · ${sdCount.takeIf { it >= 0 }?.toString() ?: "—"} archivos reportados"
            }, modifier = Modifier.testTag("dfplayer_status"))
            Text(when {
                hardware == null || !hardware.modulePresent || hardware.playback == "UNKNOWN" -> "Reproducción sin confirmar"
                hardware.playing -> "Módulo reporta reproducción · índice SD ${hardware.track.takeIf { it >= 0 } ?: "—"}"
                hardware.playback == "PAUSED" -> "Módulo reporta pausa"
                hardware.playback == "STOPPED" -> "Módulo reporta detenido"
                else -> "Reproducción sin confirmar"
            })
            OutlinedTextField(value = trackText,
                onValueChange = { if (it.length <= 4 && it.all { c -> c in '0'..'9' }) trackText = it },
                label = { Text("Número de archivo MP3 (1–2999)") }, singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("dfplayer_track"))
            Text("Archivo: /MP3/${trackText.padStart(4, '0')}.mp3", style = MaterialTheme.typography.bodyMedium)
            Text("La app no puede detectar eléctricamente una bocina ni listar nombres de pistas: muestra únicamente respuestas del DFPlayer.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (sdCount == 0) Text("El módulo informa 0 archivos. Revisa la tarjeta antes de intentar reproducir.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            Button(onClick = { onCommand("PLAY", trackText) },
                enabled = available && sdCount != 0 && trackText.toIntOrNull() in 1..2999,
                modifier = Modifier.fillMaxWidth().testTag("dfplayer_play")) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Reproducir archivo")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = { onCommand("PAUSE", null) }, enabled = available && hardware?.playing == true,
                    modifier = Modifier.weight(1f)) { Text("Pausa") }
                OutlinedButton(onClick = { onCommand("RESUME", null) }, enabled = available && hardware?.playback == "PAUSED",
                    modifier = Modifier.weight(1f)) { Text("Seguir") }
                OutlinedButton(onClick = { onCommand("STOP", null) }, enabled = authorized && hardware?.modulePresent == true && !busy,
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
                    enabled = authorized && hardware?.fresh == true && !busy,
                    modifier = Modifier.testTag("dfplayer_voice"))
            }
            Text("La microSD se administra desde el módulo; el teléfono no transfiere pistas por UART.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            feedback?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            OutlinedButton(onClick = { onCommand("GET", null) }, enabled = authorized && !busy, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Actualizar estado del módulo")
            }
        }
    }
    LocalAudioControls()
}
