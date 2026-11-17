package com.rgbcontroller.crane

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
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
import com.rgbcontroller.core.ble.BleManager
import com.rgbcontroller.core.components.registry.CraneWiring
import com.rgbcontroller.core.components.status.ComponentStatusRepository
import com.rgbcontroller.core.components.status.LocalDesiredComponent
import com.rgbcontroller.core.components.status.LocalDesiredConfig
import com.rgbcontroller.core.data.DataStoreComponentStatusStorage
import com.rgbcontroller.core.ui.RgbTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The crane console exactly as the crane APK starts it: no BLE link, no owner
 * session. Controls must be visibly blocked, the reason must be on screen, and
 * the local wiring editor must still be the usable surface it promises to be.
 */
@RunWith(AndroidJUnit4::class)
class CraneConsoleUiTest {
    @get:Rule val composeRule = createComposeRule()

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext

    private val defaults = requireNotNull(CraneWiring.profile()).defaults

    /**
     * DataStore survives between tests in the same run, so the draft the console
     * loads is seeded here instead of being assumed.
     */
    private fun seedDraft(pins: List<Int>) = runBlocking {
        val repository = ComponentStatusRepository(DataStoreComponentStatusStorage(context))
        repository.loadPersisted()
        val others = repository.state.value.desiredConfig.components
            .filterNot { it.id == CraneWiring.COMPONENT_ID }
        repository.setDesired(
            LocalDesiredConfig(others + LocalDesiredComponent(CraneWiring.COMPONENT_ID, "controller", pins))
        )
    }

    private fun showConsole(pins: List<Int> = defaults) {
        seedDraft(pins)
        composeRule.setContent {
            RgbTheme("DARK") { CraneConsoleScreen(BleManager(context)) }
        }
        composeRule.waitForIdle()
    }

    @Test fun withoutASessionTheCommandsAreBlockedAndExplained() {
        showConsole()
        composeRule.onNodeWithTag("crane_console").assertIsDisplayed()
        composeRule.onNodeWithTag("crane_badge_session").assertTextEquals("BLE sin sesión")
        composeRule.onNodeWithText("Falta la sesión de propietario").performScrollTo().assertExists()
        composeRule.onNodeWithTag("crane_hardware_note").performScrollTo().assertExists()
        composeRule.onNodeWithTag("crane_apply_angles").assertIsNotEnabled()
        composeRule.onNodeWithTag("crane_wiring_apply").assertIsNotEnabled()
    }

    @Test fun stopStaysOnScreenEvenWithNothingAuthorized() {
        showConsole()
        // The emergency control is in the command bar, so scrolling must not hide it.
        composeRule.onNodeWithTag("crane_wiring_card").performScrollTo()
        composeRule.onNodeWithTag("crane_stop").assertIsDisplayed().assertIsNotEnabled()
    }

    @Test fun theWiringEditorWorksBeforeAnyBleSession() {
        showConsole()
        composeRule.onNodeWithTag("crane_wiring_pin_2").performScrollTo()
            .assertTextEquals("GPIO ${defaults[2]}")
        composeRule.onNodeWithTag("crane_wiring_release_2").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("crane_wiring_pin_2").assertTextEquals("Liberada")
        composeRule.onNodeWithTag("crane_wiring_blocker").performScrollTo().assertExists()
    }

    @Test fun releasingPersistsTheDraftSoARestartKeepsIt() {
        showConsole()
        composeRule.onNodeWithTag("crane_wiring_release_4").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("crane_wiring_pin_4").assertTextEquals("Liberada")
        val expected = defaults.toMutableList().also { it[4] = CraneWiring.UNASSIGNED }
        // The screen writes the draft in its own coroutine, so poll the store
        // rather than assume the flush finished with the recomposition.
        val saved = runBlocking {
            val repository = ComponentStatusRepository(DataStoreComponentStatusStorage(context))
            var pins: List<Int>? = null
            var attempts = 0
            while (pins != expected && attempts < 20) {
                repository.loadPersisted()
                pins = repository.state.value.desiredConfig.components
                    .firstOrNull { it.id == CraneWiring.COMPONENT_ID }?.pins
                attempts++
                if (pins != expected) delay(100)
            }
            pins
        }
        assertEquals(expected, saved)
    }

    @Test fun capturesConsoleStatesForVisualReview() {
        showConsole()
        capture("crane-console-blocked-top.png")
        composeRule.onNodeWithTag("crane_wiring_card").performScrollTo()
        capture("crane-console-wiring.png")
        composeRule.onNodeWithTag("crane_wiring_release_0").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("crane_wiring_blocker").performScrollTo()
        capture("crane-console-released.png")
    }

    private fun capture(name: String) {
        val file = File(context.filesDir, name)
        file.outputStream().use { out ->
            composeRule.onRoot().captureToImage().asAndroidBitmap()
                .compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        assertTrue("$name should not be empty", file.length() > 0)
    }
}
