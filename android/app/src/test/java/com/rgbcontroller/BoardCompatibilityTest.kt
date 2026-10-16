package com.rgbcontroller

import com.rgbcontroller.core.components.status.LocalDesiredComponent
import com.rgbcontroller.core.export.BoardCompatibilityEngine
import com.rgbcontroller.core.export.BoardProfile
import com.rgbcontroller.core.export.BoardProfiles
import com.rgbcontroller.core.export.CompatibilityIssue
import com.rgbcontroller.core.export.CompatibilityLevel
import com.rgbcontroller.core.export.ProjectExportBuilder
import com.rgbcontroller.core.export.ProjectExportEnvelope
import org.junit.Assert.*
import org.junit.Test

class BoardCompatibilityTest {

    private fun project(vararg components: LocalDesiredComponent): ProjectExportEnvelope =
        ProjectExportBuilder.build(
            projectId = "p",
            name = "n",
            boardProfile = BoardProfiles.ESP32_LEGACY_V1.id,
            capabilities = emptyList(),
            components = components.toList(),
            alerts = emptyList(),
            automations = emptyList(),
            calibrations = emptyList(),
            nowMs = 1L
        )

    @Test
    fun fullySupportedProjectIsCompatible() {
        val report = BoardCompatibilityEngine.evaluate(
            project(LocalDesiredComponent("dht22", "sensor", listOf(32))),
            BoardProfiles.ESP32_LEGACY_V1
        )
        assertEquals(CompatibilityLevel.COMPATIBLE, report.level)
        assertTrue(report.remapped.isEmpty())
        assertTrue(report.issues.isEmpty())
    }

    @Test
    fun reservedPinRequiresRemapAndNeverReusesReservedPin() {
        val report = BoardCompatibilityEngine.evaluate(
            project(LocalDesiredComponent("led_rgb", "actuator", listOf(10))),
            BoardProfiles.ESP32_LEGACY_V1
        )
        assertEquals(CompatibilityLevel.REQUIRES_REMAP, report.level)
        assertEquals(1, report.remapped.size)
        val newPins = report.remapped.first().toPins
        assertTrue(report.issues.first() is CompatibilityIssue.PinOutOfRange)
        assertTrue(
            "remapped pin must not be reserved",
            newPins.none { it in BoardProfiles.ESP32_LEGACY_V1.reservedPins }
        )
    }

    @Test
    fun duplicatePinOnSecondComponentIsRemapped() {
        val report = BoardCompatibilityEngine.evaluate(
            project(
                LocalDesiredComponent("a", "sensor", listOf(32)),
                LocalDesiredComponent("b", "sensor", listOf(32))
            ),
            BoardProfiles.ESP32_LEGACY_V1
        )
        assertEquals(CompatibilityLevel.REQUIRES_REMAP, report.level)
        assertEquals(listOf("b"), report.remapped.map { it.componentId })
        assertTrue(report.remapped.first().toPins.none { it == 32 })
    }

    @Test
    fun unsupportedTypeIsIncompatibleWhenNothingCanBeRemapped() {
        val report = BoardCompatibilityEngine.evaluate(
            project(LocalDesiredComponent("weird", "quantum", listOf(32))),
            BoardProfiles.ESP32_LEGACY_V1
        )
        assertEquals(CompatibilityLevel.INCOMPATIBLE, report.level)
        assertTrue(report.issues.any { it is CompatibilityIssue.UnsupportedType })
        assertEquals(listOf("weird"), report.unmappedComponentIds)
    }

    @Test
    fun mixedProjectIsPartiallyCompatible() {
        val report = BoardCompatibilityEngine.evaluate(
            project(
                LocalDesiredComponent("dht22", "sensor", listOf(32)),
                LocalDesiredComponent("weird", "quantum", listOf(33))
            ),
            BoardProfiles.ESP32_LEGACY_V1
        )
        assertEquals(CompatibilityLevel.PARTIALLY_COMPATIBLE, report.level)
    }

    @Test
    fun unknownBoardIsReportedAsUnknown() {
        val report = BoardCompatibilityEngine.evaluate(
            project(LocalDesiredComponent("dht22", "sensor", listOf(32))),
            BoardProfile.unknown("esp32-unknown")
        )
        assertEquals(CompatibilityLevel.UNKNOWN, report.level)
        assertFalse(report.boardId == BoardProfiles.ESP32_LEGACY_V1.id)
    }

    @Test
    fun profilesResolveByIdAndFallBackToUnknown() {
        assertEquals(BoardProfiles.ESP32_LEGACY_V1, BoardProfiles.find("esp32-legacy-v1"))
        assertFalse(BoardProfiles.find("nope").isKnown)
    }

    @Test
    fun assignablePinsExcludeReserved() {
        val pins = BoardProfiles.ESP32_LEGACY_V1.assignablePins
        assertTrue(pins.none { it in BoardProfiles.ESP32_LEGACY_V1.reservedPins })
        assertTrue(pins.contains(32))
        assertFalse(pins.contains(10))
    }
}
