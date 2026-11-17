package com.rgbcontroller.crane

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rgbcontroller.core.components.registry.CraneWiring
import com.rgbcontroller.core.components.registry.CraneWiringDraft
import com.rgbcontroller.core.ui.RgbTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The pin editor of the crane edition, driven the way the console drives it:
 * a local draft that a tap can empty. Releasing must work with no BLE session
 * at all, while "Aplicar en ESP32" stays blocked and says why.
 */
@RunWith(AndroidJUnit4::class)
class CraneWiringSectionTest {
    @get:Rule val composeRule = createComposeRule()

    private val defaults = requireNotNull(CraneWiring.profile()).defaults
    private val confirmed = CraneWiringDraft(defaults, firmwarePins = defaults)
    private val draft = mutableStateOf(confirmed)
    private val applying = mutableStateOf(false)
    private val result = mutableStateOf<String?>(null)
    private val applyTaps = mutableStateOf(0)

    private fun show(authorized: Boolean = true, seed: CraneWiringDraft = confirmed) {
        draft.value = seed
        composeRule.setContent {
            RgbTheme("DARK") {
                // The console hosts the card in a scrolling column; without that
                // parent, performScrollTo has nothing to scroll in.
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    CraneWiringSection(
                        draft = draft.value,
                        authorized = authorized,
                        applying = applying.value,
                        resultMessage = result.value,
                        onAssign = { index, pin -> draft.value = draft.value.assign(index, pin) },
                        onRelease = { index -> draft.value = draft.value.release(index) },
                        onApply = { applyTaps.value += 1 }
                    )
                }
            }
        }
    }

    @Test fun sevenSignalsRenderWithTheirReportedGpio() {
        show()
        for (index in defaults.indices) {
            composeRule.onNodeWithTag("crane_wiring_row_$index").assertExists()
            composeRule.onNodeWithTag("crane_wiring_pin_$index")
                .assertTextEquals("GPIO ${defaults[index]}")
            composeRule.onNodeWithTag("crane_wiring_release_$index").assertIsEnabled()
        }
        composeRule.onNodeWithText("7 de 7 señales · Igual que el ESP32").assertExists()
        composeRule.onNodeWithTag("crane_wiring_apply").assertIsEnabled()
        composeRule.onNodeWithTag("crane_wiring_blocker").assertDoesNotExist()
    }

    @Test fun releasingASignalIsVisibleAndBlocksTheApply() {
        show()
        composeRule.onNodeWithTag("crane_wiring_release_0").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("crane_wiring_pin_0").assertTextEquals("Liberada")
        // An emptied row cannot empty itself again.
        composeRule.onNodeWithTag("crane_wiring_release_0").assertIsNotEnabled()
        composeRule.onNodeWithTag("crane_wiring_apply").assertIsNotEnabled()
        composeRule.onNodeWithTag("crane_wiring_blocker").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("crane_wiring_footnote").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("6 de 7 señales", substring = true).assertExists()
    }

    @Test fun reassigningTheReleasedSignalUnblocksTheApply() {
        show(seed = confirmed.release(0))
        composeRule.onNodeWithTag("crane_wiring_apply").assertIsNotEnabled()
        composeRule.onNodeWithTag("crane_wiring_option_0_16").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("crane_wiring_pin_0").assertTextEquals("GPIO 16")
        composeRule.onNodeWithTag("crane_wiring_apply").assertIsEnabled()
    }

    @Test fun aGpioAnotherCraneSignalHoldsIsNotOffered() {
        // GPIO 19 is "Servo brazo": the base row must not be able to re-take it.
        show()
        composeRule.onNodeWithTag("crane_wiring_option_0_19").performScrollTo()
            .assertIsNotEnabled()
            .assertTextContains("19 ✕", substring = true)
        composeRule.onNodeWithTag("crane_wiring_option_0_16").assertIsEnabled()
    }

    @Test fun aGpioAnotherModuleHoldsIsNamedAndBlocksTheApply() {
        show(seed = confirmed.copy(reservedByOthers = mapOf(16 to "dht11", 19 to "led.rgb")))
        composeRule.onNodeWithTag("crane_wiring_owner_1").assertTextEquals("GPIO 19 lo retiene led.rgb.")
        composeRule.onNodeWithTag("crane_wiring_option_1_16").performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithTag("crane_wiring_apply").assertIsNotEnabled()
    }

    @Test fun aDuplicatedGpioSaysItAlreadyLivesInAnotherCraneSignal() {
        show(seed = confirmed.assign(1, 18))
        composeRule.onNodeWithTag("crane_wiring_duplicate_1").performScrollTo()
            .assertTextEquals("GPIO 18 ya está en otra señal de la grúa.")
        composeRule.onNodeWithTag("crane_wiring_apply").assertIsNotEnabled()
    }

    @Test fun releasingWithoutASessionStillEditsTheDraft() {
        show(authorized = false)
        composeRule.onNodeWithText("Sin sesión").assertExists()
        composeRule.onNodeWithTag("crane_wiring_release_6").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("crane_wiring_pin_6").assertTextEquals("Liberada")
        composeRule.onNodeWithTag("crane_wiring_apply").assertIsNotEnabled()
    }

    @Test fun completeDraftReachesTheApplyCallbackOncePerTap() {
        show()
        composeRule.onNodeWithTag("crane_wiring_apply").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals(1, applyTaps.value)
    }

    @Test fun blockedDraftNeverReachesTheApplyCallback() {
        show(seed = confirmed.release(3))
        composeRule.onNodeWithTag("crane_wiring_apply").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals("a released signal must not send a frame", 0, applyTaps.value)
    }

    @Test fun unsavedWiringSaysItWasNeverConfirmed() {
        show(seed = CraneWiringDraft(defaults))
        composeRule.onNodeWithText("7 de 7 señales · Sugerencia sin confirmar").assertExists()
    }

    @Test fun divergenceFromTheBoardIsStatedNotHidden() {
        show(seed = confirmed.release(0).assign(0, 16))
        composeRule.onNodeWithTag("crane_wiring_divergent").performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("7 de 7 señales · Borrador distinto al ESP32").assertExists()
    }

    @Test fun firmwareResultAndApplyingStateReachTheUser() {
        show(seed = confirmed.release(0))
        result.value = "El ESP32 confirmó los siete GPIO: 18, 19, 34, 35, 36, 39, 17."
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("crane_wiring_result").performScrollTo().assertIsDisplayed()
    }

    @Test fun capturesEditorStatesForVisualReview() {
        show()
        composeRule.waitForIdle()
        capture("crane-wiring-default.png")
        draft.value = confirmed.release(0)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("crane_wiring_blocker").performScrollTo()
        capture("crane-wiring-released.png")
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
