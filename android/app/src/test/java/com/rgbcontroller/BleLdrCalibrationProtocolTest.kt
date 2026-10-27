package com.rgbcontroller

import com.rgbcontroller.core.ble.BleLdrCalibrationProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BleLdrCalibrationProtocolTest {
    @Test fun requestIsStrictAndBounded() {
        assertEquals("LDR,17,500,3500", BleLdrCalibrationProtocol.request(17, 500, 3500))
        assertEquals("LDR,18,3500,500", BleLdrCalibrationProtocol.request(18, 3500, 500))
        for (bad in listOf(0, -1, 65536)) {
            try {
                BleLdrCalibrationProtocol.request(bad, 500, 3500)
                throw AssertionError("Should reject invalid request id")
            } catch (_: IllegalArgumentException) {}
        }
        try {
            BleLdrCalibrationProtocol.request(1, 200, 202)
            throw AssertionError("Should reject insufficient calibration range")
        } catch (_: IllegalArgumentException) {}
    }

    @Test fun onlyExactMatchingAckCanConfirm() {
        assertEquals(
            BleLdrCalibrationProtocol.Reply.Applied(17),
            BleLdrCalibrationProtocol.reply("LDR,OK,17")
        )
        assertEquals(
            BleLdrCalibrationProtocol.Reply.Rejected(17),
            BleLdrCalibrationProtocol.reply("LDR,REJECT,17")
        )
        listOf("LDR,OK", "LDR,OK,0", "LDR,OK,17 ", "LDR,ERROR,17",
            "LDR,OK,65536", "LDR,OK,17,extra", "ENV,ON").forEach {
            assertNull(BleLdrCalibrationProtocol.reply(it))
        }
    }
}
