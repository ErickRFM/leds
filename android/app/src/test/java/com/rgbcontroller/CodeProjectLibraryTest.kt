package com.rgbcontroller

import com.rgbcontroller.core.programs.CodeProject
import com.rgbcontroller.core.programs.CodeProjectLibrary
import com.rgbcontroller.core.programs.ProgramLanguage
import org.junit.Assert.*
import org.junit.Test

class CodeProjectLibraryTest {
    @Test fun unicodeAndMultilineSourceRoundTripWithoutChanges() {
        val source = "# Encender 🟣\nRGB 255 0 128\nWAIT 500\n"
        val project = CodeProject("Experimento á", ProgramLanguage.LAB_SCRIPT, source, 12345)
        val encoded = CodeProjectLibrary.encode(listOf(project))
        assertEquals(listOf(project), CodeProjectLibrary.decode(encoded))
        assertEquals("CODE_PROJECTS_V1", CodeProjectLibrary.encode(emptyList()))
    }

    @Test fun updateKeepsExistingOrderAndOtherProjects() {
        val a = CodeProject("Alpha", ProgramLanguage.ARDUINO_CPP, "void loop() {}", 10)
        val b = CodeProject("Beta", ProgramLanguage.LAB_SCRIPT, "GPIO 19 HIGH\nWAIT 500", 20)
        val revised = a.copy(name = "alpha", source = "void setup() {}", updatedAtMillis = 30)
        val updated = CodeProjectLibrary.upsert(listOf(a, b), revised)
        assertEquals(listOf(revised, b), updated)
    }

    @Test fun rejectsOversizedOrMalformedProjects() {
        assertThrows(IllegalArgumentException::class.java) {
            CodeProjectLibrary.encode(listOf(
                CodeProject("../path", ProgramLanguage.LAB_SCRIPT, "WAIT 500", 0)
            ))
        }
        assertThrows(IllegalArgumentException::class.java) {
            CodeProjectLibrary.encode(listOf(
                CodeProject("Large", ProgramLanguage.ARDUINO_CPP,
                    "x".repeat(32 * 1024 + 1), 1)
            ))
        }
        assertThrows(IllegalArgumentException::class.java) {
            CodeProjectLibrary.decode("CODE_PROJECTS_V2\nfake")
        }
    }

    @Test fun corruptionDoesNotReturnAnEmptyList() {
        val valid = CodeProjectLibrary.encode(listOf(
            CodeProject("P", ProgramLanguage.LAB_SCRIPT, "ON", 1)
        ))
        assertThrows(Exception::class.java) { CodeProjectLibrary.decode(valid + "\ncorrupt") }
        assertThrows(Exception::class.java) { CodeProjectLibrary.decode("CODE_PROJECTS_V1\n%%%%") }
    }

    @Test fun enforcesProjectLimit() {
        val twelve = (1..12).map {
            CodeProject("p$it", ProgramLanguage.LAB_SCRIPT, "WAIT 500", it.toLong())
        }
        assertEquals(12, CodeProjectLibrary.decode(CodeProjectLibrary.encode(twelve)).size)
        assertThrows(IllegalArgumentException::class.java) {
            CodeProjectLibrary.upsert(
                twelve, CodeProject("p13", ProgramLanguage.ARDUINO_CPP, "void loop() {}", 13)
            )
        }
    }
}
