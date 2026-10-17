package com.rgbcontroller.core.components.calibration

import com.rgbcontroller.core.components.model.ComponentTelemetryCodec
import com.rgbcontroller.core.components.status.ComponentDashboardState
import com.rgbcontroller.core.components.status.ComponentStatusFreshness

/**
 * Only use a currently authorized, firmware-confirmed ADC1 reading when the
 * calibration wizard offers "Use current reading". The persisted last-known
 * sample may be displayed as history, never offered as a live calibration.
 */
object SoilLiveCapturePolicy {
    fun currentRawAdc(state: ComponentDashboardState, authorized: Boolean): Int? {
        if (!authorized || !state.connected || state.authorityError != null ||
            state.telemetryFreshness != ComponentStatusFreshness.LIVE
        ) return null
        val measurement = state.authoritativeTelemetry
            ?.let { ComponentTelemetryCodec.find(it, "adc_raw") }
            ?: return null
        if (!measurement.valid || measurement.status != "fresh" ||
            !measurement.value.isFinite() || measurement.value < 0f ||
            measurement.value > 4095f
        ) return null
        return measurement.value.toInt()
    }
}
