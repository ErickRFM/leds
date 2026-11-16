package com.rgbcontroller

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.rgbcontroller.feature.devices.CapabilityMatrixSection
import org.junit.Rule
import org.junit.Test

/**
 * The matrix has to stay hidden until asked for, and its cells must render the
 * same asymmetry the firmware has: two partial Bluetooth rows and a Wi-Fi column
 * that a release build declares unavailable.
 */
class CapabilityMatrixSectionTest {
    @get:Rule
    val compose = createComposeRule()

    private fun expand() {
        compose.onNodeWithTag("capability_matrix_toggle").performClick()
        compose.waitForIdle()
    }

    @Test fun detailStaysCollapsedUntilRequested() {
        compose.setContent { CapabilityMatrixSection(debugBuild = true) }
        compose.onNodeWithTag("capability_matrix").assertIsDisplayed()
        compose.onAllNodesWithText("Parcial").assertCountEquals(0)
        expand()
        // pin_rewire and gpio_read are the only partial Bluetooth claims.
        compose.onAllNodesWithText("Parcial").assertCountEquals(2)
    }

    @Test fun rewiringDisclosesThatBluetoothCoversFewerComponents() {
        compose.setContent { CapabilityMatrixSection(debugBuild = true) }
        expand()
        compose.onNodeWithText(
            "Por Bluetooth el firmware acepta dht11, ldr_light y robotics_crane;",
            substring = true
        ).performScrollTo().assertIsDisplayed()
    }

    @Test fun releaseBuildsSayWifiLabIsUnavailable() {
        compose.setContent { CapabilityMatrixSection(debugBuild = false) }
        expand()
        compose.onNodeWithText("la columna Wi-Fi no está disponible", substring = true)
            .performScrollTo().assertIsDisplayed()
    }
}
