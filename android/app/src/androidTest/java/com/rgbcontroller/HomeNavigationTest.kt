package com.rgbcontroller

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
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
        composeRule.onNodeWithTag("module_workspace_list").assertExists()
        composeRule.onNodeWithTag("nav_home").performClick()
        composeRule.onNodeWithText("Compi").assertExists()

        // Re-entry must work too: no restored LED route may replace Home.
        composeRule.onNodeWithTag("open_led").performClick()
        composeRule.onNodeWithTag("module_workspace_list").assertExists()
        composeRule.onNodeWithTag("nav_home").performClick()
        composeRule.onNodeWithText("Compi").assertExists()
    }

    @Test
    fun homeReturnsFromNestedBluetoothDevices() {
        composeRule.onNodeWithTag("open_led").performClick()
        composeRule.onNodeWithTag("modules_connect").performClick()
        composeRule.onNodeWithText("Mis dispositivos").assertExists()
        composeRule.onNodeWithTag("nav_home").performClick()
        composeRule.onNodeWithText("Compi").assertExists()
    }

    @Test
    fun hardwareSectionsAreReachableWithoutADevice() {
        composeRule.onNodeWithTag("open_hardware").performClick()
        composeRule.onNodeWithText("Hardware Lab").assertExists()

        composeRule.onNodeWithTag("hardware_tab_sensors").performClick()
        composeRule.onNodeWithTag("component_telemetry_dht22").assertExists()

        composeRule.onNodeWithTag("hardware_tabs")
            .performScrollToNode(androidx.compose.ui.test.hasTestTag("hardware_tab_pins"))
        composeRule.onNodeWithTag("hardware_tab_pins").performClick()
        composeRule.onNodeWithTag("component_config_edit").assertExists()

        composeRule.onNodeWithTag("hardware_tabs")
            .performScrollToNode(androidx.compose.ui.test.hasTestTag("hardware_tab_automation"))
        composeRule.onNodeWithTag("hardware_tab_automation").performClick()
        composeRule.onNodeWithTag("component_alerts").assertExists()

        composeRule.onNodeWithTag("hardware_tabs")
            .performScrollToNode(androidx.compose.ui.test.hasTestTag("hardware_tab_project"))
        composeRule.onNodeWithTag("hardware_tab_project").performClick()
        composeRule.onNodeWithTag("project_portable").assertExists()

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
        composeRule.onNodeWithTag("nav_modules").performClick()
        composeRule.onNodeWithTag("module_workspace_list").assertExists()
        composeRule.onNodeWithTag("nav_code").performClick()
        composeRule.onNodeWithText("Compi Studio").assertExists()
        composeRule.onNodeWithTag("nav_hub").performClick()
        composeRule.onNodeWithText("Hardware Lab").assertExists()
        composeRule.onNodeWithTag("nav_home").performClick()
        composeRule.onNodeWithTag("home_settings").performClick()
        composeRule.onNodeWithTag("settings_screen").assertExists()
        composeRule.onNodeWithTag("nav_back").performClick()
        composeRule.onNodeWithText("Compi").assertExists()
        composeRule.onNodeWithTag("nav_home").performClick()
        composeRule.onNodeWithText("Compi").assertExists()
    }

    @Test
    fun advancedLabToolsRemainReachableFromHardware() {
        composeRule.onNodeWithTag("nav_hub").performClick()
        composeRule.onNodeWithTag("hardware_open_lab_tools").performClick()
        composeRule.onNodeWithText("Laboratorio avanzado").assertExists()
        composeRule.onNodeWithText("Diagnóstico").performClick()
        composeRule.onNodeWithText("Identidad V4 del ESP32").assertExists()
        composeRule.onNodeWithTag("nav_back").performClick()
        composeRule.onNodeWithText("Hardware Lab").assertExists()
    }

    @Test
    fun moduleCatalogBuildsLocalDraftWithoutFirmware() {
        composeRule.onNodeWithTag("nav_modules").performClick()
        composeRule.onNodeWithTag("modules_tab_catalog").performClick()
        composeRule.onNodeWithTag("module_catalog_dht22").performClick()
        composeRule.onNodeWithTag("module_pin_editor").assertExists()
        composeRule.onNodeWithTag("module_pin_save").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("module_card_dht22").assertExists()
        composeRule.onNodeWithTag("module_config_dht22").performClick()
        composeRule.onNodeWithTag("module_pin_editor").assertExists()
    }

    @Test
    fun homeReturnsFromOtherPrimaryModules() {
        composeRule.onNodeWithTag("open_code").performClick()
        composeRule.onNodeWithTag("nav_home").performClick()
        composeRule.onNodeWithText("Compi").assertExists()

        composeRule.onNodeWithTag("home_device_status").performClick()
        composeRule.onNodeWithTag("nav_back").performClick()
        composeRule.onNodeWithText("Compi").assertExists()

        composeRule.onNodeWithTag("open_hardware").performClick()
        composeRule.onNodeWithTag("nav_home").performClick()
        composeRule.onNodeWithText("Compi").assertExists()
    }
}
