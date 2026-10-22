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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
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
    Card(
        modifier = Modifier.fillMaxWidth().testTag("compi_local_audio_player"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("Mi biblioteca", style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold)
                    Text("${tracks.size} canciones · Audio del teléfono",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Surface(shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer) {
                    Text(if (playerState.phase == AndroidLocalAudioPlayer.Phase.PLAYING) "Sonando"
                         else "Local",
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant) {
                Column(Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("♫", style = MaterialTheme.typography.displayLarge,
                        color = MaterialTheme.colorScheme.primary)
                    Text(
                        if (tracks.isEmpty()) "Aún no hay canciones"
                        else tracks.getOrNull(queue.currentIndex)?.let { raw ->
                            remember(raw) {
                                runCatching {
                                    context.contentResolver.query(Uri.parse(raw),
                                        arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                                        ?.use { cursor ->
                                            if (cursor.moveToFirst()) cursor.getString(0) else null
                                        }
                                }.getOrNull()
                            }
                        } ?: "Selecciona una pista",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(playerState.description, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { queue.previous(); playCurrent() },
                    enabled = tracks.isNotEmpty(), modifier = Modifier.weight(1f)) {
                    Text("◀◀")
                }
                Button(onClick = {
                    if (playerState.phase == AndroidLocalAudioPlayer.Phase.PAUSED) player.resume()
                    else if (playerState.phase == AndroidLocalAudioPlayer.Phase.PLAYING) player.pause()
                    else playCurrent()
                }, enabled = tracks.isNotEmpty(),
                    modifier = Modifier.weight(1.5f).testTag("compi_audio_play")) {
                    Text(when (playerState.phase) {
                        AndroidLocalAudioPlayer.Phase.PLAYING -> "Pausar"
                        AndroidLocalAudioPlayer.Phase.PAUSED -> "Continuar"
                        else -> "Reproducir"
                    })
                }
                OutlinedButton(onClick = { queue.next(); playCurrent() },
                    enabled = tracks.isNotEmpty(), modifier = Modifier.weight(1f)) {
                    Text("▶▶")
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Volumen", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.weight(1f))
                Text("${(volume * 100).toInt()} %",
                    style = MaterialTheme.typography.labelMedium)
            }
            Slider(value = volume, onValueChange = { volume = it; player.setVolume(it) },
                modifier = Modifier.fillMaxWidth())
            Button(onClick = { importer.launch(arrayOf("audio/*")) },
                modifier = Modifier.fillMaxWidth().testTag("compi_audio_import")) {
                Text("+ Añadir canciones")
            }
            if (playerState.phase != AndroidLocalAudioPlayer.Phase.IDLE) {
                TextButton(onClick = { player.stop() },
                    modifier = Modifier.fillMaxWidth()) { Text("Detener reproducción") }
            }
            if (tracks.isNotEmpty()) {
                HorizontalDivider()
                Text("Lista de reproducción", style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold)
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
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = {
                            queue.select(index); revision++; playCurrent()
                        }, modifier = Modifier.weight(1f)) {
                            Text(if (queue.currentIndex == index) "▶ $name" else name,
                                maxLines = 1)
                        }
                        TextButton(onClick = { queue.remove(index); save(); player.stop() }) {
                            Text("Quitar")
                        }
                    }
                }
            }
            Text(message, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
