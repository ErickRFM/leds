package com.rgbcontroller

import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rgbcontroller.core.ble.BleManager
import com.rgbcontroller.feature.modules.CraneRoboticsScreen
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Crane safety UX: STOP stays reachable in every state and the blocked session
 * is explicit instead of silently disabling controls.
 */
@RunWith(AndroidJUnit4::class)
class CraneRoboticsScreenUxTest {
    @get:Rule val composeRule = createComposeRule()

    private fun bleManager() = BleManager(
        InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
    )

    @Test
    fun blockedSessionKeepsStopVisibleAndExplainsWhy() {
        var openedDevices = false
        composeRule.setContent {
            MaterialTheme {
                CraneRoboticsScreen(
                    bleManager = bleManager(),
                    authorized = false,
                    moduleActive = false,
                    onOpenConnect = { openedDevices = true }
                )
            }
        }

        composeRule.onNodeWithText("Sin sesión BLE").assertExists()
        composeRule.onNodeWithTag("crane_stop").assertIsDisplayed()
        composeRule.onNodeWithTag("crane_stop").assertIsNotEnabled()
        composeRule.onNodeWithTag("crane_apply_angles").assertIsNotEnabled()
        composeRule.onNodeWithText("Ir a dispositivos").performClick()
        composeRule.waitForIdle()
        assertTrue(openedDevices)
    }

    @Test
    fun authorizedSessionEnablesStopAndReportsFailedWrite() {
        composeRule.setContent {
            MaterialTheme {
                CraneRoboticsScreen(
                    bleManager = bleManager(),
                    authorized = true,
                    moduleActive = true
                )
            }
        }

        // No BLE link exists in the test, so the write must surface a failure.
        composeRule.onNodeWithTag("crane_stop").assertIsDisplayed()
        composeRule.onNodeWithTag("crane_stop").assertIsEnabled()
        composeRule.onNodeWithTag("crane_stop").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Comando no enviado").assertExists()
    }

    @Test
    fun capturesBlockedStateForVisualReview() {
        composeRule.setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize(), color = Color(0xFF0C101B)) {
                    CraneRoboticsScreen(
                        bleManager = bleManager(),
                        authorized = false,
                        moduleActive = false
                    )
                }
            }
        }
        composeRule.waitForIdle()
        capture("crane-blocked-top.png")
        composeRule.onNodeWithTag("crane_center_home").performScrollTo()
        composeRule.waitForIdle()
        capture("crane-blocked-bottom.png")
    }

    @Test
    fun capturesEnabledStateForVisualReview() {
        composeRule.setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize(), color = Color(0xFF0C101B)) {
                    CraneRoboticsScreen(
                        bleManager = bleManager(),
                        authorized = true,
                        moduleActive = true
                    )
                }
            }
        }
        composeRule.waitForIdle()
        capture("crane-enabled-top.png")
        composeRule.onNodeWithTag("crane_center_home").performScrollTo()
        composeRule.waitForIdle()
        capture("crane-enabled-bottom.png")
    }

    private fun capture(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.filesDir, name)
        file.outputStream().use { out ->
            composeRule.onRoot().captureToImage().asAndroidBitmap()
                .compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        assertTrue("$name should not be empty", file.length() > 0)
    }
}
