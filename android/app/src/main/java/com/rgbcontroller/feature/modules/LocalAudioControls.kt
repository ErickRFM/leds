package com.rgbcontroller.feature.modules

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
    var showLibrary by remember { mutableStateOf(false) }
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
        if (added > 0) persist()
        message = if (added > 0) "$added canciones añadidas" else if (uris.isNotEmpty()) "Las canciones ya estaban añadidas o no se pudieron leer." else null
    }
    val tracks = remember(revision) { queue.tracks() }
    Card(Modifier.fillMaxWidth().testTag("local_audio_controls")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Mi biblioteca", style = MaterialTheme.typography.titleMedium)
            Text(queue.current()?.let { Uri.parse(it).lastPathSegment } ?: "Aún no hay canciones", style = MaterialTheme.typography.titleMedium, maxLines = 2)
            Text(if (tracks.isEmpty()) "Añade canciones desde tu teléfono" else "${tracks.size} canciones · ${playback.description}", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { importer.launch(arrayOf("audio/*")) }) { Text("Añadir canciones") }
            if (tracks.isNotEmpty()) {
                TextButton(onClick = { showLibrary = !showLibrary }, modifier = Modifier.testTag("phone_audio_library_toggle")) {
                    Text(if (showLibrary) "Ocultar biblioteca" else "Ver biblioteca (${tracks.size})")
                }
                if (showLibrary) {
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 240.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    itemsIndexed(tracks) { index, uri ->
                        val fileName = Uri.parse(uri).lastPathSegment ?: "Canción ${index + 1}"
                        OutlinedButton(
                            onClick = {
                                if (queue.currentIndex != index) {
                                    player.stop()
                                    queue.select(index)
                                    revision++
                                }
                            },
                            modifier = Modifier.fillMaxWidth().testTag("phone_audio_track_${index}")
                        ) {
                            Text("${index + 1}. $fileName", maxLines = 1)
                        }
                    }
                    }
                }
            }
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
                OutlinedButton(onClick = { player.stop() }, enabled = playback.phase == AndroidLocalAudioPlayer.Phase.PLAYING || playback.phase == AndroidLocalAudioPlayer.Phase.PAUSED) { Text("Parar") }
                TextButton(onClick = { player.stop(); queue.remove(queue.currentIndex); persist() }, enabled = tracks.isNotEmpty()) { Text("Quitar") }
            }
            Text("Volumen", style = MaterialTheme.typography.labelMedium)
            Slider(value = volume, onValueChange = { volume = it; player.setVolume(it) }, modifier = Modifier.testTag("phone_audio_volume"))
            message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}
