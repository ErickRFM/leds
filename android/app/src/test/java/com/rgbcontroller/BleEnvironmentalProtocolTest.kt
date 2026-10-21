package com.rgbcontroller

import com.rgbcontroller.core.ble.BleEnvironmentalProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BleEnvironmentalProtocolTest {
    @Test fun usesExactlyTheFirmwareSupportedCommands() {
        assertEquals("ENV,GET", BleEnvironmentalProtocol.Action.GET.frame)
        assertEquals("ENV,START", BleEnvironmentalProtocol.Action.START.frame)
        assertEquals("ENV,STOP", BleEnvironmentalProtocol.Action.STOP.frame)
    }

    @Test fun recognizesFirmwareConfirmedStates() {
        assertEquals(
            BleEnvironmentalProtocol.Reply.State(true),
            BleEnvironmentalProtocol.parseReply("ENV,ON")
        )
        assertEquals(
            BleEnvironmentalProtocol.Reply.State(false),
            BleEnvironmentalProtocol.parseReply("ENV,OFF")
        )
        assertTrue(
            BleEnvironmentalProtocol.parseReply("ENV,BUSY")
                is BleEnvironmentalProtocol.Reply.Busy
        )
    }

    @Test fun neverTurnsUnrecognizedReplyIntoSuccess() {
        for (frame in listOf(
            "STATE,0,0,0,0,0,NONE,100", "ENV,ON ",
            "ENV,START", "ENV,FAIL", "ERR,INVALID_COMMAND", "", "OK"
        )) {
            assertNull(BleEnvironmentalProtocol.parseReply(frame))
        }
    }
}
