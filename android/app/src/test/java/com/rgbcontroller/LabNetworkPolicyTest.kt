package com.rgbcontroller

import com.rgbcontroller.core.wifi.LabNetworkPolicy
import org.junit.Assert.*
import org.junit.Test

class LabNetworkPolicyTest {
    @Test fun onlyDedicatedSoftApRouteIsAllowed() {
        assertTrue(LabNetworkPolicy.isEligible(true, listOf("192.168.4.1")))
        assertFalse(LabNetworkPolicy.isEligible(false, listOf("192.168.4.1")))
        assertFalse(LabNetworkPolicy.isEligible(true, listOf("192.168.1.1")))
        assertFalse(LabNetworkPolicy.isEligible(true, emptyList()))
        assertFalse(LabNetworkPolicy.isEligible(false, emptyList()))
    }
}
