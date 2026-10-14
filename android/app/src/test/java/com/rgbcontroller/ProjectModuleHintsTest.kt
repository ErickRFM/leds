package com.rgbcontroller

import com.rgbcontroller.core.programs.ProgramLanguage
import com.rgbcontroller.core.programs.ProjectModuleHints
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectModuleHintsTest {
    @Test
    fun rgbScriptSuggestsLedButNotSoil() {
        val result = ProjectModuleHints.suggestions(
            "RGB 255 0 0\nWAIT 500\nOFF\nSTOP",
            ProgramLanguage.LAB_SCRIPT, "RGB")
        assertEquals(1, result.size)
        assertTrue(result.first().contains("LED RGB"))
        assertTrue(ProjectModuleHints.suggestions(
            "GPIO 19 HIGH", ProgramLanguage.LAB_SCRIPT, "GPIO").isEmpty())
    }

    @Test
    fun arduinoOnlyAcceptsExplicitAnnotations() {
        val unknown = "void loop() { analogRead(34); }"
        assertTrue(ProjectModuleHints.suggestions(
            unknown, ProgramLanguage.ARDUINO_CPP, "GPIO").isEmpty())
        val documented = "// @compi-module input.adc1.34\n" + unknown
        assertTrue(ProjectModuleHints.suggestions(
            documented, ProgramLanguage.ARDUINO_CPP, "GPIO").isNotEmpty())
    }
}
