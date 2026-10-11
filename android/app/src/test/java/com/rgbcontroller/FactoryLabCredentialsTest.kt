package com.rgbcontroller

import com.rgbcontroller.core.model.FactoryLabCredentials
import org.junit.Assert.*
import org.junit.Test

class FactoryLabCredentialsTest {
    private val label =
        """{"ssid":"ESP32-Control-Lab","password":"1234567890abcdef","deviceKey":"abcdef1234567890"}"""

    @Test fun importValidPrivateLabel() {
        val value = FactoryLabCredentials.parse(label)
        assertNotNull(value)
        assertEquals("ESP32-Control-Lab", value!!.ssid)
        assertEquals("1234567890abcdef", value.wifiPassword)
        assertEquals("abcdef1234567890", value.controlKey)
        assertFalse(value.toString().contains("1234567890abcdef"))
    }

    @Test fun rejectMissingWeakAndMaliciousLabels() {
        assertNull(FactoryLabCredentials.parse(""))
        assertNull(FactoryLabCredentials.parse("{}"))
        assertNull(FactoryLabCredentials.parse(label.replace("ESP32-Control-Lab", "Other-Network")))
        assertNull(FactoryLabCredentials.parse(label.replace("1234567890abcdef", "12345678")))
        assertNull(FactoryLabCredentials.parse(label.replace("abcdef1234567890", "1234567890abcdef")))
        assertNull(FactoryLabCredentials.parse("x".repeat(1025)))
        assertNull(FactoryLabCredentials.parse(label.replace("abcdef1234567890", "G".repeat(16))))
    }
}
