package com.rgbcontroller.feature.hardwarelab

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rgbcontroller.core.ble.BleManager
import com.rgbcontroller.core.components.alerts.AlertComparison
import com.rgbcontroller.core.components.calibration.SoilLiveCapturePolicy
import com.rgbcontroller.core.components.alerts.AlertSeverity
import com.rgbcontroller.core.components.automation.AutomationEventStatus
import com.rgbcontroller.core.components.model.ComponentRuntimeStatus
import com.rgbcontroller.core.components.model.ComponentState
import com.rgbcontroller.core.components.model.ComponentStatusCodec
import com.rgbcontroller.core.components.editors.LabConfigValidation
import com.rgbcontroller.core.components.model.ComponentTelemetryCodec
import com.rgbcontroller.core.components.telemetry.TelemetryChart
import com.rgbcontroller.core.components.widgets.TelemetryChartCard
import com.rgbcontroller.core.components.model.HardwareComponentDescriptor
import com.rgbcontroller.core.components.registry.ComponentCatalog
import com.rgbcontroller.core.components.registry.PinAssignmentPolicy
import com.rgbcontroller.core.components.status.ComponentDashboardState
import com.rgbcontroller.core.components.status.ComponentStatusFreshness
import com.rgbcontroller.core.components.status.OledRuntime
import com.rgbcontroller.core.components.status.LocalDesiredComponent
import com.rgbcontroller.core.components.status.LocalDesiredConfig
import com.rgbcontroller.core.components.widgets.HumidityTemperatureWidget
import com.rgbcontroller.core.components.widgets.OledDisplayWidget
import com.rgbcontroller.core.components.widgets.SoilMoistureWidget
import com.rgbcontroller.core.export.CompatibilityLevel
import com.rgbcontroller.core.export.CompatibilityReport
import com.rgbcontroller.core.export.ProjectExportEnvelope
import com.rgbcontroller.core.wifi.LocalLabClient
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
private enum class HardwareSection(val label: String) {
    STATUS("Estado"), SENSORS("Sensores"), PINS("Conexiones"),
    AUTOMATION("Automatización"), PROJECT("Proyecto")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HardwareLabScreen(
    session: CompiLabSession? = null,
    bleManager: BleManager? = null,
    onOpenLabTools: () -> Unit = {}
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
    var showSoilWizard by remember { mutableStateOf(false) }
    var showConfigEditor by remember { mutableStateOf(false) }
    var showSensorDetails by rememberSaveable { mutableStateOf(false) }
    var showPinTemplates by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    var section by rememberSaveable { mutableStateOf(HardwareSection.STATUS) }
    var syncMessage by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { scope.launch { viewModel.exportTo(it.toString()) } }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { scope.launch { viewModel.readImport(it.toString()) } }
    }

    val sessionConnection = session?.connectionState
    val authorized = sessionConnection == LabConnectionState.AUTHORIZED

    LaunchedEffect(Unit) { viewModel.loadPersisted() }
    LaunchedEffect(sessionConnection) {
        viewModel.onConnectionChanged()
        syncMessage = if (authorized) "Estado sincronizado desde el firmware."
        else "Sin conexión autorizada: se muestra el último estado confirmado."
    }

    val liveRawAdc = SoilLiveCapturePolicy.currentRawAdc(state, authorized)
    val configIssues = LabConfigValidation.validateDesired(state.desiredConfig.components)
    val confirmed = state.firmwareStatus?.components
    val lastKnown = state.lastKnownStatus?.components
    val freshness = state.freshness
    val knownIds = state.confirmedCatalogIds
    val missingTemplates = ComponentCatalog.components.filter { it.id !in knownIds }

    Scaffold(
        topBar = {
            Column {
            TopAppBar(
                title = { Text("Hardware Lab") },
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
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag("hardware_tabs"),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(HardwareSection.entries) { target ->
                    FilterChip(
                        selected = section == target,
                        onClick = {
                            section = target
                            scope.launch { listState.scrollToItem(0) }
                        },
                        label = { Text(target.label) },
                        modifier = Modifier.testTag("hardware_tab_${target.name.lowercase()}")
                    )
                }
            }
            HorizontalDivider()
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding)
                .padding(horizontal = 16.dp).testTag("component_dashboard_list"),
            state = listState,
            contentPadding = PaddingValues(top = 10.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "hardware_authority") {
                AuthorityCard(connected = state.connected, authorized = authorized,
                    error = state.authorityError)
            }
            when (section) {
                HardwareSection.STATUS -> {
                    item(key = "hardware_confirmed_header") {
                        Text("Estado confirmado por el firmware",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold)
                    }
                    when {
                        freshness == ComponentStatusFreshness.LIVE && confirmed != null -> {
                            item(key = "hardware_live_notice") {
                                Text("En vivo · actualizado ${formatTime(state.firmwareStatus?.receivedAtMs)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.testTag("component_live"))
                            }
                            items(confirmed, key = { "firmware_${it.id}" }) { component ->
                                FirmwareComponentCard(component = component, live = true)
                            }
                        }
                        freshness == ComponentStatusFreshness.LAST_KNOWN && lastKnown != null -> {
                            item(key = "hardware_cached_notice") {
                                Text("Último estado confirmado ${formatTime(state.lastKnownStatus?.confirmedAtMs)} · no es telemetría en vivo",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.testTag("component_last_known"))
                            }
                            items(lastKnown, key = { "cached_${it.id}" }) { component ->
                                FirmwareComponentCard(component = component, live = false)
                            }
                        }
                        else -> {
                            item(key = "hardware_no_report") {
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
                    syncMessage?.let { message ->
                        item(key = "hardware_sync_message") {
                            Text(message, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    item(key = "hardware_shortcuts") {
                        Card(Modifier.fillMaxWidth().testTag("hardware_quick_actions")) {
                            Column(Modifier.fillMaxWidth().padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Accesos rápidos", style = MaterialTheme.typography.titleMedium)
                                Button(onClick = {
                                    section = HardwareSection.SENSORS
                                    scope.launch { listState.scrollToItem(0) }
                                }, modifier = Modifier.fillMaxWidth()) { Text("Ver sensores") }
                                OutlinedButton(onClick = {
                                    section = HardwareSection.PINS
                                    scope.launch { listState.scrollToItem(0) }
                                }, modifier = Modifier.fillMaxWidth()) { Text("Configurar GPIO") }
                                OutlinedButton(
                                    onClick = onOpenLabTools,
                                    modifier = Modifier.fillMaxWidth().testTag("hardware_open_lab_tools")
                                ) { Text("Laboratorio avanzado · GPIO, diagnóstico y experimentos") }
                            }
                        }
                    }
                }
                HardwareSection.SENSORS -> {
                    item(key = "hardware_sensors_title") {
                        Text("Lecturas reales de sensores",
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    item(key = "hardware_dht22") { Dht22TelemetryCard(state = state) }
                    item(key = "hardware_soil_calibration") {
                        SoilCalibrationCard(
                            state = state,
                            onCalibrate = { showSoilWizard = true },
                            onClear = { scope.launch { viewModel.clearSoilCalibration() } }
                        )
                    }
                    item(key = "hardware_soil_sensor") { SoilTelemetryCard(state = state) }
                    item(key = "hardware_oled_title") {
                        Text("Pantalla OLED",
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    item(key = "hardware_oled_card") { OledStatusCard(state = state, session = session) }
                    item(key = "hardware_sensor_more") {
                        TextButton(
                            onClick = { showSensorDetails = !showSensorDetails },
                            modifier = Modifier.testTag("component_advanced_toggle")
                        ) { Text(if (showSensorDetails) "Ocultar gráficas e historial" else "Ver gráficas e historial") }
                    }
                    if (showSensorDetails) {
                        item(key = "hardware_charts") { TelemetryChartsSection(state = state) }
                        item(key = "hardware_history_header") {
                            Text("Historial de telemetría (local)",
                                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        item(key = "hardware_history") { TelemetryHistoryCard(state = state) }
                    }
                }
                HardwareSection.PINS -> {
                    item(key = "hardware_pins_header") {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Conexiones GPIO", style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(onClick = { showConfigEditor = true },
                                    modifier = Modifier.testTag("component_config_edit")) {
                                    Text("Editar")
                                }
                                TextButton(onClick = { showCatalog = true },
                                    modifier = Modifier.testTag("component_add")) {
                                    Icon(Icons.Default.Add, null); Text("Agregar")
                                }
                            }
                        }
                    }
                    item(key = "hardware_desired_warning") {
                        Text("Asignación guardada localmente: el firmware no ha aceptado " +
                            "estos cambios hasta confirmar por una API compatible.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (configIssues.isNotEmpty()) {
                        item(key = "hardware_config_issues") {
                            ConfigValidationIssues(components = state.desiredConfig.components)
                        }
                    }
                    if (state.desiredConfig.components.isEmpty()) {
                        item(key = "hardware_config_empty") {
                            Card(Modifier.fillMaxWidth()) {
                                Text("Sin conexiones configuradas localmente",
                                    modifier = Modifier.padding(16.dp),
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    } else {
                        items(state.desiredConfig.components, key = { "desired_${it.id}" }) { desired ->
                            DesiredComponentCard(desired = desired, confirmedCatalogIds = knownIds)
                        }
                    }
                    item(key = "hardware_templates_toggle") {
                        TextButton(onClick = { showPinTemplates = !showPinTemplates },
                            modifier = Modifier.testTag("component_advanced_toggle")) {
                            Text(if (showPinTemplates) "Ocultar plantillas" else "Ver plantillas no detectadas")
                        }
                    }
                    if (showPinTemplates && missingTemplates.isNotEmpty()) {
                        items(missingTemplates, key = { "template_${it.id}" }) { descriptor ->
                            DisabledTemplateCard(descriptor = descriptor)
                        }
                    }
                }
                HardwareSection.AUTOMATION -> {
                    item(key = "hardware_alerts_title") {
                        Text("Alertas", style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold)
                    }
                    item(key = "hardware_alerts") {
                        AlertsCard(state = state, onEdit = { showAlertEditor = true })
                    }
                    item(key = "hardware_automation_title") {
                        Text("Automatizaciones", style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold)
                    }
                    item(key = "hardware_automation") {
                        AutomationCard(state = state, onEdit = { showAutomationEditor = true })
                    }
                }
                HardwareSection.PROJECT -> {
                    item(key = "hardware_project_title") {
                        Text("Proyecto (importar / exportar)",
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    item(key = "hardware_project_description") {
                        Text("Archivos locales de componentes, alertas, automatizaciones y calibración. " +
                            "Nunca incluyen claves ni tokens.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    state.importError?.let { error ->
                        item(key = "hardware_project_error") {
                            Card(
                                Modifier.fillMaxWidth().testTag("project_import_error"),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer)
                            ) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("No se pudo importar el proyecto",
                                        color = MaterialTheme.colorScheme.onErrorContainer)
                                    Text(error, style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer)
                                    TextButton(onClick = { viewModel.cancelImport() },
                                        modifier = Modifier.testTag("project_import_error_dismiss")) {
                                        Text("Descartar")
                                    }
                                }
                            }
                        }
                    }
                    item(key = "hardware_project_actions") {
                        Card(Modifier.fillMaxWidth().testTag("project_portable")) {
                            Row(Modifier.fillMaxWidth().padding(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = {
                                    exportLauncher.launch("${ComponentDashboardViewModel.DEFAULT_PROJECT_NAME}.json")
                                }, modifier = Modifier.weight(1f).testTag("project_export")) {
                                    Text("Exportar")
                                }
                                OutlinedButton(onClick = {
                                    importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                                }, modifier = Modifier.weight(1f).testTag("project_import")) {
                                    Text("Importar")
                                }
                            }
                        }
                    }
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
                                        pins = PinAssignmentPolicy.profile(desc.id)?.defaults.orEmpty()
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

    if (showSoilWizard) {
        SoilCalibrationCaptureDialog(
            deviceId = state.deviceId,
            storedDry = state.soilCalibration?.dryRaw,
            storedWet = state.soilCalibration?.wetRaw,
            liveRaw = liveRawAdc,
            onSave = { dry, wet ->
                scope.launch { viewModel.setSoilCalibration(dry, wet) }
                showSoilWizard = false
            },
            onDismiss = { showSoilWizard = false }
        )
    }

    if (showConfigEditor) {
        ComponentConfigEditorDialog(
            components = state.desiredConfig.components,
            onSave = { scope.launch { viewModel.setLocalDesired(LocalDesiredConfig(it)) } },
            onDismiss = { showConfigEditor = false }
        )
    }

    state.importPreview?.let { preview ->
        val existingIds = state.desiredConfig.components.map { it.id }.toSet()
        val conflicts = preview.components.map { it.id }.filter { it in existingIds }
        ImportPreviewDialog(
            envelope = preview,
            conflicts = conflicts,
            compatibility = viewModel.compatibilityReport(),
            onImportAsCopy = {
                scope.launch { viewModel.applyImport(ComponentDashboardViewModel.ImportMode.COPY) }
            },
            onReplace = {
                scope.launch { viewModel.applyImport(ComponentDashboardViewModel.ImportMode.REPLACE) }
            },
            onDismiss = { viewModel.cancelImport() }
        )
    }
}

@Composable
private fun ImportPreviewDialog(
    envelope: ProjectExportEnvelope,
    conflicts: List<String>,
    compatibility: CompatibilityReport?,
    onImportAsCopy: () -> Unit,
    onReplace: () -> Unit,
    onDismiss: () -> Unit
) {
    val incompatible = compatibility?.level == CompatibilityLevel.INCOMPATIBLE
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("project_import_preview"),
        title = { Text("Vista previa del proyecto") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Nombre: ${envelope.name}", style = MaterialTheme.typography.bodyMedium)
                Text("Perfil de placa: ${envelope.boardProfile}",
                    style = MaterialTheme.typography.bodySmall)
                Text("Componentes: ${envelope.components.size}",
                    style = MaterialTheme.typography.bodySmall)
                Text("Alertas: ${envelope.alerts.size} · Automatizaciones: ${envelope.automations.size}",
                    style = MaterialTheme.typography.bodySmall)
                if (envelope.calibrations.isNotEmpty()) {
                    Text("Calibraciones: ${envelope.calibrations.size}",
                        style = MaterialTheme.typography.bodySmall)
                }
                compatibility?.let { report ->
                    Text(
                        "Compatibilidad: ${compatibilityLabel(report.level)}" +
                            if (report.remapped.isNotEmpty()) {
                                " (${report.remapped.size} componente(s) reasignado(s))"
                            } else {
                                ""
                            },
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.testTag("project_import_compatibility"),
                        color = if (incompatible) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (conflicts.isNotEmpty()) {
                    Text("Aviso: ${conflicts.size} id(s) ya existen; se importarán como copia.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error)
                }
                Text("No se auto-ejecuta ningún script del archivo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(onClick = onImportAsCopy,
                enabled = !incompatible,
                modifier = Modifier.testTag("project_import_copy")) {
                Text("Importar como copia")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDismiss,
                    modifier = Modifier.testTag("project_import_cancel")) { Text("Cancelar") }
                TextButton(onClick = onReplace,
                    enabled = !incompatible,
                    modifier = Modifier.testTag("project_import_replace")) { Text("Reemplazar") }
            }
        }
    )
}

private fun compatibilityLabel(level: CompatibilityLevel): String = when (level) {
    CompatibilityLevel.COMPATIBLE -> "compatible"
    CompatibilityLevel.REQUIRES_REMAP -> "requiere reasignación de pines"
    CompatibilityLevel.PARTIALLY_COMPATIBLE -> "parcialmente compatible"
    CompatibilityLevel.INCOMPATIBLE -> "incompatible"
    CompatibilityLevel.UNKNOWN -> "placa desconocida"
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
            Text("Pines reales: firmware · cambios locales: borrador.",
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
private fun SoilCalibrationCard(
    state: ComponentDashboardState,
    onCalibrate: () -> Unit,
    onClear: () -> Unit
) {
    val stored = state.soilCalibration
    val active = state.activeSoilCalibration
    Card(Modifier.fillMaxWidth().testTag("component_soil_calibration")) {
        Column(Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()) {
                Text("Calibración de suelo", fontWeight = FontWeight.SemiBold)
                TextButton(onClick = onCalibrate,
                    modifier = Modifier.testTag("component_soil_calibration_start")) {
                    Text("Calibrar")
                }
            }
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
private fun OledStatusCard(state: ComponentDashboardState, session: CompiLabSession?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var textInput by rememberSaveable { mutableStateOf("Hola, soy Compi!") }
    var sending by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf("Selecciona una expresión o escribe texto.") }
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
    val canSend = oled.operative && live && session?.wifiReady == true &&
        session.connectionState == LabConnectionState.AUTHORIZED
    Column(Modifier.fillMaxWidth().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Control OLED · Wi-Fi local autorizado", style = MaterialTheme.typography.titleSmall)
        Text("SDA GPIO21 · SCL GPIO22 · 0x3C. Requiere SSD1306 de 128×64.",
            style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(value = textInput, onValueChange = { textInput = it.take(39) },
            label = { Text("Texto ASCII (máximo 39 caracteres)") },
            singleLine = true, modifier = Modifier.fillMaxWidth().testTag("oled_text_input"))
        fun transmit(mode: String, value: String) {
            val activeSession = session ?: return
            if (!canSend || sending) return
            sending = true
            result = "Enviando comando…"
            scope.launch {
                try {
                    activeSession.userOperation("Pantalla OLED") {
                        LocalLabClient(context.applicationContext)
                            .showOled(activeSession.key, mode, value)
                    }
                    result = "El firmware confirmó el dibujo. Comprueba la pantalla física."
                } catch (failure: Exception) {
                    result = failure.message ?: "No se confirmó la pantalla OLED."
                } finally {
                    sending = false
                }
            }
        }
        Button(enabled = canSend && !sending &&
            textInput.isNotBlank() && textInput.all { it.code in 32..126 },
            onClick = { transmit("text", textInput) },
            modifier = Modifier.testTag("oled_send_text")) { Text("Mostrar mensaje") }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("happy" to "Feliz", "sleepy" to "Sueño",
                "angry" to "Enojo", "surprised" to "Sorpresa").forEach { (code, label) ->
                OutlinedButton(enabled = canSend && !sending,
                    onClick = { transmit("face", code) }) { Text(label) }
            }
        }
        Text(result, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!canSend) {
            Text("Para enviar: habilita el módulo OLED y conecta el SoftAP autorizado. " +
                "Bluetooth todavía no transmite pantallas.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
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
            } else {
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
            } // Non-empty history; no early return from Compose layout.
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