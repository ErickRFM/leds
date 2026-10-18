package com.rgbcontroller

import com.rgbcontroller.core.components.registry.ComponentCatalog
import com.rgbcontroller.core.components.registry.PinAssignmentPolicy
import com.rgbcontroller.core.components.model.ComponentRuntimeStatus
import com.rgbcontroller.core.components.registry.ModuleWorkspaceGrouping
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Dht11IntegrationTest {
    @Test fun bothDhtModelsHaveDistinctDefaultPins() {
        assertEquals(listOf(16), PinAssignmentPolicy.profile("dht11")?.defaults)
        assertEquals(listOf(32), PinAssignmentPolicy.profile("dht22")?.defaults)
        assertEquals("dht11", ComponentCatalog.catalogIdForFirmwareId("sensor.dht11"))
        assertEquals("dht22", ComponentCatalog.catalogIdForFirmwareId("sensor.dht22"))
    }
    @Test fun gpio16IsAllowedButRgbPinsRemainReserved() {
        assertTrue(PinAssignmentPolicy.validate("dht11", listOf(16)).isEmpty())
        assertTrue(PinAssignmentPolicy.validate("dht11", listOf(25)).isNotEmpty())
        assertTrue(PinAssignmentPolicy.validate("dht22", listOf(16)).isEmpty())
    }
    @Test fun dht11EnvironmentGroupKeepsRealModuleIdentity() {
        val groups = ModuleWorkspaceGrouping.group(listOf("dht11", "soil_moisture"))
        assertEquals(listOf("dht11", "soil_moisture"), groups.first().components)
    }
}
