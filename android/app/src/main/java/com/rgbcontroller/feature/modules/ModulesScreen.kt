package com.rgbcontroller.feature.modules

import android.os.SystemClock
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.rgbcontroller.core.ble.BleConnectionState
import com.rgbcontroller.core.ble.BleManager
import com.rgbcontroller.core.modules.CompiModule
import com.rgbcontroller.core.modules.ModuleKind
import com.rgbcontroller.core.modules.ModuleManifest
import com.rgbcontroller.core.sensors.AdcSample
import com.rgbcontroller.core.sensors.AdcTelemetry
import com.rgbcontroller.core.sensors.SoilCalibration
import com.rgbcontroller.core.ui.CompiInlineMessage
import com.rgbcontroller.core.ui.CompiModuleCard
import com.rgbcontroller.core.ui.modules.CompiMetricValue
import com.rgbcontroller.core.ui.modules.CompiAdcTrend
import com.rgbcontroller.core.wifi.CompiLabSession
import com.rgbcontroller.core.wifi.LocalLabClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/** V15: catalogue backed by verified transport, never by screenshots or guessed wiring. */
@Composable
fun ModulesScreen(
    labSession: CompiLabSession,
    bleManager: BleManager,
    onOpenLed: () -> Unit,
    onOpenHardware: () -> Unit
) {
    val context = LocalContext.current
    val client = remember(context) { LocalLabClient(context.applicationContext) }
    val bleConnection by bleManager.connectionState.collectAsState()
    val wifiReady = labSession.wifiReady && LocalLabClient.validKey(labSession.key)
    val secret = labSession.key
    val epoch = labSession.revision
    var discovered by remember { mutableStateOf<List<CompiModule>>(emptyList()) }
    var discoveryError by remember { mutableStateOf<String?>(null) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(wifiReady, secret, epoch) {
        discovered = emptyList()
        discoveryError = null
        if (!wifiReady) return@LaunchedEffect
        try {
            val response = labSession.serialized { client.modules(secret) }
            if (epoch != labSession.revision || secret != labSession.key || !labSession.wifiReady) {
                return@LaunchedEffect
            }
            discovered = ModuleManifest.parse(response.toString())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            discoveryError = "El firmware no anunció módulos V15 compatibles: " +
                (error.message ?: "respuesta no disponible")
        }
    }

    // Legacy RGB stays usable via its existing BLE/SoftAP controller.
    // Do not advertise a soil probe merely because ADC1 exists.
    val legacyRgb = CompiModule("led.rgb.main", ModuleKind.RGB, "LED RGB", listOf(27, 25, 26))
    val rgbVerified = bleConnection == BleConnectionState.Ready || wifiReady
    val available = if (discovered.isNotEmpty()) discovered
        else if (rgbVerified) listOf(legacyRgb) else emptyList()
    val hasAdc = available.any { it.id == "input.adc1.34" }
    val selectedAdc = selected == "input.adc1.34" && hasAdc

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Biblioteca de módulos", style = MaterialTheme.typography.headlineMedium)
        Text("El ESP32 anuncia capacidades; la conexión física de sensores se comprueba por separado.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        if (available.isEmpty()) {
            CompiInlineMessage("Sin módulos verificados. Conecta Bluetooth para LED o autoriza el " +
                "SoftAP desde Hardware para descubrir entradas analógicas.")
        }
        discoveryError?.let { CompiInlineMessage(it) }

        available.forEach { module ->
            CompiModuleCard(
                title = module.title,
                summary = when (module.kind) {
                    ModuleKind.RGB -> "Control RGB existente · GPIO 27/25/26"
                    ModuleKind.ADC_INPUT -> "GPIO34 · lectura ADC bruta; sensor no verificado"
                },
                icon = if (module.kind == ModuleKind.RGB) Icons.Default.ColorLens
                    else Icons.Default.WaterDrop,
                onClick = {
                    if (module.kind == ModuleKind.RGB) onOpenLed()
                    else selected = if (selected == module.id) null else module.id
                },
                modifier = Modifier.testTag("module_" + module.id)
            )
        }
        if (selectedAdc && wifiReady) {
            SoilAdcPanel(labSession, client, secret, epoch)
        }
        if (!wifiReady) {
            OutlinedButton(onClick = onOpenHardware, modifier = Modifier.fillMaxWidth()) {
                Text("Configurar Wi-Fi y sensores desde Hardware")
            }
        }
        CompiInlineMessage("La humedad (%) solo se calcula con referencias seco/húmedo reales. " +
            "El LED conserva su pantalla y comandos originales. BLE V1 no permite lecturas ADC.")
    }
}

@Composable
private fun SoilAdcPanel(
    labSession: CompiLabSession,
    client: LocalLabClient,
    secret: String,
    epoch: Int
) {
    var raw by remember(secret, epoch) { mutableStateOf<Int?>(null) }
    var history by remember(secret, epoch) { mutableStateOf(emptyList<AdcSample>()) }
    var error by remember(secret, epoch) { mutableStateOf<String?>(null) }
    var dryRaw by remember(secret, epoch) { mutableStateOf<Int?>(null) }
    var wetRaw by remember(secret, epoch) { mutableStateOf<Int?>(null) }

    LaunchedEffect(secret, epoch) {
        // Foreground only; changing modules or navigating away cancels polling.
        while (true) {
            try {
                val result = labSession.serialized {
                    client.moduleTelemetry(secret, "input.adc1.34")
                }
                if (epoch != labSession.revision || !labSession.wifiReady ||
                    secret != labSession.key) return@LaunchedEffect
                require(result.optString("moduleId") == "input.adc1.34" &&
                    result.optString("quality") == "RAW_UNCALIBRATED") {
                    "Telemetría de módulo incompatible"
                }
                val readings = result.optJSONObject("readings") ?: kotlin.error("Sin lecturas ADC")
                val next = readings.optInt("adc_raw", -1)
                require(next in 0..4095) { "ADC fuera de rango" }
                val now = SystemClock.elapsedRealtime()
                history = AdcTelemetry.append(history, AdcSample(now, next))
                raw = next
                error = null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                raw = null
                history = emptyList()
                error = failure.message ?: "Lectura interrumpida"
                // Never keep stale readings on the screen after a lost network.
                break
            }
            delay(2_000L)
        }
    }

    val calibration = if (dryRaw != null && wetRaw != null)
        runCatching { SoilCalibration(dryRaw!!, wetRaw!!) }.getOrNull() else null
    val percentage = if (raw != null && calibration != null)
        calibration.percentage(raw!!) else null

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Entrada ADC1 · GPIO34", style = MaterialTheme.typography.titleLarge)
            Text("Fuente: firmware /v2/modules/telemetry · sensor físico no verificado",
                style = MaterialTheme.typography.bodySmall)
            Box(Modifier.testTag("adc_raw")) {
                CompiMetricValue("ADC bruto", raw?.toString(), "/ 4095", "GPIO34 · firmware")
            }
            CompiMetricValue("Humedad calibrada (temporal)",
                percentage?.toString(), "%", "Solo tras dos referencias reales")
            error?.let { CompiInlineMessage(it, isError = true) }
            Text("Historial ADC (hasta 120 muestras)", style = MaterialTheme.typography.labelLarge)
            CompiAdcTrend(history)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { dryRaw = raw }, enabled = raw != null,
                    modifier = Modifier.weight(1f)) { Text("Guardar seco") }
                OutlinedButton(onClick = { wetRaw = raw }, enabled = raw != null,
                    modifier = Modifier.weight(1f)) { Text("Guardar húmedo") }
            }
            if (dryRaw != null || wetRaw != null) {
                Text("Seco: ${dryRaw ?: "pendiente"} · Húmedo: ${wetRaw ?: "pendiente"}",
                    style = MaterialTheme.typography.bodySmall)
            }
            if (dryRaw != null && wetRaw != null && calibration == null) {
                CompiInlineMessage("Las referencias deben diferir al menos 100 puntos ADC.",
                    isError = true)
            }
            Text("Calibración provisional: no persiste al cerrar la vista. Realiza ambas mediciones " +
                "con el sensor estabilizado; no automatizar riego con estos datos.",
                style = MaterialTheme.typography.bodySmall)
        }
    }
}
