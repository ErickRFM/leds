package com.rgbcontroller.core.components.registry

import com.rgbcontroller.core.components.model.ComponentRuntimeStatus
import com.rgbcontroller.core.components.status.LocalDesiredComponent

/** Where a GPIO claim came from. A firmware lease always outranks a local draft. */
enum class PinClaimSource { FIRMWARE, LOCAL_DRAFT }

data class PinClaim(
    val componentId: String,
    val signalIndex: Int,
    val pin: Int,
    val source: PinClaimSource
)

/** Two different modules that ended up claiming one GPIO. */
data class PinConflict(
    val pin: Int,
    val componentIds: List<String>,
    val firmwareInvolved: Boolean
)

/** A candidate GPIO for one signal, with the reason it may be unavailable. */
data class PinOption(
    val pin: Int,
    val available: Boolean,
    val takenBy: String?
)

/** One editable module of the board, with the pins it effectively holds. */
data class PinBoardRow(
    val componentId: String,
    val profile: ModuleWiringProfile,
    val pins: List<Int>,
    val assigned: Boolean,
    val source: PinClaimSource,
    val draftDiffersFromFirmware: Boolean
)

/**
 * Board-wide GPIO view: which module holds which pin, which pins collide and
 * which candidates a signal may still offer. Pure and offline — an unsaved,
 * in-progress edit is passed in as [pending] so the UI can refuse a collision
 * before it is ever written. Never implies firmware accepted anything.
 */
class PinBoardReport internal constructor(
    val rows: List<PinBoardRow>,
    val claims: List<PinClaim>,
    val conflicts: List<PinConflict>
) {
    fun row(componentId: String): PinBoardRow? = rows.firstOrNull { it.componentId == componentId }

    /** Other modules holding [pin]; DHT11/DHT22 are treated as one replaceable lease. */
    fun ownersOtherThan(componentId: String, pin: Int): List<String> = claims
        .filter { it.pin == pin && it.componentId != componentId }
        .map { it.componentId }
        .filterNot { PinBoardMap.areInterchangeable(it, componentId) }
        .distinct()

    /**
     * Every allowed GPIO of one signal, marked with who already holds it. Pins
     * reserved by another module stay visible but unavailable, so assignment
     * cannot silently create a collision.
     */
    fun optionsFor(componentId: String, signalIndex: Int): List<PinOption> {
        val row = row(componentId) ?: return emptyList()
        val signal = row.profile.signals.getOrNull(signalIndex) ?: return emptyList()
        val usedByOtherSignalsOfSameRow = row.pins
            .filterIndexed { index, _ -> index != signalIndex }
            .filterNot { it == PinAssignmentPolicy.UNASSIGNED }
            .toSet()
        return signal.allowedPins.sorted().map { pin ->
            val owners = ownersOtherThan(componentId, pin) +
                if (pin in usedByOtherSignalsOfSameRow) listOf(componentId) else emptyList()
            PinOption(
                pin = pin,
                available = owners.isEmpty(),
                takenBy = owners.firstOrNull()
            )
        }
    }

    val hasConflicts: Boolean get() = conflicts.isNotEmpty()
}

object PinBoardMap {
    private val DHT_FAMILY = setOf("dht11", "dht22")

    fun areInterchangeable(a: String, b: String): Boolean = a in DHT_FAMILY && b in DHT_FAMILY

    fun build(
        modules: List<String>,
        local: List<LocalDesiredComponent>,
        reported: List<ComponentRuntimeStatus>,
        pending: Map<String, List<Int>> = emptyMap()
    ): PinBoardReport {
        val firmwareById = reported
            // The same confirmed-presence rule the dashboard uses: an active
            // lease or a physically detected module occupies its GPIO.
            .filter { it.active || it.state == "detected" }
            .associateBy(
                { ComponentCatalog.catalogIdForFirmwareId(it.id) },
                { it }
            )
        val localById = local.associateBy { it.id }
        val ids = modules.filter { PinAssignmentPolicy.profile(it) != null }.distinct()

        val rows = ids.map { id ->
            val profile = PinAssignmentPolicy.profile(id)!!
            val firmware = firmwareById[id]
            val draft = localById[id]
            val firmwarePins = firmware?.pins?.takeIf { it.size == profile.signals.size }
            val draftPins = draft?.pins?.takeIf { it.size == profile.signals.size }
            val override = pending[id]?.takeIf { it.size == profile.signals.size }
            val pins = override ?: firmwarePins ?: draftPins ?: profile.defaults
            val assigned = override != null || firmwarePins != null || draftPins != null
            PinBoardRow(
                componentId = id,
                profile = profile,
                pins = pins,
                assigned = assigned,
                source = if (pins === firmwarePins) PinClaimSource.FIRMWARE else PinClaimSource.LOCAL_DRAFT,
                draftDiffersFromFirmware = firmwarePins != null && draftPins != null &&
                    firmwarePins != draftPins
            )
        }

        val claims = rows.filter { it.assigned }.flatMap { row ->
            row.pins.mapIndexedNotNull { index, pin ->
                // A released slot holds no GPIO, so it must never join the
                // ledger: two released signals would invent a conflict on 0.
                if (pin == PinAssignmentPolicy.UNASSIGNED) null
                else PinClaim(row.componentId, index, pin, row.source)
            }
        }

        val conflicts = claims.groupBy { it.pin }
            .mapNotNull { (pin, holders) ->
                val distinct = holders.map { it.componentId }.distinct()
                    .let { list ->
                        // A DHT11 draft and an active DHT22 lease are the same lease.
                        if (list.distinct().size > 1 && list.all { it in DHT_FAMILY }) {
                            listOf(list.sorted().last())
                        } else list
                    }
                if (distinct.size < 2) null
                else PinConflict(
                    pin = pin,
                    componentIds = distinct.sorted(),
                    firmwareInvolved = holders.any { it.source == PinClaimSource.FIRMWARE }
                )
            }
            .sortedBy { it.pin }

        return PinBoardReport(rows, claims.sortedBy { it.pin }, conflicts)
    }
}
