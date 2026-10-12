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
import com.rgbcontroller.feature.home.DashboardScreen
import com.rgbcontroller.feature.hub.LabHubScreen
import com.rgbcontroller.feature.led.LedModuleScreen
import com.rgbcontroller.feature.settings.SettingsScreen
import androidx.compose.ui.unit.dp

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
    data object Led : Screen("led", "LED", Icons.Default.ColorLens)
    data object Code : Screen("code", "Código", Icons.Default.Code)
    data object Settings : Screen("settings", "Ajustes", Icons.Default.Settings)
    data object Devices : Screen("devices", "Equipos", Icons.Default.Bluetooth)
    data object Hub : Screen("hub", "Laboratorio", Icons.Default.Memory)
}

/**
 * Home is always the root destination, never a restored copy of a nested screen.
 * Other tabs keep their navigation state; Bluetooth/Wi-Fi transport lives outside
 * the navigation stack and must not be disconnected when changing screens.
 */
private fun NavHostController.navigateTopLevel(screen: Screen) {
    if (currentDestination?.route == screen.route) return
    if (screen == Screen.Home) {
        // Pop the actual root rather than navigating to another Home instance with
        // restoreState=true, which can revive the screen the user just left.
        if (!popBackStack(Screen.Home.route, inclusive = false)) {
            navigate(Screen.Home.route) {
                popUpTo(graph.findStartDestination().id) { inclusive = true }
                launchSingleTop = true
            }
        }
    } else {
        navigate(screen.route) {
            popUpTo(graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppNavHost(
    bleManager: BleManager,
    preferencesRepository: PreferencesRepository,
    labSession: CompiLabSession? = null
) {
    val session = labSession ?: remember { CompiLabSession() }
    val theme by preferencesRepository.themeMode.collectAsState(initial = "SYSTEM")
    val verifiedAddress by bleManager.connectedAddress.collectAsState()
    // Persist the successful connection regardless of which Compi module is visible.
    LaunchedEffect(verifiedAddress) {
        verifiedAddress?.let { preferencesRepository.saveLastDeviceMac(it) }
    }
    RgbTheme(theme) {
        val navController = rememberNavController()
        val items = listOf(Screen.Home, Screen.Led, Screen.Code, Screen.Settings)
        val entry by navController.currentBackStackEntryAsState()
        val route = entry?.destination?.route
        val nested = route == Screen.Devices.route || route == Screen.Hub.route
        Scaffold(
            topBar = {
                if (nested) {
                    TopAppBar(
                        title = { Text(if (route == Screen.Devices.route) "Dispositivos" else "Hardware Lab") },
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
                                (screen == Screen.Home && nested),
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
                        onLed = { navController.navigateTopLevel(Screen.Led) },
                        onCode = { navController.navigateTopLevel(Screen.Code) },
                        onDevices = { navController.navigate(Screen.Devices.route) },
                        onHardware = { navController.navigate(Screen.Hub.route) }
                    )
                }
                // Retain a migration route for back stacks saved by Compi V8.
                // The redundant Centro TAB is gone; legacy state returns to Home.
                composable("center") {
                    LaunchedEffect(Unit) { navController.navigateTopLevel(Screen.Home) }
                }
                composable(Screen.Led.route) {
                    LedModuleScreen(bleManager, preferencesRepository, session,
                        onOpenDevices = { navController.navigate(Screen.Devices.route) })
                }
                composable(Screen.Code.route) { ProgramEditorScreen(session, bleManager) }
                composable(Screen.Settings.route) {
                    SettingsScreen(bleManager, preferencesRepository,
                        onOpenHub = { navController.navigate(Screen.Hub.route) },
                        onOpenPrograms = { navController.navigateTopLevel(Screen.Code) })
                }
                composable(Screen.Devices.route) {
                    DevicesScreen(bleManager, preferencesRepository,
                        onInspectSecurity = { navController.navigate(Screen.Hub.route) })
                }
                composable(Screen.Hub.route) { LabHubScreen(session, bleManager) }
            }
        }
    }
}
