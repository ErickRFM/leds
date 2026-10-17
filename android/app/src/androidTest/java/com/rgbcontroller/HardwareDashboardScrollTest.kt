package com.rgbcontroller

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
    fun scrollToEndWithoutFirmwareDoesNotCrash() {
        composeRule.setContent {
            MaterialTheme { HardwareLabScreen() }
        }
        composeRule.waitForIdle()

        // Configuration is intentionally in its own lightweight area.
        composeRule.onNodeWithTag("hardware_tab_pins").performScrollTo().performClick()
        composeRule.onNodeWithTag("component_config_edit").performScrollTo().assertExists()

        composeRule.onNodeWithTag("hardware_tab_sensors").performScrollTo().performClick()
        composeRule.onNodeWithTag("component_advanced_toggle").performScrollTo().performClick()
        composeRule.onNodeWithTag("component_telemetry_history").performScrollTo().assertExists()

        // Finally visit project tools at the bottom of the final area.
        composeRule.onNodeWithTag("hardware_tab_project").performScrollTo().performClick()
        composeRule.onNodeWithTag("project_portable").performScrollTo().assertExists()
    }
}
