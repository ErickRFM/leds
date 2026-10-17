package com.rgbcontroller

import com.rgbcontroller.core.components.calibration.SoilLiveCapturePolicy
import com.rgbcontroller.core.components.model.ComponentMeasurement
import com.rgbcontroller.core.components.status.ComponentDashboardState
import com.rgbcontroller.core.components.status.FirmwareReportedTelemetry
import com.rgbcontroller.core.components.status.LastKnownTelemetry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SoilLiveCapturePolicyTest {
    private fun raw(value: Float = 2500f, valid: Boolean = true, status: String = "fresh") =
        ComponentMeasurement("adc_raw", status, valid, value, "raw", 300L)
    private fun connected(sample: ComponentMeasurement = raw()) =
        ComponentDashboardState(
            deviceId = "board-a",
            connected = true,
            firmwareTelemetry = FirmwareReportedTelemetry("board-a", listOf(sample), 300L)
        )

    @Test fun authorizedFreshReadingCanBeCaptured() {
        assertEquals(2500, SoilLiveCapturePolicy.currentRawAdc(connected(), true))
    }

    @Test fun cachedReadingIsNeverLabeledCurrentEvenWhenMarkedFresh() {
        val offline = connected().copy(
            connected = false,
            firmwareTelemetry = null,
            lastKnownTelemetry = LastKnownTelemetry("board-a", listOf(raw()), 300L)
        )
        assertNull(SoilLiveCapturePolicy.currentRawAdc(offline, true))
        assertNull(SoilLiveCapturePolicy.currentRawAdc(offline.copy(connected = true), true))
    }

    @Test fun missingAuthorizationErrorAndWrongBoardBlockCapture() {
        assertNull(SoilLiveCapturePolicy.currentRawAdc(connected(), false))
        assertNull(SoilLiveCapturePolicy.currentRawAdc(
            connected().copy(authorityError = "Sesión caducada"), true))
        assertNull(SoilLiveCapturePolicy.currentRawAdc(
            connected().copy(firmwareTelemetry = FirmwareReportedTelemetry(
                "another-board", listOf(raw()), 300L)), true))
    }

    @Test fun invalidStaleOrOutOfRangeAdcCannotBeCaptured() {
        for (sample in listOf(
            raw(valid = false), raw(status = "stale"), raw(status = "absent"),
            raw(value = -1f), raw(value = 4096f), raw(value = Float.NaN),
            raw(value = Float.POSITIVE_INFINITY)
        )) {
            assertNull(SoilLiveCapturePolicy.currentRawAdc(connected(sample), true))
        }
    }

    @Test fun permitsHardwareAdcLimits() {
        assertEquals(0, SoilLiveCapturePolicy.currentRawAdc(connected(raw(0f)), true))
        assertEquals(4095, SoilLiveCapturePolicy.currentRawAdc(connected(raw(4095f)), true))
    }
}
