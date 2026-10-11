package com.rgbcontroller

import com.rgbcontroller.core.lab.LabExperimentLibrary
import com.rgbcontroller.core.lab.LabStep
import org.junit.Assert.*
import org.junit.Test

class LabExperimentLibraryTest {
    @Test fun templatesAreExecutableAndBounded() {
        for (template in LabExperimentLibrary.templates) {
            assertNotNull(template.name, LabExperimentLibrary.parse(template.sequence))
        }
    }

    @Test fun visualEditsRoundTripThroughValidatedParser() {
        val first = LabExperimentLibrary.append("", LabStep(true, 500))
        assertEquals("1:500", first)
        val second = LabExperimentLibrary.append(first!!, LabStep(false, 250))
        assertEquals("1:500,0:250", second)
        assertEquals(listOf(LabStep(true, 500), LabStep(false, 250)), LabExperimentLibrary.parse(second!!))
        assertEquals("0:250", LabExperimentLibrary.removeAt(second, 0))
        assertEquals("", LabExperimentLibrary.removeAt("1:500", 0))
    }

    @Test fun invalidInputCannotEscapeHardwareLimits() {
        assertNull(LabExperimentLibrary.append("1:500", LabStep(true, 99)))
        assertNull(LabExperimentLibrary.append("1:2000,".repeat(10).dropLast(1), LabStep(true, 2000)))
        assertNull(LabExperimentLibrary.append("1:500,".repeat(16).dropLast(1), LabStep(true, 500)))
        assertNull(LabExperimentLibrary.removeAt("1:500", 2))
        assertNull(LabExperimentLibrary.parse("1:0500"))
        assertNull(LabExperimentLibrary.parse("1:500\n0:500"))
        assertNull(LabExperimentLibrary.parse("1:500,2:500"))
    }
}
