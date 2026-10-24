package com.rgbcontroller.feature.modules

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.rgbcontroller.core.audio.AndroidLocalAudioPlayer
import com.rgbcontroller.core.audio.AudioPlaylistQueue
import org.json.JSONArray

/** Local documents stay on the phone; they are never sent to the DFPlayer. */
@Composable
fun LocalAudioControls() {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("compi_local_audio_v1", 0) }
    val queue = remember(prefs) {
        AudioPlaylistQueue().apply {
            val saved = runCatching { JSONArray(prefs.getString("uris", "[]")) }.getOrDefault(JSONArray())
            for (i in 0 until minOf(saved.length(), 50)) enqueue(saved.optString(i))
        }
    }
    val player = remember(context) { AndroidLocalAudioPlayer(context.applicationContext) }
    DisposableEffect(player) { onDispose { player.close() } }
    val playback by player.state.collectAsState()
    var revision by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    var volume by remember { mutableFloatStateOf(.6f) }
    fun persist() {
        prefs.edit().putString("uris", JSONArray(queue.tracks()).toString()).apply()
        revision++
    }
    fun playUri(uri: String) {
        player.play(Uri.parse(uri)) {
            queue.advanceOrNull()?.let { playUri(it) }
            revision++
        }
        revision++
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        var added = 0
        uris.take(50).forEach { uri ->
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (queue.enqueue(uri.toString())) added++
            } catch (_: SecurityException) { message = "No se pudo conservar permiso de lectura del archivo." }
        }
        persist()
        if (added > 0) message = "$added archivos añadidos al teléfono."
    }
    val tracks = remember(revision) { queue.tracks() }
    Card(Modifier.fillMaxWidth().testTag("local_audio_controls")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Audio del teléfono", style = MaterialTheme.typography.titleMedium)
            Text(playback.description)
            Text("${tracks.size} archivos · selección ${if (tracks.isEmpty()) "—" else (queue.currentIndex + 1).toString()}")
            Text(queue.current()?.let { Uri.parse(it).lastPathSegment } ?: "Importa tus archivos de audio.", maxLines = 2)
            OutlinedButton(onClick = { importer.launch(arrayOf("audio/*")) }) { Text("Añadir audios") }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = { queue.previous()?.let(::playUri) }, enabled = tracks.isNotEmpty()) { Text("Anterior") }
                Button(onClick = {
                    when (playback.phase) {
                        AndroidLocalAudioPlayer.Phase.PLAYING -> player.pause()
                        AndroidLocalAudioPlayer.Phase.PAUSED -> player.resume()
                        else -> queue.current()?.let(::playUri)
                    }
                }, enabled = tracks.isNotEmpty() && playback.phase != AndroidLocalAudioPlayer.Phase.PREPARING) {
                    Text(if (playback.phase == AndroidLocalAudioPlayer.Phase.PLAYING) "Pausa" else "Escuchar")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = { queue.next()?.let(::playUri) }, enabled = tracks.isNotEmpty()) { Text("Siguiente") }
                OutlinedButton(onClick = { player.stop() }) { Text("Parar") }
                TextButton(onClick = { player.stop(); queue.remove(queue.currentIndex); persist() }, enabled = tracks.isNotEmpty()) { Text("Quitar") }
            }
            Slider(value = volume, onValueChange = { volume = it; player.setVolume(it) })
            message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            Text("Salida: teléfono o su dispositivo de audio conectado.", style = MaterialTheme.typography.bodySmall)
        }
    }
}
