package com.rgbcontroller

import com.rgbcontroller.core.model.ColorUtils
import com.rgbcontroller.core.model.RgbAckPolicy
import com.rgbcontroller.core.model.RgbDeviceState
import org.junit.Assert.*
import org.junit.Test

class RgbAckPolicyTest {
    private val requested = ColorUtils.RgbColor(12, 45, 67, 100)
    private val matching = RgbDeviceState(12, 45, 67, 100, true)

    @Test fun oldStateCannotConfirmNewCommandEvenIfRgbMatches() {
        assertFalse(RgbAckPolicy.confirmed(requested, 7, 7, matching))
        assertFalse(RgbAckPolicy.confirmed(requested, 7, 6, matching))
        assertTrue(RgbAckPolicy.confirmed(requested, 7, 8, matching))
    }

    @Test fun confirmationRejectsWrongPowerEffectOrChannels() {
        assertFalse(RgbAckPolicy.confirmed(requested, 2, 3, null))
        assertFalse(RgbAckPolicy.confirmed(requested, 2, 3, matching.copy(power = false)))
        assertFalse(RgbAckPolicy.confirmed(requested, 2, 3, matching.copy(effect = "RAINBOW")))
        assertFalse(RgbAckPolicy.confirmed(requested, 2, 3, matching.copy(green = 44)))
    }
}
