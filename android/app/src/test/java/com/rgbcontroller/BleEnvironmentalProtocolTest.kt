package com.rgbcontroller

import com.rgbcontroller.core.ble.BleEnvironmentalProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BleEnvironmentalProtocolTest {
    @Test fun usesExactlyTheFirmwareSupportedCommands() {
        assertEquals("ENV,GET", BleEnvironmentalProtocol.Action.GET.frame)
        assertEquals("ENV,START", BleEnvironmentalProtocol.Action.START.frame)
        assertEquals("ENV,STOP", BleEnvironmentalProtocol.Action.STOP.frame)
    }

    @Test fun acceptsOnlyTheFramesTheFirmwareEmits() {
        // encodeBleEnvironmentalStatus emits ENV,STATE plus ten fields, and the
        // busy path is publishControlReply("ENV,BUSY"). ENV,ON and ENV,OFF came
        // from a pre-V31 firmware and must not stay accepted: they fabricated an
        // ENABLED state before the colour had been read back.
        assertNull(BleEnvironmentalProtocol.parseReply("ENV,ON"))
        assertNull(BleEnvironmentalProtocol.parseReply("ENV,OFF"))
        assertTrue(
            BleEnvironmentalProtocol.parseReply("ENV,BUSY")
                is BleEnvironmentalProtocol.Reply.Busy
        )
    }

    @Test fun reportsUnrecognizedFirmwareStateAsUnknown() {
        // A newer firmware state must never fall back to a success-looking state.
        val frame = "ENV,STATE,ON,RECALIBRATING,OK,Comfort,0,0,0,0,0,0"
        val snap = BleEnvironmentalProtocol.parseReply(frame)
            as BleEnvironmentalProtocol.Reply.State
        assertEquals(BleEnvironmentalProtocol.StateEnum.UNKNOWN, snap.snapshot.state)
        assertTrue(snap.snapshot.enabled)
        assertEquals("OK", snap.snapshot.reason)
    }

    @Test fun refusesTruncatedStateFrames() {
        val truncated = "ENV,STATE,ON,RUNNING,OK,Comfort,54,186,116,80,0"
        assertNull(BleEnvironmentalProtocol.parseReply(truncated))
    }

    @Test fun parsesFullFirmwareEnvironmentalStatusStateFrame() {
        val frame = "ENV,STATE,ON,RUNNING,OK,Comfort,54,186,116,80,0,0"
        val reply = BleEnvironmentalProtocol.parseReply(frame)
        assertTrue(reply is BleEnvironmentalProtocol.Reply.State)
        val snap = (reply as BleEnvironmentalProtocol.Reply.State).snapshot
        assertTrue(snap.enabled)
        assertEquals(BleEnvironmentalProtocol.StateEnum.RUNNING, snap.state)
        assertEquals("OK", snap.reason)
        assertEquals("Comfort", snap.band)
        assertEquals(54, snap.red)
        assertEquals(186, snap.green)
        assertEquals(116, snap.blue)
        assertEquals(80, snap.brightness)
        assertFalse(snap.humidityUsed)
        assertFalse(snap.lightUsed)
    }

    @Test fun parsesSuspendedAndConflictStateFrames() {
        val suspendedFrame = "ENV,STATE,ON,SUSPENDED,STALE_TEMPERATURE,Comfort,0,0,0,0,0,0"
        val reply1 = BleEnvironmentalProtocol.parseReply(suspendedFrame)
        assertTrue(reply1 is BleEnvironmentalProtocol.Reply.State)
        val snap1 = (reply1 as BleEnvironmentalProtocol.Reply.State).snapshot
        assertTrue(snap1.enabled)
        assertEquals(BleEnvironmentalProtocol.StateEnum.SUSPENDED, snap1.state)
        assertEquals("STALE_TEMPERATURE", snap1.reason)

        val conflictFrame = "ENV,STATE,ON,CONFLICT,MANUAL_OVERRIDE,Comfort,0,0,0,0,0,0"
        val reply2 = BleEnvironmentalProtocol.parseReply(conflictFrame)
        assertTrue(reply2 is BleEnvironmentalProtocol.Reply.State)
        val snap2 = (reply2 as BleEnvironmentalProtocol.Reply.State).snapshot
        assertTrue(snap2.enabled)
        assertEquals(BleEnvironmentalProtocol.StateEnum.CONFLICT, snap2.state)
        assertEquals("MANUAL_OVERRIDE", snap2.reason)
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
