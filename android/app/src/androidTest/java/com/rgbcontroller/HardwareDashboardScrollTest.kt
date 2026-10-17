package com.rgbcontroller

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
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
        composeRule.onNodeWithTag("hardware_tabs")
            .performScrollToNode(hasTestTag("hardware_tab_pins"))
        composeRule.onNodeWithTag("hardware_tab_pins").performClick()
        composeRule.onNodeWithTag("component_dashboard_list")
            .performScrollToNode(hasTestTag("component_config_edit"))
        composeRule.onNodeWithTag("component_config_edit").assertExists()

        composeRule.onNodeWithTag("hardware_tabs")
            .performScrollToNode(hasTestTag("hardware_tab_sensors"))
        composeRule.onNodeWithTag("hardware_tab_sensors").performClick()
        composeRule.onNodeWithTag("component_dashboard_list")
            .performScrollToNode(hasTestTag("component_advanced_toggle"))
        composeRule.onNodeWithTag("component_advanced_toggle").performClick()
        composeRule.onNodeWithTag("component_dashboard_list")
            .performScrollToNode(hasTestTag("component_telemetry_history"))
        composeRule.onNodeWithTag("component_telemetry_history").assertExists()

        // Finally visit project tools at the bottom of the final area.
        composeRule.onNodeWithTag("hardware_tabs")
            .performScrollToNode(hasTestTag("hardware_tab_project"))
        composeRule.onNodeWithTag("hardware_tab_project").performClick()
        composeRule.onNodeWithTag("component_dashboard_list")
            .performScrollToNode(hasTestTag("project_portable"))
        composeRule.onNodeWithTag("project_portable").assertExists()
    }
}
