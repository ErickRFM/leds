package com.rgbcontroller

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rgbcontroller.core.ui.RgbTheme
import com.rgbcontroller.feature.led.EnvironmentalStateBadge
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The environmental badge is the only place the firmware's own state words
 * become a user-visible promise, and a connected board is the only thing that
 * can produce RUNNING, SUSPENDED, CONFLICT, ERROR or DISABLED. Rather than
 * feeding fake telemetry to the shipping card, each state is composed directly
 * in the three palettes the app ships, so the Spanish label and the theme
 * colours are proven on a real screen.
 */
@RunWith(AndroidJUnit4::class)
class EnvironmentalStateBadgeTest {
    @get:Rule val composeRule = createComposeRule()

    private val states = listOf(
        "RUNNING" to "Activo",
        "SUSPENDED" to "En espera",
        "CONFLICT" to "En conflicto",
        "ERROR" to "Error",
        "OFFLINE" to "Sin conexión",
        "DISABLED" to "Desactivado",
        "RECALIBRATING" to "Sin confirmar"
    )

    private fun gallery(mode: String) {
        composeRule.setContent {
            RgbTheme(mode) {
                // The test activity window is light by default, so without the
                // theme background painted here the screenshot would not show
                // which palette the badge is actually sitting on.
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        states.forEach { (state, _) ->
                            EnvironmentalStateBadge(state, Modifier.testTag("badge_$state"))
                        }
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    /**
     * `uiautomator dump` cannot run while an instrumented test owns the device,
     * so the screenshot has to come from inside the test. Only enabled through
     * the `shotDir` runner argument; CI leaves it unset and nothing is written.
     */
    private fun writeShot(mode: String) {
        val arg = InstrumentationRegistry.getArguments().getString("shotDir") ?: return
        val dir = if (arg == "auto") {
            // Internal storage: Android 11 hides /sdcard/Android/data from adb,
            // and the connected task uninstalls the app before a host could
            // read an external screenshot. run-as reaches this directory.
            InstrumentationRegistry.getInstrumentation().targetContext.filesDir
        } else {
            File(arg)
        }
        dir.mkdirs()
        val png = File(dir, "badge_gallery_${mode.lowercase()}.png")
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        png.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        Log.i("COMPI_QA", "wrote ${png.absolutePath} ${bitmap.width}x${bitmap.height}")
    }

    private fun assertGalleryIsHonest() {
        states.forEach { (_, label) ->
            composeRule.onAllNodesWithText(label).assertCountEquals(1)
        }
        // A read-only state must never look tappable to TalkBack or to a finger.
        composeRule.onNode(hasClickAction()).assertDoesNotExist()
    }

    @Test
    fun darkThemeLabelsEveryFirmwareState() {
        Log.i("COMPI_QA", "badge gallery DARK")
        gallery("DARK")
        assertGalleryIsHonest()
        writeShot("DARK")
    }

    @Test
    fun lightThemeLabelsEveryFirmwareState() {
        Log.i("COMPI_QA", "badge gallery LIGHT")
        gallery("LIGHT")
        assertGalleryIsHonest()
        writeShot("LIGHT")
    }

    @Test
    fun deepBlackThemeLabelsEveryFirmwareState() {
        Log.i("COMPI_QA", "badge gallery DEEP_BLACK")
        gallery("DEEP_BLACK")
        assertGalleryIsHonest()
        writeShot("DEEP_BLACK")
    }
}
