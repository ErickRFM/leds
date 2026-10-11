package com.rgbcontroller

import com.rgbcontroller.core.programs.LabRgbScriptPolicy
import org.junit.Assert.*
import org.junit.Test

class LabRgbScriptPolicyTest {
    @Test fun acceptsRgbScriptAndComments() {
        val valid = LabRgbScriptPolicy.validate("# escena\nRGB 255 0 0\nWAIT 500\nRGB 0 0 255 120\nSTOP")
        assertTrue(valid.message, valid.accepted)
        assertEquals(4, valid.steps)
    }
    @Test fun rejectsCppUnsafeCommandsAndOversize() {
        for (source in listOf("void setup() {}", "GPIO 27 HIGH", "RGB 1 2 300",
                "RGB 1 2 3 256", "WAIT 10\nON", "ON\nSTOP\nOFF",
                "WAIT 500", "ON\nWAIT 2001", "ON\nWAIT -1",
                "ON\n".repeat(33) + "STOP", "a".repeat(513))) {
            assertFalse(source, LabRgbScriptPolicy.validate(source).accepted)
        }
    }
    @Test fun timeBudgetAndDecimalLimits() {
        assertTrue(LabRgbScriptPolicy.validate("ON\n" + "WAIT 2000\n".repeat(10)).accepted)
        assertFalse(LabRgbScriptPolicy.validate("ON\n" + "WAIT 2000\n".repeat(11)).accepted)
        assertTrue(LabRgbScriptPolicy.validate("RGB 0 0 0\nOFF").accepted)
    }
}
