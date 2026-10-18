package com.rgbcontroller

import com.rgbcontroller.core.ble.BleRgbPermission
import com.rgbcontroller.core.ble.BleSecurityState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BleRgbPermissionTest {
    @Test fun connectedDoesNotAuthorizePhysicalRgbWrites() {
        val connected = BleSecurityState.Bonded
        for (command in listOf("ON", "OFF", "SET,0,112,255,255", "FX,STOP", "FX,RAINBOW,200")) {
            assertFalse(command, BleRgbPermission.permits(true, connected, command))
        }
        assertTrue(BleRgbPermission.permits(true, connected, "GET"))
    }

    @Test fun unauthorizedOrFailedCannotActEvenWhenGattReady() {
        for (state in BleSecurityState.entries.filter { it != BleSecurityState.Authorized }) {
            assertFalse(state.toString(), BleRgbPermission.permits(true, state, "SET,255,0,0,255"))
        }
    }

    @Test fun onlyAuthorizedReadyConnectionCanChangeRgb() {
        assertTrue(BleRgbPermission.permits(true, BleSecurityState.Authorized, "ON"))
        assertTrue(BleRgbPermission.permits(true, BleSecurityState.Authorized, "SET,1,2,3,255"))
        assertFalse(BleRgbPermission.permits(false, BleSecurityState.Authorized, "ON"))
        assertFalse(BleRgbPermission.permits(true, BleSecurityState.Authorized, "AUTH legacykey"))
        assertFalse(BleRgbPermission.permits(true, BleSecurityState.Authorized, "bad-command"))
    }
}
