package com.rgbcontroller

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.rgbcontroller.core.ble.AudioHardwareState
import com.rgbcontroller.core.ble.OledHardwareState
import com.rgbcontroller.feature.modules.CompiAudioPlayerCard
import com.rgbcontroller.feature.modules.OledInteractiveModule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** UI wiring evidence only; these tests do not emulate a physical module. */
class MediaControlsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun physicalAudioCannotPlayWithoutConfirmedSd() {
        compose.setContent {
            MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
                CompiAudioPlayerCard(true,
                    AudioHardwareState(true, 0, -1, "UNKNOWN", -1, 1, false, false),
                    false, null, { _, _ -> error("No command may be sent without SD") })
            } }
        }
        compose.onNodeWithTag("dfplayer_status").assertTextContains("MicroSD ausente")
        compose.onNodeWithTag("dfplayer_play").assertIsNotEnabled()
    }

    @Test fun physicalPlayInvokesTheHardwareCommand() {
        var sent: Pair<String, String?>? = null
        compose.setContent {
            MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
                CompiAudioPlayerCard(true,
                    AudioHardwareState(true, 1, 24, "STOPPED", 0, 1, false, true),
                    false, null, { verb, value -> sent = verb to value })
            } }
        }
        compose.onNodeWithTag("dfplayer_play").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("PLAY" to "1", sent) }
    }

    @Test fun oledControlsSendCommandsAndAwaitReadback() {
        var inversion: Boolean? = null
        var pause: Boolean? = null
        compose.setContent {
            MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
                OledInteractiveModule(true, {}, {},
                    OledHardwareState(true, "cat", 127, false, false),
                    { inversion = it }, { pause = it })
            } }
        }
        compose.onNodeWithTag("oled_invert").performScrollTo().performClick()
        compose.onNodeWithTag("oled_pause").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(true, inversion); assertEquals(true, pause) }
        compose.onNodeWithTag("oled_invert").assertTextContains("Invertir", substring = true)
    }
}
