package com.rgbcontroller.feature.modules

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rgbcontroller.core.audio.AndroidLocalAudioPlayer
import com.rgbcontroller.core.audio.AudioPlaylistQueue
import com.rgbcontroller.core.ui.CompiBadge
import com.rgbcontroller.core.ui.CompiBadgeType
import com.rgbcontroller.core.ui.CompiTokens
import org.json.JSONArray

data class QuickTrackGroup(
    val category: String,
    val range: String,
    val color: Color,
    val tracks: List<Pair<Int, String>>
)

/**
 * Integrated Audio Player Component.
 * Supports DFPlayer Mini UART MP3 hardware, 24 firmware quick tracks map,
 * Android local media playback, and environmental voice toggles.
 */
@Composable
fun CompiAudioPlayerCard(
    canControlDfplayer: Boolean = false,
    onPlayDfplayerTrack: (Int) -> Unit = {},
    onSetEnvironmentalVoice: (Boolean) -> Unit = {},
    hardwareFeedback: String? = null,
    confirmedVoiceEnabled: Boolean? = null
) {
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
        var dfplayerMessage by remember { mutableStateOf("DFPlayer: estado físico sin confirmar. Requiere respuesta real del ESP32 y del módulo.") }

    // 24 Tracks Map from firmware specification
    val quickTrackGroups = listOf(
        QuickTrackGroup("Frío", "0001–0004", Color(0xFF46C6EF), listOf(
            1 to "0001: Hola Compi", 2 to "0002: Hace frío",
            3 to "0003: Temp baja", 4 to "0004: Abrígate"
        )),
        QuickTrackGroup("Agradable", "0005–0008", Color(0xFF27D987), listOf(
            5 to "0005: Agradable", 6 to "0006: Temp ideal",
            7 to "0007: Clima perfecto", 8 to "0008: En orden"
        )),
        QuickTrackGroup("Calor", "0009–0012", Color(0xFFFFB84D), listOf(
            9 to "0009: Hace calor", 10 to "0010: Temp alta",
            11 to "0011: Caluroso", 12 to "0012: Hidrátate"
        )),
        QuickTrackGroup("Humedad", "0013–0016", Color(0xFF42E3D0), listOf(
            13 to "0013: Humedad alta", 14 to "0014: Humedad baja",
            15 to "0015: Clima húmedo", 16 to "0016: Clima seco"
        )),
        QuickTrackGroup("Expresiones", "0017–0020", Color(0xFFA88BFF), listOf(
            17 to "0017: Carita feliz", 18 to "0018: Sorpresa",
            19 to "0019: Modo sueño", 20 to "0020: Efecto especial"
        )),
        QuickTrackGroup("Sistema", "0021–0024", Color(0xFF94A3B8), listOf(
            21 to "0021: Sistema listo", 22 to "0022: Conectado",
            23 to "0023: Desconectado", 24 to "0024: Alerta"
        ))
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // DFPlayer Mini Physical Hardware Status Card
        Card(
            shape = CompiTokens.Shapes.Large,
            colors = CardDefaults.cardColors(containerColor = CompiTokens.Colors.SurfaceCard),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(CompiTokens.Colors.PrimaryVioletContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = CompiTokens.Colors.PrimaryViolet,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "DFPlayer Mini MP3",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "UART2 · GPIO 17 (TX), GPIO 18 (RX)",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }

                    Text("24 pistas previstas", style = MaterialTheme.typography.labelSmall, color = Color.White)
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = CompiTokens.Colors.PrimaryViolet, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Módulo sin verificar", style = MaterialTheme.typography.labelSmall, color = Color.White)
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.SdCard, contentDescription = null, tint = CompiTokens.Colors.PrimaryViolet, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("MicroSD sin verificar", style = MaterialTheme.typography.labelSmall, color = Color.White)
                    }
                }
            }
        }

        // Environmental Voice Toggle Card
        Card(
            shape = CompiTokens.Shapes.Medium,
            colors = CardDefaults.cardColors(containerColor = CompiTokens.Colors.SurfaceElevated),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(CompiTokens.Colors.ActiveGreenContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Mic, contentDescription = null, tint = CompiTokens.Colors.ActiveGreen, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Voz ambiental Compi",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        Text(
                            text = if (canControlDfplayer) "Control por ESP32 · estado físico por confirmar" else "Conecta y autoriza Wi-Fi para controlar anuncios",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }

                Switch(
                    checked = confirmedVoiceEnabled == true,
                    onCheckedChange = { requested ->
                        if (canControlDfplayer) {
                            onSetEnvironmentalVoice(requested)
                            dfplayerMessage = "Solicitud de voz ambiental enviada; respuesta del ESP32 pendiente."
                        }
                    },
                    enabled = canControlDfplayer
                )
            }
        }

        // Quick Tracks Palette (24 Tracks)
        Text(
            text = "Pistas rápidas DFPlayer (0001–0024)",
            style = CompiTokens.Typography.SectionHeader,
            color = Color.White
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            quickTrackGroups.forEach { group ->
                Surface(
                    shape = CompiTokens.Shapes.Medium,
                    color = CompiTokens.Colors.SurfaceCard,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = group.category,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = group.color
                            )
                            Text(
                                text = group.range,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF94A3B8)
                            )
                        }
                        Spacer(Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            group.tracks.forEach { (trackNum, label) ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = group.color.copy(alpha = 0.15f),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable(enabled = canControlDfplayer) {
                                            onPlayDfplayerTrack(trackNum)
                                            dfplayerMessage = "Solicitada pista $trackNum ($label). Reproducción física sin confirmar."
                                        }
                                ) {
                                    Text(
                                        text = label.substringAfter(": "),
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        color = Color.White,
                                        maxLines = 1,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Text(
            text = hardwareFeedback ?: dfplayerMessage,
            style = MaterialTheme.typography.bodySmall,
            color = CompiTokens.Colors.PrimaryViolet
        )

        // Decorative Waveform Indicator
        Surface(
            shape = CompiTokens.Shapes.Medium,
            color = CompiTokens.Colors.SurfaceElevated,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.GraphicEq,
                    contentDescription = null,
                    tint = CompiTokens.Colors.PrimaryViolet,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Animación decorativa de reproducción",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF94A3B8)
                )
            }
        }
    }
}
