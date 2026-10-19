package com.rgbcontroller

import com.rgbcontroller.core.ble.enrollment.ownerEnrollmentErrorMessage
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OwnerEnrollmentErrorMessageTest {
    @Test fun wrongCodeExplainsPhysicalWindowWithoutClaimingSuccess() {
        val message = ownerEnrollmentErrorMessage("bad_code")
        assertTrue(message.contains("no coincide"))
        assertTrue(message.contains("BOOT"))
        assertTrue(message.contains("2 minutos"))
        assertFalse(message.contains("autenticado", ignoreCase = true))
    }

    @Test fun expiredWindowRequiresFreshPhysicalCode() {
        assertTrue(ownerEnrollmentErrorMessage("window_expired").contains("caducó"))
        assertTrue(ownerEnrollmentErrorMessage("not_in_window").contains("PAIRING WINDOW"))
    }

    @Test fun ownerRecoveryRequiresPhysicalButton() {
        assertTrue(ownerEnrollmentErrorMessage("already_enrolled").contains("recuperación física"))
        assertTrue(ownerEnrollmentErrorMessage("not_in_recovery").contains("BOOT"))
    }

    @Test fun proofFailureDoesNotClaimOwnership() {
        assertTrue(ownerEnrollmentErrorMessage("bad_proof").contains("no se habilitó control"))
    }
}
