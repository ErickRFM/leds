package com.rgbcontroller

import com.rgbcontroller.core.components.registry.CraneWiring
import com.rgbcontroller.core.components.registry.CraneWiringDraft
import com.rgbcontroller.core.components.registry.PinAssignmentPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The crane editor's promise: a signal can be emptied in the app, and the apply
 * stays blocked with a reason that says the board takes all seven at once.
 * No BLE, no Wi-Fi, no emulator.
 */
class CraneWiringDraftTest {
    private val defaults = requireNotNull(CraneWiring.profile()).defaults

    @Test fun completeDraftAppliesWithoutBlockers() {
        val draft = CraneWiringDraft(defaults)
        assertTrue(draft.blockers.joinToString(), draft.canApply)
        assertEquals(7, draft.assignedCount)
        assertEquals(emptyList<Int>(), draft.released)
    }

    @Test fun releasingASignalBlocksApplyAndNamesTheSignal() {
        val draft = CraneWiringDraft(defaults).release(2)
        assertEquals(listOf(2), draft.released)
        assertEquals(6, draft.assignedCount)
        assertFalse(draft.canApply)
        val reason = draft.blockers.joinToString()
        assertTrue(reason, reason.contains("Joystick arriba"))
        // The wording must not imply the board let go of the GPIO.
        assertTrue(reason, reason.contains("no suelta el GPIO"))
    }

    @Test fun reassigningTheReleasedSignalUnblocksApply() {
        val draft = CraneWiringDraft(defaults).release(2).assign(2, 34)
        assertTrue(draft.blockers.joinToString(), draft.canApply)
        assertEquals(defaults, draft.pins)
    }

    @Test fun everySignalCanBeReleasedIndividually() {
        for (index in defaults.indices) {
            val draft = CraneWiringDraft(defaults).release(index)
            assertEquals(index.toString(), listOf(index), draft.released)
            assertFalse(draft.canApply)
        }
    }

    @Test fun twoReleasedSignalsAreNotAGpioZeroCollision() {
        val draft = CraneWiringDraft(defaults).release(2).release(3)
        assertEquals(listOf(2, 3), draft.released)
        assertEquals(emptyList<Int>(), draft.duplicates)
    }

    @Test fun sameGpioOnTwoSignalsIsReportedOncePerPair() {
        val draft = CraneWiringDraft(defaults).assign(3, 34)
        assertEquals(listOf(34), draft.duplicates)
        assertFalse(draft.canApply)
    }

    @Test fun servoPinsCannotCarryJoystickSignals() {
        // GPIO34 is an input-only pad: legal for a joystick direction, not for a servo.
        val draft = CraneWiringDraft(defaults).assign(0, 34)
        assertTrue(draft.illegal.joinToString(), draft.illegal.any { it.contains("Servo base") })
        assertFalse(draft.canApply)
    }

    @Test fun pinHeldByAnotherModuleIsACollision() {
        val draft = CraneWiringDraft(defaults, reservedByOthers = mapOf(18 to "led.rgb"))
        assertTrue(draft.collisions.joinToString().contains("GPIO 18 ya lo tiene led.rgb"))
        assertFalse(draft.canApply)
    }

    @Test fun savedDraftSurvivesAConfirmedFirmwareReport() {
        // The 4 s status poll used to overwrite the editor, so no pin could be removed.
        val edited = defaults.toMutableList().also { it[6] = CraneWiring.UNASSIGNED }
        val draft = CraneWiringDraft.of(draft = edited, firmware = defaults)
        assertEquals(edited, draft.pins)
        assertEquals(listOf(6), draft.released)
        assertTrue(draft.divergesFromFirmware)
        assertEquals("Borrador distinto al ESP32", draft.stateLabel)
    }

    @Test fun firmwareSeedsTheDraftWhenNothingWasSaved() {
        val draft = CraneWiringDraft.of(draft = null, firmware = defaults)
        assertEquals(defaults, draft.pins)
        assertFalse(draft.divergesFromFirmware)
        assertEquals("Igual que el ESP32", draft.stateLabel)
    }

    @Test fun appliedDraftMatchesFirmwareAgain() {
        val draft = CraneWiringDraft(defaults, firmwarePins = defaults)
        assertFalse(draft.divergesFromFirmware)
        assertEquals("Igual que el ESP32", draft.stateLabel)
    }

    @Test fun firstRunWithNothingSavedOpensOnTheSuggestedWiring() {
        val draft = CraneWiringDraft.of(draft = null, firmware = null)
        assertEquals(defaults, draft.pins)
        // Seven empty rows would read as "the board has nothing wired", which
        // was never asked; the label says the suggestion is unconfirmed instead.
        assertEquals("Sugerencia sin confirmar", draft.stateLabel)
        assertTrue(draft.canApply)
    }

    @Test fun truncatedSavedListsFallBackInsteadOfCrashing() {
        val draft = CraneWiringDraft.of(draft = listOf(18, 19), firmware = listOf(9))
        assertEquals(defaults, draft.pins)
        assertFalse(draft.divergesFromFirmware)
    }

    @Test fun assigningOutsideTheSignalRangeIsIgnored() {
        assertEquals(defaults, CraneWiringDraft(defaults).assign(7, 32).pins)
        assertEquals(defaults, CraneWiringDraft(defaults).assign(-1, 32).pins)
    }

    @Test fun firmwarePolicyStillRefusesReleasedSlots() {
        // Cross-check with the shared validator: a released signal is a problem there too.
        val problems = PinAssignmentPolicy
            .validate(CraneWiring.COMPONENT_ID, CraneWiringDraft(defaults).release(0).pins)
        assertTrue(problems.joinToString(), problems.any { it.contains("señal liberada") })
        assertTrue(PinAssignmentPolicy.validate(CraneWiring.COMPONENT_ID, defaults).isEmpty())
    }
}
