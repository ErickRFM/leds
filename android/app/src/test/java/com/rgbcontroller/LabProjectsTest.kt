package com.rgbcontroller

import com.rgbcontroller.core.lab.LabProject
import com.rgbcontroller.core.lab.LabProjects
import org.junit.Assert.*
import org.junit.Test

class LabProjectsTest {
    @Test fun saveLoadOverwriteAndDeleteRoundTrip() {
        var projects = LabProjects.upsert(emptyList(), "Taller RGB", "1:500,0:500")
        projects = LabProjects.upsert(projects, "Botón", "1:100,0:100")
        projects = LabProjects.upsert(projects, "taller rgb", "1:250,0:250")
        assertEquals(2, projects.size)
        assertEquals("1:250,0:250", projects.first().sequence)
        val raw = LabProjects.encode(projects)
        assertFalse(raw.contains("Taller RGB")) // names encoded, no plain text leaks
        assertEquals(projects, LabProjects.decode(raw))
        assertEquals(1, LabProjects.remove(projects, "BOTÓN").size)
    }

    @Test fun capacityAndPayloadValidationAreEnforced() {
        var projects = emptyList<LabProject>()
        for (n in 1..8) projects = LabProjects.upsert(projects, "Proyecto " + n, "1:500")
        assertThrows(IllegalArgumentException::class.java) {
            LabProjects.upsert(projects, "Proyecto 9", "1:500")
        }
        assertThrows(IllegalArgumentException::class.java) {
            LabProjects.upsert(projects, "Mal", "1:10")
        }
        assertThrows(IllegalArgumentException::class.java) {
            LabProjects.upsert(emptyList(), "\n", "1:500")
        }
        assertTrue(LabProjects.decode("broken").isEmpty())
        assertTrue(LabProjects.decode("a".repeat(5000)).isEmpty())
        assertEquals(emptyList<LabProject>(), LabProjects.decode(""))
    }
}
