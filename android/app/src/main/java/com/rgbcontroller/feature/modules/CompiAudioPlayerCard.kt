package com.rgbcontroller.feature.modules

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
    var output by rememberSaveable { mutableStateOf("PHONE") }
    var trackText by rememberSaveable { mutableStateOf("1") }
    var advancedHelp by rememberSaveable { mutableStateOf(false) }
    // Preserve slider edits across automatic UART polling. Only initialize the
    // local edit when module identity changes, never on each volume readback.
    var volume by remember(hardware?.modulePresent) {
        mutableFloatStateOf((hardware?.volume?.takeIf { it >= 0 } ?: 15).toFloat())
    }
    // Preserve a confirmed state while UART polling temporarily invalidates
    // its readback. Never treat acceptance of a command as playback proof.
    var lastConfirmedPlayback by rememberSaveable { mutableStateOf("UNKNOWN") }
    LaunchedEffect(hardware?.playback, hardware?.modulePresent) {
        if (hardware?.modulePresent != true) lastConfirmedPlayback = "UNKNOWN"
        else if (hardware.playback in listOf("PLAYING", "PAUSED", "STOPPED")) {
            lastConfirmedPlayback = hardware.playback
        }
    }
    val shownPlayback = if (hardware?.playback in listOf("PLAYING", "PAUSED", "STOPPED"))
        hardware!!.playback else lastConfirmedPlayback
    val moduleReady = authorized && hardware?.modulePresent == true && !busy
    val available = moduleReady && hardware?.sdPresent == 1
    val sdCount = hardware?.tracks ?: -1
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Música Compi", style = MaterialTheme.typography.titleLarge)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = output == "PHONE",
                onClick = { output = "PHONE" },
                label = { Text("Teléfono") },
                modifier = Modifier.weight(1f).testTag("audio_output_phone")
            )
            FilterChip(
                selected = output == "DFPLAYER",
                onClick = { output = "DFPLAYER" },
                label = { Text("DFPlayer") },
                modifier = Modifier.weight(1f).testTag("audio_output_dfplayer")
            )
        }
        Text(
            when {
                !authorized -> "DFPlayer · conexión no autorizada"
                hardware == null -> "DFPlayer · consultando el ESP32"
                !hardware.modulePresent -> "DFPlayer · sin respuesta UART"
                hardware.sdPresent == 0 -> "DFPlayer conectado · sin microSD"
                hardware.sdPresent == 1 -> "DFPlayer conectado · microSD detectada"
                else -> "DFPlayer conectado · microSD sin confirmar"
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("dfplayer_overview_status")
        )
        if (output == "PHONE") {
            LocalAudioControls()
        } else {
    Card(Modifier.fillMaxWidth().testTag("dfplayer_physical_controls"), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Módulo físico", style = MaterialTheme.typography.titleMedium)
                Text(if (!authorized) "○ Sin autorización" else if (hardware?.modulePresent == true) "● UART confirmado" else "○ Sin respuesta",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (authorized && hardware?.modulePresent == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Text(when {
                !authorized -> "Conecta y autoriza Bluetooth o Wi-Fi."
                hardware == null -> "Esperando estado del firmware…"
                !hardware.modulePresent -> "DFPlayer sin respuesta"
                hardware.sdPresent == 0 -> "MicroSD ausente."
                hardware.sdPresent != 1 -> "DFPlayer conectado · tarjeta sin confirmar"
                sdCount == 0 -> "MicroSD vacía · 0 canciones"
                !hardware.fresh -> "Módulo conectado · actualizando datos"
                else -> "MicroSD lista · ${sdCount.takeIf { it >= 0 }?.toString() ?: "—"} pistas"
            }, modifier = Modifier.testTag("dfplayer_status"))
            Text(when {
                !authorized || hardware == null || !hardware.modulePresent || shownPlayback == "UNKNOWN" -> "Reproducción sin confirmar"
                shownPlayback == "PLAYING" -> "Reproduciendo · pista ${hardware.track.takeIf { it >= 0 } ?: "—"}"
                shownPlayback == "PAUSED" -> "En pausa"
                shownPlayback == "STOPPED" -> "Detenido"
                else -> "Reproducción sin confirmar"
            })
            OutlinedTextField(value = trackText,
                onValueChange = { if (it.length <= 4 && it.all { c -> c in '0'..'9' }) trackText = it },
                label = { Text("Número de archivo MP3 (1–2999)") }, singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("dfplayer_track"))
            if (advancedHelp) Text("Archivo: /MP3/${trackText.padStart(4, '0')}.mp3", style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = { advancedHelp = !advancedHelp }) {
                Text(if (advancedHelp) "Ocultar detalles del módulo" else "Ver detalles del módulo")
            }
            if (advancedHelp) Text("La app solo muestra respuestas UART; verifica 5 V, GND, ESP32 TX14 → RX y RX13 ← TX si el módulo no responde. No se detecta eléctricamente la bocina.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (sdCount == 0 && advancedHelp) Text("Comprueba FAT32 y la ruta /MP3/0001.mp3.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            Button(onClick = { onCommand("PLAY", trackText) },
                enabled = available && sdCount != 0 && trackText.toIntOrNull() in 1..2999,
                modifier = Modifier.fillMaxWidth().testTag("dfplayer_play")) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Reproducir archivo")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = { onCommand("PAUSE", null) }, enabled = available && shownPlayback == "PLAYING",
                    modifier = Modifier.weight(1f)) { Text("Pausa") }
                OutlinedButton(onClick = { onCommand("RESUME", null) }, enabled = available && shownPlayback == "PAUSED",
                    modifier = Modifier.weight(1f)) { Text("Seguir") }
                OutlinedButton(onClick = { onCommand("STOP", null) }, enabled = authorized && hardware?.modulePresent == true && !busy,
                    modifier = Modifier.weight(1f)) { Text("Parar") }
            }
            Text("Volumen · ${hardware?.volume?.takeIf { it >= 0 } ?: "—"}/30")
            Slider(value = volume, onValueChange = { volume = it }, valueRange = 0f..30f,
                enabled = moduleReady, modifier = Modifier.testTag("dfplayer_volume"))
            OutlinedButton(onClick = { onCommand("VOLUME", volume.toInt().toString()) }, enabled = moduleReady) {
                Text("Aplicar volumen ${volume.toInt()}")
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Avisos ambientales")
                Switch(checked = hardware?.voiceEnabled == true,
                    onCheckedChange = { onCommand("VOICE", if (it) "1" else "0") },
                    enabled = authorized && hardware?.fresh == true && !busy,
                    modifier = Modifier.testTag("dfplayer_voice"))
            }
            if (advancedHelp) Text("Estado disponible con ESP32 autorizado; aceptar una orden no certifica sonido.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (advancedHelp) Text("La microSD se administra desde el módulo; el teléfono no transfiere pistas por UART.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            feedback?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            OutlinedButton(onClick = { onCommand("GET", null) }, enabled = authorized && !busy, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Actualizar estado del módulo")
            }
        }
    }
        }
    }
}
