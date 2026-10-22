package com.rgbcontroller.feature.home

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rgbcontroller.core.ble.BleConnectionState
import com.rgbcontroller.core.ble.BleManager
import com.rgbcontroller.core.components.model.ComponentMeasurement
import com.rgbcontroller.core.components.model.ComponentTelemetryCodec
import com.rgbcontroller.core.components.status.ComponentDashboardState
import com.rgbcontroller.core.components.status.ComponentStatusFreshness
import com.rgbcontroller.core.components.telemetry.TelemetrySample
import com.rgbcontroller.core.ui.CompiBadge
import com.rgbcontroller.core.ui.CompiBadgeType
import com.rgbcontroller.core.ui.CompiHaptics
import com.rgbcontroller.core.ui.CompiMascot
import com.rgbcontroller.core.ui.CompiMascotState
import com.rgbcontroller.core.ui.CompiTokens
import com.rgbcontroller.core.wifi.CompiLabSession
import com.rgbcontroller.core.wifi.LabConnectionState
import com.rgbcontroller.core.wifi.LabTransport
import com.rgbcontroller.core.wifi.LocalLabClient
import com.rgbcontroller.feature.hardwarelab.ComponentDashboardViewModel
import kotlinx.coroutines.CancellationException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Compi Aurora Home Dashboard Screen.
 * Executive summary with circular climate gauge, reactive mascot,
 * real interactive telemetry chart, 2-column metrics, and quick shortcuts.
 */
@Composable
fun DashboardScreen(
    bleManager: BleManager,
    onLed: () -> Unit,
    onCode: () -> Unit,
    onDevices: () -> Unit,
    onHardware: () -> Unit,
    onSettings: () -> Unit = {},
    session: CompiLabSession? = null
) {
    val connection by bleManager.connectionState.collectAsState()
    val context = LocalContext.current
    val moduleVm: ComponentDashboardViewModel = viewModel(
        factory = remember(session, bleManager) {
            ComponentDashboardViewModel.factory(context.applicationContext, session, bleManager)
        }
    )
    val moduleState by moduleVm.uiState.collectAsState()
    LaunchedEffect(Unit) { moduleVm.loadPersisted() }
    LaunchedEffect(session?.connectionState, session?.transport) {
        moduleVm.onConnectionChanged()
    }
    val bleReady = connection == BleConnectionState.Ready
    val wifiReady = session?.let {
        it.transport == LabTransport.WIFI_LAB &&
            it.connectionState == LabConnectionState.AUTHORIZED && it.wifiReady
    } == true
    val ready = bleReady || wifiReady

    var wifiStats by remember { mutableStateOf<HomeDeviceStats?>(null) }
    LaunchedEffect(wifiReady, session?.revision) {
        wifiStats = null
        if (wifiReady && session != null) {
            val revision = session.revision
            try {
                val client = LocalLabClient(context.applicationContext)
                val result = session.serialized {
                    check(session.revision == revision && session.wifiReady)
                    val started = SystemClock.elapsedRealtime()
                    val diagnostics = client.diagnostics(session.key)
                    val duration = SystemClock.elapsedRealtime() - started
                    val firmware = client.firmwareInfo(session.key)
                    HomeDeviceStats(
                        wifiDbm = diagnostics.optInt("wifiRssi", 0).takeIf { it != 0 },
                        latencyMs = duration.coerceAtLeast(0),
                        firmware = firmware.optString("firmwareVersion").takeIf { it.isNotBlank() },
                        freeHeap = diagnostics.optLong("freeHeap", -1).takeIf { it >= 0 }
                    )
                }
                if (session.revision == revision && session.wifiReady) wifiStats = result
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { wifiStats = null }
        }
    }

    val telemetryLive = moduleState.telemetryFreshness == ComponentStatusFreshness.LIVE && ready
    val readings = if (telemetryLive) moduleState.authoritativeTelemetry.orEmpty() else emptyList()
    val tempMeasurement = ComponentTelemetryCodec.find(readings, "temperature")
        ?.takeIf { it.valid && it.value.isFinite() }
    val humidMeasurement = ComponentTelemetryCodec.find(readings, "humidity")
        ?.takeIf { it.valid && it.value.isFinite() }
    val lightMeasurement = ComponentTelemetryCodec.find(readings, "ambient_light")
        ?.takeIf { it.valid && it.value.isFinite() }
    val rawLightMeasurement = ComponentTelemetryCodec.find(readings, "ambient_light_raw")
        ?.takeIf { it.valid && it.value.isFinite() }

    val currentTemp = tempMeasurement?.value ?: 24.5f
    val currentHumid = humidMeasurement?.value ?: 55f
    val hasRealTemp = tempMeasurement != null

    val climateState = when {
        !ready -> CompiMascotState.OFFLINE
        currentTemp < 18f -> CompiMascotState.COLD
        currentTemp > 30f -> CompiMascotState.HOT
        else -> CompiMascotState.COMFORTABLE
    }

    val climateLabel = when {
        !ready -> "ESP32 sin conexión"
        currentTemp < 18f -> "Hace frío ❄️"
        currentTemp > 30f -> "Hace calor ☀️"
        else -> "Ambiente agradable 😌"
    }

    val mascotSpeech = when {
        !ready -> "Conecta tu ESP32 para monitorear"
        currentTemp < 18f -> "¡Siento un poco de frío!"
        currentTemp > 30f -> "¡Uff, que calor hace!"
        else -> "¡Qué rico ambiente tenemos hoy!"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CompiTokens.Colors.MatteBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top App Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "COMPI",
                    style = CompiTokens.Typography.HeadlineTitle,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = if (wifiReady) "Estación Inteligente · Wi-Fi" else "Estación Inteligente · ESP32",
                    style = MaterialTheme.typography.labelSmall,
                    color = CompiTokens.Colors.PrimaryViolet
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CompiBadge(
                    label = if (ready) "Conectado" else "Desconectado",
                    type = if (ready) CompiBadgeType.LIVE else CompiBadgeType.OFFLINE
                )
                IconButton(
                    onClick = onSettings,
                    modifier = Modifier.testTag("home_settings")
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Ajustes",
                        tint = Color(0xFF94A3B8)
                    )
                }
            }
        }

        // Hero Ambient Climate Card
        Card(
            shape = CompiTokens.Shapes.Large,
            colors = CardDefaults.cardColors(containerColor = CompiTokens.Colors.SurfaceCard),
            modifier = Modifier.fillMaxWidth().testTag("home_climate_hero")
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
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
                                imageVector = Icons.Default.Thermostat,
                                contentDescription = null,
                                tint = CompiTokens.Colors.PrimaryViolet,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Ambiente",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = if (hasRealTemp) "Confirmado por ESP32" else "Esperando lectura real",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }

                    CompiBadge(
                        label = if (telemetryLive) "En vivo" else "Guardado",
                        type = if (telemetryLive) CompiBadgeType.LIVE else CompiBadgeType.STALE
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Circular Temperature Gauge
                    Box(
                        modifier = Modifier.size(140.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularClimateGauge(
                            temperature = currentTemp,
                            minTemp = 10f,
                            maxTemp = 40f
                        )
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = if (hasRealTemp) "%.1f°C".format(Locale.ROOT, currentTemp) else "— °C",
                                style = CompiTokens.Typography.MetricValue,
                                color = Color.White
                            )
                            Text(
                                text = climateLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = CompiTokens.Colors.ActiveGreen
                            )
                            Spacer(Modifier.height(2.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.WaterDrop,
                                    contentDescription = null,
                                    tint = CompiTokens.Colors.ColdBlue,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = if (humidMeasurement != null) "Humedad %.0f%%".format(Locale.ROOT, currentHumid) else "Humedad —",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                    color = Color(0xFFCBD5E1)
                                )
                            }
                        }
                    }

                    // Reactive Compi Mascot
                    CompiMascot(
                        state = climateState,
                        size = 110.dp,
                        speechText = mascotSpeech
                    )
                }
            }
        }

        // Interactive Telemetry Chart
        TelemetryChartSection(moduleState = moduleState, live = ready)

        // 2-Column Compact Metric Grid
        Text(
            text = "Mediciones y módulos",
            style = CompiTokens.Typography.SectionHeader,
            color = Color.White
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Humidity Card
            CompactMetricCard(
                title = "Humedad",
                value = humidMeasurement?.value?.let { "%.0f%%".format(Locale.ROOT, it) } ?: "—",
                subtitle = if (humidMeasurement != null) "Ambiente normal" else "Sin lectura",
                icon = Icons.Default.WaterDrop,
                iconTint = CompiTokens.Colors.ColdBlue,
                modifier = Modifier.weight(1f),
                onClick = onLed
            )

            // LDR Light Card
            CompactMetricCard(
                title = "Luz LDR",
                value = when {
                    lightMeasurement != null -> "%.0f%%".format(Locale.ROOT, lightMeasurement.value)
                    rawLightMeasurement != null -> "ADC %.0f".format(Locale.ROOT, rawLightMeasurement.value)
                    else -> "—"
                },
                subtitle = if (lightMeasurement != null) "Luz relativa" else if (rawLightMeasurement != null) "Lectura ADC cruda" else "Sin lectura",
                icon = Icons.Default.Lightbulb,
                iconTint = CompiTokens.Colors.WarningAmber,
                modifier = Modifier.weight(1f),
                onClick = onLed
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // RGB Ambient Card
            CompactMetricCard(
                title = "RGB Ambiental",
                value = if (ready) "Automático" else "Apagado",
                subtitle = "Sincronizado ESP32",
                icon = Icons.Default.ColorLens,
                iconTint = CompiTokens.Colors.PrimaryViolet,
                modifier = Modifier.weight(1f),
                onClick = onLed
            )

            // Audio Player Card
            CompactMetricCard(
                title = "Audio Compi",
                value = "Local / MP3",
                subtitle = "Avisos por voz",
                icon = Icons.Default.MusicNote,
                iconTint = CompiTokens.Colors.ActiveGreen,
                modifier = Modifier.weight(1f),
                onClick = onLed
            )
        }

        // Quick Shortcuts
        Text(
            text = "Accesos rápidos",
            style = CompiTokens.Typography.SectionHeader,
            color = Color.White
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            QuickActionTile("Módulos", Icons.Default.ColorLens, onLed, Modifier.weight(1f))
            QuickActionTile("Hardware", Icons.Default.Memory, onHardware, Modifier.weight(1f))
            QuickActionTile("Studio", Icons.Default.Code, onCode, Modifier.weight(1f))
        }
    }
}

/**
 * Circular Climate Progress Gauge
 */
@Composable
private fun CircularClimateGauge(
    temperature: Float,
    minTemp: Float = 10f,
    maxTemp: Float = 40f
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val sweepAngle = 240f
        val startAngle = 150f
        val diameter = size.minDimension
        val strokeWidth = 12.dp.toPx()
        val arcSize = Size(diameter - strokeWidth, diameter - strokeWidth)
        val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)

        // Background Track
        drawArc(
            color = Color(0xFF222B3F),
            startAngle = startAngle,
            sweepAngle = sweepAngle,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )

        // Active Temperature Arc
        val progress = ((temperature - minTemp) / (maxTemp - minTemp)).coerceIn(0f, 1f)
        val activeSweep = sweepAngle * progress

        drawArc(
            brush = Brush.sweepGradient(
                colors = listOf(Color(0xFF46C6EF), Color(0xFF27D987), Color(0xFFFFB84D), Color(0xFFFF5252))
            ),
            startAngle = startAngle,
            sweepAngle = activeSweep,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
    }
}

/**
 * Interactive Real-Time Telemetry Canvas Chart
 */
@Composable
private fun TelemetryChartSection(
    moduleState: ComponentDashboardState,
    live: Boolean
) {
    var selectedInterval by remember { mutableStateOf("24h") }
    val samples = moduleState.series("dht11", "temperature")
        .ifEmpty { moduleState.series("dht22", "temperature") }
    var touchedSample by remember { mutableStateOf<TelemetrySample?>(null) }

    Card(
        shape = CompiTokens.Shapes.Large,
        colors = CardDefaults.cardColors(containerColor = CompiTokens.Colors.SurfaceCard),
        modifier = Modifier.fillMaxWidth().testTag("home_telemetry_chart")
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
                Column {
                    Text(
                        text = "Tendencia de temperatura",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = touchedSample?.let {
                            "%.1f °C · %s".format(Locale.ROOT, it.value, formatHourMinute(it.recordedAtMs))
                        } ?: "Historial en tiempo real",
                        style = MaterialTheme.typography.labelSmall,
                        color = CompiTokens.Colors.PrimaryViolet
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("15m", "1h", "6h", "24h").forEach { interval ->
                        FilterChip(
                            selected = selectedInterval == interval,
                            onClick = { selectedInterval = interval },
                            label = { Text(interval, fontSize = 11.sp) }
                        )
                    }
                }
            }

            // Real Canvas Chart
            val validSamples = samples.filter { it.isPlottable && it.value != null }
            if (validSamples.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(140.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Esperando telemetría real del ESP32...",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF64748B)
                    )
                }
            } else {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .pointerInput(validSamples) {
                            detectTapGestures { offset ->
                                val width = size.width
                                val index = ((offset.x / width) * validSamples.size).toInt().coerceIn(0, validSamples.lastIndex)
                                touchedSample = validSamples.getOrNull(index)
                            }
                        }
                ) {
                    val w = size.width
                    val h = size.height
                    val minVal = (validSamples.minOfOrNull { it.value!! } ?: 15f) - 2f
                    val maxVal = (validSamples.maxOfOrNull { it.value!! } ?: 35f) + 2f
                    val range = (maxVal - minVal).coerceAtLeast(1f)

                    val path = Path()
                    val fillPath = Path()

                    validSamples.forEachIndexed { i, sample ->
                        val valF = sample.value!!
                        val x = (i.toFloat() / (validSamples.size - 1).coerceAtLeast(1)) * w
                        val y = h - ((valF - minVal) / range) * h

                        if (i == 0) {
                            path.moveTo(x, y)
                            fillPath.moveTo(x, h)
                            fillPath.lineTo(x, y)
                        } else {
                            val prevVal = validSamples[i - 1].value!!
                            val prevX = ((i - 1).toFloat() / (validSamples.size - 1).coerceAtLeast(1)) * w
                            val prevY = h - ((prevVal - minVal) / range) * h
                            val cx = (prevX + x) / 2f
                            path.cubicTo(cx, prevY, cx, y, x, y)
                            fillPath.cubicTo(cx, prevY, cx, y, x, y)
                        }

                        if (i == validSamples.lastIndex) {
                            fillPath.lineTo(x, h)
                            fillPath.close()
                        }
                    }

                    // Draw Gradient Fill
                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            listOf(Color(0x55A88BFF), Color(0x00A88BFF))
                        )
                    )

                    // Draw Line Path
                    drawPath(
                        path = path,
                        color = Color(0xFFA88BFF),
                        style = Stroke(width = 3.5f)
                    )
                }
            }
        }
    }
}

/**
 * 2-Column Compact Metric Card Component
 */
@Composable
private fun CompactMetricCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        shape = CompiTokens.Shapes.Medium,
        colors = CardDefaults.cardColors(containerColor = CompiTokens.Colors.SurfaceCard),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(iconTint.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = Color(0xFF64748B),
                    modifier = Modifier.size(16.dp)
                )
            }

            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF94A3B8)
                )
            }
        }
    }
}

/**
 * Quick Action Button Tile
 */
@Composable
private fun QuickActionTile(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        shape = CompiTokens.Shapes.Medium,
        colors = CardDefaults.cardColors(containerColor = CompiTokens.Colors.SurfaceCard),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(CompiTokens.Colors.PrimaryVioletContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = CompiTokens.Colors.PrimaryViolet,
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        }
    }
}

private data class HomeDeviceStats(
    val wifiDbm: Int?,
    val latencyMs: Long,
    val firmware: String?,
    val freeHeap: Long?
)

private fun formatHourMinute(epochMs: Long): String {
    if (epochMs <= 0) return "—"
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(epochMs))
}
