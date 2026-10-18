package com.rgbcontroller

import com.rgbcontroller.core.ble.BleConnectionState
import com.rgbcontroller.core.ble.BleRgbControlPolicy
import com.rgbcontroller.core.ble.BleSecurityState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BleRgbControlPolicyTest {
    @Test
    fun connectedAndBondedAreNeverEnoughToTurnOnRgb() {
        for (state in BleSecurityState.values()) {
            if (state == BleSecurityState.Authorized) continue
            assertFalse(
                "Control enabled without firmware owner proof: $state",
                BleRgbControlPolicy.allowed(BleConnectionState.Ready, state, "SET,255,0,0,255")
            )
            assertFalse(
                BleRgbControlPolicy.allowed(BleConnectionState.Ready, state, "ON")
            )
        }
    }

    @Test
    fun approvedOwnerCanApplyRgbAndEffects() {
        for (command in listOf("ON", "OFF", "GET", "SET,255,0,0,255",
            "FX,STOP", "FX,RAINBOW,100")) {
            assertTrue(BleRgbControlPolicy.allowed(
                BleConnectionState.Ready, BleSecurityState.Authorized, command
            ))
        }
    }

    @Test
    fun disconnectImmediatelyRevokesControlAndLegacyTokensNeverWork() {
        assertFalse(BleRgbControlPolicy.allowed(
            BleConnectionState.Disconnected, BleSecurityState.Authorized, "SET,1,2,3,255"
        ))
        assertFalse(BleRgbControlPolicy.allowed(
            BleConnectionState.Connecting, BleSecurityState.Authorized, "ON"
        ))
        assertFalse(BleRgbControlPolicy.allowed(
            BleConnectionState.Ready, BleSecurityState.Authorized, "AUTH obsolete"
        ))
        assertFalse(BleRgbControlPolicy.allowed(
            BleConnectionState.Ready, BleSecurityState.Authorized, "SET,999,0,0,255"
        ))
    }
}
