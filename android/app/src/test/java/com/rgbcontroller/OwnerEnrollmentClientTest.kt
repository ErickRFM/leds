package com.rgbcontroller

import com.rgbcontroller.core.ble.enrollment.EnrollmentReply
import com.rgbcontroller.core.ble.enrollment.OwnerEnrollmentClient
import com.rgbcontroller.core.security.BouncyCastleSessionCrypto
import com.rgbcontroller.core.security.Hex
import com.rgbcontroller.core.security.SessionKeySchedule
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OwnerEnrollmentClientTest {

    private val crypto = BouncyCastleSessionCrypto()

    @Test
    fun ecdhIsSymmetric() {
        val a = crypto.generateKeyPair()
        val b = crypto.generateKeyPair()
        assertArrayEquals(
            crypto.sharedSecret(a.privateKey, b.publicKey),
            crypto.sharedSecret(b.privateKey, a.publicKey),
        )
    }

    @Test
    fun keyScheduleAgreesOnBothSides() {
        val board = crypto.generateKeyPair()
        val owner = crypto.generateKeyPair()
        val deviceNonce = crypto.randomBytes(SessionKeySchedule.NONCE_BYTES)
        val clientNonce = crypto.randomBytes(SessionKeySchedule.NONCE_BYTES)

        val boardSide = SessionKeySchedule.derive(
            crypto, board.privateKey, owner.publicKey, deviceNonce, clientNonce, 7,
        )
        val clientSide = SessionKeySchedule.derive(
            crypto, owner.privateKey, board.publicKey, deviceNonce, clientNonce, 7,
        )

        assertArrayEquals(boardSide.sessionKey, clientSide.sessionKey)
        assertArrayEquals(boardSide.proof, clientSide.proof)
        assertEquals(32, boardSide.proof.size)
    }

    @Test
    fun differentCountersProduceDifferentProofs() {
        val board = crypto.generateKeyPair()
        val owner = crypto.generateKeyPair()
        val deviceNonce = crypto.randomBytes(SessionKeySchedule.NONCE_BYTES)
        val clientNonce = crypto.randomBytes(SessionKeySchedule.NONCE_BYTES)

        val one = SessionKeySchedule.derive(
            crypto, board.privateKey, owner.publicKey, deviceNonce, clientNonce, 1,
        )
        val two = SessionKeySchedule.derive(
            crypto, board.privateKey, owner.publicKey, deviceNonce, clientNonce, 2,
        )
        assertFalse(SessionKeySchedule.constantTimeEquals(one.proof, two.proof))
    }

    @Test
    fun clientCompletesBoardHandshake() {
        val board = crypto.generateKeyPair()
        val owner = crypto.generateKeyPair()
        val client = OwnerEnrollmentClient(crypto, "pixel-1", owner, lastCounter = 0)

        val pair = client.pairLine("deadbeef")
        assertEquals("PAIR deadbeef pixel-1 ${Hex.encode(owner.publicKey)}", pair)

        val deviceNonce = crypto.randomBytes(SessionKeySchedule.NONCE_BYTES)
        val challenge = EnrollmentReply.Challenge(
            Hex.encode(deviceNonce),
            Hex.encode(board.publicKey),
        )

        client.beginLine()
        val auth = client.authLine(challenge)
        val tokens = auth.split(' ')
        assertEquals(4, tokens.size)
        assertEquals("AUTH", tokens[0])
        assertEquals(32, tokens[1].length)
        assertEquals("1", tokens[2])
        assertEquals(64, tokens[3].length)

        val clientNonce = Hex.decode(tokens[1], SessionKeySchedule.NONCE_BYTES)!!
        val boardView = SessionKeySchedule.derive(
            crypto, board.privateKey, owner.publicKey, deviceNonce, clientNonce, 1,
        )
        val presentedProof = Hex.decode(tokens[3], 32)!!
        assertTrue(SessionKeySchedule.constantTimeEquals(boardView.proof, presentedProof))

        client.onAuthenticated()
        assertEquals(1, client.lastCounter)
    }

    @Test
    fun counterIsStrictlyIncreasingAcrossHandshakes() {
        val board = crypto.generateKeyPair()
        val owner = crypto.generateKeyPair()
        val client = OwnerEnrollmentClient(crypto, "pixel-1", owner, lastCounter = 5)

        fun authCounter(): Int {
            val nonce = crypto.randomBytes(SessionKeySchedule.NONCE_BYTES)
            client.beginLine()
            val line = client.authLine(
                EnrollmentReply.Challenge(Hex.encode(nonce), Hex.encode(board.publicKey)),
            )
            return line.split(' ')[2].toInt()
        }

        assertEquals(6, authCounter())
        // A failed attempt must not advance the persisted counter.
        client.reset()
        assertEquals(6, authCounter())
        client.onAuthenticated()
        assertEquals(6, client.lastCounter)
        assertEquals(7, authCounter())
    }

    @Test
    fun challengeParsingIsStrict() {
        val nonce = "00".repeat(16)
        val pub = "ab".repeat(32)

        assertEquals(EnrollmentReply.Ok, EnrollmentReply.parse("OK"))
        assertEquals("bad_code", (EnrollmentReply.parse("ERR bad_code") as EnrollmentReply.Err).reason)

        val challenge = EnrollmentReply.parse("CHALLENGE $nonce $pub") as EnrollmentReply.Challenge
        assertEquals(Hex.encode(ByteArray(16)), challenge.deviceNonceHex)
        assertEquals(pub, challenge.devicePublicKeyHex)

        assertNull(EnrollmentReply.parse(""))
        assertNull(EnrollmentReply.parse("nope"))
        assertNull(EnrollmentReply.parse("OK extra"))
        assertNull(EnrollmentReply.parse("ERR"))
        assertNull(EnrollmentReply.parse("ERR bad code"))
        assertNull(EnrollmentReply.parse("CHALLENGE ${"00".repeat(15)} $pub"))
        assertNull(EnrollmentReply.parse("CHALLENGE ${"00".repeat(16)} ${"ab".repeat(31)}"))
        assertNull(EnrollmentReply.parse("CHALLENGE ${"00".repeat(16)} $pub extra"))
    }

    @Test
    fun pairLineRejectsInvalidInput() {
        val owner = crypto.generateKeyPair()
        val client = OwnerEnrollmentClient(crypto, "pixel-1", owner)
        assertFalse(runCatching { client.pairLine("short") }.isSuccess)
        assertFalse(runCatching { client.pairLine("zzzzzzzz") }.isSuccess)
    }
}
