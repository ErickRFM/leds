package com.rgbcontroller

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rgbcontroller.core.ble.BleManager
import com.rgbcontroller.core.data.PreferencesRepository
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Navigation regression: the BLE/Wi-Fi status must never trap a user inside LED
 * or its device picker. Uses no scanning, pairing, network access, or firmware.
 * Run with connectedDebugAndroidTest on an Android device/emulator.
 */
@RunWith(AndroidJUnit4::class)
class HomeNavigationTest {
    @get:Rule val composeRule = createComposeRule()

    private lateinit var bleManager: BleManager

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        bleManager = BleManager(context)
        val preferences = PreferencesRepository(context)
        composeRule.setContent {
            MainAppNavHost(bleManager, preferences)
        }
    }

    @After
    fun tearDown() {
        bleManager.shutdown()
    }

    @Test
    fun homeReturnsFromLedWithoutConnectingToEsp32() {
        composeRule.onNodeWithTag("open_led").performClick()
        composeRule.onNodeWithText("LED RGB").assertExists()
        composeRule.onNodeWithTag("nav_home").performClick()
        composeRule.onNodeWithText("Compi").assertExists()

        // Re-entry must work too: no restored LED route may replace Home.
        composeRule.onNodeWithTag("open_led").performClick()
        composeRule.onNodeWithText("LED RGB").assertExists()
        composeRule.onNodeWithTag("nav_home").performClick()
        composeRule.onNodeWithText("Compi").assertExists()
    }

    @Test
    fun homeReturnsFromNestedBluetoothDevices() {
        composeRule.onNodeWithTag("open_led").performClick()
        composeRule.onNodeWithText("Buscar dispositivo Bluetooth").performClick()
        composeRule.onNodeWithText("Mis dispositivos").assertExists()
        composeRule.onNodeWithTag("nav_home").performClick()
        composeRule.onNodeWithText("Compi").assertExists()
    }

    @Test
    fun homeReturnsFromOtherPrimaryModules() {
        composeRule.onNodeWithTag("open_code").performClick()
        composeRule.onNodeWithTag("nav_home").performClick()
        composeRule.onNodeWithText("Compi").assertExists()

        composeRule.onNodeWithTag("open_center").performClick()
        composeRule.onNodeWithTag("nav_home").performClick()
        composeRule.onNodeWithText("Compi").assertExists()
    }
}
