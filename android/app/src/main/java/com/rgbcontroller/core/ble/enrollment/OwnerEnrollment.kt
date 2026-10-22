package com.rgbcontroller.core.ble.enrollment

/**
 * Kotlin mirror of the firmware BLE owner-enrollment state machine
 * (`firmware/esp32_rgb/core/OwnerEnrollment.h`). Pure and deterministic: no
 * Android, no crypto primitives. The cryptographic proof is verified elsewhere
 * and only the resulting boolean is fed in, exactly like the firmware splits the
 * policy from mbedTLS.
 *
 * Nothing here authorizes a command by itself: [commandAllowed] is true only for
 * [OwnerState.AUTHENTICATED], and every incomplete/unknown path is fail-closed.
 */
enum class OwnerState {
    UNPAIRED,
    PAIRING_WINDOW,
    ENROLLED,
    AUTHENTICATED,
    REVOKED,
    RECOVERY
}

enum class EnrollmentOutcome {
    OK,
    NOT_IN_WINDOW,
    WINDOW_EXPIRED,
    BAD_CODE,
    ALREADY_ENROLLED,
    NOT_ENROLLED,
    NO_SESSION,
    BAD_PROOF,
    REPLAY,
    INVALID_ARGUMENT,
    REVOKED
}

enum class ChallengeOutcome { OK, NOT_ENROLLED, REVOKED }

/**
 * Owner-enrollment state machine. It never stores secrets itself: it tracks the
 * owner id, the current window deadline and the last accepted counter, and
 * reports the state the UI/transport must reflect.
 */
class OwnerEnrollment {

    var state: OwnerState = OwnerState.UNPAIRED
        private set
    var reason: String = "unpaired"
        private set
    var ownerId: String? = null
        private set
    var lastAcceptedCounter: Int = 0
        private set

    private var oneTimeCode: String? = null
    private var windowDeadlineMs: Long = 0
    private var baseStateOnEntry: OwnerState = OwnerState.UNPAIRED
    private var deviceNonce: String? = null

    val hasOwner: Boolean
        get() = state == OwnerState.ENROLLED || state == OwnerState.AUTHENTICATED

    val authenticated: Boolean
        get() = state == OwnerState.AUTHENTICATED

    fun windowOpen(nowMs: Long): Boolean =
        state == OwnerState.PAIRING_WINDOW && nowMs < windowDeadlineMs

    fun openPairingWindow(nowMs: Long, windowMs: Long, code: String): EnrollmentOutcome {
        if (state == OwnerState.ENROLLED || state == OwnerState.AUTHENTICATED) {
            reason = "already_enrolled"
            return EnrollmentOutcome.ALREADY_ENROLLED
        }
        if (!validOneTimeCode(code) || windowMs <= 0) {
            reason = "invalid_window"
            return EnrollmentOutcome.INVALID_ARGUMENT
        }
        oneTimeCode = code
        windowDeadlineMs = nowMs + windowMs
        baseStateOnEntry = if (state == OwnerState.REVOKED) OwnerState.REVOKED else OwnerState.UNPAIRED
        state = OwnerState.PAIRING_WINDOW
        reason = "pairing_window"
        return EnrollmentOutcome.OK
    }

    fun tick(nowMs: Long) {
        if (state != OwnerState.PAIRING_WINDOW) return
        if (nowMs < windowDeadlineMs) return
        oneTimeCode = null
        state = baseStateOnEntry
        reason = "window_expired"
    }

    fun enroll(
        ownerId: String,
        ownerPubKeyHex: String,
        presentedCode: String,
        nowMs: Long
    ): EnrollmentOutcome {
        if (state != OwnerState.PAIRING_WINDOW) {
            reason = "not_in_window"
            return EnrollmentOutcome.NOT_IN_WINDOW
        }
        if (nowMs >= windowDeadlineMs) {
            closeExpiredWindow()
            reason = "window_expired"
            return EnrollmentOutcome.WINDOW_EXPIRED
        }
        if (!validOwnerId(ownerId) || !validPubKeyHex(ownerPubKeyHex)) {
            reason = "invalid_arguments"
            return EnrollmentOutcome.INVALID_ARGUMENT
        }
        if (presentedCode != oneTimeCode) {
            reason = "bad_code"
            return EnrollmentOutcome.BAD_CODE
        }
        this.ownerId = ownerId
        this.oneTimeCode = null
        this.lastAcceptedCounter = 0
        this.deviceNonce = null
        state = OwnerState.ENROLLED
        reason = "enrolled"
        return EnrollmentOutcome.OK
    }

    fun beginSession(deviceNonceHex: String): ChallengeOutcome {
        if (state == OwnerState.REVOKED) {
            reason = "revoked"
            return ChallengeOutcome.REVOKED
        }
        if (!hasOwner) {
            reason = "not_enrolled"
            return ChallengeOutcome.NOT_ENROLLED
        }
        if (!validNonceHex(deviceNonceHex)) {
            reason = "invalid_nonce"
            return ChallengeOutcome.NOT_ENROLLED
        }
        this.deviceNonce = deviceNonceHex
        state = OwnerState.ENROLLED
        reason = "challenge_issued"
        return ChallengeOutcome.OK
    }

    fun completeSession(
        deviceNonceHex: String,
        clientNonceHex: String,
        counter: Int,
        proofValid: Boolean
    ): EnrollmentOutcome {
        if (state == OwnerState.REVOKED) {
            reason = "revoked"
            return EnrollmentOutcome.REVOKED
        }
        if (!hasOwner) {
            reason = "not_enrolled"
            return EnrollmentOutcome.NOT_ENROLLED
        }
        val pending = deviceNonce
        if (pending == null) {
            reason = "no_session"
            return EnrollmentOutcome.NO_SESSION
        }
        if (pending != deviceNonceHex) {
            reason = "bad_challenge"
            return EnrollmentOutcome.BAD_PROOF
        }
        if (!validNonceHex(clientNonceHex)) {
            reason = "invalid_nonce"
            return EnrollmentOutcome.INVALID_ARGUMENT
        }
        if (counter <= lastAcceptedCounter) {
            reason = "replay"
            return EnrollmentOutcome.REPLAY
        }
        if (!proofValid) {
            reason = "bad_proof"
            return EnrollmentOutcome.BAD_PROOF
        }
        lastAcceptedCounter = counter
        deviceNonce = null
        state = OwnerState.AUTHENTICATED
        reason = "authenticated"
        return EnrollmentOutcome.OK
    }

    fun onDisconnect() {
        deviceNonce = null
        if (state == OwnerState.AUTHENTICATED) {
            state = OwnerState.ENROLLED
            reason = "disconnected"
        }
    }

    fun revoke() {
        deviceNonce = null
        state = OwnerState.REVOKED
        reason = "revoked"
    }

    fun openRecovery(nowMs: Long, windowMs: Long, code: String): EnrollmentOutcome {
        if (!validOneTimeCode(code) || windowMs <= 0) {
            reason = "invalid_window"
            return EnrollmentOutcome.INVALID_ARGUMENT
        }
        oneTimeCode = code
        windowDeadlineMs = nowMs + windowMs
        baseStateOnEntry = OwnerState.REVOKED
        state = OwnerState.RECOVERY
        reason = "recovery"
        return EnrollmentOutcome.OK
    }

    fun factoryReset(nowMs: Long, presentedCode: String): EnrollmentOutcome {
        if (state != OwnerState.RECOVERY) {
            reason = "not_in_recovery"
            return EnrollmentOutcome.NOT_IN_WINDOW
        }
        if (windowDeadlineMs != 0L && nowMs >= windowDeadlineMs) {
            oneTimeCode = null
            state = OwnerState.REVOKED
            reason = "window_expired"
            return EnrollmentOutcome.WINDOW_EXPIRED
        }
        val expected = oneTimeCode
        if (expected != null && presentedCode != expected) {
            reason = "bad_code"
            return EnrollmentOutcome.BAD_CODE
        }
        ownerId = null
        oneTimeCode = null
        deviceNonce = null
        lastAcceptedCounter = 0
        state = OwnerState.UNPAIRED
        reason = "factory_reset"
        return EnrollmentOutcome.OK
    }

    private fun closeExpiredWindow() {
        oneTimeCode = null
        state = if (hasOwner) OwnerState.ENROLLED else baseStateOnEntry
    }

    companion object {
        const val OWNER_ID_MAX = 32
        const val PUBKEY_HEX_LENGTH = 64
        const val NONCE_HEX_MAX = 32
        const val ONE_TIME_CODE_LENGTH = 8
        const val DEFAULT_WINDOW_MS = 120_000L

        fun commandAllowed(state: OwnerState): Boolean = state == OwnerState.AUTHENTICATED

        private fun isHex(text: String): Boolean =
            text.isNotEmpty() && text.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }

        private fun validOneTimeCode(code: String?): Boolean =
            code != null && code.length == ONE_TIME_CODE_LENGTH &&
                code.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }

        private fun validOwnerId(ownerId: String): Boolean =
            ownerId.isNotEmpty() && ownerId.length <= OWNER_ID_MAX &&
                ownerId.all { it.isLetterOrDigit() || it == '-' || it == '_' }

        private fun validPubKeyHex(hex: String): Boolean =
            hex.length == PUBKEY_HEX_LENGTH && isHex(hex)

        private fun validNonceHex(hex: String): Boolean =
            hex.length in 1..NONCE_HEX_MAX && isHex(hex)
    }
}
