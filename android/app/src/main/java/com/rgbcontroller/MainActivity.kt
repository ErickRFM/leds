package com.rgbcontroller

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.rgbcontroller.core.ble.BleManager
import com.rgbcontroller.core.data.PreferencesRepository
import com.rgbcontroller.feature.control.ControlScreen
import com.rgbcontroller.feature.devices.DevicesScreen
import com.rgbcontroller.feature.effects.EffectsScreen
import com.rgbcontroller.feature.presets.PresetsScreen
import com.rgbcontroller.feature.settings.SettingsScreen

class MainActivity : ComponentActivity() {
    private val model by viewModels<RgbControllerViewModel>()
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* A new scan is explicitly requested by the user after permission grant. */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestBluetoothPermissionsIfNeeded()
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainAppNavHost(model.bleManager, model.preferencesRepository)
                }
            }
        }
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
    data object Devices : Screen("devices", "Dispositivos", Icons.Default.Bluetooth)
    data object Control : Screen("control", "RGB", Icons.Default.ColorLens)
    data object Presets : Screen("presets", "Colores", Icons.Default.Palette)
    data object Effects : Screen("effects", "Efectos", Icons.Default.WbSunny)
    data object Settings : Screen("settings", "Ajustes", Icons.Default.Settings)
}

@Composable
fun MainAppNavHost(bleManager: BleManager, preferencesRepository: PreferencesRepository) {
    val navController = rememberNavController()
    val items = listOf(Screen.Devices, Screen.Control, Screen.Presets, Screen.Effects, Screen.Settings)
    Scaffold(
        bottomBar = {
            NavigationBar {
                val entry by navController.currentBackStackEntryAsState()
                val route = entry?.destination?.route
                items.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                        label = { Text(screen.title) },
                        selected = route == screen.route,
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
            startDestination = Screen.Devices.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(Screen.Devices.route) { DevicesScreen(bleManager, preferencesRepository) }
            composable(Screen.Control.route) { ControlScreen(bleManager) }
            composable(Screen.Presets.route) { PresetsScreen(bleManager, preferencesRepository) }
            composable(Screen.Effects.route) { EffectsScreen(bleManager) }
            composable(Screen.Settings.route) { SettingsScreen(bleManager) }
        }
    }
}
