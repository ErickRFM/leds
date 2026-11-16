package com.rgbcontroller

import com.rgbcontroller.core.components.model.ComponentRuntimeStatus
import com.rgbcontroller.core.components.registry.PinClaimSource
import com.rgbcontroller.feature.modules.moduleWorkspaceBoardOf
import com.rgbcontroller.core.components.registry.ModuleWorkspaceGrouping
import com.rgbcontroller.core.components.status.LocalDesiredComponent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The single derivation the Modules destination reads. Offline by design: a
 * compiled driver, an unconfirmed snapshot or a draft must never look like a
 * firmware-backed module, a group or a lease.
 */
class ModuleWorkspaceBoardTest {
    private fun status(id: String, state: String, active: Boolean, pins: List<Int>) =
        ComponentRuntimeStatus(id, "sensor", "gpio", state, active, pins)

    private fun draft(id: String, vararg pins: Int) =
        LocalDesiredComponent(id, "sensor", pins.toList())

    @Test fun catalogOnlyDriverNeitherGroupsNorClaimsAGpio() {
        val board = moduleWorkspaceBoardOf(
            desired = emptyList(),
            authoritative = listOf(status("sensor.dht22", "catalog", false, listOf(32))),
            persisted = null,
            bleAuthorized = false
        )
        assertTrue(board.groups.isEmpty())
        assertTrue(board.pinBoard.rows.isEmpty())
    }

    @Test fun environmentAndSoilShareOneWorkspaceWithTheModelThatIsPresent() {
        val dht11 = moduleWorkspaceBoardOf(
            desired = listOf(draft("dht11", 16), draft("soil_moisture", 34)),
            authoritative = emptyList(), persisted = null, bleAuthorized = false
        )
        assertEquals("dht11", dht11.environmentSensor)
        assertEquals(listOf("dht11", "soil_moisture"), dht11.groups.single().components)
        assertEquals(ModuleWorkspaceGrouping.ENVIRONMENT, dht11.groups.single().id)

        val dht22 = moduleWorkspaceBoardOf(
            desired = listOf(draft("dht22", 16), draft("soil_moisture", 34)),
            authoritative = emptyList(), persisted = null, bleAuthorized = false
        )
        assertEquals("dht22", dht22.environmentSensor)
    }

    @Test fun ledModuleAppearsOnlyWhenBluetoothOwnershipIsConfirmed() {
        val off = moduleWorkspaceBoardOf(
            desired = listOf(draft("dht11", 16)),
            authoritative = emptyList(), persisted = null, bleAuthorized = false
        )
        assertFalse(off.groups.any { it.components.contains("led_rgb") })

        val on = moduleWorkspaceBoardOf(
            desired = listOf(draft("dht11", 16)),
            authoritative = emptyList(), persisted = null, bleAuthorized = true
        )
        assertTrue(on.groups.any { it.components.contains("led_rgb") })
    }

    @Test fun lastKnownSnapshotGroupsModulesWithoutInventingAFirmwareLease() {
        val board = moduleWorkspaceBoardOf(
            desired = listOf(draft("dht11", 17)),
            authoritative = null,
            persisted = listOf(status("sensor.dht11", "active", true, listOf(16))),
            bleAuthorized = false
        )
        assertTrue(board.groups.any { it.components.contains("dht11") })
        val row = board.pinBoard.row("dht11")!!
        // Only a complete authoritative snapshot is a firmware claim, so the
        // persisted draft stays the effective pin and nothing reads as divergent.
        assertEquals(PinClaimSource.LOCAL_DRAFT, row.source)
        assertEquals(listOf(17), row.pins)
        assertFalse(row.draftDiffersFromFirmware)
    }

    @Test fun optimisticChoiceIsPartOfTheSameSnapshotAsTheGroups() {
        val board = moduleWorkspaceBoardOf(
            desired = listOf(draft("soil_moisture", 34)),
            authoritative = emptyList(), persisted = null, bleAuthorized = false,
            pending = mapOf("soil_moisture" to listOf(35))
        )
        assertEquals(listOf(35), board.pinBoard.row("soil_moisture")!!.pins)
        assertFalse(board.pinBoard.optionsFor("analog_sensor", 0).any { it.pin == 35 && it.available })
    }
}
