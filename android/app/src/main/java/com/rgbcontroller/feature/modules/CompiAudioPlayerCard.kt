package com.rgbcontroller.feature.modules

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
    var lastManualRequest by rememberSaveable { mutableStateOf("") }
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
    val effectivePlayback = when {
        hardware?.modulePresent != true -> "UNKNOWN"
        hardware.playback in listOf("PLAYING", "PAUSED", "STOPPED") -> hardware.playback
        else -> shownPlayback
    }
    val canPause = effectivePlayback == "PLAYING" || (lastManualRequest == "PLAY" && effectivePlayback != "PAUSED")
    val canResume = effectivePlayback == "PAUSED" || (lastManualRequest == "PAUSE" && effectivePlayback != "PLAYING")
    val moduleReady = authorized && hardware?.modulePresent == true && !busy
    val available = moduleReady && hardware?.sdPresent == 1
    val sdCount = hardware?.tracks ?: -1
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Música Compi", style = MaterialTheme.typography.headlineSmall)
        Text("Elige dónde reproducir. Los controles del módulo físico se sincronizan por UART.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Módulo físico", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
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
                sdCount == 0 -> "MicroSD detectada · conteo UART 0 (no incluye necesariamente /MP3)"
                !hardware.fresh -> "Módulo conectado · actualizando datos"
                else -> "MicroSD lista · conteo UART: ${sdCount.takeIf { it >= 0 }?.toString() ?: "—"} (no es el catálogo /MP3)"
            }, modifier = Modifier.testTag("dfplayer_status"))
            Text(when {
                !authorized || hardware == null || !hardware.modulePresent || shownPlayback == "UNKNOWN" -> "Reproducción sin confirmar"
                shownPlayback == "PLAYING" -> "Reproduciendo · pista ${hardware.track.takeIf { it >= 0 } ?: "—"}"
                shownPlayback == "PAUSED" -> "En pausa"
                shownPlayback == "STOPPED" -> "Detenido"
                else -> "Reproducción sin confirmar"
            })
            Text("Reproducir desde microSD", style = MaterialTheme.typography.titleMedium)
            Text(
                "Selecciona cualquier pista /MP3/NNNN.mp3 (1–2999). El DFPlayer no entrega los nombres de los archivos por UART y su contador no es una lista fiable de la carpeta.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text("Atajos de los 9 avisos automáticos", style = MaterialTheme.typography.titleSmall)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("1" to "Saludo", "2" to "Frío", "3" to "Agradable", "4" to "Tibio", "5" to "Calor", "6" to "Extremo", "7" to "Humedad", "8" to "Oscuro", "9" to "Luz").forEach { (number, name) ->
                    FilterChip(selected = trackText == number, onClick = { trackText = number }, label = { Text(name) })
                }
            }
            if (sdCount == 0) Text(
                "El módulo informó 0 al consultar el total, pero podría reproducir archivos /MP3. Prueba su número; un archivo ausente dará error del reproductor.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(value = trackText,
                onValueChange = { if (it.length <= 4 && it.all { c -> c in '0'..'9' }) trackText = it },
                label = { Text("Número de archivo MP3 (1–2999)") }, singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("dfplayer_track"))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { trackText = ((trackText.toIntOrNull() ?: 1) - 1).coerceIn(1, 2999).toString() },
                    enabled = (trackText.toIntOrNull() ?: 1) > 1,
                    modifier = Modifier.weight(1f).testTag("dfplayer_previous_track")
                ) { Text("Anterior") }
                OutlinedButton(
                    onClick = { trackText = ((trackText.toIntOrNull() ?: 0) + 1).coerceIn(1, 2999).toString() },
                    enabled = (trackText.toIntOrNull() ?: 0) < 2999,
                    modifier = Modifier.weight(1f).testTag("dfplayer_next_track")
                ) { Text("Siguiente") }
            }
            if (advancedHelp) Text("Archivo: /MP3/${trackText.padStart(4, '0')}.mp3", style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = { advancedHelp = !advancedHelp }) {
                Text(if (advancedHelp) "Ocultar detalles del módulo" else "Ver detalles del módulo")
            }
            if (advancedHelp) Text("La app solo muestra respuestas UART; verifica 5 V, GND, ESP32 TX14 → RX y RX13 ← TX si el módulo no responde. No se detecta eléctricamente la bocina.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (sdCount == 0 && advancedHelp) Text("La consulta de conteo no confirma si existe una pista. Verifica FAT32 y /MP3/NNNN.mp3.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = { lastManualRequest = "PLAY"; onCommand("PLAY", trackText) },
                enabled = available && trackText.toIntOrNull() in 1..2999,
                modifier = Modifier.fillMaxWidth().testTag("dfplayer_play")) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Reproducir pista")
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { lastManualRequest = "PAUSE"; onCommand("PAUSE", null) }, enabled = available && canPause,
                    modifier = Modifier.weight(1f)) { Text("Pausa") }
                OutlinedButton(onClick = { lastManualRequest = "RESUME"; onCommand("RESUME", null) }, enabled = available && canResume,
                    modifier = Modifier.weight(1f)) { Text("Seguir") }
                OutlinedButton(onClick = { lastManualRequest = "STOP"; onCommand("STOP", null) }, enabled = authorized && hardware?.modulePresent == true && !busy,
                    modifier = Modifier.weight(1f)) { Text("Parar") }
            }
            HorizontalDivider()
            Text("Volumen del módulo · ${hardware?.volume?.takeIf { it >= 0 } ?: "—"}/30", style = MaterialTheme.typography.titleSmall)
            Slider(value = volume, onValueChange = { volume = it }, valueRange = 0f..30f,
                enabled = moduleReady, modifier = Modifier.testTag("dfplayer_volume"))
            OutlinedButton(onClick = { onCommand("VOLUME", volume.toInt().toString()) }, enabled = moduleReady) {
                Text("Aplicar volumen ${volume.toInt()}")
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Avisos ambientales", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                Switch(checked = hardware?.voiceEnabled == true,
                    onCheckedChange = { onCommand("VOICE", if (it) "1" else "0") },
                    enabled = authorized && hardware?.fresh == true && !busy,
                    modifier = Modifier.testTag("dfplayer_voice"))
            }
            Text("Para escuchar avisos automáticos, activa este interruptor y deja las 9 pistas en la microSD.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
