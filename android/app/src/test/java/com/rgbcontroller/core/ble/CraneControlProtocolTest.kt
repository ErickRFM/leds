package com.rgbcontroller.core.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CraneControlProtocolTest {
    @Test
    fun parsesAcceptedStateReply() {
        val reply = CraneControlProtocol.parse("CRN,12,OK,90.0,45.5,REMOTE,1")

        assertEquals(12, reply?.requestId)
        assertTrue(reply?.accepted == true)
        assertEquals(90.0f, reply?.baseDeg)
        assertEquals(45.5f, reply?.armDeg)
        assertEquals("REMOTE", reply?.mode)
        assertTrue(reply?.moving == true)
        assertNull(reply?.error)
    }

    @Test
    fun parsesLocalStoppedReply() {
        val reply = CraneControlProtocol.parse("CRN,3,OK,10.0,170.0,LOCAL,0")

        assertEquals("LOCAL", reply?.mode)
        assertFalse(reply?.moving == true)
    }

    @Test
    fun parsesUnavailableErrorWithoutState() {
        val reply = CraneControlProtocol.parse("CRN,7,ERR,UNAVAILABLE")

        assertEquals(7, reply?.requestId)
        assertFalse(reply?.accepted == true)
        assertEquals("UNAVAILABLE", reply?.error)
        assertNull(reply?.baseDeg)
    }

    @Test
    fun parsesRejectedErrorWithState() {
        val reply = CraneControlProtocol.parse("CRN,9,ERR,REJECTED,80.0,60.0,REMOTE,0")

        assertFalse(reply?.accepted == true)
        assertEquals("REJECTED", reply?.error)
        assertEquals(80.0f, reply?.baseDeg)
        assertEquals("REMOTE", reply?.mode)
    }

    @Test
    fun rejectsMalformedFrames() {
        assertNull(CraneControlProtocol.parse("RGB,1,OK"))
        assertNull(CraneControlProtocol.parse("CRN"))
        assertNull(CraneControlProtocol.parse("CRN,0,OK,90,90,LOCAL,0"))
        assertNull(CraneControlProtocol.parse("CRN,65536,OK,90,90,LOCAL,0"))
        assertNull(CraneControlProtocol.parse("CRN,1,OK,90,90,SEQUENCE,0"))
        assertNull(CraneControlProtocol.parse("CRN,1,OK,90,90,LOCAL,2"))
        assertNull(CraneControlProtocol.parse("CRN,1,OK,NaN,90,LOCAL,0"))
        assertNull(CraneControlProtocol.parse("CRN,1,OK,181,90,LOCAL,0"))
        assertNull(CraneControlProtocol.parse("CRN,1,ERR,"))
        assertNull(CraneControlProtocol.parse("CRN,1,WHAT"))
    }
}
