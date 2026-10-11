package com.rgbcontroller

import com.rgbcontroller.core.programs.EditorProject
import com.rgbcontroller.core.programs.EditorProjects
import com.rgbcontroller.core.programs.ProgramLanguage
import org.junit.Assert.*
import org.junit.Test

class EditorProjectsTest {
    private val sketch = "void setup() {}\nvoid loop() { delay(1000); }\n"
    private val lab = "RGB 255 0 0\nWAIT 300\nSTOP"

    @Test fun roundTripPreservesModeNameAndUnicode() {
        val entries = listOf(
            EditorProject("Arduino Prueba", ProgramLanguage.ARDUINO_CPP, sketch),
            EditorProject("LED azul", ProgramLanguage.LAB_SCRIPT, lab)
        )
        assertEquals(entries, EditorProjects.decode(EditorProjects.encode(entries)))
        assertEquals(emptyList<EditorProject>(), EditorProjects.decode(EditorProjects.encode(emptyList())))
    }

    @Test fun replacingProjectIsCaseInsensitiveAndKeepsOnlyLatest() {
        val first = EditorProjects.upsert(emptyList(), "Luces", ProgramLanguage.LAB_SCRIPT, lab)
        val updated = EditorProjects.upsert(first, "LUCES", ProgramLanguage.ARDUINO_CPP, sketch)
        assertEquals(1, updated.size)
        assertEquals(sketch, updated.single().source)
        assertEquals(0, EditorProjects.remove(updated, "luces").size)
    }

    @Test fun invalidNamesAndOversizeAreRejected() {
        for (name in listOf("", " ", "../secret", "A/B", "a\u0000b", "N".repeat(41))) {
            try {
                EditorProjects.upsert(emptyList(), name, ProgramLanguage.LAB_SCRIPT, lab)
                fail("Accepted invalid name $name")
            } catch (_: IllegalArgumentException) { /* rejected */ }
        }
        try {
            EditorProjects.upsert(emptyList(), "TooLarge", ProgramLanguage.ARDUINO_CPP,
                "x".repeat(ProgramSourcePolicyLimit()))
            fail("Source size cap not enforced")
        } catch (_: IllegalArgumentException) { /* expected */ }
    }

    @Test fun enforcesProjectCountAndNoLeaks() {
        var projects = emptyList<EditorProject>()
        repeat(EditorProjects.MAX_PROJECTS) { i ->
            projects = EditorProjects.upsert(projects, "Proyecto $i", ProgramLanguage.LAB_SCRIPT, lab)
        }
        assertEquals(8, projects.size)
        try {
            EditorProjects.upsert(projects, "Noveno", ProgramLanguage.LAB_SCRIPT, lab)
            fail("Allowed too many")
        } catch (_: IllegalArgumentException) { /* expected */ }
        assertFalse(EditorProjects.encode(projects).contains("RGB 255 0 0"))
    }

    @Test fun corruptionAndUnknownVersionFailClosed() {
        assertTrue(EditorProjects.decode("ESP32-PROJECTS-V999").isEmpty())
        assertTrue(EditorProjects.decode("ESP32-PROJECTS-V1\n-!$.LAB_SCRIPT.aGVsbG8").isEmpty())
        assertTrue(EditorProjects.decode("ESP32-PROJECTS-V1\ninvalid").isEmpty())
        val valid = EditorProjects.encode(listOf(EditorProject("Uno", ProgramLanguage.LAB_SCRIPT, lab)))
        assertTrue(EditorProjects.decode(valid + "\n" + valid.substringAfter('\n')).isEmpty())
    }

    private fun ProgramSourcePolicyLimit(): Int = 32769
}
