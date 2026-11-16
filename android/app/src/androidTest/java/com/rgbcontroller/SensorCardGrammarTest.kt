package com.rgbcontroller

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
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
import com.rgbcontroller.core.components.telemetry.TelemetryChart
import com.rgbcontroller.core.components.telemetry.TelemetryProvenance
import com.rgbcontroller.core.components.telemetry.TelemetrySample
import com.rgbcontroller.core.components.widgets.HumidityTemperatureWidget
import com.rgbcontroller.core.components.widgets.SoilMoistureWidget
import com.rgbcontroller.core.components.widgets.TelemetryChartCard
import com.rgbcontroller.core.ui.RgbTheme
import com.rgbcontroller.feature.hardwarelab.HardwareLabScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The sensor redesign promises one visual grammar for every reading card: the
 * same caption/number/unit block, the same card edge and the same empty state.
 * Only a real screen can show whether that grammar survives the three palettes
 * and whether an absent reading is still rendered as an absence, so each card
 * is composed with and without data in DARK, LIGHT and DEEP_BLACK. No shipping
 * widget receives test data: these are the widgets the app already draws.
 */
@RunWith(AndroidJUnit4::class)
class SensorCardGrammarTest {
    @get:Rule val composeRule = createComposeRule()

    private val now = System.currentTimeMillis()

    private enum class Card { DHT_LIVE, DHT_EMPTY, SOIL_LIVE, SOIL_UNCALIBRATED, CHART_WITH_GAP }

    private val modeState = mutableStateOf("DARK")
    private val cardState = mutableStateOf(Card.DHT_LIVE)

    private fun samples() = listOf(
        sample("temperature", 20.1f, "fresh", true, now - 120_000L),
        sample("temperature", 21.4f, "fresh", true, now - 90_000L),
        sample("temperature", null, "sensor_error", false, now - 60_000L),
        sample("temperature", 22.0f, "fresh", true, now - 30_000L),
        sample("temperature", 22.4f, "fresh", true, now)
    )

    private fun sample(type: String, value: Float?, status: String, valid: Boolean, at: Long) =
        TelemetrySample(
            deviceId = "device-a", componentId = "dht22", measurementType = type,
            value = value, unit = "C", status = status, valid = valid,
            sourceTimestampMs = at, recordedAtMs = at, provenance = TelemetryProvenance.FIRMWARE
        )

    private fun show(cardToShow: Card) {
        cardState.value = cardToShow
        composeRule.waitForIdle()
    }

    /**
     * One composition per test (Compose rules forbid a second setContent), so the
     * card and the palette are hoisted into state and swapped from the test body.
     * Exactly one card is on screen at a time, which is also what makes the
     * placeholder counts below unambiguous.
     */
    private fun gallery(modeName: String) {
        modeState.value = modeName
        composeRule.setContent {
            RgbTheme(modeState.value) {
                // The test window is light by default; without the palette
                // painted behind the card, the shot would not prove which theme
                // the card is actually sitting on.
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    when (cardState.value) {
                        Card.DHT_LIVE -> HumidityTemperatureWidget(
                            humidity = 48.3f, temperature = 21.7f, hasData = true,
                            lastUpdate = now, live = true, status = "En vivo", model = "DHT22"
                        )
                        Card.DHT_EMPTY -> HumidityTemperatureWidget(
                            humidity = null, temperature = null, hasData = false,
                            live = true, model = "DHT22"
                        )
                        Card.SOIL_LIVE -> SoilMoistureWidget(
                            moisturePercent = 61.2f, rawAdc = 2048, calibrated = true,
                            hasData = true, live = true, lastUpdate = now
                        )
                        Card.SOIL_UNCALIBRATED -> SoilMoistureWidget(
                            moisturePercent = null, rawAdc = 3120, calibrated = false,
                            hasData = true, live = true
                        )
                        Card.CHART_WITH_GAP -> TelemetryChartCard(
                            "Temperatura · °C",
                            TelemetryChart.build("dht22", "temperature", samples(), nowMs = now),
                            live = true
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
        Card.values().forEach { card ->
            show(card)
            verify(card)
            writeShot(modeName, card)
        }
    }

    private fun verify(card: Card) {
        // A reading card reports; it never invites a tap.
        composeRule.onNode(hasClickAction()).assertDoesNotExist()
        when (card) {
            Card.DHT_LIVE -> {
                composeRule.onNodeWithText("TEMPERATURA").assertExists()
                composeRule.onNodeWithText("°C").assertExists()
                composeRule.onNodeWithText("21.7").assertExists()
                composeRule.onNodeWithText("48.3").assertExists()
                composeRule.onNodeWithText("● En vivo").assertExists()
                composeRule.onNodeWithText("Esperando mediciones válidas del sensor")
                    .assertDoesNotExist()
            }
            Card.DHT_EMPTY -> {
                composeRule.onNodeWithText("21.7").assertDoesNotExist()
                composeRule.onNodeWithText("48.3").assertDoesNotExist()
                composeRule.onAllNodesWithText("—").assertCountEquals(2)
                composeRule.onNodeWithText("Esperando mediciones válidas del sensor").assertExists()
                composeRule.onNodeWithText("Sin lectura en vivo").assertExists()
            }
            Card.SOIL_LIVE -> {
                composeRule.onNodeWithText("HUMEDAD").assertExists()
                composeRule.onNodeWithText("ADC CRUDO").assertExists()
                composeRule.onNodeWithText("61.2").assertExists()
                composeRule.onNodeWithText("2048").assertExists()
            }
            Card.SOIL_UNCALIBRATED -> {
                // An uncalibrated probe must not get a percentage it never earned.
                composeRule.onNodeWithText("61.2").assertDoesNotExist()
                composeRule.onAllNodesWithText("—").assertCountEquals(1)
                composeRule.onNodeWithText("3120").assertExists()
                composeRule.onNodeWithText(
                    "Sin calibrar - No hay porcentaje de humedad; solo lectura cruda del ADC1"
                ).assertExists()
            }
            Card.CHART_WITH_GAP -> {
                composeRule.onNodeWithText("● En vivo  ·  4 muestras").assertExists()
                composeRule.onNodeWithText("Lecturas interrumpidas").assertExists()
                composeRule.onNodeWithText("1 inválidas · 0 antiguas").assertExists()
                composeRule.onNodeWithText("Esperando mediciones reales").assertDoesNotExist()
            }
        }
        Log.i("COMPI_QA", "verified $card")
    }

    /**
     * `uiautomator dump` cannot run while an instrumented test owns the device,
     * so the screenshot has to come from inside the test. Only enabled through
     * the `shotDir` runner argument; CI leaves it unset and nothing is written.
     */
    private fun writeShot(mode: String, card: Card) =
        writeNamedShot("sensor_${mode.lowercase()}_${card.name.lowercase()}.png")

    private fun writeNamedShot(fileName: String) {
        val arg = InstrumentationRegistry.getArguments().getString("shotDir") ?: return
        val dir = if (arg == "auto") {
            // Internal storage: Android 11 hides /sdcard/Android/data from adb,
            // and the connected task uninstalls the app before a host could read
            // an external screenshot. run-as reaches this directory.
            InstrumentationRegistry.getInstrumentation().targetContext.filesDir
        } else {
            File(arg)
        }
        dir.mkdirs()
        val png = File(dir, fileName)
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        png.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        Log.i("COMPI_QA", "wrote ${png.absolutePath} ${bitmap.width}x${bitmap.height}")
    }

    /**
     * The cards above are the widgets in isolation; this renders the real
     * Sensors tab so the shared range chips, the added light chart and the
     * redesigned cards are proven together in one shipped screen.
     */
    @Test fun realSensorsTabUsesTheSameReadingCards() {
        composeRule.setContent {
            RgbTheme("DARK") {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    HardwareLabScreen()
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("hardware_tab_sensors").performClick()
        // Which DHT tag the card carries depends on what firmware last confirmed,
        // so the honest assertion is that exactly one ambient card is on screen.
        composeRule.onAllNodes(
            hasTestTag("component_telemetry_dht11").or(hasTestTag("component_telemetry_dht22"))
        ).assertCountEquals(1)
        composeRule.onNodeWithTag("component_telemetry_soil_moisture").assertExists()
        composeRule.onNodeWithTag("component_dashboard_list")
            .performScrollToNode(hasTestTag("component_sensor_details_toggle"))
        composeRule.onNodeWithTag("component_sensor_details_toggle").performClick()
        composeRule.onNodeWithTag("component_dashboard_list")
            .performScrollToNode(hasTestTag("lab_window_86400000"))
        composeRule.onNodeWithTag("lab_window_86400000").assertExists()
        writeNamedShot("sensors_tab_ranges_dark.png")
        composeRule.onNodeWithTag("component_dashboard_list")
            .performScrollToNode(hasTestTag("chart_ldr_light_ambient_light"))
        composeRule.onNodeWithTag("chart_ldr_light_ambient_light").assertExists()
        writeNamedShot("sensors_tab_dark.png")
        Log.i("COMPI_QA", "verified real sensors tab")
    }

    @Test fun darkPaletteKeepsOneReadingGrammar() {
        Log.i("COMPI_QA", "sensor card grammar DARK")
        gallery("DARK")
    }

    @Test fun lightPaletteKeepsOneReadingGrammar() {
        Log.i("COMPI_QA", "sensor card grammar LIGHT")
        gallery("LIGHT")
    }

    @Test fun deepBlackPaletteKeepsOneReadingGrammar() {
        Log.i("COMPI_QA", "sensor card grammar DEEP_BLACK")
        gallery("DEEP_BLACK")
    }
}
