package com.rgbcontroller

import com.rgbcontroller.core.components.registry.ModuleGrouping
import org.junit.Assert.assertEquals
import org.junit.Test

class ModuleGroupingTest {
    @Test fun groupsOnlyWhenBothRealComponentProfilesArePresent() {
        assertEquals(listOf("dht22"), ModuleGrouping.visibleModuleIds(listOf("dht22")))
        assertEquals(listOf("soil_moisture"), ModuleGrouping.visibleModuleIds(listOf("soil_moisture")))
        assertEquals(listOf(ModuleGrouping.ENVIRONMENTAL, "led_rgb"),
            ModuleGrouping.visibleModuleIds(
                listOf("dht22", "soil_moisture", "led_rgb", "dht22")))
    }

    @Test fun groupIsVisualOnlyAndNeverOwnsPins() {
        assertEquals(listOf("dht22", "soil_moisture"),
            ModuleGrouping.partIds(ModuleGrouping.ENVIRONMENTAL))
        assertEquals(listOf("led_rgb"), ModuleGrouping.partIds("led_rgb"))
        assertEquals(emptyList<String>(), ModuleGrouping.visibleModuleIds(emptyList()))
    }
}
