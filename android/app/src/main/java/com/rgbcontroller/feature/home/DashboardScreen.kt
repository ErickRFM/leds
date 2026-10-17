package com.rgbcontroller.feature.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rgbcontroller.core.components.model.ComponentTelemetryCodec
import com.rgbcontroller.core.components.registry.ComponentCatalog
import com.rgbcontroller.core.components.status.ComponentStatusFreshness
import com.rgbcontroller.feature.hardwarelab.ComponentDashboardViewModel
import com.rgbcontroller.core.components.model.ComponentMeasurement
import com.rgbcontroller.core.components.status.ComponentDashboardState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rgbcontroller.core.ble.BleConnectionState
import com.rgbcontroller.core.ble.BleManager
import com.rgbcontroller.core.ui.CompiModuleCard
import com.rgbcontroller.core.ui.CompiInlineMessage
import com.rgbcontroller.core.wifi.CompiLabSession
import com.rgbcontroller.core.wifi.LabConnectionState
import com.rgbcontroller.core.wifi.LabTransport
import com.rgbcontroller.core.wifi.LocalLabClient
import android.os.SystemClock
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import kotlinx.coroutines.CancellationException

/**
 * Compi V9 home: the visual reference is the layout contract, but device states
 * always originate in BleManager. Never display fabricated Wi-Fi telemetry.
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
    LaunchedEffect(wifiReady) {
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
                        firmware = firmware.optString("firmwareVersion").ifBlank { null },
                        freeHeap = diagnostics.optLong("freeHeap", -1).takeIf { it >= 0 }
                    )
                }
                if (session.revision == revision && session.wifiReady) wifiStats = result
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { wifiStats = null }
        }
    }
    val status = when (connection) {
        BleConnectionState.Ready -> "ESP32 listo"
        BleConnectionState.Scanning -> "Buscando ESP32"
        BleConnectionState.Connecting -> "Conectando ESP32"
        BleConnectionState.Discovering -> "Sincronizando ESP32"
        BleConnectionState.Disconnected -> "ESP32 sin conexión"
        is BleConnectionState.Error -> "Conexión necesita atención"
    }
    val subtitle = when (val current = connection) {
        BleConnectionState.Ready -> "Bluetooth conectado y confirmado"
        BleConnectionState.Scanning -> "Buscando dispositivos cercanos"
        BleConnectionState.Connecting -> "Estableciendo conexión Bluetooth"
        BleConnectionState.Discovering -> "Verificando servicios y estado"
        BleConnectionState.Disconnected -> "Conecta tu ESP32 para usar los controles"
        is BleConnectionState.Error -> current.message
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Compi", style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            IconButton(onClick = onSettings, modifier = Modifier.testTag("home_settings")) {
                Icon(Icons.Default.Settings, contentDescription = "Ajustes")
            }
        }
        Card(
            onClick = onDevices,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth().testTag("home_device_status"),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("CONEXIÓN Y DISPOSITIVO", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null,
                        tint = if (ready) Color(0xFF27D987) else MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(12.dp))
                    Text(if (wifiReady) "ESP32 Wi-Fi autorizado" else status,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Icon(Icons.Default.ChevronRight, contentDescription = null)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Bluetooth, contentDescription = null, tint = Color(0xFF57A4FF))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (wifiReady) "Wi-Fi laboratorio" else "Bluetooth", fontWeight = FontWeight.SemiBold)
                        Text(if (wifiReady) "Sesión local autorizada" else subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (wifiReady && wifiStats != null) {
                    val observed = wifiStats!!
                    Row(Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HomeDiagnosticBadge("Señal",
                            observed.wifiDbm?.let { "${it} dBm" } ?: "No disponible",
                            Icons.Default.Wifi, Modifier.weight(1f))
                        HomeDiagnosticBadge("Latencia",
                            "${observed.latencyMs} ms", Icons.Default.Speed, Modifier.weight(1f))
                    }
                    Row(Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HomeDiagnosticBadge("Firmware",
                            observed.firmware ?: "No disponible",
                            Icons.Default.DeveloperBoard, Modifier.weight(1f))
                        HomeDiagnosticBadge("Memoria",
                            observed.freeHeap?.let { "${it / 1024} KiB" } ?: "No disponible",
                            Icons.Default.Storage, Modifier.weight(1f))
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                Text(if (wifiReady) "Wi-Fi: enlace autorizado, pendiente de lecturas de módulos"
                    else "Conexiones Wi-Fi: consulta el estado desde Hardware",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        HomeActiveModuleCard(moduleState, onLed)
        Text("Accesos rápidos", style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickAction("Módulos", Icons.Default.ColorLens, onLed, Modifier.weight(1f))
            QuickAction("Conectar", Icons.Default.Devices, onDevices, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickAction("Studio", Icons.Default.Code, onCode, Modifier.weight(1f))
            QuickAction("Hardware", Icons.Default.Memory, onHardware, Modifier.weight(1f))
        }

        Text("Herramientas", style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold)
        CompiModuleCard("Mis módulos", "Biblioteca, componentes y conexiones GPIO",
            Icons.Default.ColorLens, onClick = onLed, modifier = Modifier.testTag("open_led"))
        CompiModuleCard("Compi Studio", "Editar código, importar y ejecutar scripts compatibles",
            Icons.Default.Code, onClick = onCode, modifier = Modifier.testTag("open_code"))
        CompiModuleCard("Hardware Lab", "GPIO, sensores, Wi-Fi y diagnóstico",
            Icons.Default.Memory, onClick = onHardware, modifier = Modifier.testTag("open_hardware"))

        Text("Actividad reciente", style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold)
        Card(shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text(if (wifiReady) "Sesión Wi-Fi autorizada"
                    else if (bleReady) "Dispositivo Bluetooth sincronizado"
                    else "Sin actividad verificada",
                    fontWeight = FontWeight.Medium)
                Text("La información se confirma desde el ESP32; no se simulan registros.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        CompiInlineMessage("Wi-Fi local y las operaciones de hardware requieren un ESP32 compatible. " +
            "El editor no instala firmware automáticamente.")
    }
}



private data class HomeDeviceStats(
    val wifiDbm: Int?,
    val latencyMs: Long,
    val firmware: String?,
    val freeHeap: Long?
)

@Composable
private fun HomeDiagnosticBadge(
    label: String, value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Surface(modifier, shape = RoundedCornerShape(13.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(5.dp))
                Text(label, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(value, style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

/**
 * Condensed Home module summary from the same firmware/last-known contract as
 * Hardware. The card never uses local draft values as actual sensor readings.
 */
@Composable
private fun HomeActiveModuleCard(
    state: ComponentDashboardState,
    onOpenModules: () -> Unit
) {
    val live = state.freshness == ComponentStatusFreshness.LIVE &&
        state.connected && state.authorityError == null
    val reported = state.authoritative ?: state.persisted
    val available = reported.orEmpty().filter { it.active }
    val isEnvironmental = available.any {
        ComponentCatalog.catalogIdForFirmwareId(it.id) == "dht22"
    }
    val isSoil = available.any {
        ComponentCatalog.catalogIdForFirmwareId(it.id) == "soil_moisture"
    }
    val firstId = available.firstOrNull()?.id?.let(ComponentCatalog::catalogIdForFirmwareId)
    val title = when {
        isEnvironmental && isSoil -> "Sensor ambiental"
        firstId != null -> ComponentCatalog.get(firstId)?.name ?: firstId
        state.desiredConfig.components.isNotEmpty() -> "Módulos por configurar"
        else -> "Configura tu primer módulo"
    }
    val telemetry = state.authoritativeTelemetry ?: state.persistedTelemetry
    fun reading(type: String): ComponentMeasurement? =
        telemetry?.let { ComponentTelemetryCodec.find(it, type) }
            ?.takeIf { it.valid && it.status == "fresh" && it.value.isFinite() }

    Card(
        onClick = onOpenModules,
        modifier = Modifier.fillMaxWidth().testTag("home_active_module"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Módulo actual", style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                Text("Cambiar", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary)
                Icon(Icons.Default.ChevronRight, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.DeveloperBoard, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold)
                    Text(when {
                        live -> "Estado confirmado por el ESP32"
                        reported != null -> "Última confirmación · no en vivo"
                        else -> "Borrador local · pendiente de conexión"
                    }, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (isEnvironmental || isSoil) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (isEnvironmental) {
                        HomeMetric("Temperatura", reading("temperature")?.value, "°C",
                            Modifier.weight(1f))
                        HomeMetric("Humedad", reading("humidity")?.value, "%",
                            Modifier.weight(1f))
                    }
                    if (isSoil) {
                        HomeMetric("Suelo", reading("soil_moisture")?.value, "%",
                            Modifier.weight(1f))
                    }
                }
                Text(if (live) "Lecturas del firmware"
                     else "Últimas lecturas confirmadas · no son actuales",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else if (reported == null) {
                Text("Selecciona un sensor, LED, pantalla o actuador de la biblioteca.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun HomeMetric(label: String, value: Float?, suffix: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(value?.let { "%.1f".format(java.util.Locale.ROOT, it) + suffix } ?: "—",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (value == null) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun QuickAction(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        onClick = onClick,
        modifier = modifier.heightIn(min = 76.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.fillMaxWidth().heightIn(min = 76.dp).padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(6.dp))
            Text(title, style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold, maxLines = 2)
        }
    }
}
