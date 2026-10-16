package com.rgbcontroller.feature.hardwarelab

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rgbcontroller.core.ble.BleManager
import com.rgbcontroller.core.components.alerts.AlertComparison
import com.rgbcontroller.core.components.alerts.AlertSeverity
import com.rgbcontroller.core.components.automation.AutomationEventStatus
import com.rgbcontroller.core.components.model.ComponentRuntimeStatus
import com.rgbcontroller.core.components.model.ComponentState
import com.rgbcontroller.core.components.model.ComponentStatusCodec
import com.rgbcontroller.core.components.model.ComponentTelemetryCodec
import com.rgbcontroller.core.components.telemetry.TelemetryChart
import com.rgbcontroller.core.components.widgets.TelemetryChartCard
import com.rgbcontroller.core.components.model.HardwareComponentDescriptor
import com.rgbcontroller.core.components.registry.ComponentCatalog
import com.rgbcontroller.core.components.status.ComponentDashboardState
import com.rgbcontroller.core.components.status.ComponentStatusFreshness
import com.rgbcontroller.core.components.status.OledRuntime
import com.rgbcontroller.core.components.status.LocalDesiredComponent
import com.rgbcontroller.core.components.status.LocalDesiredConfig
import com.rgbcontroller.core.components.widgets.HumidityTemperatureWidget
import com.rgbcontroller.core.components.widgets.OledDisplayWidget
import com.rgbcontroller.core.components.widgets.SoilMoistureWidget
import com.rgbcontroller.core.wifi.CompiLabSession
import com.rgbcontroller.core.wifi.LabConnectionState
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Persistent component dashboard. Authoritative GPIO/lifecycle come only from the
 * firmware (`GET /v2/components/status`); the local desired configuration is
 * always shown separately and never treated as accepted. Offline mode shows the
 * last confirmed snapshot, clearly labelled as not live.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HardwareLabScreen(
    session: CompiLabSession? = null,
    bleManager: BleManager? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val viewModel: ComponentDashboardViewModel = viewModel(
        factory = remember(session, bleManager) {
            ComponentDashboardViewModel.factory(context.applicationContext, session, bleManager)
        }
    )
    val state by viewModel.uiState.collectAsState()
    var showCatalog by remember { mutableStateOf(false) }
    var showAlertEditor by remember { mutableStateOf(false) }
    var showAutomationEditor by remember { mutableStateOf(false) }
    var syncMessage by remember { mutableStateOf<String?>(null) }

    val sessionConnection = session?.connectionState
    val authorized = sessionConnection == LabConnectionState.AUTHORIZED

    LaunchedEffect(Unit) { viewModel.loadPersisted() }
    LaunchedEffect(sessionConnection) {
        viewModel.onConnectionChanged()
        syncMessage = if (authorized) "Estado sincronizado desde el firmware."
        else "Sin conexión autorizada: se muestra el último estado confirmado."
    }

    val confirmed = state.firmwareStatus?.components
    val lastKnown = state.lastKnownStatus?.components
    val freshness = state.freshness
    val knownIds = state.confirmedCatalogIds
    val missingTemplates = ComponentCatalog.components.filter { it.id !in knownIds }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dashboard de componentes") },
                actions = {
                    IconButton(
                        onClick = {
                            scope.launch {
                                viewModel.refresh()
                            }
                        },
                        modifier = Modifier.testTag("component_refresh")
                    ) { Icon(Icons.Default.Refresh, contentDescription = "Actualizar estado") }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                AuthorityCard(connected = state.connected, authorized = authorized,
                    error = state.authorityError)
            }
            item {
                Text("Estado confirmado por el firmware",
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            when {
                freshness == ComponentStatusFreshness.LIVE && confirmed != null -> {
                    item {
                        Text("En vivo · actualizado ${formatTime(state.firmwareStatus?.receivedAtMs)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.testTag("component_live"))
                    }
                    items(confirmed, key = { it.id }) { component ->
                        FirmwareComponentCard(component = component, live = true)
                    }
                }
                freshness == ComponentStatusFreshness.LAST_KNOWN && lastKnown != null -> {
                    item {
                        Text("Último estado confirmado ${formatTime(state.lastKnownStatus?.confirmedAtMs)} · no es telemetría en vivo",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.testTag("component_last_known"))
                    }
                    items(lastKnown, key = { it.id }) { component ->
                        FirmwareComponentCard(component = component, live = false)
                    }
                }
                else -> {
                    item {
                        Card(Modifier.fillMaxWidth().testTag("component_none")) {
                            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                                Text("Sin estado confirmado")
                                Text("Conecta y autoriza el ESP32 para leer GPIO reales. " +
                                    "Nada se simula mientras está desconectado.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            item {
                Text("Lecturas reales de sensores",
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            item {
                Dht22TelemetryCard(state = state)
            }
            item {
                SoilTelemetryCard(state = state)
            }
            item {
                SoilCalibrationCard(state = state, onClear = {
                    scope.launch { viewModel.clearSoilCalibration() }
                })
            }
            item {
                Text("Alertas",
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            item {
                AlertsCard(state = state, onEdit = { showAlertEditor = true })
            }
            item {
                Text("Gráficas (ventana de 30 min)",
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            item {
                TelemetryChartsSection(state = state)
            }
            item {
                Text("Automatizaciones",
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            item {
                AutomationCard(state = state, onEdit = { showAutomationEditor = true })
            }
            item {
                Text("Pantalla OLED (display)",
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            item {
                OledStatusCard(state = state)
            }
            item {
                Text("Historial de telemetría (local)",
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            item {
                TelemetryHistoryCard(state = state)
            }

            syncMessage?.let { message ->
                item {
                    Text(message, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Configuración local (pendiente)",
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    TextButton(onClick = { showCatalog = true },
                        modifier = Modifier.testTag("component_add")) {
                        Icon(Icons.Default.Add, contentDescription = null); Text("Agregar")
                    }
                }
            }
            item {
                Text("Esta configuración es un deseo local; no implica que el ESP32 la haya aceptado.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (state.desiredConfig.components.isEmpty()) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Text("No hay componentes locales configurados",
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            } else {
                items(state.desiredConfig.components, key = { "desired_${it.id}" }) { desired ->
                    DesiredComponentCard(desired = desired, confirmedCatalogIds = knownIds)
                }
            }

            if (missingTemplates.isNotEmpty()) {
                item {
                    Text("Plantillas no detectadas por el firmware",
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                item {
                    Text("Estos componentes del catálogo no están presentes/activos según el firmware.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                items(missingTemplates, key = { "template_${it.id}" }) { descriptor ->
                    DisabledTemplateCard(descriptor = descriptor)
                }
            }
        }
    }

    if (showCatalog) {
        AlertDialog(
            onDismissRequest = { showCatalog = false },
            title = { Text("Catálogo de componentes") },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(ComponentCatalog.components, key = { it.id }) { desc ->
                        Card(
                            onClick = {
                                val next = state.desiredConfig.components.toMutableList()
                                if (next.none { it.id == desc.id }) {
                                    next += LocalDesiredComponent(
                                        id = desc.id,
                                        type = desc.type.name.lowercase(Locale.getDefault()),
                                        pins = desc.requiredPins.sorted()
                                    )
                                }
                                scope.launch { viewModel.setLocalDesired(LocalDesiredConfig(next)) }
                                showCatalog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(desc.name, style = MaterialTheme.typography.titleSmall)
                                Text(desc.description, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showCatalog = false }) { Text("Cerrar") } }
        )
    }

    if (showAlertEditor) {
        AlertRulesEditorDialog(
            rules = state.alertRules,
            onSave = { scope.launch { viewModel.setAlertRules(it) }; showAlertEditor = false },
            onDismiss = { showAlertEditor = false }
        )
    }

    if (showAutomationEditor) {
        AutomationRulesEditorDialog(
            rules = state.automationRules,
            onSave = { scope.launch { viewModel.setAutomationRules(it) }; showAutomationEditor = false },
            onDismiss = { showAutomationEditor = false }
        )
    }
}

@Composable
private fun AuthorityCard(connected: Boolean, authorized: Boolean, error: String?) {
    val title = when {
        authorized -> "Conectado y autorizado"
        connected -> "Conexión sin autorizar"
        else -> "ESP32 desconectado"
    }
    val color = if (authorized) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onSurfaceVariant
    Card(Modifier.fillMaxWidth().testTag("component_authority"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, color = color)
            Text("Autoridad GPIO: firmware. Los GPIO actuales provienen del ESP32, nunca de la configuración local.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            error?.let {
                Text(it, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("component_error"))
            }
        }
    }
}

@Composable
private fun FirmwareComponentCard(component: ComponentRuntimeStatus, live: Boolean) {
    val state = ComponentStatusCodec.stateFor(component)
    Card(Modifier.fillMaxWidth().testTag("component_${component.id}")) {
        Column(Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()) {
                Text(component.id, fontWeight = FontWeight.SemiBold)
                Text(
                    when (state) {
                        ComponentState.ACTIVE -> if (live) "ACTIVO" else "ÚLTIMO"
                        ComponentState.ERROR -> "ERROR"
                        ComponentState.INCOMPATIBLE -> "NO SOPORTADO"
                        ComponentState.DISCONNECTED -> "DESCONECTADO"
                        else -> "DESCONOCIDO"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (live && component.active && component.state == "active")
                        MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text("Tipo: ${component.type} · Interfaz: ${component.interfaceType}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("GPIO: ${if (component.pins.isEmpty()) "—" else component.pins.joinToString(", ")}",
                style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun DesiredComponentCard(
    desired: LocalDesiredComponent,
    confirmedCatalogIds: Set<String>
) {
    val accepted = desired.id in confirmedCatalogIds
    Card(Modifier.fillMaxWidth().testTag("desired_${desired.id}")) {
        Column(Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(desired.id, fontWeight = FontWeight.SemiBold)
            Text("Deseado localmente · ${if (accepted) "coincide con el firmware" else "sin confirmar por el firmware"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("GPIO deseados: ${if (desired.pins.isEmpty()) "—" else desired.pins.joinToString(", ")}",
                style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun Dht22TelemetryCard(state: ComponentDashboardState) {
    val live = state.telemetryFreshness == ComponentStatusFreshness.LIVE
    val measurements = state.authoritativeTelemetry ?: state.persistedTelemetry
    val humidity = ComponentTelemetryCodec.find(measurements.orEmpty(), "humidity")
    val temperature = ComponentTelemetryCodec.find(measurements.orEmpty(), "temperature")
    // Only firmware-validated numbers are ever shown; invalid/stale stay null.
    val validHumidity = humidity?.takeIf { it.valid }?.value
    val validTemperature = temperature?.takeIf { it.valid }?.value
    val hasData = validHumidity != null && validTemperature != null
    val timestamp = if (live) {
        humidity?.timestampMs?.takeIf { it > 0L }
            ?: temperature?.timestampMs?.takeIf { it > 0L }
            ?: state.firmwareTelemetry?.receivedAtMs
    } else {
        state.lastKnownTelemetry?.confirmedAtMs
    }
    val status = humidity?.status ?: temperature?.status
    HumidityTemperatureWidget(
        humidity = validHumidity,
        temperature = validTemperature,
        hasData = hasData,
        lastUpdate = timestamp,
        live = live,
        status = status?.let { telemetryStatusLabel(it) },
        modifier = Modifier.testTag("component_telemetry_dht22")
    )
}

@Composable
private fun SoilTelemetryCard(state: ComponentDashboardState) {
    val live = state.telemetryFreshness == ComponentStatusFreshness.LIVE
    val measurements = state.authoritativeTelemetry ?: state.persistedTelemetry
    val moisture = measurements?.let { ComponentTelemetryCodec.find(it, "soil_moisture") }
    val raw = measurements?.let { ComponentTelemetryCodec.find(it, "adc_raw") }
    // A percentage is only shown for a calibrated, fresh, valid moisture reading.
    val calibrated = moisture?.status == "fresh" && moisture.valid
    val moisturePercent = moisture?.takeIf { calibrated }?.value
    val rawValue = raw?.takeIf { it.valid }?.value?.toInt()
    val hasData = rawValue != null || moisture != null
    val timestamp = if (live) {
        moisture?.timestampMs?.takeIf { it > 0L } ?: raw?.timestampMs?.takeIf { it > 0L }
    } else {
        state.lastKnownTelemetry?.confirmedAtMs
    }
    val status = moisture?.status ?: raw?.status
    SoilMoistureWidget(
        moisturePercent = moisturePercent,
        rawAdc = rawValue,
        calibrated = calibrated,
        hasData = hasData,
        lastUpdate = timestamp,
        live = live,
        status = status?.let { telemetryStatusLabel(it) },
        modifier = Modifier.testTag("component_telemetry_soil_moisture")
    )
}

@Composable
private fun SoilCalibrationCard(state: ComponentDashboardState, onClear: () -> Unit) {
    val stored = state.soilCalibration
    val active = state.activeSoilCalibration
    Card(Modifier.fillMaxWidth().testTag("component_soil_calibration")) {
        Column(Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Calibración de suelo", fontWeight = FontWeight.SemiBold)
            when {
                active != null -> {
                    Text(
                        "Activa para este dispositivo · seco ${active.dryRaw} / húmedo ${active.wetRaw}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary)
                }
                stored != null -> {
                    Text(
                        "La calibración guardada pertenece a otro dispositivo (${stored.deviceId}) " +
                            "y se ignora: no se aplica a este sensor.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = onClear,
                        modifier = Modifier.testTag("component_soil_calibration_clear")) {
                        Text("Descartar calibración incompatible")
                    }
                }
                else -> {
                    Text(
                        "Sin calibración para este dispositivo. Sin referencias seco/húmedo " +
                            "no se muestra un porcentaje de humedad.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun OledStatusCard(state: ComponentDashboardState) {
    val live = state.freshness == ComponentStatusFreshness.LIVE
    val status = (state.authoritative ?: state.persisted)
        ?.let { ComponentStatusCodec.find(it, OledRuntime.FIRMWARE_ID) }
    val oled = OledRuntime.from(status)
    val sync = if (live) state.firmwareStatus?.receivedAtMs else state.lastKnownStatus?.confirmedAtMs
    OledDisplayWidget(
        detected = oled.detected,
        configured = oled.configured,
        operative = oled.operative,
        sda = oled.sda,
        scl = oled.scl,
        address = if (oled.detected) OledRuntime.DEFAULT_ADDRESS else null,
        stateLabel = if (oled.detected) firmwareStateLabel(oled.state) else null,
        lastSync = sync,
        live = live,
        modifier = Modifier.testTag("component_oled_status")
    )
}

@Composable
private fun AlertsCard(state: ComponentDashboardState, onEdit: () -> Unit) {
    val active = state.alertRules.filter { state.alertStates[it.id]?.active == true }
    val recent = state.alertEvents.take(5)
    Card(Modifier.fillMaxWidth().testTag("component_alerts")) {
        Column(Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()) {
                Text("${state.alertRules.size} umbrales configurados",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = onEdit,
                    modifier = Modifier.testTag("component_alerts_edit")) {
                    Text("Editar umbrales")
                }
            }
            if (active.isEmpty()) {
                Text("Sin alertas activas. Los umbrales son locales y no implican aceptación del firmware.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                for (rule in active) {
                    val value = state.alertStates[rule.id]?.lastValue
                    val color = when (rule.severity) {
                        AlertSeverity.CRITICAL -> MaterialTheme.colorScheme.error
                        AlertSeverity.WARNING -> MaterialTheme.colorScheme.tertiary
                        AlertSeverity.INFO -> MaterialTheme.colorScheme.primary
                    }
                    Text(
                        "${rule.componentId} · ${rule.measurementType} " +
                            "${if (rule.comparison == AlertComparison.ABOVE) "≥" else "≤"} ${formatValue(rule.threshold)} " +
                            "(${rule.severity.name.lowercase()})",
                        fontWeight = FontWeight.SemiBold, color = color,
                        style = MaterialTheme.typography.bodySmall)
                    Text("Último valor: ${value?.let { formatValue(it) } ?: "—"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (recent.isNotEmpty()) {
                HorizontalDivider()
                Text("Historial reciente", fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodySmall)
                for (event in recent) {
                    Text(
                        "${formatTime(event.atMs)} · ${if (event.clear) "normalizada" else "activada"} " +
                            "${event.ruleId} · ${formatValue(event.value)} (umbral ${formatValue(event.threshold)})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun AutomationCard(state: ComponentDashboardState, onEdit: () -> Unit) {
    val rules = state.automationRules
    val recent = state.automationEvents.take(5)
    Card(Modifier.fillMaxWidth().testTag("component_automations")) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()) {
                Text("${rules.size} automatizaciones configuradas",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = onEdit,
                    modifier = Modifier.testTag("component_automations_edit")) {
                    Text("Editar automatizaciones")
                }
            }
            if (rules.isEmpty()) {
                Text("Sin automatizaciones configuradas.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                for (rule in rules) {
                    Text(
                        "${rule.action.label} · ${rule.componentId} · ${rule.measurementType} " +
                            "${if (rule.comparison == AlertComparison.ABOVE) "≥" else "≤"} ${formatValue(rule.threshold)}" +
                            (if (rule.enabled) "" else " (deshabilitada)"),
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (rule.enabled) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(
                "Las acciones de hardware (RGB/OLED) solo se ejecutan por el canal ya " +
                    "autorizado; si el enlace no está autorizado quedan bloqueadas, nunca simuladas.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (recent.isNotEmpty()) {
                HorizontalDivider()
                Text("Últimas automatizaciones",
                    style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                for (event in recent) {
                    Text(
                        "${event.componentId} · ${event.action.label} · " +
                            "${statusLabel(event.status)} (${formatValue(event.value)})",
                        style = MaterialTheme.typography.bodySmall,
                        color = when (event.status) {
                            AutomationEventStatus.DISPATCHED -> MaterialTheme.colorScheme.primary
                            AutomationEventStatus.FAILED, AutomationEventStatus.BLOCKED ->
                                MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        })
                }
            }
            if (rules.any { !it.enabled && it.isHardware }) {
                Text("Las acciones sobre hardware están desactivadas hasta que el usuario las habilite.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun automationActionLabel(rule: com.rgbcontroller.core.components.automation.AutomationRule): String =
    if (rule.isHardware) rule.action.label else rule.action.label

private fun statusLabel(status: AutomationEventStatus): String = when (status) {
    AutomationEventStatus.NOTIFIED -> "avisada"
    AutomationEventStatus.PENDING -> "pendiente"
    AutomationEventStatus.DISPATCHED -> "ejecutada"
    AutomationEventStatus.BLOCKED -> "bloqueada (sin autorización)"
    AutomationEventStatus.FAILED -> "fallida"
}

@Composable
private fun TelemetryChartsSection(state: ComponentDashboardState) {
    val now = System.currentTimeMillis()
    val live = state.telemetryFreshness == ComponentStatusFreshness.LIVE
    val disconnected = !state.connected
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TelemetryChartCard(
            title = "Temperatura ambiental (DHT22)",
            geometry = TelemetryChart.build(
                "dht22", "temperature", state.series("dht22", "temperature"),
                nowMs = now, disconnected = disconnected),
            live = live
        )
        TelemetryChartCard(
            title = "Humedad ambiental (DHT22)",
            geometry = TelemetryChart.build(
                "dht22", "humidity", state.series("dht22", "humidity"),
                nowMs = now, disconnected = disconnected),
            live = live
        )
        TelemetryChartCard(
            title = "Humedad del suelo",
            geometry = TelemetryChart.build(
                "soil_moisture", "soil_moisture", state.series("soil_moisture", "soil_moisture"),
                nowMs = now, disconnected = disconnected),
            live = live
        )
    }
}

@Composable
private fun TelemetryHistoryCard(state: ComponentDashboardState) {
    val history = state.deviceHistory
    Card(Modifier.fillMaxWidth().testTag("component_telemetry_history")) {
        Column(Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("${history.size} muestras · dispositivo ${state.deviceId ?: "—"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (history.isEmpty()) {
                Text("Sin historial local todavía. Nada se simula sin lecturas reales.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                return@Column
            }
            val series = history.groupBy { it.componentId to it.measurementType }
                .toSortedMap(compareBy({ it.first }, { it.second }))
            for ((key, samples) in series) {
                val (componentId, type) = key
                val valid = samples.count { it.isPlottable }
                val last = samples.maxByOrNull { it.recordedAtMs }
                Text(
                    "$componentId · $type · ${samples.size} muestras ($valid válidas)",
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodySmall)
                Text(
                    "Último: ${last?.value?.let { formatValue(it) } ?: "sin valor"} " +
                        "${last?.unit ?: ""} · ${last?.status ?: "—"} · ${formatTime(last?.recordedAtMs)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun formatValue(value: Float): String {
    val rounded = (value * 100f).toInt() / 100f
    return rounded.toString()
}

/** Human label for a firmware component lifecycle state. */
private fun firmwareStateLabel(state: String): String = when (state) {
    "active" -> "activo"
    "error" -> "error"
    "unsupported" -> "no soportado"
    "disconnected" -> "desconectado"
    "configured" -> "configurado"
    "detected" -> "detectado"
    "catalog" -> "solo catálogo"
    else -> state
}

/** Human label for a firmware measurement status; never upgrades an invalid one. */
private fun telemetryStatusLabel(status: String): String = when (status) {
    "fresh" -> "fresco"
    "stale" -> "obsoleto"
    "invalid" -> "inválido"
    "sensor_error" -> "error del sensor"
    "not_calibrated" -> "sin calibrar"
    "absent" -> "ausente"
    else -> status
}

@Composable
private fun DisabledTemplateCard(descriptor: HardwareComponentDescriptor) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("template_${descriptor.id}"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(descriptor.name, fontWeight = FontWeight.SemiBold)
            Text("Plantilla · no detectado por el firmware",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(descriptor.description, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun formatTime(epochMs: Long?): String {
    if (epochMs == null || epochMs <= 0L) return "—"
    return SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(epochMs))
}