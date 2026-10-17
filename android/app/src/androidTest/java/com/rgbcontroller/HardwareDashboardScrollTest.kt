package com.rgbcontroller

import android.util.Log
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rgbcontroller.feature.hardwarelab.HardwareLabScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression for the reported crash when scrolling to the end of Hardware.
 * Exercise BOTH the compact feed and all opt-in advanced sections without
 * BLE/Wi-Fi or firmware. This must be run on a device/emulator; JVM tests alone
 * cannot validate Compose lazy-item measuring and late composition.
 */
@RunWith(AndroidJUnit4::class)
class HardwareDashboardScrollTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun initialDashboardCompositionIsStable() {
        composeRule.setContent { MaterialTheme { HardwareLabScreen() } }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("hardware_tab_status").assertExists()
        composeRule.waitForIdle()
    }

    @Test
    fun scrollToEndWithoutFirmwareDoesNotCrash() {
        Log.i("COMPI_QA", "START full scroll")
        composeRule.setContent {
            MaterialTheme { HardwareLabScreen() }
        }
        composeRule.waitForIdle()
        Log.i("COMPI_QA", "initial composition complete")

        // Configuration is intentionally in its own lightweight area.
        Log.i("COMPI_QA", "opening pins")
        composeRule.onNodeWithTag("hardware_tab_pins").performScrollTo().performClick()
        composeRule.onNodeWithTag("component_config_edit").performScrollTo().assertExists()
        Log.i("COMPI_QA", "pins reached")

        Log.i("COMPI_QA", "opening sensors")
        composeRule.onNodeWithTag("hardware_tab_sensors").performScrollTo().performClick()
        composeRule.onNodeWithTag("component_advanced_toggle").performScrollTo().performClick()
        Log.i("COMPI_QA", "sensor details expanded")
        composeRule.onNodeWithTag("component_telemetry_history").performScrollTo().assertExists()
        Log.i("COMPI_QA", "sensor history reached")

        // Finally visit project tools at the bottom of the final area.
        Log.i("COMPI_QA", "opening project")
        composeRule.onNodeWithTag("hardware_tab_project").performScrollTo().performClick()
        composeRule.onNodeWithTag("project_portable").performScrollTo().assertExists()
        Log.i("COMPI_QA", "project end reached")
    }
}
