package com.rgbcontroller

import com.rgbcontroller.core.model.DeviceIdCodec
import org.junit.Assert.*
import org.junit.Test

class DeviceIdCodecTest {
    @Test fun acceptOnlyExactUppercasePublicId() {
        assertEquals("1234ABCDDEADBEEF", DeviceIdCodec.accept("1234ABCDDEADBEEF"))
        assertNull(DeviceIdCodec.accept("1234abcdDEADBEEF"))
        assertNull(DeviceIdCodec.accept("1234ABCDDEADBEEF\n"))
        assertNull(DeviceIdCodec.accept("1234ABCDDEADBEE"))
        assertNull(DeviceIdCodec.accept(""))
    }
}
