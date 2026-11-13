package com.rgbcontroller

import com.rgbcontroller.core.ble.BleSensorPinProtocol
import org.junit.Assert.*
import org.junit.Test

class BleSensorPinProtocolTest {
    @Test fun `sensor GPIO requests are exact CAS transactions`() {
        assertEquals("PIN,0123456789abcdef,D,16,19",
            BleSensorPinProtocol.request("0123456789abcdef", BleSensorPinProtocol.Sensor.DHT11, 16, 19))
        assertEquals("PIN,0123456789abcdef,L,32,34",
            BleSensorPinProtocol.request("0123456789abcdef", BleSensorPinProtocol.Sensor.LDR, 32, 34))
        assertThrows(IllegalArgumentException::class.java) {
            BleSensorPinProtocol.request("12345678", BleSensorPinProtocol.Sensor.DHT11, 16, 19)
        }
        assertThrows(IllegalArgumentException::class.java) {
            BleSensorPinProtocol.request("0123456789abcdef", BleSensorPinProtocol.Sensor.LDR, 32, 40)
        }
    }
    @Test fun `only correlated known receipts are accepted`() {
        assertEquals(BleSensorPinProtocol.Outcome.APPLIED,
            BleSensorPinProtocol.receipt("PIN,0123456789abcdef,APPLIED")!!.outcome)
        assertEquals(BleSensorPinProtocol.Outcome.REPLAYED,
            BleSensorPinProtocol.receipt("PIN,0123456789abcdef,REPLAYED")!!.outcome)
        for (frame in listOf("PIN,0123456789abcdef,PLAYING", "PIN,abc,APPLIED",
            "PIN,0123456789abcdef,APPLIED,extra", "PIN,0123456789abcdeg,APPLIED")) {
            assertNull(BleSensorPinProtocol.receipt(frame))
        }
    }
    @Test fun `crane component requests keep all seven signals in one frame`() {
        val from = listOf(18, 19, 34, 35, 36, 39, 17)
        val to = listOf(18, 19, 34, 35, 21, 22, 17)
        assertEquals("CPN,0123456789abcdef,C,1818191934343535362139221717",
            BleSensorPinProtocol.componentRequest("0123456789abcdef", 'C', from, to))
        // Inactive predecessor: zero from-pins mean "previously unassigned".
        assertEquals("CPN,0123456789abcdef,C,0018001900340035002100220017",
            BleSensorPinProtocol.componentRequest("0123456789abcdef", 'C', List(7) { 0 }, to))
        assertThrows(IllegalArgumentException::class.java) {
            BleSensorPinProtocol.componentRequest("0123456789abcdef", 'C', from, to + 40)
        }
        assertThrows(IllegalArgumentException::class.java) {
            BleSensorPinProtocol.componentRequest("0123456789abcdef", 'C', from, to.dropLast(1))
        }
        assertThrows(IllegalArgumentException::class.java) {
            BleSensorPinProtocol.componentRequest("0123456789abcdeg", 'C', from, to)
        }
    }
}
