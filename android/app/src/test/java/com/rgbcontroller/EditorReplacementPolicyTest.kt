package com.rgbcontroller

import com.rgbcontroller.core.programs.EditorReplacementPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorReplacementPolicyTest {
    @Test fun emptyEditorCanImportWithoutWarning() {
        assertFalse(EditorReplacementPolicy.requiresConfirmation("", "RGB 255 0 0"))
        assertFalse(EditorReplacementPolicy.requiresConfirmation("  ", "RGB 255 0 0"))
    }

    @Test fun differentSourceRequiresConfirmation() {
        assertTrue(EditorReplacementPolicy.requiresConfirmation("RGB 255 0 0", "RGB 0 255 0"))
        assertTrue(EditorReplacementPolicy.requiresConfirmation("void setup() {}", "GPIO 19 HIGH"))
    }

    @Test fun importingIdenticalSourceDoesNotWarn() {
        assertFalse(EditorReplacementPolicy.requiresConfirmation("RGB 255 0 0", "RGB 255 0 0"))
    }
}
