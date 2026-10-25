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

    @Test fun unauthorizedAudioNeverClaimsActiveHardware() {
        compose.setContent {
            MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
                CompiAudioPlayerCard(false,
                    AudioHardwareState(true, 1, 24, "PLAYING", 1, 20, false, true),
                    false, null, { _, _ -> error("Cannot control unauthenticated hardware") })
            } }
        }
        compose.onNodeWithTag("dfplayer_status").assertTextContains("Conecta y autoriza", substring = true)
        compose.onNodeWithTag("dfplayer_play").assertIsNotEnabled()
    }

    @Test fun physicalAudioCannotPlayWithoutConfirmedSd() {
        compose.setContent {
            MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
                CompiAudioPlayerCard(true,
                    AudioHardwareState(true, 0, -1, "UNKNOWN", -1, 1, false, false),
                    false, null, { _, _ -> error("No command may be sent without SD") })
            } }
        }
        compose.onNodeWithTag("dfplayer_status").assertTextEquals("MicroSD ausente.")
        compose.onNodeWithTag("dfplayer_play").assertIsNotEnabled()
    }

    @Test fun missingUartExplainsPhysicalWiringAndBlocksPlayback() {
        compose.setContent {
            MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
                CompiAudioPlayerCard(true,
                    AudioHardwareState(false, -1, -1, "UNKNOWN", -1, -1, false, false),
                    false, null, { _, _ -> error("No playback without UART response") })
            } }
        }
        compose.onNodeWithTag("dfplayer_status").assertTextContains("TX14", substring = true)
        compose.onNodeWithTag("dfplayer_play").assertIsNotEnabled()
    }

    @Test fun zeroFileCountExplainsWhyPlaybackIsDisabled() {
        compose.setContent {
            MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
                CompiAudioPlayerCard(true,
                    AudioHardwareState(true, 1, 0, "STOPPED", 0, 10, false, true),
                    false, null, { _, _ -> error("No playback for empty SD") })
            } }
        }
        compose.onNodeWithTag("dfplayer_status").assertTextContains("0 archivos", substring = true)
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
