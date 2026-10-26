package com.rgbcontroller.feature.modules

import androidx.activity.compose.BackHandler

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.unit.sp
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
import com.rgbcontroller.feature.led.EnvironmentalRgbControlCard
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
    onHardware: () -> Unit,
    sharedDashboardVm: ComponentDashboardViewModel? = null
) {
    val context = LocalContext.current
    val vm: ComponentDashboardViewModel = sharedDashboardVm ?: viewModel(
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
    var previousOpenedId by rememberSaveable { mutableStateOf<String?>(null) }
    var stationSection by rememberSaveable { mutableStateOf("Resumen") }
    var editingId by remember { mutableStateOf<String?>(null) }
    var applyMessage by remember { mutableStateOf<String?>(null) }
    var ldrCalibrationMessage by remember { mutableStateOf<String?>(null) }
    var applying by remember { mutableStateOf(false) }
    // Physical Back should close a nested component editor/detail before leaving Modules.
    BackHandler(enabled = editingId != null || openedId != null) {
        if (editingId != null) editingId = null else {
            openedId = previousOpenedId
            previousOpenedId = null
        }
    }
    val labClient = remember(context) { LocalLabClient(context.applicationContext) }
    val mediaAuthorized = wifiAuthorized || bleAuthorized
    val media = rememberHardwareMediaControls(session, bleManager, labClient, mediaAuthorized,
        audioVisible = openedId in setOf("compi_dfplayer", "dfplayer_mp3") ||
            (openedId == "compi_station" && stationSection in setOf("Resumen", "Audio")),
        oledVisible = openedId == "ssd1306_oled" ||
            (openedId == "compi_station" && stationSection in setOf("Resumen", "OLED")))

    LaunchedEffect(Unit) {
        vm.loadPersisted()
        vm.onConnectionChanged()
    }
    LaunchedEffect(session.connectionState, session.transport, session.revision, bleReady, bleSecurity) {
        vm.onConnectionChanged()
    }
    // Foreground only: refresh real sensor values while this destination is visible.
    // Cancellation on navigation avoids background Wi-Fi traffic/battery drain.
    LaunchedEffect(wifiAuthorized, bleAuthorized, session.revision) {
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
            (if (bleAuthorized) listOf("led_rgb") else emptyList())).distinct()
    )
    val environmentSensor = configured.firstOrNull {
        it.id == ModuleWorkspaceGrouping.ENVIRONMENT
    }?.components?.firstOrNull { it == "dht11" || it == "dht22" } ?: "dht11"
    val runtime = { id: String -> confirmed.firstOrNull {
        ComponentCatalog.catalogIdForFirmwareId(it.id) == id
    } }

    if (openedId != null) {
        // Hardware DFPlayer and Music always share a single player route.
        val id = if (openedId == "dfplayer_mp3") "compi_dfplayer" else openedId!!
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    openedId = previousOpenedId
                    previousOpenedId = null
                }, modifier = Modifier.testTag("module_back")) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Volver a módulos")
                }
                Text(if (id == ModuleWorkspaceGrouping.ENVIRONMENT) "Sensor ambiental"
                     else if (id == "compi_station") "Estación Compi"
                     else if (id == "compi_dfplayer") "Música"
                     else ComponentCatalog.get(id)?.name ?: id,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f))
                if (id != "compi_dfplayer" && id != "compi_station") {
                    TextButton(
                        onClick = { editingId = if (id == ModuleWorkspaceGrouping.ENVIRONMENT) environmentSensor else id },
                        modifier = Modifier.testTag("module_edit_pins")
                    ) { Icon(Icons.Default.Tune, null); Text("GPIO") }
                }
            }
            if (id == "compi_station") {
                Column(Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("Resumen", "Ambiente", "Iluminación", "OLED", "Audio").forEach { sec ->
                            FilterChip(
                                selected = stationSection == sec,
                                onClick = { stationSection = sec },
                                label = { Text(sec, fontSize = 12.sp, maxLines = 1, softWrap = false) }
                            )
                        }
                    }

                    Text(
                        text = when (stationSection) {
                            "Ambiente" -> "Temperatura y humedad · datos confirmados por sensores"
                            "Iluminación" -> "RGB ambiental y luz · control y calibración"
                            "OLED" -> "Expresiones y estados de la pantalla física"
                            "Audio" -> "Música y avisos"
                            else -> "Resumen de dispositivos y telemetría de la estación"
                        },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().testTag("compi_station_dashboard"),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if (stationSection == "Resumen") {
                            item {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text("Ambiente actual", style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold)
                                        Text("Temperatura · humedad · luz",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.bodySmall)
                                    }
                                    Surface(
                                        color = if (currentlyVerified && state.telemetryFreshness == ComponentStatusFreshness.LIVE) MaterialTheme.colorScheme.primaryContainer
                                            else MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text(if (currentlyVerified && state.telemetryFreshness == ComponentStatusFreshness.LIVE) "En vivo" else "Sin datos en vivo",
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                            item {
                                val readings = if (currentlyVerified &&
                                    state.telemetryFreshness == ComponentStatusFreshness.LIVE)
                                    state.authoritativeTelemetry.orEmpty() else emptyList()
                                val temp = ComponentTelemetryCodec.find(readings, "temperature")
                                    ?.takeIf { it.valid && it.status == "fresh" && it.value.isFinite() }?.value
                                val humid = ComponentTelemetryCodec.find(readings, "humidity")
                                    ?.takeIf { it.valid && it.status == "fresh" && it.value.isFinite() }?.value
                                val light = ComponentTelemetryCodec.find(readings, "ambient_light")
                                    ?.takeIf { it.valid && it.status == "fresh" && it.value.isFinite() }?.value
                                val raw = ComponentTelemetryCodec.find(readings, "ambient_light_raw")
                                    ?.takeIf { it.valid && it.status == "fresh" && it.value.isFinite() }?.value
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    StationMetric("Temperatura",
                                        temp?.let { "%.1f°".format(java.util.Locale.ROOT, it) } ?: "—",
                                        Icons.Default.Thermostat, Modifier.weight(1f))
                                    StationMetric("Humedad",
                                        humid?.let { "%.1f%%".format(java.util.Locale.ROOT, it) } ?: "—",
                                        Icons.Default.WaterDrop, Modifier.weight(1f))
                                }
                                StationMetric("Luz ambiental", light?.let {
                                    "%.0f%%".format(java.util.Locale.ROOT, it)
                                } ?: raw?.let { "ADC %.0f".format(java.util.Locale.ROOT, it) } ?: "—",
                                    Icons.Default.Lightbulb, Modifier.fillMaxWidth())
                                if (light == null && raw != null) {
                                    Text("LDR pendiente de calibración",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            item {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = { scope.launch { vm.refresh() } },
                                        modifier = Modifier.weight(1f)) { Text("Actualizar") }
                                    OutlinedButton(onClick = {
                                        previousOpenedId = openedId
                                        openedId = "ldr_light"
                                    }, modifier = Modifier.weight(1f)) { Text("Calibrar LDR") }
                                }
                            }

                        }

                        if (stationSection == "Ambiente") {
                            item(key = "station_sensor_charts") {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Historial ambiental",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold)
                                    SensorDashboard(
                                        id = environmentSensor,
                                        state = state,
                                        active = runtime(environmentSensor)?.active == true,
                                        live = currentlyVerified,
                                        onRefresh = { scope.launch { vm.refresh() } }
                                    )
                                }
                            }
                        }

                        if (stationSection == "Resumen" || stationSection == "Iluminación") {
                            item(key = "station_rgb_shortcut") {
                                // Route to the audited BLE/Wi-Fi RGB module instead of
                                // rendering local sliders that cannot drive hardware.
                                Card(
                                    onClick = {
                                        previousOpenedId = openedId
                                        openedId = "led_rgb"
                                    },
                                    modifier = Modifier.fillMaxWidth().testTag("station_rgb_shortcut")
                                ) {
                                    Row(Modifier.fillMaxWidth().padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Icon(Icons.Default.Lightbulb, null,
                                            tint = MaterialTheme.colorScheme.primary)
                                        Column(Modifier.weight(1f)) {
                                            Text("Control RGB", style = MaterialTheme.typography.titleMedium)
                                            Text("Colores y efectos",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Icon(Icons.Default.ChevronRight, null)
                                    }
                                }
                            }
                            item {
                                EnvironmentalRgbControlCard(session, labClient, bleManager)
                            }
                        }

                        if (stationSection == "OLED") {
                            item {
                                OledInteractiveModule(
                                    isAuthorized = mediaAuthorized && !media.busy,
                                    confirmed = media.oled,
                                    feedback = media.oledMessage,
                                    onSendExpression = { scope.launch { media.command("OLD", "FACE", it) } },
                                    onSetContrast = { scope.launch { media.command("OLD", "CONTRAST", it.toString()) } },
                                    onSetInverted = { scope.launch { media.command("OLD", "INVERT", if (it) "1" else "0") } },
                                    onSetPaused = { scope.launch { media.command("OLD", "PAUSE", if (it) "1" else "0") } }
                                )
                            }
                        }

                        if (stationSection == "Audio") {
                            item(key = "station_audio_player") {
                                CompiAudioPlayerCard(
                                    authorized = mediaAuthorized,
                                    hardware = media.audio,
                                    busy = media.busy,
                                    feedback = media.audioMessage,
                                    onCommand = { verb, value ->
                                        scope.launch { media.command("AUD", verb, value) }
                                    }
                                )
                            }
                        }
                        if (stationSection == "Resumen") {
                            item {
                                Card(
                                    onClick = {
                                        previousOpenedId = openedId
                                        openedId = "compi_dfplayer"
                                    },
                                    modifier = Modifier.fillMaxWidth().testTag("station_audio_shortcut")
                                ) {
                                    Row(Modifier.fillMaxWidth().padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Icon(Icons.Default.Widgets, null,
                                            tint = MaterialTheme.colorScheme.primary)
                                        Column(Modifier.weight(1f)) {
                                            Text("Música y avisos", style = MaterialTheme.typography.titleMedium)
                                            Text("Tus canciones y avisos",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Icon(Icons.Default.ChevronRight, null)
                                    }
                                }
                            }
                        }
                    }
                }
            } else if (id == "compi_dfplayer") {
                var showWiring by rememberSaveable(id) { mutableStateOf(false) }
                LazyColumn(
                    modifier = Modifier.fillMaxSize().testTag("dfplayer_module_detail"),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 40.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Música Compi",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold)
                            Text("Añade canciones y elige dónde reproducir.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Teléfono · DFPlayer",
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                        }
                    }
                    item(key = "dfplayer_audio_controls") {
                        CompiAudioPlayerCard(
                            authorized = mediaAuthorized,
                            hardware = media.audio,
                            busy = media.busy,
                            feedback = media.audioMessage,
                            onCommand = { verb, value -> scope.launch { media.command("AUD", verb, value) } }
                        )
                    }
                    item(key = "dfplayer_wiring") {
                        Card(
                            onClick = { showWiring = !showWiring },
                            modifier = Modifier.fillMaxWidth().testTag("dfplayer_wiring_toggle"),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Column(Modifier.fillMaxWidth().padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text("Conexión y especificaciones",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold)
                                        Text("ESP32 · UART · bocina 4 Ω / 3 W",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Icon(Icons.Default.ChevronRight,
                                        contentDescription = if (showWiring) "Ocultar conexión" else "Mostrar conexión")
                                }
                                if (showWiring) {
                                    HorizontalDivider()
                                    Text("Alimentación: 5 V regulados y GND compartida.",
                                        style = MaterialTheme.typography.bodyMedium)
                                    Text("GPIO14 (TX ESP32) → RX DFPlayer, con resistencia de 1 kΩ.",
                                        style = MaterialTheme.typography.bodyMedium)
                                    Text("TX DFPlayer → GPIO13 (RX ESP32), solo si la señal es de hasta 3.3 V.",
                                        style = MaterialTheme.typography.bodyMedium)
                                    Text("Bocina Samsung 4 Ω / 3 W → SPK1 y SPK2.",
                                        style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            } else if (id == "led_rgb") {
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
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Surface(color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(16.dp)) {
                                    Icon(Icons.Default.Thermostat, contentDescription = null,
                                        modifier = Modifier.padding(14.dp),
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                }
                                Column(Modifier.weight(1f)) {
                                    Text("Ambiente en tiempo real",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold)
                                    Text("Temperatura · humedad · suelo",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        item(key = "environmental_voice_summary") {
                            EnvironmentalSpeechCard(state)
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
                        item(key = "environmental_audio_library") {
                            Card(
                                onClick = {
                                    previousOpenedId = openedId
                                    openedId = "compi_dfplayer"
                                },
                                modifier = Modifier.fillMaxWidth().testTag("environment_open_audio"),
                                shape = RoundedCornerShape(18.dp)
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(Icons.Default.Widgets, contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary)
                                    Column(Modifier.weight(1f)) {
                                        Text("Música y avisos", style = MaterialTheme.typography.titleSmall)
                                        Text("Abrir biblioteca y controles de audio",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Icon(Icons.Default.ChevronRight, contentDescription = null)
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
                            canControlOled = mediaAuthorized && media.oled?.ready == true && !media.busy,
                            oledActionMessage = media.oledMessage,
                            onOledFace = { face -> scope.launch { media.command("OLD", "FACE", face) } },
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
                Card(onClick = onConnect,
                    modifier = Modifier.fillMaxWidth().testTag("modules_connection")) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(if (wifiAuthorized || bleAuthorized) Icons.Default.CheckCircle
                            else Icons.Default.Bluetooth, null,
                            tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f)) {
                            Text(if (wifiAuthorized || bleAuthorized) "ESP32 conectado"
                                else if (bleReady) "ESP32 · falta autorizar"
                                else "Conectar ESP32",
                                style = MaterialTheme.typography.titleSmall)
                            Text("Conexiones y permisos",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Default.ChevronRight, null)
                    }
                }
            }
            item(key = "compi_station_hardware_shortcut") {
                Card(onClick = { openedId = "compi_station" },
                    modifier = Modifier.fillMaxWidth().testTag("compi_station_hardware_shortcut"),
                    shape = RoundedCornerShape(22.dp)) {
                    Row(Modifier.fillMaxWidth().padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Surface(color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(16.dp)) {
                            Icon(Icons.Default.DeviceHub, contentDescription = null,
                                modifier = Modifier.padding(14.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        Column(Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Estación Compi", style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold)
                            Text("Sensores · OLED · luces · automatización",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            val stationLive = currentlyVerified &&
                                state.telemetryFreshness == ComponentStatusFreshness.LIVE
                            val readings = if (stationLive)
                                state.authoritativeTelemetry.orEmpty() else emptyList()
                            val temperature = ComponentTelemetryCodec.find(readings, "temperature")
                                ?.takeIf { it.valid }?.value
                            val humidity = ComponentTelemetryCodec.find(readings, "humidity")
                                ?.takeIf { it.valid }?.value
                            val light = ComponentTelemetryCodec.find(readings, "ambient_light")
                                ?.takeIf { it.valid }?.value
                            val rawLight = ComponentTelemetryCodec.find(readings, "ambient_light_raw")
                                ?.takeIf { it.valid }?.value
                            Text(
                                listOf(
                                    "T " + (temperature?.let { "%.1f°C".format(java.util.Locale.ROOT, it) } ?: "—"),
                                    "H " + (humidity?.let { "%.0f%%".format(java.util.Locale.ROOT, it) } ?: "—"),
                                    "L " + when {
                                        light != null -> "%.0f%%".format(java.util.Locale.ROOT, light)
                                        rawLight != null -> "ADC %.0f".format(java.util.Locale.ROOT, rawLight)
                                        else -> "—"
                                    }
                                ).joinToString("  ·  "),
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.testTag("station_live_metrics")
                            )
                            if (!stationLive) {
                                Text("Esperando lecturas confirmadas",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text("Abrir estación",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary)
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null)
                    }
                }
            }
            item(key = "compi_dfplayer_module") {
                Card(onClick = { openedId = "compi_dfplayer" },
                    modifier = Modifier.fillMaxWidth().testTag("module_card_compi_dfplayer"),
                    shape = RoundedCornerShape(22.dp)) {
                    Row(Modifier.fillMaxWidth().padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Surface(color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(16.dp)) {
                            Icon(Icons.Default.Widgets, contentDescription = null,
                                modifier = Modifier.padding(14.dp),
                                tint = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                        Column(Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Música y DFPlayer", style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold)
                            Text("Biblioteca local · módulo MP3 físico",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Abrir reproductor",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary)
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null)
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
                items(configured.filterNot { it.id == "dfplayer_mp3" }, key = { "module_${it.id}" }) { group ->
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
            canApply = currentlyVerified && current != null && (
                (wifiAuthorized && id in setOf("dht11", "dht22", "soil_moisture", "ldr_light", "ssd1306_oled", "led_rgb")) ||
                (bleAuthorized && id in setOf("dht11", "ldr_light"))),
            applying = applying,
            applyMessage = applyMessage,
            onApply = { pins ->
                scope.launch {
                    applying = true
                    applyMessage = null
                    try {
                        val revision = session.revision
                        session.userOperation("Aplicar GPIO") {
                            check(session.revision == revision) { "La sesión cambió antes de aplicar GPIO." }
                            if (bleAuthorized) {
                                check(pins.size == 1) { "Solo DHT11 y LDR admiten esta transacción BLE." }
                                val sensor = when (id) {
                                    "dht11" -> com.rgbcontroller.core.ble.BleSensorPinProtocol.Sensor.DHT11
                                    "ldr_light" -> com.rgbcontroller.core.ble.BleSensorPinProtocol.Sensor.LDR
                                    else -> error("Este módulo requiere Wi-Fi para reasignar GPIO.")
                                }
                                bleManager.applySensorPinBle(sensor, pins.single())
                            } else {
                                check(wifiAuthorized) { "Se perdió la autorización Wi-Fi." }
                                labClient.applyComponentPinsConfirmed(session.key, id, pins)
                            }
                            check(session.revision == revision) { "La sesión cambió tras aplicar GPIO; consulta el dispositivo actual." }
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
