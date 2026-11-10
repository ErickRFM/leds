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

    @Test fun labSubnetAddressMatchesHostOctetWithinRange() {
        assertTrue(LabNetworkPolicy.isLabSubnetAddress("192.168.4.2"))
        assertTrue(LabNetworkPolicy.isLabSubnetAddress("192.168.4.254"))
        assertTrue(LabNetworkPolicy.isLabSubnetAddress("192.168.4.2/24"))
        assertFalse(LabNetworkPolicy.isLabSubnetAddress("192.168.5.2"))
        assertFalse(LabNetworkPolicy.isLabSubnetAddress("192.168.4.0"))
        assertFalse(LabNetworkPolicy.isLabSubnetAddress("192.168.4.255"))
        assertFalse(LabNetworkPolicy.isLabSubnetAddress("192.168.4.abc"))
        assertFalse(LabNetworkPolicy.isLabSubnetAddress(""))
    }

    @Test fun labLinkIsEligibleWithGatewayOrSubnetEvidence() {
        assertTrue(LabNetworkPolicy.isEligibleLink(true, listOf("192.168.4.1"), emptyList()))
        assertTrue(LabNetworkPolicy.isEligibleLink(true, emptyList(), listOf("192.168.4.2/24")))
        assertFalse(LabNetworkPolicy.isEligibleLink(false, listOf("192.168.4.1"), listOf("192.168.4.2")))
        assertFalse(LabNetworkPolicy.isEligibleLink(true, listOf("10.0.0.138"), listOf("192.168.43.10")))
        assertFalse(LabNetworkPolicy.isEligibleLink(true, emptyList(), emptyList()))
    }
}
