package com.rgbcontroller

import com.rgbcontroller.core.qa.LabSnapshot
import com.rgbcontroller.core.qa.LabSelfCheck
import com.rgbcontroller.core.model.RgbReadback
import com.rgbcontroller.core.model.RgbDeviceState
import org.junit.Assert.*
import org.junit.Test

class LabSelfCheckTest {
    private val valid = LabSnapshot(2, "STATE,2,3,4,255,1,NONE,100", "ESP32", 4_194_304L,
        120_000L, 1_966_080L, true, false)

    @Test fun validLabContractPassesButDoesNotClaimHardware() {
        assertTrue(LabSelfCheck.evaluate(valid).all { it.passed })
        val report = LabSelfCheck.report(valid)
        assertTrue(report.contains("NO VERIFICADO: hardware eléctrico"))
        assertFalse(report.contains("X-Device-Key"))
    }

    @Test fun corruptOrUnexpectedDeviceStateCannotPass() {
        val invalid = valid.copy(
            apiVersion = 99, rgbFrame = "STATE,2,3,999,255,1",
            chip = "unknown", flashBytes = 0, freeHeapBytes = 0,
            nextOtaSlotBytes = 0, otaSlotPresent = false, otaUploadEnabled = true
        )
        assertEquals(0, LabSelfCheck.evaluate(invalid).count { it.passed })
        assertTrue(LabSelfCheck.report(invalid).contains("[FALLO]"))
    }

    @Test fun wifiSetMustBeConfirmedExactlyByActualState() {
        val state = RgbDeviceState.parse("STATE,10,20,30,200,1,NONE,100")
        assertTrue(RgbReadback.confirmsSet(10, 20, 30, 200, state))
        assertFalse(RgbReadback.confirmsSet(10, 20, 30, 201, state))
        assertFalse(RgbReadback.confirmsSet(10, 20, 30, 200,
            RgbDeviceState.parse("STATE,10,20,30,200,0,NONE,100")))
        assertFalse(RgbReadback.confirmsSet(10, 20, 30, 200, null))
    }
}
