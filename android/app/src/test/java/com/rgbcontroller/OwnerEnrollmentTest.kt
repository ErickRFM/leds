package com.rgbcontroller

import com.rgbcontroller.core.ble.enrollment.ChallengeOutcome
import com.rgbcontroller.core.ble.enrollment.EnrollmentOutcome
import com.rgbcontroller.core.ble.enrollment.OwnerEnrollment
import com.rgbcontroller.core.ble.enrollment.OwnerState
import org.junit.Assert.*
import org.junit.Test

class OwnerEnrollmentTest {

    private val pubA =
        "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
    private val pubB =
        "fedcba9876543210fedcba9876543210fedcba9876543210fedcba9876543210"
    private val devNonce = "00112233445566778899aabbccddeeff"
    private val clientNonce = "ffeeddccbbaa99887766554433221100"
    private val code = "deadbeef"

    @Test
    fun freshDeviceIsUnpairedAndFailClosed() {
        val e = OwnerEnrollment()
        assertEquals(OwnerState.UNPAIRED, e.state)
        assertFalse(e.hasOwner)
        assertFalse(e.authenticated)
        assertFalse(OwnerEnrollment.commandAllowed(e.state))
    }

    @Test
    fun enrollingWithoutWindowIsRefused() {
        val e = OwnerEnrollment()
        assertEquals(EnrollmentOutcome.NOT_IN_WINDOW, e.enroll("owner-1", pubA, code, 1000))
    }

    @Test
    fun windowValidatesCodeAndDuration() {
        val e = OwnerEnrollment()
        assertEquals(EnrollmentOutcome.INVALID_ARGUMENT, e.openPairingWindow(1000, 0, code))
        assertEquals(EnrollmentOutcome.INVALID_ARGUMENT, e.openPairingWindow(1000, 120000, "nope!!!"))
        assertEquals(EnrollmentOutcome.OK, e.openPairingWindow(1000, 120000, code))
        assertTrue(e.windowOpen(1000))
        assertTrue(e.windowOpen(120999))
        assertFalse(e.windowOpen(121000))
    }

    @Test
    fun wrongCodeGrantsNothing() {
        val e = OwnerEnrollment()
        e.openPairingWindow(1000, 120000, code)
        assertEquals(EnrollmentOutcome.BAD_CODE, e.enroll("owner-1", pubA, "00000000", 1100))
        assertEquals(OwnerState.PAIRING_WINDOW, e.state)
        assertFalse(e.hasOwner)
    }

    @Test
    fun malformedArgumentsAreRefused() {
        val e = OwnerEnrollment()
        e.openPairingWindow(1000, 120000, code)
        assertEquals(EnrollmentOutcome.INVALID_ARGUMENT, e.enroll("bad id!", pubA, code, 1100))
        assertEquals(EnrollmentOutcome.INVALID_ARGUMENT, e.enroll("owner-1", "abcd", code, 1100))
    }

    @Test
    fun enrollmentLandsInEnrolledNotAuthenticated() {
        val e = OwnerEnrollment()
        e.openPairingWindow(1000, 120000, code)
        assertEquals(EnrollmentOutcome.OK, e.enroll("owner-1", pubA, code, 1100))
        assertEquals(OwnerState.ENROLLED, e.state)
        assertTrue(e.hasOwner)
        assertFalse(e.authenticated)
        assertFalse(OwnerEnrollment.commandAllowed(e.state))
        assertEquals("owner-1", e.ownerId)
    }

    @Test
    fun secondWindowBlockedWhileOwnerExists() {
        val e = OwnerEnrollment()
        e.openPairingWindow(1000, 120000, code)
        e.enroll("owner-1", pubA, code, 1100)
        assertEquals(EnrollmentOutcome.ALREADY_ENROLLED, e.openPairingWindow(1200, 120000, code))
    }

    @Test
    fun sessionRequiresChallengeAndValidProof() {
        val e = OwnerEnrollment()
        e.openPairingWindow(1000, 120000, code)
        e.enroll("owner-1", pubA, code, 1100)

        assertEquals(EnrollmentOutcome.NO_SESSION, e.completeSession(devNonce, clientNonce, 1, true))
        assertEquals(ChallengeOutcome.NOT_ENROLLED, e.beginSession("zz"))
        assertEquals(ChallengeOutcome.OK, e.beginSession(devNonce))

        assertEquals(EnrollmentOutcome.BAD_PROOF, e.completeSession("00", clientNonce, 1, true))
        assertFalse(e.authenticated)
        assertEquals(EnrollmentOutcome.BAD_PROOF, e.completeSession(devNonce, clientNonce, 1, false))
        assertFalse(e.authenticated)

        assertEquals(EnrollmentOutcome.OK, e.completeSession(devNonce, clientNonce, 1, true))
        assertEquals(OwnerState.AUTHENTICATED, e.state)
        assertTrue(OwnerEnrollment.commandAllowed(e.state))
        assertEquals(1, e.lastAcceptedCounter)
    }

    @Test
    fun replayAndRevocationAreRejected() {
        val e = OwnerEnrollment()
        e.openPairingWindow(1000, 120000, code)
        e.enroll("owner-1", pubA, code, 1100)
        e.beginSession(devNonce)
        e.completeSession(devNonce, clientNonce, 1, true)

        e.onDisconnect()
        assertEquals(OwnerState.ENROLLED, e.state)
        assertFalse(e.authenticated)

        e.beginSession(devNonce)
        assertEquals(EnrollmentOutcome.REPLAY, e.completeSession(devNonce, clientNonce, 1, true))
        assertFalse(e.authenticated)
        assertEquals(EnrollmentOutcome.OK, e.completeSession(devNonce, clientNonce, 2, true))
        assertTrue(e.authenticated)

        e.revoke()
        assertEquals(OwnerState.REVOKED, e.state)
        assertEquals(ChallengeOutcome.REVOKED, e.beginSession(devNonce))
        assertEquals(EnrollmentOutcome.REVOKED, e.completeSession(devNonce, clientNonce, 3, true))
        assertEquals(EnrollmentOutcome.NOT_IN_WINDOW, e.enroll("owner-2", pubB, code, 1400))
    }

    @Test
    fun recoveryAndFactoryResetWipeOwner() {
        val e = OwnerEnrollment()
        e.openPairingWindow(1000, 120000, code)
        e.enroll("owner-1", pubA, code, 1100)
        e.revoke()

        assertEquals(EnrollmentOutcome.OK, e.openRecovery(1400, 120000, code))
        assertEquals(OwnerState.RECOVERY, e.state)
        assertEquals(EnrollmentOutcome.BAD_CODE, e.factoryReset(1500, "00000000"))
        assertEquals(OwnerState.RECOVERY, e.state)
        assertEquals(EnrollmentOutcome.OK, e.factoryReset(1500, code))
        assertEquals(OwnerState.UNPAIRED, e.state)
        assertFalse(e.hasOwner)
        assertNull(e.ownerId)
    }

    @Test
    fun expiredWindowReturnsToBaseState() {
        val e = OwnerEnrollment()
        assertEquals(EnrollmentOutcome.OK, e.openPairingWindow(100, 50, code))
        // Still open at exactly the deadline.
        assertEquals(EnrollmentOutcome.WINDOW_EXPIRED, e.enroll("owner-1", pubA, code, 150))
        assertEquals(OwnerState.UNPAIRED, e.state)
    }

    @Test
    fun expiredWindowTickClosesIt() {
        val e = OwnerEnrollment()
        e.openPairingWindow(100, 50, code)
        e.tick(200)
        assertEquals(OwnerState.UNPAIRED, e.state)
        assertFalse(e.windowOpen(200))
    }
}
