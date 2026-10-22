package com.rgbcontroller

import com.rgbcontroller.core.programs.ProgramLanguage
import com.rgbcontroller.core.programs.ProgramSourcePolicy
import java.io.ByteArrayInputStream
import org.junit.Assert.*
import org.junit.Test

class ProgramSourcePolicyTest {
    @Test fun importsUtf8AndStripsBom() {
        val bytes = "\uFEFFRGB 255 0 0\nWAIT 500".toByteArray(Charsets.UTF_8)
        assertEquals("RGB 255 0 0\nWAIT 500", ProgramSourcePolicy.readUtf8(ByteArrayInputStream(bytes)))
    }

    @Test fun rejectsOversizedFilesAndMalformedUtf8() {
        assertThrows(IllegalArgumentException::class.java) {
            ProgramSourcePolicy.readUtf8(ByteArrayInputStream(ByteArray(ProgramSourcePolicy.MAX_BYTES + 1)))
        }
        assertThrows(java.nio.charset.CharacterCodingException::class.java) {
            ProgramSourcePolicy.readUtf8(ByteArrayInputStream(byteArrayOf(0xC3.toByte(), 0x28)))
        }
    }

    @Test fun separatesArduinoFromDeclarativeSources() {
        assertEquals(ProgramLanguage.ARDUINO_CPP,
            ProgramSourcePolicy.inferLanguage("blink.ino", "void setup() {}"))
        assertEquals(ProgramLanguage.ARDUINO_CPP,
            ProgramSourcePolicy.inferLanguage("unknown.txt", "#include <Arduino.h>\nvoid loop() {}"))
        assertEquals(ProgramLanguage.LAB_SCRIPT,
            ProgramSourcePolicy.inferLanguage("sequence.txt", "RGB 255 0 0\nWAIT 500"))
    }

    @Test fun rejectsBinaryControlsAndLongOrEmptyInput() {
        assertFalse(ProgramSourcePolicy.inspect("RGB 255\u0000 0").acceptedForEditing)
        assertFalse(ProgramSourcePolicy.inspect("  \n").acceptedForEditing)
        assertFalse(ProgramSourcePolicy.inspect("a".repeat(ProgramSourcePolicy.MAX_BYTES + 1)).acceptedForEditing)
        assertTrue(ProgramSourcePolicy.inspect("GPIO 19 HIGH\nWAIT 500").acceptedForEditing)
    }

    @Test fun inspectionNeverClaimsExecutionOrCompilation() {
        val message = ProgramSourcePolicy.inspect("void setup() {}").explanation.lowercase()
        assertTrue(message.contains("no está compilado"))
    }
}
