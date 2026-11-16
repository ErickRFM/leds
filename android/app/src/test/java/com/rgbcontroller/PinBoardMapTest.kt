package com.rgbcontroller

import com.rgbcontroller.core.components.model.ComponentRuntimeStatus
import com.rgbcontroller.core.components.registry.PinAssignmentPolicy
import com.rgbcontroller.core.components.registry.PinBoardMap
import com.rgbcontroller.core.components.registry.PinClaimSource
import com.rgbcontroller.core.components.status.LocalDesiredComponent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Board-wide GPIO truth used by the Modules pins screen. Offline by design: the
 * report must never invent an occupancy a firmware never confirmed.
 */
class PinBoardMapTest {
    private fun firmware(id: String, pins: List<Int>) =
        ComponentRuntimeStatus(id, "sensor", "gpio", "active", true, pins)

    @Test fun firmwareLeaseIsTheEffectivePinAndOutranksTheDraft() {
        val report = PinBoardMap.build(
            modules = listOf("dht11"),
            local = listOf(LocalDesiredComponent("dht11", "sensor", listOf(16))),
            reported = listOf(firmware("sensor.dht11", listOf(19)))
        )
        val row = report.row("dht11")!!
        assertEquals(listOf(19), row.pins)
        assertEquals(PinClaimSource.FIRMWARE, row.source)
        assertTrue(row.draftDiffersFromFirmware)
        assertTrue(report.conflicts.isEmpty())
    }

    @Test fun twoModulesOnOneGpioAreReportedAsConflictBeforeAnyApply() {
        val report = PinBoardMap.build(
            modules = listOf("dht11", "ldr_light"),
            local = listOf(
                LocalDesiredComponent("dht11", "sensor", listOf(32)),
                LocalDesiredComponent("ldr_light", "sensor", listOf(32))
            ),
            reported = emptyList()
        )
        assertEquals(listOf(32), report.conflicts.map { it.pin })
        assertEquals(listOf("dht11", "ldr_light"), report.conflicts.single().componentIds)
        assertFalse(report.conflicts.single().firmwareInvolved)
        assertTrue(report.hasConflicts)
    }

    @Test fun activeFirmwareClaimMakesTheConflictFirmwareBacked() {
        val report = PinBoardMap.build(
            modules = listOf("dht11", "soil_moisture"),
            local = listOf(LocalDesiredComponent("dht11", "sensor", listOf(34))),
            reported = listOf(firmware("sensor.soil_moisture", listOf(34)))
        )
        val conflict = report.conflicts.single()
        assertEquals(listOf("dht11", "soil_moisture"), conflict.componentIds)
        assertTrue(conflict.firmwareInvolved)
    }

    @Test fun switchingDhtModelIsOneLeaseNotACollision() {
        val report = PinBoardMap.build(
            modules = listOf("dht11", "dht22", "ldr_light"),
            local = listOf(LocalDesiredComponent("dht11", "sensor", listOf(16))),
            reported = listOf(firmware("sensor.dht22", listOf(16)))
        )
        assertTrue(report.conflicts.isEmpty())
        assertTrue(report.ownersOtherThan("dht22", 16).isEmpty())
        assertTrue(report.ownersOtherThan("ldr_light", 16)
            .containsAll(listOf("dht11", "dht22")))
    }

    @Test fun unassignedModulesClaimNothing() {
        val report = PinBoardMap.build(
            modules = listOf("ldr_light"),
            local = listOf(LocalDesiredComponent("ldr_light", "sensor", emptyList())),
            reported = emptyList()
        )
        val row = report.row("ldr_light")!!
        assertEquals(PinAssignmentPolicy.profile("ldr_light")!!.defaults, row.pins)
        assertFalse(row.assigned)
        assertTrue(report.claims.isEmpty())
        assertTrue(report.conflicts.isEmpty())
    }

    @Test fun occupiedGpioStaysVisibleButUnselectable() {
        val report = PinBoardMap.build(
            modules = listOf("dht11", "analog_sensor", "soil_moisture", "dht22"),
            local = listOf(
                LocalDesiredComponent("dht11", "sensor", listOf(16)),
                LocalDesiredComponent("analog_sensor", "sensor", listOf(34))
            ),
            reported = emptyList()
        )
        val soil = report.optionsFor("soil_moisture", 0)
        val taken34 = soil.first { it.pin == 34 }
        assertFalse(taken34.available)
        assertEquals("analog_sensor", taken34.takenBy)
        assertTrue(soil.filter { it.pin != 34 }.all { it.available })
        // The sibling DHT model replaces the same lease, so GPIO 16 stays free for it.
        assertTrue(report.optionsFor("dht22", 0).first { it.pin == 16 }.available)
        assertTrue(report.conflicts.isEmpty())
    }

    @Test fun pendingEditBlocksAnotherModuleImmediately() {
        val modules = listOf("soil_moisture", "analog_sensor")
        val saved = listOf(LocalDesiredComponent("soil_moisture", "sensor", listOf(34)))
        val before = PinBoardMap.build(modules = modules, local = saved, reported = emptyList())
        assertFalse(before.optionsFor("analog_sensor", 0).first { it.pin == 34 }.available)

        val moved = PinBoardMap.build(
            modules = modules,
            local = saved,
            reported = emptyList(),
            pending = mapOf("soil_moisture" to listOf(35))
        )
        assertEquals(listOf(35), moved.row("soil_moisture")!!.pins)
        assertTrue(moved.optionsFor("analog_sensor", 0).first { it.pin == 34 }.available)
        assertFalse(moved.optionsFor("analog_sensor", 0).first { it.pin == 35 }.available)
        assertTrue(moved.conflicts.isEmpty())
    }

    @Test fun duplicateGpioWithinOneModuleIsNotOfferedTwice() {
        val report = PinBoardMap.build(
            modules = listOf("ssd1306_oled"),
            local = listOf(LocalDesiredComponent("ssd1306_oled", "display", listOf(21, 21))),
            reported = emptyList()
        )
        val scl = report.optionsFor("ssd1306_oled", 1).first { it.pin == 21 }
        assertFalse(scl.available)
        assertEquals("ssd1306_oled", scl.takenBy)
    }

    @Test fun compiledDriverWithoutActiveLeaseOccupiesNoGpio() {
        val report = PinBoardMap.build(
            modules = listOf("dht22"),
            local = emptyList(),
            reported = listOf(
                ComponentRuntimeStatus("sensor.dht22", "sensor", "gpio", "catalog", false, listOf(32))
            )
        )
        val row = report.row("dht22")!!
        assertFalse(row.assigned)
        assertEquals(PinClaimSource.LOCAL_DRAFT, row.source)
        assertTrue(report.claims.isEmpty())
    }

    @Test fun physicallyDetectedModuleOccupiesItsGpio() {
        val report = PinBoardMap.build(
            modules = listOf("ldr_light", "soil_moisture"),
            local = emptyList(),
            reported = listOf(
                ComponentRuntimeStatus("sensor.ldr_light", "sensor", "adc", "detected", false, listOf(34))
            )
        )
        assertEquals(listOf(34), report.row("ldr_light")!!.pins)
        assertEquals(PinClaimSource.FIRMWARE, report.row("ldr_light")!!.source)
        assertFalse(report.optionsFor("soil_moisture", 0).first { it.pin == 34 }.available)
        assertEquals(listOf("ldr_light"), report.ownersOtherThan("soil_moisture", 34))
    }

    @Test fun moduleWithoutWiringProfileIsNotOnTheBoard() {
        val report = PinBoardMap.build(
            modules = listOf("compi_dfplayer", "robotics_crane"),
            local = listOf(LocalDesiredComponent("compi_dfplayer", "audio", listOf(3, 1))),
            reported = emptyList()
        )
        assertEquals(listOf("robotics_crane"), report.rows.map { it.componentId })
    }
}
