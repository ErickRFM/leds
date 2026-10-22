package com.rgbcontroller.feature.modules

import androidx.activity.compose.BackHandler

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
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.DeviceHub
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rgbcontroller.core.ble.BleConnectionState
import com.rgbcontroller.core.ble.BleSecurityState
import com.rgbcontroller.core.ble.BleManager
import com.rgbcontroller.core.components.model.ComponentMeasurement
import com.rgbcontroller.core.components.model.ComponentRuntimeStatus
import com.rgbcontroller.core.components.model.ComponentTelemetryCodec
import com.rgbcontroller.core.components.registry.ComponentCatalog
import com.rgbcontroller.core.components.registry.ModuleWorkspaceGrouping
import com.rgbcontroller.core.components.registry.PinAssignmentPolicy
import com.rgbcontroller.core.components.status.ComponentDashboardState
import com.rgbcontroller.core.components.status.ModuleEvidence
import com.rgbcontroller.core.components.status.ComponentStatusFreshness
import com.rgbcontroller.core.components.status.LocalDesiredComponent
import com.rgbcontroller.core.components.status.LocalDesiredConfig
import com.rgbcontroller.core.components.widgets.SensorDashboard
import com.rgbcontroller.core.components.widgets.TelemetryChartCard
import com.rgbcontroller.core.components.telemetry.TelemetryChart
import com.rgbcontroller.core.components.widgets.SoilMoistureWidget
import com.rgbcontroller.core.data.PreferencesRepository
import com.rgbcontroller.core.wifi.CompiLabSession
import com.rgbcontroller.core.wifi.LocalLabClient
import com.rgbcontroller.core.wifi.ComponentApplyUnconfirmedException
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
    val bleSecurity by bleManager.securityState.collectAsState()
    val bleAuthorized = session.transport == LabTransport.BLUETOOTH &&
        bleReady && bleSecurity == BleSecurityState.Authorized
    val wifiAuthorized = session.transport == LabTransport.WIFI_LAB &&
        session.connectionState == LabConnectionState.AUTHORIZED && session.wifiReady
    var showLibrary by rememberSaveable { mutableStateOf(false) }
    var openedId by rememberSaveable { mutableStateOf<String?>(null) }
    var editingId by remember { mutableStateOf<String?>(null) }
    var applyMessage by remember { mutableStateOf<String?>(null) }
    var ldrCalibrationMessage by remember { mutableStateOf<String?>(null) }
    var oledActionMessage by remember { mutableStateOf<String?>(null) }
    var applying by remember { mutableStateOf(false) }
    // Physical Back should close a nested component editor/detail before leaving Modules.
    BackHandler(enabled = editingId != null || openedId != null) {
        if (editingId != null) editingId = null else openedId = null
    }
    val labClient = remember(context) { LocalLabClient(context.applicationContext) }

    LaunchedEffect(Unit) {
        vm.loadPersisted()
        vm.onConnectionChanged()
    }
    LaunchedEffect(session.connectionState, session.transport, bleReady, bleSecurity) {
        vm.onConnectionChanged()
    }
    // Foreground only: refresh real sensor values while this destination is visible.
    // Cancellation on navigation avoids background Wi-Fi traffic/battery drain.
    LaunchedEffect(wifiAuthorized, bleAuthorized) {
        if (wifiAuthorized || bleAuthorized) {
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
    // BLE component status is authoritative only after a complete owner-
    // authenticated, sequence-bound snapshot. Bonding alone proves nothing.
    val configured = ModuleWorkspaceGrouping.group(
        (state.desiredConfig.components.map { it.id } + names +
            (if (bleReady) listOf("led_rgb") else emptyList())).distinct()
    )
    val environmentSensor = configured.firstOrNull {
        it.id == ModuleWorkspaceGrouping.ENVIRONMENT
    }?.components?.firstOrNull { it == "dht11" || it == "dht22" } ?: "dht11"
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
                Text(if (id == ModuleWorkspaceGrouping.ENVIRONMENT) "Sensor ambiental"
                     else ComponentCatalog.get(id)?.name ?: id,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f))
                TextButton(
                    onClick = { editingId = if (id == ModuleWorkspaceGrouping.ENVIRONMENT) environmentSensor else id },
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
                    if (id == ModuleWorkspaceGrouping.ENVIRONMENT) {
                        item {
                            Text("${environmentSensor.uppercase()} + sensor de suelo ADC1",
                                style = MaterialTheme.typography.titleMedium)
                        }
                        item(key = "environmental_voice_summary") {
                            EnvironmentalSpeechCard(state)
                        }
                        item(key = "environmental_audio_library") {
                            CompiAudioPlayerCard()
                        }
                        for (child in listOf(environmentSensor, "soil_moisture")) {
                            item(key = "env_status_$child") {
                                ModuleRuntimeCard(child, runtime(child), currentlyVerified,
                                    ModuleEvidence.dhtResponding(state, child))
                            }
                            val draftPins = state.desiredConfig.components
                                .firstOrNull { it.id == child }?.pins
                            val actualPins = runtime(child)?.pins
                            val shown = actualPins ?: draftPins
                            if (shown != null && PinAssignmentPolicy.profile(child) != null) {
                                item(key = "env_wiring_$child") {
                                    ModuleWiringDiagram(
                                        PinAssignmentPolicy.profile(child)!!,
                                        shown, firmwareConfirmed = actualPins == shown && currentlyVerified
                                    )
                                }
                            }
                            item(key = "env_readings_$child") {
                                ModuleReadings(child, state, onRefresh = { scope.launch { vm.refresh() } })
                            }
                            item(key = "env_edit_$child") {
                                OutlinedButton(onClick = { editingId = child },
                                    modifier = Modifier.fillMaxWidth()
                                        .testTag("environment_config_$child")) {
                                    Text("Reasignar GPIO de ${ComponentCatalog.get(child)?.name ?: child}")
                                }
                            }
                        }
                    } else {
                    item {
                        ModuleRuntimeCard(id, runtime(id), currentlyVerified,
                            ModuleEvidence.dhtResponding(state, id))
                    }
                    // Real measurements and history must be visible before GPIO wiring.
                    item(key = "module_metrics_$id") {
                        ModuleReadings(
                            id, state, onRefresh = { scope.launch { vm.refresh() } },
                            canCalibrateLdr = wifiAuthorized || bleAuthorized,
                            calibrationMessage = ldrCalibrationMessage,
                            canControlOled = wifiAuthorized,
                            oledActionMessage = oledActionMessage,
                            onOledFace = { face ->
                                scope.launch {
                                    ldrCalibrationMessage = null
                                    try {
                                        val revision = session.revision
                                        check(wifiAuthorized) { "Conecta al Wi-Fi autorizado." }
                                        val key = session.key
                                        session.serialized {
                                            check(wifiAuthorized && session.revision == revision) {
                                                "La conexión cambió; no se envió la expresión."
                                            }
                                            labClient.showOled(key, "face", face)
                                        }
                                        oledActionMessage = "Carita $face confirmada por el ESP32."
                                    } catch (cancelled: CancellationException) {
                                        throw cancelled
                                    } catch (failure: Exception) {
                                        oledActionMessage = "OLED: ${failure.message}"
                                    }
                                }
                            },
                            onCalibrateLdr = { dark, bright ->
                                scope.launch {
                                    ldrCalibrationMessage = "Aplicando calibración en el ESP32…"
                                    try {
                                        val revision = session.revision
                                        if (bleAuthorized) {
                                            check(bleManager.calibrateLdrBle(dark, bright)) {
                                                "El propietario BLE no recibió confirmación de calibración."
                                            }
                                            check(bleAuthorized && session.revision == revision) {
                                                "La sesión BLE cambió; vuelve a comprobar el estado."
                                            }
                                        } else {
                                            check(wifiAuthorized) { "Conecta al Wi-Fi autorizado." }
                                            val key = session.key
                                            session.serialized {
                                                check(wifiAuthorized && session.revision == revision) {
                                                    "Cambió la conexión antes de calibrar."
                                                }
                                                labClient.calibrateLdr(key, dark, bright)
                                            }
                                            check(wifiAuthorized && session.revision == revision) {
                                                "La sesión cambió; vuelve a comprobar la calibración."
                                            }
                                        }
                                        delay(650L)
                                        vm.refresh()
                                        ldrCalibrationMessage = "Calibración guardada por el ESP32 en memoria persistente. " +
                                            "Verifica el porcentaje en vivo y tras reiniciar."
                                    } catch (cancelled: CancellationException) {
                                        throw cancelled
                                    } catch (failure: Exception) {
                                        ldrCalibrationMessage = "No se calibró: ${failure.message}"
                                    }
                                }
                            }
                        )
                    }
                    if (id == "dht11" || id == "dht22" || id == "ldr_light") {
                        item(key = "module_voice_$id") { EnvironmentalSpeechCard(state) }
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
                                firmwareConfirmed = physicalPins == shownPins && currentlyVerified &&
                                    runtime(id)?.active == true
                            )
                        }
                    }
                    item {
                        OutlinedButton(onClick = { editingId = id }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Tune, null); Spacer(Modifier.width(8.dp))
                            Text("Reasignar conexiones")
                        }
                    }
                    }
                    item {
                        Text("Borrador local · aplica los cambios desde el editor GPIO.",
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
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Módulos", style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold)
                    }
                    Surface(color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(12.dp)) {
                        Text("${configured.size} módulos",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
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
                                bleAuthorized -> "ESP32 · Bluetooth propietario autorizado"
                                bleReady -> "ESP32 · Bluetooth conectado (sin autorización)"
                                else -> "ESP32 desconectado"
                            }, style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f))
                        }
                        Text(
                            if (wifiAuthorized) "GPIO y sensores: firmware."
                            else if (bleAuthorized)
                                "Sensores y módulos: lectura BLE autenticada. Calibración LDR: BLE o Wi-Fi autorizado; cambios GPIO según soporte del firmware."
                            else if (bleReady) "Bluetooth conectado; autoriza propietario para leer sensores."
                            else "Sin conexión · puedes preparar módulos.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = onConnect,
                                modifier = Modifier.weight(1f).testTag("modules_connect")) {
                                Text(ModuleEvidence.connectionAction(wifiAuthorized, bleReady))
                            }
                            OutlinedButton(onClick = onHardware, modifier = Modifier.weight(1f)) {
                                Text("Hardware")
                            }
                        }
                    }
                }
            }
            item(key = "compi_station_hardware_shortcut") {
                Card(
                    onClick = onHardware,
                    modifier = Modifier.fillMaxWidth().testTag("compi_station_hardware_shortcut"),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.DeviceHub, contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(10.dp))
                            Text("Estación Compi · hardware integrado",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f))
                            Icon(Icons.Default.ChevronRight, contentDescription = null)
                        }
                        Text("DHT11 · GPIO16  |  Luz LDR · GPIO32  |  OLED · GPIO21/22  |  RGB · 27/25/26",
                            style = MaterialTheme.typography.bodySmall)
                        Text(
                            "Abre Laboratorio de hardware para verificar lecturas, calibrar la luz " +
                                "y controlar la OLED. El DFPlayer necesita microSD para reproducir; " +
                                "sin tarjeta no bloquea los sensores ni la pantalla.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text("Abrir Laboratorio de hardware →",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary)
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
                            ModuleVisualIcon(descriptor.id)
                            Spacer(Modifier.width(12.dp))
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
                items(configured, key = { "module_${it.id}" }) { group ->
                    val id = group.id
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
                            ModuleVisualIcon(id)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(if (id == ModuleWorkspaceGrouping.ENVIRONMENT)
                                        "Sensor ambiental" else descriptor?.name ?: id,
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.titleMedium)
                                Text(
                                    when {
                                        id == ModuleWorkspaceGrouping.ENVIRONMENT ->
                                            if (group.components.all { runtime(it)?.active == true } &&
                                                currentlyVerified) "${environmentSensor.uppercase()} + suelo · drivers activos"
                                            else "${environmentSensor.uppercase()} + suelo · estado parcial o último conocido"
                                        id == "led_rgb" && physical != null && currentlyVerified ->
                                            "Control RGB preparado · sin detección física"
                                        id in setOf("dht11", "dht22") && currentlyVerified ->
                                            if (ModuleEvidence.dhtResponding(state, id))
                                                "Temperatura y humedad · en vivo"
                                            else "GPIO preparado · esperando datos válidos"
                                        physical != null && currentlyVerified ->
                                            "Firmware: ${if (physical.active) "activo" else "no activo"}"
                                        physical != null -> "Última confirmación del firmware · no en vivo"
                                        bleReady && id == "led_rgb" -> "Control BLE RGB disponible · GPIO sin confirmar"
                                        else -> "Borrador local · sin confirmar"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    if (id == ModuleWorkspaceGrouping.ENVIRONMENT)
                                        "${environmentSensor.uppercase()} y ADC1 · conexiones independientes"
                                    else "GPIO ${(physical?.pins ?: local?.pins).orEmpty().joinToString().ifBlank { "sin asignar" }}",
                                    style = MaterialTheme.typography.labelMedium
                                )
                                val climateModel = id == "dht11" || id == "dht22" ||
                                    id == ModuleWorkspaceGrouping.ENVIRONMENT
                                val respondingModel = if (id == ModuleWorkspaceGrouping.ENVIRONMENT)
                                    environmentSensor else id
                                val sensorHasValidReadings =
                                    ModuleEvidence.dhtResponding(state, respondingModel)
                                val sensorConfirmed = if (id == "led_rgb") {
                                    // PWM leases do not provide physical LED detection.
                                    false
                                } else if (climateModel) {
                                    sensorHasValidReadings && currentlyVerified &&
                                        (if (id == ModuleWorkspaceGrouping.ENVIRONMENT)
                                            group.components.any { runtime(it)?.active == true }
                                        else physical?.active == true)
                                } else physical?.active == true
                                val evidenceLabel = when {
                                    id == "led_rgb" && physical?.active == true &&
                                        currentlyVerified -> "PWM activo · LED no detectable"
                                    sensorConfirmed && currentlyVerified ->
                                        "Sensor respondiendo · datos en vivo"
                                    climateModel && physical?.active == true &&
                                        currentlyVerified -> "GPIO activo · sin lecturas válidas"
                                    physical != null -> "Estado guardado · no en vivo"
                                    bleReady && id == "led_rgb" -> "BLE RGB listo · GPIO sin confirmar"
                                    else -> "Pendiente de aplicar"
                                }
                                Surface(
                                    color = if (sensorConfirmed && currentlyVerified)
                                        Color(0xFF225440) else MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(evidenceLabel,
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (sensorConfirmed && currentlyVerified)
                                            Color(0xFFA6F1D2)
                                        else MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            IconButton(onClick = {
                                editingId = if (id == ModuleWorkspaceGrouping.ENVIRONMENT) environmentSensor else id
                            },
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
                id in setOf("dht11", "dht22", "soil_moisture", "ldr_light", "ssd1306_oled", "led_rgb"),
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
                        if (!wifiAuthorized || session.revision != revision) {
                            throw ComponentApplyUnconfirmedException(
                                "La sesión cambió tras el recibo; verifica los GPIO del dispositivo actual.")
                        }
                        applyMessage = "Pines confirmados por el firmware. Solo vigentes hasta reiniciar el ESP32."
                        try { vm.refresh() }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { /* Receipt already verified; leave success intact. */ }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (unconfirmed: ComponentApplyUnconfirmedException) {
                        applyMessage = "Sin confirmar: ${unconfirmed.message ?: "consulta GPIO antes de reintentar"}"
                        try { vm.refresh() }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { /* Status may remain unavailable. */ }
                    } catch (failure: Exception) {
                        applyMessage = "Rechazado: ${failure.message ?: "verifica la sesión y las conexiones"}"
                        try { vm.refresh() }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { /* Preserve the explicit rejection. */ }
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


/** Reusable identity tile. Component IDs, not screenshots, choose the icon. */
@Composable
private fun ModuleVisualIcon(id: String) {
    val (icon, accent) = when (id) {
        ModuleWorkspaceGrouping.ENVIRONMENT -> Icons.Default.Eco to Color(0xFFA980F4)
        "dht11", "dht22" -> Icons.Default.Thermostat to Color(0xFFEC7797)
        "soil_moisture" -> Icons.Default.WaterDrop to Color(0xFF46C6EF)
        "led_rgb" -> Icons.Default.Lightbulb to Color(0xFFFAB862)
        "ssd1306_oled" -> Icons.Default.DesktopWindows to Color(0xFFFFA45B)
        "analog_sensor", "digital_sensor" -> Icons.Default.DeviceHub to Color(0xFF78A8F7)
        else -> Icons.Default.Widgets to Color(0xFFA980F4)
    }
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = accent.copy(alpha = 0.16f),
        modifier = Modifier.size(52.dp).testTag("module_symbol_$id")
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = accent,
                modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
private fun ModuleRuntimeCard(
    id: String,
    runtime: ComponentRuntimeStatus?,
    live: Boolean,
    sensorResponding: Boolean = false
) {
    val verified = runtime?.active == true && live
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ModuleVisualIcon(id)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(ComponentCatalog.get(id)?.name ?: id,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold)
                    Text(if (runtime == null) "Configuración local · por verificar"
                         else ModuleEvidence.moduleStatus(id, runtime.active, live, sensorResponding),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (verified && (id !in setOf("led_rgb", "dht11", "dht22") ||
                    sensorResponding))
                    Icon(Icons.Default.CheckCircle,
                    contentDescription = if (sensorResponding) "Lectura confirmada"
                                         else "Controlador confirmado",
                    tint = Color(0xFF54D8A3))
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text(
                when {
                    runtime == null -> "El ESP32 aún no ha confirmado este componente."
                    !runtime.active && runtime.state == "catalog" ->
                        "Driver disponible, pero sensor no activado ni detectado."
                    runtime.active && id in setOf("dht11", "dht22") && sensorResponding ->
                        "Temperatura y humedad válidas recibidas del ESP32 por Wi-Fi."
                    runtime.active && id in setOf("dht11", "dht22") ->
                        "Driver ${id.uppercase()} configurado; aún no se confirma una lectura física."
                    runtime.active && id == "led_rgb" ->
                        "Salidas PWM preparadas. El ESP32 no dispone de sensor de corriente ni retorno para detectar si hay un LED conectado."
                    runtime.active -> "Estado del firmware: ${runtime.state}"
                    else -> "El firmware informa: ${runtime.state}"
                },
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                if (runtime?.active == true) "GPIO en firmware: ${runtime.pins.joinToString()}"
                else "GPIO sin asignación activa confirmada",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ModuleReadings(
    id: String,
    state: ComponentDashboardState,
    onRefresh: () -> Unit,
    canCalibrateLdr: Boolean = false,
    calibrationMessage: String? = null,
    canControlOled: Boolean = false,
    oledActionMessage: String? = null,
    onOledFace: (String) -> Unit = {},
    onCalibrateLdr: (Int, Int) -> Unit = { _, _ -> }
) {
    val live = state.telemetryFreshness == ComponentStatusFreshness.LIVE &&
        state.authorityError == null && state.connected
    val reported = if (live) state.authoritative else state.persisted
    val active = reported.orEmpty().any {
        it.active && ComponentCatalog.catalogIdForFirmwareId(it.id) == id
    }
    val measurements = if (live) state.authoritativeTelemetry else state.persistedTelemetry
    fun reading(type: String): ComponentMeasurement? =
        ComponentTelemetryCodec.find(measurements.orEmpty(), type)
            ?.takeIf { it.valid && it.status == "fresh" }
    when (id) {
        "dht11", "dht22" -> SensorDashboard(id, state, active, live, onRefresh)
        "soil_moisture" -> {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SoilMoistureWidget(
                    moisturePercent = reading("soil_moisture")?.value,
                    rawAdc = reading("adc_raw")?.value?.toInt(),
                    calibrated = reading("soil_moisture") != null, live = live,
                    hasData = reading("soil_moisture") != null || reading("adc_raw") != null
                )
                val windowMs = TelemetryChart.DEFAULT_WINDOW_MS
                val geometry = TelemetryChart.build(
                    "soil_moisture", "soil_moisture",
                    state.series("soil_moisture", "soil_moisture"),
                    nowMs = System.currentTimeMillis(), windowMs = windowMs,
                    staleAfterMs = windowMs, disconnected = !live
                )
                TelemetryChartCard("Historial de humedad del suelo", geometry, live)
            }
        }
        "ldr_light" -> {
            var darkReference by remember { mutableStateOf<Int?>(null) }
            var brightReference by remember { mutableStateOf<Int?>(null) }
            val light = if (active && live) reading("ambient_light")?.value else null
            val raw = if (active && live) reading("ambient_light_raw")?.value?.toInt() else null
            val status = ComponentTelemetryCodec.find(measurements.orEmpty(), "ambient_light")?.status
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Card(Modifier.fillMaxWidth().testTag("ldr_light_readings")) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Luz ambiental · LDR", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (light != null) "Iluminación relativa: %.1f %%".format(java.util.Locale.ROOT, light)
                            else "Iluminación relativa: —",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.testTag("ldr_light_percent")
                        )
                        Text(
                            when {
                                !live -> "Sin telemetría en vivo; no se presentan valores almacenados como actuales."
                                !active -> "El firmware no tiene un GPIO ADC1 activo para este módulo."
                                status == "not_calibrated" -> "ADC activo, falta calibrar referencias de oscuridad y claridad."
                                status == "sensor_error" || status == "invalid" -> "Lectura ADC inválida. Comprueba el divisor de 3.3 V y el cableado."
                                light != null -> "Porcentaje calibrado: es una escala relativa, no lux."
                                else -> "Esperando una lectura calibrada y válida."
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text("ADC1 crudo: ${raw?.toString() ?: "—"} (0–4095)",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.testTag("ldr_light_raw"))
                        OutlinedButton(onClick = onRefresh) { Text("Actualizar lectura") }
                        Text("Calibración relativa: cubre el sensor, captura oscuridad; " +
                            "después ilumínalo y captura claridad. Requiere Wi-Fi autorizado.",
                            style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { darkReference = raw },
                                enabled = canCalibrateLdr && raw != null,
                                modifier = Modifier.weight(1f).testTag("ldr_capture_dark")
                            ) { Text("Oscuridad") }
                            OutlinedButton(
                                onClick = { brightReference = raw },
                                enabled = canCalibrateLdr && raw != null,
                                modifier = Modifier.weight(1f).testTag("ldr_capture_bright")
                            ) { Text("Claridad") }
                        }
                        Text("Referencia oscura: ${darkReference ?: "—"} · clara: ${brightReference ?: "—"}",
                             style = MaterialTheme.typography.bodySmall)
                        Button(
                            enabled = canCalibrateLdr && darkReference != null &&
                                brightReference != null &&
                                brightReference!! - darkReference!! >= 32,
                            onClick = {
                                val dark = darkReference
                                val bright = brightReference
                                if (dark != null && bright != null) onCalibrateLdr(dark, bright)
                            },
                            modifier = Modifier.fillMaxWidth().testTag("ldr_apply_calibration")
                        ) { Text("Aplicar calibración") }
                        if (calibrationMessage != null) {
                            Text(calibrationMessage, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                val windowMs = TelemetryChart.DEFAULT_WINDOW_MS
                val geometry = TelemetryChart.build(
                    "ldr_light", "ambient_light",
                    state.series("ldr_light", "ambient_light"),
                    nowMs = System.currentTimeMillis(), windowMs = windowMs,
                    staleAfterMs = windowMs, disconnected = !live
                )
                TelemetryChartCard("Historial de luz relativa", geometry, live)
            }
        }
        "ssd1306_oled" -> {
            // The display consumes the shared DHT11/LDR snapshots; it is not
            // itself a sensor. Never invent frames, measurements or audio input.
            val temp = if (live) reading("temperature")?.value else null
            val humidity = if (live) reading("humidity")?.value else null
            val light = if (live) reading("ambient_light")?.value else null
            val raw = if (live) reading("ambient_light_raw")?.value else null
            Card(Modifier.fillMaxWidth().testTag("oled_environment_dashboard")) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Compi · Pantalla y ambiente", style = MaterialTheme.typography.titleLarge)
                    Text(
                        if (live && active) "OLED conectada · telemetría en vivo"
                        else "Sin confirmación actual de la pantalla y sus datos",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF07111A))) {
                        Column(
                            Modifier.fillMaxWidth().padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("◕ ᴗ ◕", style = MaterialTheme.typography.displayMedium,
                                color = Color(0xFF9EE6FF))
                            Text("COMPI · vista ilustrativa", color = Color(0xFF9EE6FF),
                                style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Text("Temperatura: " + (temp?.let { "%.1f °C".format(java.util.Locale.ROOT, it) } ?: "—"))
                    Text("Humedad: " + (humidity?.let { "%.0f %%".format(java.util.Locale.ROOT, it) } ?: "—"))
                    Text(
                        "Luz: " + when {
                            light != null -> "%.0f %% (calibrada)".format(java.util.Locale.ROOT, light)
                            raw != null -> "%.0f ADC (sin calibrar)".format(java.util.Locale.ROOT, raw)
                            else -> "—"
                        }
                    )
                    Text(
                        "Esta carita es una representación de Compi, no una captura de los píxeles físicos. " +
                            "La OLED muestra localmente las lecturas disponibles. " +
                            "El indicador de sonido requiere un sensor o fuente de audio confirmada.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text("Expresiones físicas", style = MaterialTheme.typography.titleMedium)
                    if (oledActionMessage != null) {
                        Text(oledActionMessage, style = MaterialTheme.typography.bodySmall,
                            color = if (oledActionMessage.startsWith("OLED:")) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.primary)
                    }
                    Text("Se envían a la OLED mediante Wi-Fi autorizado. Las caritas manuales se muestran temporalmente antes de recuperar la vista ambiental.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for ((face, label) in listOf("happy" to "Feliz", "sleepy" to "Sueño")) {
                            OutlinedButton(onClick = { onOledFace(face) }, enabled = canControlOled,
                                modifier = Modifier.weight(1f).testTag("oled_face_$face")) { Text(label) }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for ((face, label) in listOf("surprised" to "Sorpresa", "angry" to "Alerta")) {
                            OutlinedButton(onClick = { onOledFace(face) }, enabled = canControlOled,
                                modifier = Modifier.weight(1f).testTag("oled_face_$face")) { Text(label) }
                        }
                    }
                    OutlinedButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
                        Text("Actualizar sensores")
                    }
                }
            }
        }
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
                Modifier.fillMaxWidth().heightIn(max = 310.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("1 · Asigna los GPIO de cada señal",
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text("Guardar prepara la configuración local; no modifica el ESP32. Aplicar requiere Wi-Fi autorizado y estado vigente del firmware.",
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
                Text("2 · Revisa el cableado", style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold)
                ModuleWiringDiagram(profile, pins, firmwareConfirmed = false,
                    modifier = Modifier.testTag("module_pin_preview"))
                Text("El diagrama muestra una propuesta, no una conexión física detectada.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(if (issues.isEmpty()) "Sin conflictos de GPIO detectados"
                     else "Corrige los conflictos antes de guardar o aplicar",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (issues.isEmpty()) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error)
                for (issue in issues) {
                    Text(issue, color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.testTag("module_pin_warning"))
                }
                if (applyMessage != null) {
                    Text(applyMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (applyMessage.startsWith("Rechazado") ||
                            applyMessage.startsWith("Sin confirmar")) MaterialTheme.colorScheme.error
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
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("3 · Guarda o aplica", style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold)
                if (!canApply) {
                    Text("Aplicar en ESP32 no está disponible: verifica conexión Wi-Fi, autorización y estado vigente. Puedes guardar el borrador.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (canApply) {
                    Button(
                        onClick = { onApply(pins) },
                        enabled = issues.isEmpty() && !applying,
                        modifier = Modifier.fillMaxWidth().testTag("module_pin_apply")
                    ) { Text(if (applying) "Aplicando…" else "Aplicar y verificar en ESP32") }
                }
                OutlinedButton(
                    onClick = { onSave(pins) },
                    enabled = issues.isEmpty() && !applying,
                    modifier = Modifier.fillMaxWidth().testTag("module_pin_save")
                ) { Text("Guardar borrador local") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
    } // Keep the wizard composition balanced when switching component profiles.
}
