package com.rgbcontroller

import com.rgbcontroller.core.modules.ModuleKind
import com.rgbcontroller.core.modules.ModuleManifest
import org.junit.Assert.*
import org.junit.Test

class ModuleManifestTest {
    private val valid = """
        {"schemaVersion":1,"modules":[
            {"id":"led.rgb.main","kind":"actuator.rgb","uiKind":"rgb-control",
             "title":"LED RGB","pins":[27,25,26]},
            {"id":"input.adc1.34","kind":"sensor.adc_raw","uiKind":"adc-gauge",
             "title":"Entrada ADC1 GPIO34","pins":[34]}
        ]}
    """.trimIndent()

    @Test fun parsesSupportedModulesOnly() {
        val parsed = ModuleManifest.parse(valid)
        assertEquals(listOf(ModuleKind.RGB, ModuleKind.ADC_INPUT), parsed.map { it.kind })
        assertEquals(listOf(34), parsed[1].pins)
    }

    @Test fun rejectsDuplicateIdsAndIncompatibleVersions() {
        assertThrows(IllegalArgumentException::class.java) {
            ModuleManifest.parse(valid.replace("\"schemaVersion\":1", "\"schemaVersion\":2"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ModuleManifest.parse(valid.replace("input.adc1.34", "led.rgb.main"))
        }
    }

    @Test fun mismatchedPinsCannotCreateRgbControl() {
        val badPins = valid.replace("[27,25,26]", "[18,19,21]")
        assertEquals(listOf(ModuleKind.ADC_INPUT),
            ModuleManifest.parse(badPins).map { it.kind })
    }

    @Test fun rejectsOversizedManifest() {
        assertThrows(IllegalArgumentException::class.java) {
            ModuleManifest.parse(" ".repeat(4100) + valid)
        }
    }
}
