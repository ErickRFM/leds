package com.rgbcontroller

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.assertTextContains
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
        composeRule.onNodeWithText("Buscar ESP32").performClick()
        composeRule.onNodeWithText("Mis dispositivos").assertExists()
        composeRule.onNodeWithTag("nav_home").performClick()
        composeRule.onNodeWithText("Compi").assertExists()
    }

    @Test
    fun hardwareSectionsAreReachableWithoutADevice() {
        composeRule.onNodeWithTag("open_hardware").performClick()
        composeRule.onNodeWithText("Hardware Lab").assertExists()

        composeRule.onNodeWithText("Diagnóstico").performClick()
        composeRule.onNodeWithText("Identidad V4 del ESP32").assertExists()

        composeRule.onNodeWithText("GPIO").performClick()
        composeRule.onNodeWithText("Entradas y salidas GPIO").assertExists()

        composeRule.onNodeWithText("Sensores").performClick()
        composeRule.onNodeWithText("Sensores / ADC1").assertExists()

        composeRule.onNodeWithText("Experimentos").performClick()
        composeRule.onNodeWithText("Laboratorio protegido").assertExists()

        composeRule.onNodeWithTag("nav_home").performClick()
        composeRule.onNodeWithText("Compi").assertExists()
    }

    @Test
    fun studioKeepsIndependentGpioAndRgbDrafts() {
        composeRule.onNodeWithTag("open_code").performClick()
        composeRule.onNodeWithTag("studio_source").performTextInput("GPIO 19 HIGH\\nWAIT 500\\nSTOP".replace("\\n", "\n"))
        composeRule.onNodeWithTag("studio_engine_rgb").performClick()
        composeRule.onNodeWithTag("studio_source").performTextInput("RGB 255 0 0")
        composeRule.onNodeWithTag("studio_engine_gpio").performClick()
        composeRule.onNodeWithTag("studio_source").assertTextContains("GPIO 19 HIGH", substring = true)
        composeRule.onNodeWithTag("studio_engine_rgb").performClick()
        composeRule.onNodeWithTag("studio_source").assertTextContains("RGB 255 0 0", substring = true)
        composeRule.onNodeWithTag("nav_home").performClick()
        composeRule.onNodeWithText("Compi").assertExists()
        composeRule.onNodeWithTag("open_code").performClick()
        composeRule.onNodeWithTag("studio_source").assertTextContains("RGB 255 0 0", substring = true)
    }

    @Test
    fun v9FourPrimaryDestinationsAndSettingsAreReachable() {
        composeRule.onNodeWithTag("nav_led").performClick()
        composeRule.onNodeWithText("LED RGB").assertExists()
        composeRule.onNodeWithTag("nav_code").performClick()
        composeRule.onNodeWithText("Compi Studio").assertExists()
        composeRule.onNodeWithTag("nav_hub").performClick()
        composeRule.onNodeWithText("Hardware Lab").assertExists()
        composeRule.onNodeWithTag("nav_home").performClick()
        composeRule.onNodeWithTag("home_settings").performClick()
        composeRule.onNodeWithText("Ajustes").assertExists()
        composeRule.onNodeWithTag("nav_back").performClick()
        composeRule.onNodeWithText("Compi").assertExists()
        composeRule.onNodeWithTag("nav_home").performClick()
        composeRule.onNodeWithText("Compi").assertExists()
    }

    @Test
    fun modulesCanBeOpenedWithoutClaimingAConnectedSensor() {
        composeRule.onNodeWithTag("open_modules").performClick()
        composeRule.onNodeWithText("El ESP32 anuncia capacidades", substring = true).assertExists()
        composeRule.onNodeWithText("Sin módulos verificados", substring = true).assertExists()
        composeRule.onNodeWithTag("nav_home").performClick()
        composeRule.onNodeWithText("Compi").assertExists()
    }

    @Test
    fun homeReturnsFromOtherPrimaryModules() {
        composeRule.onNodeWithTag("open_code").performClick()
        composeRule.onNodeWithTag("nav_home").performClick()
        composeRule.onNodeWithText("Compi").assertExists()

        composeRule.onNodeWithTag("open_devices").performClick()
        composeRule.onNodeWithTag("nav_back").performClick()
        composeRule.onNodeWithText("Compi").assertExists()

        composeRule.onNodeWithTag("open_hardware").performClick()
        composeRule.onNodeWithTag("nav_home").performClick()
        composeRule.onNodeWithText("Compi").assertExists()
    }
}
