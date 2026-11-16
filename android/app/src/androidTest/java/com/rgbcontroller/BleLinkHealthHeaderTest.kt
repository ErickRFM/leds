package com.rgbcontroller

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rgbcontroller.core.ble.BleLinkHealth
import com.rgbcontroller.feature.devices.BleLinkHealthHeader
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The header is the only place a user can read a radio number as a granted
 * command, so both its facts and its disclaimer have to be on screen together.
 * The stateless overload exists so this can be pinned without a live stack.
 */
@RunWith(AndroidJUnit4::class)
class BleLinkHealthHeaderTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun anUnmeasuredLinkSaysNothingIsKnownAndNeverQuotesSignal() {
        composeRule.setContent { BleLinkHealthHeader(BleLinkHealth()) }
        composeRule.onNodeWithTag("ble_link_health").assertExists()
        composeRule.onNodeWithText("Enlace Bluetooth sin medir", substring = true).assertExists()
        composeRule.onAllNodesWithText("dBm", substring = true).assertCountEquals(0)
    }

    @Test
    fun aMeasuredLinkQuotesSignalAndPayloadBesideTheAuthorizationRule() {
        val health = BleLinkHealth(connected = true, mtu = 128, rssiDb = -70, measuredAtMs = 1L)
        composeRule.setContent { BleLinkHealthHeader(health) }
        composeRule.onNodeWithText("Enlace aceptable", substring = true).assertExists()
        composeRule.onNodeWithText("-70 dBm", substring = true).assertExists()
        composeRule.onNodeWithText("payload 125 B", substring = true).assertExists()
        composeRule.onNodeWithText("decide la sesión de propietario", substring = true).assertExists()
    }

    @Test
    fun aFailedReadingIsShownInsteadOfSilentlyDroppingTheLastValue() {
        val health = BleLinkHealth(
            connected = true, mtu = null, rssiDb = -80, measuredAtMs = 1L,
            staleNotice = "La última lectura de señal falló"
        )
        composeRule.setContent { BleLinkHealthHeader(health) }
        composeRule.onNodeWithText("La última lectura de señal falló", substring = true).assertExists()
        composeRule.onNodeWithText("Negociando MTU…", substring = true).assertExists()
        composeRule.onAllNodesWithText("payload", substring = true).assertCountEquals(0)
    }
}
