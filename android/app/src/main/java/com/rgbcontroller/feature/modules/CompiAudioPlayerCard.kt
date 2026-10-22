package com.rgbcontroller.feature.modules

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.rgbcontroller.core.audio.AndroidLocalAudioPlayer
import com.rgbcontroller.core.audio.AudioPlaylistQueue
import org.json.JSONArray

/** Real local phone playback. External 8Ω speaker cannot be driven by an ESP32 GPIO. */
@Composable
fun CompiAudioPlayerCard() {
    val context = LocalContext.current
    val prefs = remember(context) {
        context.getSharedPreferences("compi_local_audio_v1", Context.MODE_PRIVATE)
    }
    val queue = remember(prefs) {
        AudioPlaylistQueue().apply {
            val raw = prefs.getString("uris", "[]").orEmpty()
            val array = runCatching { JSONArray(raw) }.getOrDefault(JSONArray())
            for (i in 0 until minOf(array.length(), 50)) {
                array.optString(i)?.let(::enqueue)
            }
        }
    }
    val player = remember(context) { AndroidLocalAudioPlayer(context.applicationContext) }
    DisposableEffect(player) { onDispose { player.close() } }
    val playerState by player.state.collectAsState()
    var revision by remember { mutableIntStateOf(0) }
    var volume by remember { mutableFloatStateOf(0.6f) }
    var message by remember { androidx.compose.runtime.mutableStateOf(
        "Elige archivos de audio del teléfono para escucharlos en Compi."
    ) }

    fun save() {
        val array = JSONArray()
        queue.tracks().forEach(array::put)
        prefs.edit().putString("uris", array.toString()).apply()
        revision++
    }
    fun playCurrent() {
        val uri = queue.current()?.let(Uri::parse) ?: return
        if (!player.play(uri) {
                queue.next()
                revision++
                playCurrent()
            }) {
            message = "No se pudo abrir la pista. Verifica permisos o formato."
        } else {
            message = "Audio enviado al reproductor Android; no al altavoz del ESP32."
        }
    }

    val importer = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        var added = 0
        for (uri in uris) {
            if (uri.scheme != "content") continue
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {
                // Can still attempt playback in this session; next launch may require import.
            }
            if (queue.enqueue(uri.toString())) added++
        }
        save()
        message = if (added > 0) "$added archivo(s) añadidos a la biblioteca local."
            else "No se añadieron nuevos archivos."
    }
    val tracks = queue.tracks() // revision is read to trigger recomposition
    @Suppress("UNUSED_VARIABLE") val listRevision = revision
    Card(Modifier.fillMaxWidth().testTag("compi_local_audio_player")) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Reproductor de audio", style = MaterialTheme.typography.titleMedium)
            Text("Salida: teléfono Android. El DFPlayer MP3-TF-16P conectado a la bocina Samsung 4 Ω / 3 W " +
                "reproduce pistas desde microSD; sin tarjeta, esa salida no puede reproducir música. " +
                "Este reproductor NO transmite audio Bluetooth al ESP32.",
                style = MaterialTheme.typography.bodySmall)
            Text(message, style = MaterialTheme.typography.bodySmall)
            Button(onClick = { importer.launch(arrayOf("audio/*")) },
                modifier = Modifier.fillMaxWidth().testTag("compi_audio_import")) {
                Text("Añadir audios del dispositivo")
            }
            Text("Estado: ${playerState.description}", style = MaterialTheme.typography.bodySmall)
            Text("Volumen: ${(volume * 100).toInt()} %")
            Slider(value = volume, onValueChange = { volume = it; player.setVolume(it) })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { queue.previous(); playCurrent() },
                    enabled = tracks.isNotEmpty(), modifier = Modifier.weight(1f)) {
                    Text("Anterior")
                }
                Button(onClick = {
                    if (playerState.phase == AndroidLocalAudioPlayer.Phase.PAUSED) {
                        player.resume()
                    } else {
                        playCurrent()
                    }
                }, enabled = tracks.isNotEmpty(),
                    modifier = Modifier.weight(1f).testTag("compi_audio_play")) {
                    Text(if (playerState.phase == AndroidLocalAudioPlayer.Phase.PAUSED)
                        "Continuar" else "Reproducir")
                }
                OutlinedButton(onClick = { queue.next(); playCurrent() },
                    enabled = tracks.isNotEmpty(), modifier = Modifier.weight(1f)) {
                    Text("Siguiente")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { player.pause() },
                    enabled = playerState.phase == AndroidLocalAudioPlayer.Phase.PLAYING,
                    modifier = Modifier.weight(1f)) { Text("Pausa") }
                OutlinedButton(onClick = { player.stop() },
                    modifier = Modifier.weight(1f)) { Text("Detener") }
            }
            if (tracks.isEmpty()) {
                Text("Biblioteca vacía", style = MaterialTheme.typography.bodySmall)
            } else {
                tracks.forEachIndexed { index, raw ->
                    val uri = Uri.parse(raw)
                    val name = remember(raw) {
                        runCatching {
                            context.contentResolver.query(
                                uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
                            )?.use { cursor ->
                                if (cursor.moveToFirst()) cursor.getString(0) else null
                            }
                        }.getOrNull() ?: "Pista ${index + 1}"
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = { queue.select(index); revision++; playCurrent() },
                            modifier = Modifier.weight(1f)
                        ) { Text(if (queue.currentIndex == index) "▶ $name" else name) }
                        TextButton(onClick = { queue.remove(index); save(); player.stop() }) {
                            Text("Quitar")
                        }
                    }
                }
            }
        }
    }
}
