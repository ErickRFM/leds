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
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** UI wiring evidence only; these tests do not emulate a physical module. */
class MediaControlsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun stationAudioDefaultsToPhoneAndOffersPhysicalOutput() {
        compose.setContent {
            MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
                CompiAudioPlayerCard(false, null, false, null, { _, _ -> })
            } }
        }
        compose.onNodeWithTag("local_audio_controls").assertExists()
        compose.onNodeWithTag("dfplayer_physical_controls").assertDoesNotExist()
        compose.onNodeWithTag("audio_output_dfplayer").performClick()
        compose.onNodeWithTag("dfplayer_physical_controls").assertExists()
        compose.onNodeWithTag("local_audio_controls").assertDoesNotExist()
        compose.onNodeWithTag("audio_output_phone").performClick()
        compose.onNodeWithTag("local_audio_controls").assertExists()
    }

    @Test fun phoneMusicLibraryCanSelectAndCancelClearing() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("compi_local_audio_v1", 0)
        prefs.edit().putString("uris",
            """["content://compi.tests/song01.mp3","content://compi.tests/song02.mp3"]""")
            .putInt("selected_index", 0).commit()
        try {
            compose.setContent {
                MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
                    CompiAudioPlayerCard(false, null, false, null, { _, _ -> })
                } }
            }
            compose.onNodeWithTag("phone_audio_library_toggle").performScrollTo().performClick()
            compose.onNodeWithTag("phone_audio_track_1").performClick()
            compose.onNodeWithTag("phone_audio_clear_library").performScrollTo().performClick()
            compose.onNodeWithText("Cancelar").performClick()
            compose.onNodeWithTag("phone_audio_library_toggle").assertExists()
            compose.runOnIdle { assertEquals(1, prefs.getInt("selected_index", -1)) }
        } finally {
            prefs.edit().clear().commit()
        }
    }

    @Test fun unauthorizedAudioNeverClaimsActiveHardware() {
        compose.setContent {
            MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
                CompiAudioPlayerCard(false,
                    AudioHardwareState(true, 1, 24, "PLAYING", 1, 20, false, true),
                    false, null, { _, _ -> error("Cannot control unauthenticated hardware") })
            } }
        }
        compose.onNodeWithTag("audio_output_dfplayer").performClick()
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
        compose.onNodeWithTag("dfplayer_overview_status").assertTextContains("sin microSD", substring = true)
        compose.onNodeWithTag("audio_output_dfplayer").performClick()
        compose.onNodeWithTag("dfplayer_status").assertTextEquals("MicroSD ausente.")
        compose.onNodeWithTag("dfplayer_play").assertIsNotEnabled()
    }

    @Test fun dfplayerVolumeAvailableEvenWithoutSd() {
        var command: Pair<String, String?>? = null
        compose.setContent {
            MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
                CompiAudioPlayerCard(true,
                    AudioHardwareState(true, 0, -1, "UNKNOWN", -1, 10, false, true),
                    false, null, { verb, value -> command = verb to value })
            } }
        }
        compose.onNodeWithTag("audio_output_dfplayer").performClick()
        compose.onNodeWithTag("dfplayer_play").assertIsNotEnabled()
        compose.onNodeWithTag("dfplayer_volume").assertIsEnabled()
    }

    @Test fun missingUartExplainsPhysicalWiringAndBlocksPlayback() {
        compose.setContent {
            MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
                CompiAudioPlayerCard(true,
                    AudioHardwareState(false, -1, -1, "UNKNOWN", -1, -1, false, false),
                    false, null, { _, _ -> error("No playback without UART response") })
            } }
        }
        compose.onNodeWithTag("audio_output_dfplayer").performClick()
        compose.onNodeWithTag("dfplayer_status").assertTextContains("sin respuesta", substring = true)
        compose.onNodeWithTag("dfplayer_play").assertIsNotEnabled()
    }

    @Test fun zeroFileCountAllowsNumberedPlaybackAndTrackTenRemainsAvailable() {
        var sent: Pair<String, String?>? = null
        compose.setContent {
            MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
                CompiAudioPlayerCard(true,
                    AudioHardwareState(true, 1, 0, "STOPPED", 0, 10, false, true),
                    false, null, { verb, value -> sent = verb to value })
            } }
        }
        compose.onNodeWithTag("audio_output_dfplayer").performClick()
        compose.onNodeWithTag("dfplayer_status").assertTextContains("conteo UART 0", substring = true)
        compose.onNodeWithTag("dfplayer_track").performTextReplacement("9")
        compose.onNodeWithTag("dfplayer_next_track").performScrollTo().performClick()
        compose.onNodeWithTag("dfplayer_track").assertTextContains("10")
        compose.onNodeWithTag("dfplayer_play").assertIsEnabled()
        compose.onNodeWithTag("dfplayer_play").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("PLAY" to "10", sent) }
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
        compose.onNodeWithTag("audio_output_dfplayer").performClick()
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
        compose.onNodeWithTag("oled_advanced_settings").performScrollTo().performClick()
        compose.onNodeWithTag("oled_invert").performScrollTo().performClick()
        compose.onNodeWithTag("oled_pause").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(true, inversion); assertEquals(true, pause) }
        compose.onNodeWithTag("oled_invert").assertTextContains("Invertir", substring = true)
    }
}
