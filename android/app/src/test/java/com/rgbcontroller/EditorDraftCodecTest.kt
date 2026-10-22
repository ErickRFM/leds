package com.rgbcontroller

import com.rgbcontroller.core.programs.EditorDraftCodec
import com.rgbcontroller.core.programs.EditorDraftSnapshot
import com.rgbcontroller.core.programs.ProgramLanguage
import org.junit.Assert.*
import org.junit.Test

class EditorDraftCodecTest {
    private val snapshot = EditorDraftSnapshot(
        gpio = "GPIO 19 HIGH\nWAIT 500\nGPIO 19 LOW\nSTOP",
        rgb = "RGB 255 0 0\nWAIT 500\nOFF\nSTOP",
        arduino = "void setup() { /* 🟣 */ }",
        gpioName = "GPIO prueba", rgbName = "Respiración roja",
        arduinoName = "Sketch 🟣", selected = ProgramLanguage.LAB_SCRIPT,
        engine = "RGB"
    )
    @Test fun threeMotorsSurviveColdLaunchCodecRoundTrip() {
        assertEquals(snapshot, EditorDraftCodec.decode(EditorDraftCodec.encode(snapshot)))
    }
    @Test fun rejectsTamperedAndUnknownVersionWithoutDataLoss() {
        val saved = EditorDraftCodec.encode(snapshot)
        assertThrows(IllegalArgumentException::class.java) {
            EditorDraftCodec.decode(saved.replace("COMPI_EDITOR_DRAFTS_V1", "COMPI_EDITOR_DRAFTS_V2"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            EditorDraftCodec.decode(saved.dropLast(1) + "0".takeIf { saved.last() != '0' }.orEmpty() +
                "f".takeIf { saved.last() == '0' }.orEmpty())
        }
    }
    @Test fun refusesOversizedSourceAndInvalidEngine() {
        assertThrows(IllegalArgumentException::class.java) {
            EditorDraftCodec.encode(snapshot.copy(rgb = "x".repeat(32769)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            EditorDraftCodec.encode(snapshot.copy(engine = "OTA"))
        }
    }
    @Test fun formatDoesNotContainSecretFields() {
        val encoded = EditorDraftCodec.encode(snapshot)
        assertFalse(encoded.contains("X-Device-Key"))
        assertFalse(encoded.contains("controlKey"))
    }
}
