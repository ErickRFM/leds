package com.rgbcontroller

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import com.rgbcontroller.core.ui.RgbTheme
import com.rgbcontroller.crane.CraneConsoleScreen

/**
 * Launcher of the crane-only edition: one screen, one device. It reuses the
 * application-scoped transport so a configuration change never drops the owner
 * session that authorizes the crane commands.
 */
class CraneMainActivity : ComponentActivity() {
    private val model by viewModels<RgbControllerViewModel>()
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* Scanning again after granting is the user's action, not an automatic retry. */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestBluetoothPermissionsIfNeeded()
        setContent {
            val theme by model.preferencesRepository.themeMode.collectAsState(initial = "SYSTEM")
            RgbTheme(theme) {
                CraneConsoleScreen(model.bleManager)
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
