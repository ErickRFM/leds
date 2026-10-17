package com.rgbcontroller.feature.modules

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Tune
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
import com.rgbcontroller.core.ble.BleConnectionState
import com.rgbcontroller.core.ble.BleManager
import com.rgbcontroller.core.components.model.ComponentMeasurement
import com.rgbcontroller.core.components.model.ComponentRuntimeStatus
import com.rgbcontroller.core.components.model.ComponentTelemetryCodec
import com.rgbcontroller.core.components.registry.ComponentCatalog
import com.rgbcontroller.core.components.registry.PinAssignmentPolicy
import com.rgbcontroller.core.components.status.ComponentDashboardState
import com.rgbcontroller.core.components.status.ComponentStatusFreshness
import com.rgbcontroller.core.components.status.LocalDesiredComponent
import com.rgbcontroller.core.components.status.LocalDesiredConfig
import com.rgbcontroller.core.components.widgets.HumidityTemperatureWidget
import com.rgbcontroller.core.components.widgets.SoilMoistureWidget
import com.rgbcontroller.core.data.PreferencesRepository
import com.rgbcontroller.core.wifi.CompiLabSession
import com.rgbcontroller.core.wifi.LocalLabClient
import kotlinx.coroutines.CancellationException
import com.rgbcontroller.core.wifi.LabConnectionState
import com.rgbcontroller.core.wifi.LabTransport
import com.rgbcontroller.feature.hardwarelab.ComponentDashboardViewModel
import com.rgbcontroller.feature.led.LedModuleScreen
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

/**
 * Component-driven workspace. Firmware reports are read-only; edited wiring is
 * always a draft until a future supported firmware apply/ack protocol exists.
 * Never report BLE RGB readiness as general GPIO/sensor authorization.
 */
@Composable
fun ModuleWorkspaceScreen(
    bleManager: BleManager,
    preferencesRepository: PreferencesRepository,
    session: CompiLabSession,
    onConnect: () -> Unit,
    onHardware: () -> Unit
) {
    val context = LocalContext.current
    val vm: ComponentDashboardViewModel = viewModel(
        factory = remember(session, bleManager) {
            ComponentDashboardViewModel.factory(context.applicationContext, session, bleManager)
        }
    )
    val state by vm.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    val bleConnection by bleManager.connectionState.collectAsState()
    val bleReady = bleConnection == BleConnectionState.Ready
    val wifiAuthorized = session.transport == LabTransport.WIFI_LAB &&
        session.connectionState == LabConnectionState.AUTHORIZED && session.wifiReady
    var showLibrary by rememberSaveable { mutableStateOf(false) }
    var openedId by rememberSaveable { mutableStateOf<String?>(null) }
    var editingId by remember { mutableStateOf<String?>(null) }
    var applyMessage by remember { mutableStateOf<String?>(null) }
    var applying by remember { mutableStateOf(false) }
    val labClient = remember(context) { LocalLabClient(context.applicationContext) }

    LaunchedEffect(Unit) {
        vm.loadPersisted()
        vm.onConnectionChanged()
    }
    LaunchedEffect(session.connectionState, session.transport, bleReady) {
        vm.onConnectionChanged()
    }
    // Foreground only: refresh real sensor values while this destination is visible.
    // Cancellation on navigation avoids background Wi-Fi traffic/battery drain.
    LaunchedEffect(wifiAuthorized) {
        if (wifiAuthorized) {
            while (true) {
                delay(7_500L)
                vm.refresh()
            }
        }
    }

    val currentlyVerified = state.freshness == ComponentStatusFreshness.LIVE &&
        state.authorityError == null && state.connected
    val confirmed = (state.authoritative ?: state.persisted).orEmpty()
    // Catalog-only entries mean the driver was compiled, not that a sensor
    // is physically present. Never auto-create a user's module from them.
    val names = confirmed.filter { it.active || it.state != "catalog" }
        .map { ComponentCatalog.catalogIdForFirmwareId(it.id) }
    // A BLE Ready state confirms access to the existing RGB protocol, not
    // general sensor/GPIO firmware leases. Expose only the RGB module in BLE mode.
    val configured = (state.desiredConfig.components.map { it.id } + names +
        (if (bleReady) listOf("led_rgb") else emptyList())).distinct()
    val runtime = { id: String -> confirmed.firstOrNull {
        ComponentCatalog.catalogIdForFirmwareId(it.id) == id
    } }

    if (openedId != null) {
        val id = openedId!!
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { openedId = null }, modifier = Modifier.testTag("module_back")) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Volver a módulos")
                }
                Text(ComponentCatalog.get(id)?.name ?: id,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f))
                TextButton(
                    onClick = { editingId = id },
                    modifier = Modifier.testTag("module_edit_pins")
                ) { Icon(Icons.Default.Tune, null); Text("GPIO") }
            }
            if (id == "led_rgb") {
                // Reuse the *real* V16 BLE/Wi-Fi control path; no fake RGB widget.
                Box(Modifier.weight(1f)) {
                    LedModuleScreen(bleManager, preferencesRepository, session,
                        onOpenDevices = onConnect)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().testTag("module_detail_list"),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        ModuleRuntimeCard(id, runtime(id), currentlyVerified)
                    }
                    val savedPins = state.desiredConfig.components
                        .firstOrNull { it.id == id }?.pins
                    val physicalPins = runtime(id)?.pins
                    val shownPins = physicalPins?.takeIf {
                        it.size == PinAssignmentPolicy.profile(id)?.signals?.size
                    } ?: savedPins
                    if (shownPins != null && PinAssignmentPolicy.profile(id) != null) {
                        item(key = "module_visual_$id") {
                            ModuleWiringDiagram(
                                profile = PinAssignmentPolicy.profile(id)!!,
                                pins = shownPins,
                                firmwareConfirmed = physicalPins == shownPins && currentlyVerified
                            )
                        }
                    }
                    item { ModuleReadings(id, state) }
                    item {
                        OutlinedButton(onClick = { editingId = id }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Tune, null); Spacer(Modifier.width(8.dp))
                            Text("Reasignar conexiones")
                        }
                    }
                    item {
                        Text("Las conexiones editadas son locales hasta que el firmware permita " +
                            "aplicarlas y confirme el nuevo estado.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("module_workspace_list"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text("Módulos", style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold)
                Text("Tu espacio se adapta a los componentes conectados.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("modules_connection"),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (wifiAuthorized || bleReady) Icons.Default.CheckCircle
                                else Icons.Default.Bluetooth, contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text(when {
                                wifiAuthorized -> "ESP32 · Wi-Fi autorizado"
                                bleReady -> "ESP32 · Bluetooth conectado"
                                else -> "ESP32 desconectado"
                            }, style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f))
                        }
                        Text(
                            if (wifiAuthorized) "Las lecturas requieren respuesta del firmware."
                            else if (bleReady) "BLE RGB conectado. Los GPIO y sensores requieren una sesión Wi-Fi autorizada."
                            else "Conecta tu ESP32 o prepara un módulo sin conexión.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = onConnect,
                                modifier = Modifier.weight(1f).testTag("modules_connect")) {
                                Text("Conectar")
                            }
                            OutlinedButton(onClick = onHardware, modifier = Modifier.weight(1f)) {
                                Text("Hardware")
                            }
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !showLibrary, onClick = { showLibrary = false },
                        label = { Text("Mis módulos") },
                        modifier = Modifier.testTag("modules_tab_mine")
                    )
                    FilterChip(
                        selected = showLibrary, onClick = { showLibrary = true },
                        label = { Text("Biblioteca") },
                        modifier = Modifier.testTag("modules_tab_catalog")
                    )
                }
            }
            if (showLibrary) {
                item {
                    Text("Elige qué conectaste",
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                items(ComponentCatalog.components, key = { "catalog_${it.id}" }) { descriptor ->
                    Card(onClick = { editingId = descriptor.id },
                        modifier = Modifier.fillMaxWidth().testTag("module_catalog_${descriptor.id}")) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(descriptor.name, style = MaterialTheme.typography.titleSmall)
                                Text(descriptor.description, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    PinAssignmentPolicy.profile(descriptor.id)?.signals
                                        ?.joinToString(" · ") { it.label } ?: "Sin perfil disponible",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary)
                            }
                            Icon(Icons.Default.Add, contentDescription = "Configurar")
                        }
                    }
                }
            } else {
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Tus componentes", style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        TextButton(onClick = { showLibrary = true },
                            modifier = Modifier.testTag("modules_add")) {
                            Icon(Icons.Default.Add, null); Text("Agregar")
                        }
                    }
                }
                if (configured.isEmpty()) {
                    item {
                        Card(Modifier.fillMaxWidth().testTag("modules_empty")) {
                            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Aún no hay módulos configurados",
                                    style = MaterialTheme.typography.titleMedium)
                                Text("Abre la biblioteca para indicar qué conectaste y qué GPIO utiliza.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyMedium)
                                Button(onClick = { showLibrary = true }) { Text("Abrir biblioteca") }
                            }
                        }
                    }
                }
                items(configured, key = { "module_$it" }) { id ->
                    val descriptor = ComponentCatalog.get(id)
                    val physical = runtime(id)
                    val local = state.desiredConfig.components.firstOrNull { it.id == id }
                    Card(
                        onClick = { openedId = id },
                        modifier = Modifier.fillMaxWidth().testTag("module_card_$id"),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Row(Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(descriptor?.name ?: id, fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.titleMedium)
                                Text(
                                    when {
                                        physical != null && currentlyVerified ->
                                            "Firmware: ${physical.state} · ${if (physical.active) "activo" else "no activo"}"
                                        physical != null -> "Última confirmación del firmware · no en vivo"
                                        bleReady && id == "led_rgb" -> "Control BLE RGB disponible · GPIO sin confirmar"
                                        else -> "Borrador local · sin confirmar"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "GPIO ${(physical?.pins ?: local?.pins).orEmpty().joinToString().ifBlank { "sin asignar" }}",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                            IconButton(onClick = { editingId = id },
                                modifier = Modifier.testTag("module_config_$id")) {
                                Icon(Icons.Default.Tune, contentDescription = "Editar GPIO de $id")
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = null)
                        }
                    }
                }
                if (state.authorityError != null) {
                    item {
                        Text(state.authorityError!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }

    editingId?.let { id ->
        val draft = state.desiredConfig.components.firstOrNull { it.id == id }
        val current = runtime(id)
        val effectiveDraft = draft ?: current?.let {
            LocalDesiredComponent(id, it.type, it.pins)
        }
        ModulePinWizard(
            componentId = id,
            canApply = wifiAuthorized && currentlyVerified && current != null &&
                id in setOf("dht22", "soil_moisture", "ssd1306_oled"),
            applying = applying,
            applyMessage = applyMessage,
            onApply = { pins ->
                scope.launch {
                    applying = true
                    applyMessage = null
                    try {
                        check(wifiAuthorized) { "Se perdió la autorización Wi-Fi." }
                        val revision = session.revision
                        val key = session.key
                        session.serialized {
                            check(wifiAuthorized && session.revision == revision) {
                                "La sesión cambió antes de aplicar GPIO."
                            }
                            labClient.applyComponentPinsConfirmed(key, id, pins)
                        }
                        check(wifiAuthorized && session.revision == revision) {
                            "Sesión cambiada: vuelve a verificar los GPIO del firmware."
                        }
                        vm.refresh()
                        applyMessage = "Pines confirmados por el firmware. Solo vigentes hasta reiniciar el ESP32."
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Exception) {
                        applyMessage = "No aplicado: ${failure.message ?: "verifica el ESP32 y sus conexiones"}"
                        vm.refresh()
                    } finally {
                        applying = false
                    }
                }
            },
            initial = effectiveDraft,
            otherLocal = state.desiredConfig.components,
            reported = state.authoritative.orEmpty(),
            onDismiss = { if (!applying) { editingId = null; applyMessage = null } },
            onSave = { pins ->
                val desc = ComponentCatalog.get(id)
                if (desc != null) {
                    val next = state.desiredConfig.components.filterNot { it.id == id } +
                        LocalDesiredComponent(id, desc.type.name.lowercase(), pins)
                    scope.launch { vm.setLocalDesired(LocalDesiredConfig(next)) }
                }
                editingId = null
                applyMessage = null
                showLibrary = false
            },
            onRemove = if (draft == null) null else {
                {
                    scope.launch {
                        vm.setLocalDesired(LocalDesiredConfig(
                            state.desiredConfig.components.filterNot { it.id == id }
                        ))
                    }
                    editingId = null
                }
            }
        )
    }
}

@Composable
private fun ModuleRuntimeCard(id: String, runtime: ComponentRuntimeStatus?, live: Boolean) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("Conexiones y estado", style = MaterialTheme.typography.titleMedium)
            Text(
                when {
                    runtime == null -> "Sin confirmación del firmware para ${id}."
                    live -> "Estado confirmado: ${runtime.state}"
                    else -> "Último estado confirmado (sin conexión en vivo): ${runtime.state}"
                },
                style = MaterialTheme.typography.bodyMedium
            )
            Text("GPIO reales: ${runtime?.pins?.joinToString() ?: "no disponibles"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ModuleReadings(id: String, state: ComponentDashboardState) {
    val live = state.telemetryFreshness == ComponentStatusFreshness.LIVE &&
        state.authorityError == null && state.connected
    val measurements = state.authoritativeTelemetry ?: state.persistedTelemetry
    fun reading(type: String): ComponentMeasurement? =
        ComponentTelemetryCodec.find(measurements.orEmpty(), type)
            ?.takeIf { it.valid && it.status == "fresh" }
    when (id) {
        "dht22" -> HumidityTemperatureWidget(
            humidity = reading("humidity")?.value,
            temperature = reading("temperature")?.value,
            hasData = reading("humidity") != null && reading("temperature") != null,
            live = live, status = if (measurements == null) "sin lecturas" else null
        )
        "soil_moisture" -> SoilMoistureWidget(
            moisturePercent = reading("soil_moisture")?.value,
            rawAdc = reading("adc_raw")?.value?.toInt(),
            calibrated = reading("soil_moisture") != null, live = live,
            hasData = reading("soil_moisture") != null || reading("adc_raw") != null
        )
        else -> Card(Modifier.fillMaxWidth()) {
            Text("Este componente todavía no reporta lecturas compatibles con la app.",
                modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** A guided, per-signal GPIO editor. Saves a LOCAL draft, never firmware changes. */
@Composable
private fun ModulePinWizard(
    componentId: String,
    initial: LocalDesiredComponent?,
    otherLocal: List<LocalDesiredComponent>,
    reported: List<ComponentRuntimeStatus>,
    canApply: Boolean,
    applying: Boolean,
    applyMessage: String?,
    onApply: (List<Int>) -> Unit,
    onDismiss: () -> Unit,
    onSave: (List<Int>) -> Unit,
    onRemove: (() -> Unit)?
) {
    val profile = PinAssignmentPolicy.profile(componentId)
    if (profile == null) {
        AlertDialog(onDismissRequest = onDismiss,
            title = { Text("Perfil no disponible") },
            text = { Text("Este componente requiere un perfil compatible antes de reasignar GPIO.") },
            confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } })
    } else {
    var pins by remember(componentId, initial) {
        mutableStateOf(initial?.pins?.takeIf { it.size == profile.signals.size } ?: profile.defaults)
    }
    var expanded by remember { mutableIntStateOf(-1) }
    val issues = PinAssignmentPolicy.validate(componentId, pins, otherLocal, reported)
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("module_pin_editor"),
        title = { Text("Conexiones · ${ComponentCatalog.get(componentId)?.name ?: componentId}") },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 410.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Selecciona las señales. Guardar conserva el borrador; aplicar requiere ESP32 autorizado y módulo activo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                for ((index, signal) in profile.signals.withIndex()) {
                    Text(signal.label, style = MaterialTheme.typography.labelLarge)
                    Box {
                        OutlinedButton(onClick = { expanded = index },
                            modifier = Modifier.fillMaxWidth().testTag("module_pin_signal_$index")) {
                            Text("GPIO ${pins[index]}", modifier = Modifier.weight(1f))
                            Icon(Icons.Default.Tune, null)
                        }
                        DropdownMenu(expanded = expanded == index, onDismissRequest = { expanded = -1 }) {
                            for (candidate in signal.allowedPins.sorted()) {
                                DropdownMenuItem(
                                    text = { Text("GPIO $candidate") },
                                    onClick = {
                                        pins = pins.toMutableList().also { it[index] = candidate }
                                        expanded = -1
                                    }
                                )
                            }
                        }
                    }
                }
                ModuleWiringDiagram(profile, pins, firmwareConfirmed = false,
                    modifier = Modifier.testTag("module_pin_preview"))
                for (issue in issues) {
                    Text(issue, color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.testTag("module_pin_warning"))
                }
                if (applyMessage != null) {
                    Text(applyMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (applyMessage.startsWith("No aplicado")) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.testTag("module_apply_result"))
                }
                if (onRemove != null) {
                    TextButton(onClick = onRemove, modifier = Modifier.testTag("module_pin_remove")) {
                        Text("Quitar configuración local")
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    onClick = { onSave(pins) },
                    enabled = issues.isEmpty() && !applying,
                    modifier = Modifier.testTag("module_pin_save")
                ) { Text("Guardar borrador") }
                if (canApply) {
                    Button(
                        onClick = { onApply(pins) },
                        enabled = issues.isEmpty() && !applying,
                        modifier = Modifier.testTag("module_pin_apply")
                    ) { Text(if (applying) "Aplicando…" else "Activar / aplicar en ESP32") }
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
    } // Keep the wizard composition balanced when switching component profiles.
}
