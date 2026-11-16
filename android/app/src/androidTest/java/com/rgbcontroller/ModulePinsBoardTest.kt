package com.rgbcontroller

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rgbcontroller.core.components.status.LocalDesiredComponent
import com.rgbcontroller.core.components.status.LocalDesiredConfig
import com.rgbcontroller.core.data.DataStoreComponentStatusStorage
import com.rgbcontroller.core.data.PreferencesRepository
import com.rgbcontroller.core.ble.BleManager
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The pins board and the at-a-glance readings, driven through the real app
 * shell. Module configurations are seeded through the app's own persistence
 * boundary; nothing is mocked into the UI, and no firmware claim is implied.
 */
@RunWith(AndroidJUnit4::class)
class ModulePinsBoardTest {
    @get:Rule val composeRule = createComposeRule()

    private lateinit var storage: DataStoreComponentStatusStorage
    private lateinit var bleManager: BleManager
    private lateinit var preferences: PreferencesRepository

    private fun seed(vararg components: LocalDesiredComponent) {
        runBlocking { storage.saveDesired(LocalDesiredConfig(components.toList())) }
    }

    private fun desired(): LocalDesiredConfig = runBlocking { storage.loadDesired() }

    private fun draft(id: String, vararg pins: Int) =
        LocalDesiredComponent(id, "sensor", pins.toList())

    private fun openPinesTab() {
        composeRule.onNodeWithTag("nav_modules").performClick()
        composeRule.onNodeWithTag("modules_tab_pins").performClick()
        composeRule.waitForIdle()
    }

    private fun scrollTo(tag: String) {
        composeRule.onNodeWithTag("module_workspace_list")
            .performScrollToNode(hasTestTag(tag))
    }

    /** The real navigation host; it applies the theme stored in app preferences. */
    private fun shell() {
        composeRule.setContent { MainAppNavHost(bleManager, preferences) }
        composeRule.waitForIdle()
    }

    /**
     * `uiautomator dump` cannot run while an instrumented test owns the device,
     * so the screenshot has to come from inside the test. Only enabled through
     * the `shotDir` runner argument; CI leaves it unset and nothing is written.
     */
    private fun writeShot(
        fileName: String,
        bitmap: Bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
    ) {
        val arg = InstrumentationRegistry.getArguments().getString("shotDir") ?: return
        val dir = if (arg == "auto") {
            InstrumentationRegistry.getInstrumentation().targetContext.filesDir
        } else {
            File(arg)
        }
        dir.mkdirs()
        val png = File(dir, fileName)
        png.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        Log.i("COMPI_QA", "wrote ${png.absolutePath} ${bitmap.width}x${bitmap.height}")
    }

    /** Coarse brightness of a frame, used to prove a palette actually repainted. */
    private fun meanLuminance(bitmap: Bitmap): Int {
        var sum = 0L
        var count = 0
        var y = 0
        while (y < bitmap.height) {
            var x = 0
            while (x < bitmap.width) {
                val pixel = bitmap.getPixel(x, y)
                val red = (pixel shr 16 and 0xFF)
                val green = (pixel shr 8 and 0xFF)
                val blue = (pixel and 0xFF)
                sum += (red * 385 + green * 757 + blue * 156) / 1298
                count++
                x += 16
            }
            y += 16
        }
        return if (count == 0) 0 else (sum / count).toInt()
    }

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        storage = DataStoreComponentStatusStorage(context)
        bleManager = BleManager(context)
        preferences = PreferencesRepository(context)
        // Written before any Compose root exists: a DataStore write on the main
        // thread while the app is composing is what stalls this bench long
        // enough for the system to tear the test activity down.
        runBlocking { preferences.saveThemeMode("DARK") }
        seed()
    }

    @After
    fun releaseBle() {
        bleManager.shutdown()
    }

    companion object {
        /** Leaves the shared app DataStore as a fresh install for other classes. */
        @JvmStatic
        @AfterClass
        fun restoreFreshInstallDefaults() {
            val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
            runBlocking {
                DataStoreComponentStatusStorage(context).saveDesired(LocalDesiredConfig())
                PreferencesRepository(context).saveThemeMode("SYSTEM")
            }
        }
    }

    @Test
    fun everyAssignedModuleAppearsOnThePinesBoardWithoutOpeningIt() {
        seed(draft("dht11", 16), draft("ldr_light", 34))
        shell()
        openPinesTab()

        scrollTo("modules_pins_summary")
        composeRule.onNodeWithTag("modules_pins_summary").assertExists()
        scrollTo("module_pin_row_dht11")
        composeRule.onNodeWithTag("module_pin_row_dht11").assertExists()
        scrollTo("module_pin_row_ldr_light")
        composeRule.onNodeWithTag("module_pin_row_ldr_light").assertExists()
        // Both drafts are local intentions, so both must say so.
        composeRule.onAllNodesWithText("Borrador local").assertCountEquals(2)
        // Two drafts that do not share a GPIO must not be presented as a dispute.
        composeRule.onNodeWithTag("modules_pins_conflicts").assertDoesNotExist()
    }

    @Test
    fun aGpioHeldByAnotherModuleStaysVisibleButCannotBePicked() {
        seed(draft("dht11", 32), draft("ldr_light", 34))
        shell()
        openPinesTab()

        scrollTo("module_pin_row_ldr_light")
        composeRule.onNodeWithTag("module_pin_option_ldr_light_0_34").assertIsEnabled()
        composeRule.onNodeWithTag("module_pin_option_ldr_light_0_32").assertIsNotEnabled()
        scrollTo("module_pin_taken_ldr_light_0")
        composeRule.onNodeWithTag("module_pin_taken_ldr_light_0", useUnmergedTree = true)
            .assertTextContains("GPIO 32 en DHT11", substring = true)
    }

    @Test
    fun savedDraftsThatShareAGpioAreAnnouncedAsAConflict() {
        seed(draft("dht11", 32), draft("ldr_light", 32))
        shell()
        openPinesTab()

        scrollTo("modules_pins_conflicts")
        composeRule.onNodeWithTag("modules_pins_conflicts").assertExists()
        composeRule.onNodeWithTag("modules_pins_conflict_32", useUnmergedTree = true)
            .assertTextContains("GPIO 32 · DHT11 y LDR", substring = true)
        scrollTo("module_pin_conflict_dht11")
        composeRule.onNodeWithTag("module_pin_conflict_dht11", useUnmergedTree = true)
            .assertTextContains("resuelve antes de aplicar", substring = true)
    }

    @Test
    fun pickingAFreeGpioPersistsTheLocalDraftForThatModuleOnly() {
        seed(draft("dht11", 32), draft("soil_moisture", 34))
        shell()
        openPinesTab()

        scrollTo("module_pin_option_soil_moisture_0_35")
        composeRule.onNodeWithTag("module_pin_option_soil_moisture_0_35").performClick()
        composeRule.waitForIdle()

        scrollTo("modules_pins_result")
        composeRule.onNodeWithTag("modules_pins_result")
            .assertTextContains("GPIO 35", substring = true)
        scrollTo("module_pin_row_dht11")
        composeRule.onNodeWithTag("module_pin_option_dht11_0_32").assertIsEnabled()
        composeRule.waitUntil(10_000) {
            desired().components.firstOrNull { it.id == "soil_moisture" }?.pins == listOf(35)
        }
        // The other module keeps exactly what it had.
        assertEquals(listOf(32), desired().components.first { it.id == "dht11" }.pins)
    }

    @Test
    fun moduleCardShowsItsReadingLineWithoutOpeningTheDetail() {
        seed(draft("dht11", 16))
        shell()
        composeRule.onNodeWithTag("nav_modules").performClick()
        composeRule.waitForIdle()

        scrollTo("module_card_dht11")
        composeRule.onNodeWithTag("module_card_dht11").assertExists()
        // The card is clickable, so its children only exist in the unmerged tree.
        composeRule.onNodeWithTag("module_glance_dht11", useUnmergedTree = true).assertExists()
        // No firmware session exists here, so the card must not invent numbers.
        composeRule.onNodeWithText("Sin lecturas válidas", substring = false,
            useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("No en vivo", substring = false,
            useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("● En vivo", substring = false,
            useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithTag("module_pins_line_dht11", useUnmergedTree = true)
            .assertTextContains("GPIO 16", substring = true)
        writeShot("modules_glance_dark.png")
        Log.i("COMPI_QA", "verified module card glance")
    }

    /**
     * The board is built from CompiTokens, so a palette change must not hide the
     * dispute banner or re-enable a GPIO another module already holds. The theme
     * goes through the app's own preference, which only accepts SYSTEM, DARK and
     * LIGHT: DEEP_BLACK exists in the theme engine but no setting offers it, so it
     * is not reachable from a real screen. Each palette writes a screenshot when
     * the runner passes `-e shotDir auto`.
     *
     * The luminance bands are the measured frames of this very screen (dark 29,
     * light 242) with room on both sides, so a palette that stopped painting the
     * board fails here instead of only producing an identical pair of PNGs.
     */
    private fun paletteGallery(mode: String, maxMean: Int, minMean: Int) {
        seed(draft("dht11", 32), draft("ldr_light", 32))
        runBlocking { preferences.saveThemeMode(mode) }
        shell()
        openPinesTab()

        scrollTo("modules_pins_conflicts")
        composeRule.onNodeWithTag("modules_pins_summary").assertExists()
        composeRule.onNodeWithTag("modules_pins_conflicts").assertExists()
        scrollTo("module_pin_row_ldr_light")
        // The GPIO the module already holds stays readable and selected even inside
        // a dispute; the row's conflict line is what says it is not applicable.
        composeRule.onNodeWithTag("module_pin_option_ldr_light_0_32").assertIsEnabled()
        composeRule.onNodeWithTag("module_pin_conflict_ldr_light", useUnmergedTree = true)
            .assertTextContains("resuelve antes de aplicar", substring = true)
        val frame = composeRule.onRoot().captureToImage().asAndroidBitmap()
        val mean = meanLuminance(frame)
        Log.i("COMPI_QA", "pins $mode mean=$mean")
        assertTrue("$mode board mean luminance was $mean", mean in minMean..maxMean)
        writeShot("pins_conflict_${mode.lowercase()}.png", frame)
    }

    @Test fun pinesBoardInDark() = paletteGallery("DARK", maxMean = 120, minMean = 0)

    @Test fun pinesBoardInLight() = paletteGallery("LIGHT", maxMean = 255, minMean = 180)
}
