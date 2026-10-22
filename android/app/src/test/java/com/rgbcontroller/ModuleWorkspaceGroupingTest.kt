package com.rgbcontroller

import com.rgbcontroller.core.components.registry.ModuleWorkspaceGrouping
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModuleWorkspaceGroupingTest {
    @Test fun combinedEnvironmentUsesBothPhysicalModules() {
        val result = ModuleWorkspaceGrouping.group(
            listOf("dht22", "soil_moisture", "led_rgb", "dht22")
        )
        assertEquals("environmental_workspace", result.first().id)
        assertEquals(listOf("dht22", "soil_moisture"), result.first().components)
        assertEquals("led_rgb", result.last().id)
        assertEquals(2, result.size)
    }
    @Test fun missingSensorRemainsIndependent() {
        val result = ModuleWorkspaceGrouping.group(listOf("dht22", "ssd1306_oled"))
        assertEquals(listOf("dht22", "ssd1306_oled"), result.map { it.id })
        assertFalse(result.any { it.id == ModuleWorkspaceGrouping.ENVIRONMENT })
    }
    @Test fun noModulesAreInventedOffline() {
        assertTrue(ModuleWorkspaceGrouping.group(emptyList()).isEmpty())
    }
}
