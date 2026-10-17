package com.rgbcontroller

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rgbcontroller.core.components.registry.PinAssignmentPolicy
import com.rgbcontroller.feature.modules.ModuleWiringDiagram
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Regression for changes between incomplete and valid signal configurations. */
@RunWith(AndroidJUnit4::class)
class ModuleWiringRegressionTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun incompleteValidIncompleteTransitionsDoNotCrashComposition() {
        val profile = PinAssignmentPolicy.profile("led_rgb")!!
        var pins by mutableStateOf(listOf(27, 25))
        composeRule.setContent {
            MaterialTheme {
                ModuleWiringDiagram(profile, pins, firmwareConfirmed = false)
            }
        }
        composeRule.onNodeWithTag("module_wiring_incomplete").assertExists()
        composeRule.runOnUiThread { pins = listOf(27, 25, 26) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("module_wiring_diagram").assertExists()
        composeRule.runOnUiThread { pins = emptyList() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("module_wiring_incomplete").assertExists()
    }
}
