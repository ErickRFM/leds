package com.rgbcontroller

import com.rgbcontroller.core.model.RgbDeviceState
import org.junit.Assert.*
import org.junit.Test

class RgbDeviceStateTest {
    @Test fun parsesLegacyState() {
        val state = RgbDeviceState.parse("STATE,255,120,0,200,1")
        assertNotNull(state)
        assertEquals(255, state?.red)
        assertTrue(state?.power == true)
        assertEquals("NONE", state?.effect)
    }

    @Test fun parsesEffectsState() {
        val state = RgbDeviceState.parse("STATE,12,34,56,200,1,RAINBOW,120")
        assertEquals("RAINBOW", state?.effect)
        assertEquals(120, state?.speed)
    }

    @Test fun rejectsMalformedStates() {
        listOf("ERR,INVALID_COMMAND", "STATE,256,0,0,255,1",
               "STATE,1,2,3,4,9", "STATE,1,2,3,4,1,UNKNOWN,40",
               "STATE,-1,2,3,4,1", "STATE,1,2,3,4,1,FADE,no")
            .forEach { assertNull(it, RgbDeviceState.parse(it)) }
    }
}
