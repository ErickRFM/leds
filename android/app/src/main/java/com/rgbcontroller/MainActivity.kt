package com.rgbcontroller

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.DisposableEffect
import com.rgbcontroller.core.voice.CompiVoiceController
import com.rgbcontroller.core.ble.BleConnectionState
import com.rgbcontroller.core.ble.BleSecurityState
import com.rgbcontroller.core.wifi.LabConnectionState
import com.rgbcontroller.core.wifi.LabTransport
import kotlinx.coroutines.delay
import com.rgbcontroller.core.wifi.CompiLabSession
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.core.content.ContextCompat
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.rgbcontroller.core.ble.BleManager
import com.rgbcontroller.core.data.PreferencesRepository
import com.rgbcontroller.core.ui.RgbTheme
import com.rgbcontroller.feature.programs.ProgramEditorScreen
import com.rgbcontroller.feature.devices.DevicesScreen
import com.rgbcontroller.feature.devices.ConnectionCenterScreen
import com.rgbcontroller.feature.home.DashboardScreen
import com.rgbcontroller.feature.hub.LabHubScreen
import com.rgbcontroller.feature.hardwarelab.HardwareLabScreen
import com.rgbcontroller.feature.modules.ModuleWorkspaceScreen
import com.rgbcontroller.feature.settings.SettingsScreen
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rgbcontroller.feature.hardwarelab.ComponentDashboardViewModel

class MainActivity : ComponentActivity() {
    private val model by viewModels<RgbControllerViewModel>()
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* Ask the user to scan again after granting permissions. */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestBluetoothPermissionsIfNeeded()
        setContent { MainAppNavHost(model.bleManager, model.preferencesRepository, model.labSession) }
    }

    private fun requestBluetoothPermissionsIfNeeded() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (permissions.any {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }) requestPermissionLauncher.launch(permissions)
    }
}

private sealed class Screen(
    val route: String, val title: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    data object Home : Screen("home", "Inicio", Icons.Default.Home)
    data object Modules : Screen("modules", "Estación", Icons.Default.ColorLens)
    data object Code : Screen("code", "Estudio", Icons.Default.Code)
    data object Settings : Screen("settings", "Ajustes", Icons.Default.Settings)
    data object Devices : Screen("devices", "Equipos", Icons.Default.Bluetooth)
    data object Connect : Screen("connect", "Conectar", Icons.Default.Bluetooth)
    data object Hub : Screen("hub", "Laboratorio", Icons.Default.Memory)
    data object LabTools : Screen("lab-tools", "Laboratorio", Icons.Default.Memory)
}

/**
 * Home is always the root destination, never a restored copy of a nested screen.
 * Module/Hardware tab changes must not revive old nested screens. Studio keeps
 * its local unsaved editor buffers while switching tabs. Transport state lives
 * outside the navigation stack and is never disconnected by navigation.
 */
private fun NavHostController.navigateTopLevel(screen: Screen) {
    if (currentDestination?.route == screen.route) return
    if (screen == Screen.Home) {
        // Pop the actual root rather than navigating to another Home instance with
        // restoreState=true, which can revive the screen the user just left.
        if (!popBackStack(Screen.Home.route, inclusive = false, saveState = false)) {
            navigate(Screen.Home.route) {
                popUpTo(graph.findStartDestination().id) { inclusive = true }
                launchSingleTop = true
            }
        }
    } else {
        navigate(screen.route) {
            // Save transient Studio drafts before leaving; restore only Studio.
            // Other tabs always enter at their root (no saved nested LED/GPIO).
            popUpTo(graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = screen == Screen.Code
        }
    }
}

/** Nested destinations should never stack duplicate copies on repeated taps. */
private fun NavHostController.navigateNested(route: String) {
    if (currentDestination?.route == route) return
    navigate(route) { launchSingleTop = true }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppNavHost(
    bleManager: BleManager,
    preferencesRepository: PreferencesRepository,
    labSession: CompiLabSession? = null
) {
    val session = labSession ?: remember { CompiLabSession() }
    val context = LocalContext.current
    // One dashboard owner across Home, Station and Hardware: no independently
    // refreshed repositories or contradictory sensor readiness between tabs.
    val dashboardVm: ComponentDashboardViewModel = viewModel(
        factory = remember(session, bleManager) {
            ComponentDashboardViewModel.factory(context.applicationContext, session, bleManager)
        }
    )
    // One speech owner for all screens. Only live, confirmed telemetry is
    // eligible for announcements; persisted history never triggers a voice.
    val phoneVoice = remember(context) { CompiVoiceController(context.applicationContext) }
    DisposableEffect(phoneVoice) { onDispose { phoneVoice.close() } }
    val voiceLifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(phoneVoice, voiceLifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) phoneVoice.pause()
        }
        voiceLifecycleOwner.lifecycle.addObserver(observer)
        onDispose { voiceLifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val liveDashboard by dashboardVm.uiState.collectAsState()
    val liveTelemetry = liveDashboard.firmwareTelemetry
    LaunchedEffect(liveDashboard.deviceId, liveDashboard.connected, liveTelemetry?.receivedAtMs) {
        val measurements = liveDashboard.authoritativeTelemetry.orEmpty()
        fun value(type: String): Float? = measurements.firstOrNull {
            it.type == type && it.valid && it.value.isFinite() &&
                it.status.equals("fresh", ignoreCase = true)
        }?.value
        phoneVoice.accept(
            liveDashboard.deviceId, liveDashboard.connected,
            value("temperature"), value("ambient_light"),
            liveTelemetry?.receivedAtMs ?: 0L, System.currentTimeMillis(),
            liveDashboard.firmwareVoiceEvent?.id,
            liveDashboard.firmwareVoiceEvent?.cue
        )
    }
    val bleConnection by bleManager.connectionState.collectAsState()
    val bleSecurity by bleManager.securityState.collectAsState()
    val wifiConnection = session.connectionState
    val transport = session.transport
    LaunchedEffect(bleConnection, bleSecurity, wifiConnection, transport, session.revision) {
        val authorized = when (transport) {
            LabTransport.BLUETOOTH -> bleConnection == BleConnectionState.Ready &&
                bleSecurity == BleSecurityState.Authorized
            LabTransport.WIFI_LAB -> wifiConnection == LabConnectionState.AUTHORIZED &&
                session.wifiReady
            else -> false
        }
        dashboardVm.onConnectionChanged()
        if (authorized) {
            while (true) {
                delay(3_000L)
                dashboardVm.refresh()
            }
        }
    }
    val theme by preferencesRepository.themeMode.collectAsState(initial = "SYSTEM")
    val verifiedAddress by bleManager.connectedAddress.collectAsState()
    // Persist the successful connection regardless of which Compi module is visible.
    LaunchedEffect(verifiedAddress) {
        verifiedAddress?.let { preferencesRepository.saveLastDeviceMac(it) }
    }
    RgbTheme(theme) {
        val navController = rememberNavController()
        val items = listOf(Screen.Home, Screen.Modules, Screen.Code, Screen.Hub)
        val entry by navController.currentBackStackEntryAsState()
        val route = entry?.destination?.route
        val nested = route == Screen.Devices.route || route == Screen.Settings.route ||
            route == Screen.Connect.route || route == Screen.LabTools.route || route == "led"
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                if (nested) {
                    TopAppBar(
                        title = { Text(when (route) {
                            Screen.Devices.route -> "Dispositivos"
                            Screen.Connect.route -> "Conexiones"
                            Screen.LabTools.route -> "Laboratorio avanzado"
                            "led" -> "Módulo LED RGB"
                            else -> "Ajustes"
                        }) },
                        navigationIcon = {
                            IconButton(
                                modifier = Modifier.testTag("nav_back"),
                                onClick = {
                                    if (!navController.popBackStack()) {
                                        navController.navigateTopLevel(Screen.Home)
                                    }
                                }
                            ) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Regresar")
                            }
                        },
                        actions = {
                            TextButton(onClick = { navController.navigateTopLevel(Screen.Home) }) {
                                Text("Inicio")
                            }
                        }
                    )
                }
            },
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                    items.forEach { screen ->
                        NavigationBarItem(
                            modifier = Modifier.testTag("nav_${screen.route}"),
                            icon = { Icon(screen.icon, contentDescription = screen.title) },
                            label = { Text(screen.title) },
                            alwaysShowLabel = true,
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer
                            ),
                            selected = route == screen.route ||
                                (screen == Screen.Hub && route == Screen.LabTools.route) ||
                                (screen == Screen.Modules && route == "led"),
                            onClick = { navController.navigateTopLevel(screen) }
                        )
                    }
                }
            }
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = Screen.Home.route,
                modifier = Modifier.padding(padding)
            ) {
                composable(Screen.Home.route) {
                    DashboardScreen(
                        bleManager = bleManager,
                        onLed = { navController.navigateTopLevel(Screen.Modules) },
                        onCode = { navController.navigateTopLevel(Screen.Code) },
                        onDevices = { navController.navigateNested(Screen.Connect.route) },
                        onHardware = { navController.navigateTopLevel(Screen.Hub) },
                        onSettings = { navController.navigateNested(Screen.Settings.route) },
                        session = session,
                        sharedDashboardVm = dashboardVm
                    )
                }
                // Retain a migration route for back stacks saved by Compi V8.
                // The redundant Centro TAB is gone; legacy state returns to Home.
                composable("center") {
                    LaunchedEffect(Unit) { navController.navigateTopLevel(Screen.Home) }
                }
                composable(Screen.Modules.route) {
                    ModuleWorkspaceScreen(
                        bleManager, preferencesRepository, session,
                        onConnect = { navController.navigateNested(Screen.Connect.route) },
                        onHardware = { navController.navigateTopLevel(Screen.Hub) },
                        sharedDashboardVm = dashboardVm
                    )
                }
                // Keep the legacy route address for restored V8-V16 stacks,
                // but never render the retired LED page. Its current controls
                // live in Modules and share the audited BLE/Wi-Fi transport.
                composable("led") {
                    LaunchedEffect(Unit) {
                        navController.navigate(Screen.Modules.route) {
                            popUpTo("led") { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                }
                composable(Screen.Code.route) { ProgramEditorScreen(session, bleManager) }
                composable(Screen.Settings.route) {
                    SettingsScreen(bleManager, preferencesRepository,
                        onVoiceTest = { phoneVoice.speakTest() },
                        onOpenHub = { navController.navigateTopLevel(Screen.Hub) },
                        onOpenPrograms = { navController.navigateTopLevel(Screen.Code) })
                }
                composable(Screen.Connect.route) {
                    ConnectionCenterScreen(
                        bleManager, preferencesRepository, session,
                        onHardware = { navController.navigateTopLevel(Screen.Hub) }
                    )
                }
                composable(Screen.Devices.route) {
                    DevicesScreen(bleManager, preferencesRepository,
                        onInspectSecurity = { navController.navigateTopLevel(Screen.Hub) })
                }
                composable(Screen.Hub.route) {
                    HardwareLabScreen(
                        session = session,
                        bleManager = bleManager,
                        onOpenLabTools = { navController.navigateNested(Screen.LabTools.route) },
                        sharedDashboardVm = dashboardVm
                    )
                }
                // Preserve the existing diagnostics/GPIO/experiment engine instead
                // of losing access when the component dashboard became Hardware.
                composable(Screen.LabTools.route) {
                    LabHubScreen(labSession = session, bleManager = bleManager)
                }
            }
        }
    }
}
