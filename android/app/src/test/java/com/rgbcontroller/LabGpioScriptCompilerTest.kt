package com.rgbcontroller

import com.rgbcontroller.core.programs.LabGpioScriptCompiler
import com.rgbcontroller.core.lab.LabExperimentLibrary
import org.junit.Assert.*
import org.junit.Test

class LabGpioScriptCompilerTest {
    @Test fun compilesScriptToExistingFirmwareContract() {
        val script = "# Comentario\nGPIO 19 HIGH\nWAIT 500\nGPIO 19 LOW\nWAIT 500\nSTOP"
        val output = LabGpioScriptCompiler.compile(script)
        assertEquals(19, output.pin)
        assertEquals("1:500,0:500", output.sequence)
        assertEquals(2, output.stepCount)
        assertEquals(1000, output.durationMs)
        assertNotNull(LabExperimentLibrary.parse(output.sequence))
    }

    @Test fun rejectsRgbPinsAndOtherUnapprovedPins() {
        for (pin in listOf(0, 6, 12, 25, 26, 27, 34)) {
            assertThrows(IllegalArgumentException::class.java) {
                LabGpioScriptCompiler.compile("GPIO $pin HIGH\nWAIT 500")
            }
        }
    }

    @Test fun rejectsUntimedOrMixedPins() {
        for (script in listOf(
            "GPIO 19 HIGH",
            "WAIT 500",
            "GPIO 19 HIGH\nGPIO 19 LOW\nWAIT 500",
            "GPIO 19 HIGH\nWAIT 500\nGPIO 18 LOW\nWAIT 500",
            "GPIO 19 HIGH\nWAIT 500\nSTOP\nGPIO 19 LOW\nWAIT 500"
        )) {
            assertThrows(IllegalArgumentException::class.java) {
                LabGpioScriptCompiler.compile(script)
            }
        }
    }

    @Test fun preventsOutOfRangeAndOverBudgetSequences() {
        for (script in listOf(
            "GPIO 19 HIGH\nWAIT 99",
            "GPIO 19 HIGH\nWAIT 2001",
            List(11) { "GPIO 19 HIGH\nWAIT 2000" }.joinToString("\n"),
            List(17) { "GPIO 19 HIGH\nWAIT 100" }.joinToString("\n"),
            "GPIO 19 HIGH\nWAIT 500\nRGB 255 0 0"
        )) {
            assertThrows(IllegalArgumentException::class.java) {
                LabGpioScriptCompiler.compile(script)
            }
        }
    }

    @Test fun allowsCommentsBlankLinesAndFinalStop() {
        val output = LabGpioScriptCompiler.compile("\n# test\nGPIO 18 LOW # seguro\nWAIT 100\n\nSTOP")
        assertEquals("0:100", output.sequence)
    }
}
