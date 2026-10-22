package com.rgbcontroller

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.rgbcontroller.core.ble.BleManager
import com.rgbcontroller.core.data.PreferencesRepository
import com.rgbcontroller.core.wifi.CompiLabSession

/**
 * Application-context BLE transport survives Activity configuration changes.
 * Cleanup happens only when the retained ViewModel is actually cleared.
 */
class RgbControllerViewModel(application: Application) : AndroidViewModel(application) {
    val bleManager = BleManager(application.applicationContext)
    val preferencesRepository = PreferencesRepository(application.applicationContext)
    val labSession = CompiLabSession()

    override fun onCleared() {
        labSession.clear()
        bleManager.shutdown()
        super.onCleared()
    }
}
