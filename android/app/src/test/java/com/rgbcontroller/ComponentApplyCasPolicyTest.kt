package com.rgbcontroller

import com.rgbcontroller.core.wifi.ComponentApplyCasPolicy
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ComponentApplyCasPolicyTest {
    private fun status(active: Boolean, pins: List<Int>): JSONObject =
        JSONObject().put("active", active).put("pins", JSONArray(pins))

    @Test fun supportsFirstTimeActivationOfInactiveDhtAndOled() {
        assertEquals(listOf(0, 0), ComponentApplyCasPolicy.expected(status(false, emptyList()), 1))
        assertEquals(listOf(0, 0), ComponentApplyCasPolicy.expected(status(false, emptyList()), 2))
    }

    @Test fun activeAssignmentsMustMatchExactSignalCount() {
        assertEquals(listOf(32, 0), ComponentApplyCasPolicy.expected(status(true, listOf(32)), 1))
        assertEquals(listOf(21, 22), ComponentApplyCasPolicy.expected(status(true, listOf(21, 22)), 2))
        assertThrows(IllegalStateException::class.java) {
            ComponentApplyCasPolicy.expected(status(true, emptyList()), 1)
        }
        assertThrows(IllegalStateException::class.java) {
            ComponentApplyCasPolicy.expected(status(true, listOf(21)), 2)
        }
    }

    @Test fun staleInactiveReportsAndInvalidPinsAreRejected() {
        assertThrows(IllegalStateException::class.java) {
            ComponentApplyCasPolicy.expected(status(false, listOf(34)), 1)
        }
        assertThrows(IllegalStateException::class.java) {
            ComponentApplyCasPolicy.expected(status(true, listOf(45)), 1)
        }
    }

    @Test fun noMissingOrUnexpectedLeaseArraysAccepted() {
        assertThrows(IllegalStateException::class.java) {
            ComponentApplyCasPolicy.expected(JSONObject().put("active", false), 1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ComponentApplyCasPolicy.expected(status(true, listOf(21)), 3)
        }
    }
}
