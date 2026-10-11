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
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.rgbcontroller.core.ble.BleManager
import com.rgbcontroller.core.data.PreferencesRepository
import com.rgbcontroller.core.ui.RgbTheme
import com.rgbcontroller.feature.programs.ProgramEditorScreen
import com.rgbcontroller.feature.devices.DevicesScreen
import com.rgbcontroller.feature.home.CenterLandingScreen
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
        setContent { MainAppNavHost(model.bleManager, model.preferencesRepository) }
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
    data object Center : Screen("center", "Centro", Icons.Default.Dashboard)
    data object Settings : Screen("settings", "Ajustes", Icons.Default.Settings)
    data object Devices : Screen("devices", "Equipos", Icons.Default.Bluetooth)
    data object Hub : Screen("hub", "Laboratorio", Icons.Default.Memory)
}

@Composable
fun MainAppNavHost(bleManager: BleManager, preferencesRepository: PreferencesRepository) {
    val theme by preferencesRepository.themeMode.collectAsState(initial = "SYSTEM")
    RgbTheme(theme) {
        val navController = rememberNavController()
        val items = listOf(Screen.Home, Screen.Led, Screen.Code, Screen.Center, Screen.Settings)
        Scaffold(
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                    val entry by navController.currentBackStackEntryAsState()
                    val route = entry?.destination?.route
                    items.forEach { screen ->
                        NavigationBarItem(
                            icon = { Icon(screen.icon, contentDescription = screen.title) },
                            label = { Text(screen.title) },
                            alwaysShowLabel = true,
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer
                            ),
                            selected = route == screen.route ||
                                (screen == Screen.Center && route in setOf(Screen.Devices.route, Screen.Hub.route)),
                            onClick = {
                                if (route != screen.route) {
                                    navController.navigate(screen.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            }
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
                        onLed = { navController.navigate(Screen.Led.route) },
                        onCode = { navController.navigate(Screen.Code.route) },
                        onCenter = { navController.navigate(Screen.Center.route) }
                    )
                }
                composable(Screen.Led.route) {
                    LedModuleScreen(bleManager, preferencesRepository,
                        onOpenDevices = { navController.navigate(Screen.Devices.route) })
                }
                composable(Screen.Code.route) { ProgramEditorScreen() }
                composable(Screen.Center.route) {
                    CenterLandingScreen(
                        onDevices = { navController.navigate(Screen.Devices.route) },
                        onLab = { navController.navigate(Screen.Hub.route) },
                        onCode = { navController.navigate(Screen.Code.route) }
                    )
                }
                composable(Screen.Settings.route) {
                    SettingsScreen(bleManager, preferencesRepository,
                        onOpenHub = { navController.navigate(Screen.Hub.route) },
                        onOpenPrograms = { navController.navigate(Screen.Code.route) })
                }
                composable(Screen.Devices.route) {
                    DevicesScreen(bleManager, preferencesRepository,
                        onInspectSecurity = { navController.navigate(Screen.Hub.route) })
                }
                composable(Screen.Hub.route) { LabHubScreen() }
            }
        }
    }
}
