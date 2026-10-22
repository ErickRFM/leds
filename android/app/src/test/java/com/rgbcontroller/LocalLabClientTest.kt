package com.rgbcontroller

import com.rgbcontroller.core.wifi.LocalLabClient
import org.junit.Assert.*
import org.junit.Test

class LocalLabClientTest {
    @Test fun keyAndWifiInputsAreBounded() {
        assertTrue(LocalLabClient.validKey("1234567890abcdef"))
        assertFalse(LocalLabClient.validKey("1234567890abcde"))
        assertFalse(LocalLabClient.validKey("not a secret"))
        assertTrue(LocalLabClient.validSsid("Taller"))
        assertFalse(LocalLabClient.validSsid(""))
        assertFalse(LocalLabClient.validSsid("a".repeat(33)))
        assertTrue(LocalLabClient.validPassword("abcdefgh"))
        assertTrue(LocalLabClient.validPassword(""))
        assertFalse(LocalLabClient.validPassword("1234567"))
        assertFalse(LocalLabClient.validPassword("x".repeat(64)))
    }

    @Test fun validatesBoundedDeclarativeSteps() {
        assertTrue(LocalLabClient.validSequence("1:500,0:500,1:300"))
        assertFalse(LocalLabClient.validSequence("1:500,"))
        assertFalse(LocalLabClient.validSequence("1:50,0:500"))
        assertFalse(LocalLabClient.validSequence("1:2500"))
        assertFalse(LocalLabClient.validSequence("2:500"))
        assertFalse(LocalLabClient.validSequence("1:2000,".repeat(17)))
        assertFalse(LocalLabClient.validSequence("1:2000,".repeat(11).dropLast(1)))
    }

    @Test fun pinPoliciesRejectDangerousValues() {
        assertTrue(LocalLabClient.allowedOutput(18))
        assertFalse(LocalLabClient.allowedOutput(0))
        assertFalse(LocalLabClient.allowedOutput(34))
        assertFalse(LocalLabClient.allowedOutput(6))
        assertTrue(LocalLabClient.allowedAnalog(34))
        assertFalse(LocalLabClient.allowedAnalog(25))
        assertFalse(LocalLabClient.allowedAnalog(18))
        assertTrue(LocalLabClient.allowedInput(34))
    }
}
