package com.rgbcontroller.core.export

import com.rgbcontroller.core.components.status.LocalDesiredComponent
import com.rgbcontroller.core.components.registry.PinAssignmentPolicy

/**
 * How an exported project relates to a target board. Never assumes a project
 * "just works": unknown boards are reported as [UNKNOWN] and pin conflicts must
 * be remapped before the project can be trusted on the target.
 */
enum class CompatibilityLevel {
    COMPATIBLE,
    REQUIRES_REMAP,
    PARTIALLY_COMPATIBLE,
    INCOMPATIBLE,
    UNKNOWN
}

/** A board the app knows about, with its usable and reserved GPIO. */
data class BoardProfile(
    val id: String,
    val displayName: String,
    val supportedTypes: Set<String>,
    val reservedPins: Set<Int>,
    val preferredPins: Map<String, List<Int>> = emptyMap(),
    val isKnown: Boolean = true
) {
    /** Pins that may be assigned, i.e. 0..39 minus the reserved ones. */
    val assignablePins: Set<Int>
        get() = if (id == "esp32-legacy-v1") {
            // Board-level assignability includes dedicated sensor/display pins,
            // even though GPIO21/22 are NOT general-purpose lab outputs.
            // Per-module signal profiles prevent cross-claiming OLED pins.
            (PinAssignmentPolicy.pwmOutputs + PinAssignmentPolicy.adc1Inputs +
                setOf(16, 17, 21, 22)) - reservedPins
        } else {
            (0..39).filterNot { it in reservedPins }.toSet()
        }

    companion object {
        /** Marker profile used when no manifest matches the requested id. */
        fun unknown(id: String): BoardProfile =
            BoardProfile(id, "Desconocida ($id)", emptySet(), emptySet(), emptyMap(), isKnown = false)
    }
}

/** One component that had to move because of a pin conflict. */
data class RemappedComponent(
    val componentId: String,
    val fromPins: List<Int>,
    val toPins: List<Int>
)

/** A blocking or warning problem found while evaluating compatibility. */
sealed interface CompatibilityIssue {
    val componentId: String

    data class UnsupportedType(
        override val componentId: String,
        val type: String
    ) : CompatibilityIssue

    data class PinConflict(
        override val componentId: String,
        val conflictingPins: List<Int>
    ) : CompatibilityIssue

    data class PinOutOfRange(
        override val componentId: String,
        val pins: List<Int>
    ) : CompatibilityIssue

    data class SignalMismatch(
        override val componentId: String,
        val expected: Int,
        val actual: Int
    ) : CompatibilityIssue
}

/** Result of matching a project against a [BoardProfile]. */
data class CompatibilityReport(
    val boardId: String,
    val level: CompatibilityLevel,
    val issues: List<CompatibilityIssue>,
    val remapped: List<RemappedComponent>,
    val unmappedComponentIds: List<String>
) {
    /** Components with remapped pins, keyed by id, ready to apply on import. */
    fun remappedById(): Map<String, List<Int>> = remapped.associate { it.componentId to it.toPins }

    /** True when the project can be applied (with or without remapping). */
    val applicable: Boolean
        get() = level == CompatibilityLevel.COMPATIBLE || level == CompatibilityLevel.REQUIRES_REMAP
}

/**
 * Deterministic board-compatibility and remap engine. It only ever assigns pins
 * from [BoardProfile.assignablePins]; reserved pins and already-used pins are
 * never handed out. Failures are reported, never silently patched with an
 * arbitrary pin.
 */
object BoardCompatibilityEngine {

    fun evaluate(project: ProjectExportEnvelope, target: BoardProfile): CompatibilityReport {
        if (!target.isKnown) {
            return CompatibilityReport(
                boardId = target.id,
                level = CompatibilityLevel.UNKNOWN,
                issues = emptyList(),
                remapped = emptyList(),
                unmappedComponentIds = emptyList()
            )
        }

        val issues = mutableListOf<CompatibilityIssue>()
        val remapped = mutableListOf<RemappedComponent>()
        val unmapped = mutableListOf<String>()
        val usedPins = mutableSetOf<Int>()
        var compatibleCount = 0
        var incompatibleCount = 0

        for (component in project.components) {
            if (component.type !in target.supportedTypes) {
                issues += CompatibilityIssue.UnsupportedType(component.id, component.type)
                incompatibleCount++
                unmapped += component.id
                continue
            }

            val profile = if (target.id == "esp32-legacy-v1") {
                PinAssignmentPolicy.profile(component.id)
            } else null
            val countMismatch = profile != null && component.pins.size != profile.signals.size
            val outOfRange = component.pins.filterIndexed { index, pin ->
                pin !in target.assignablePins ||
                    (profile != null && (index >= profile.signals.size ||
                        pin !in profile.signals[index].allowedPins)) ||
                    (target.id == "esp32-legacy-v1" && component.id != "led_rgb" &&
                        pin in PinAssignmentPolicy.rgbReserved)
            }
            val duplicatePins = component.pins.size != component.pins.distinct().size
            val conflict = component.pins.any { it in usedPins }
            if (outOfRange.isEmpty() && !countMismatch && !duplicatePins && !conflict) {
                usedPins += component.pins
                compatibleCount++
                continue
            }
            if (countMismatch) {
                issues += CompatibilityIssue.SignalMismatch(
                    component.id, profile!!.signals.size, component.pins.size
                )
            }
            if (outOfRange.isNotEmpty()) {
                issues += CompatibilityIssue.PinOutOfRange(component.id, outOfRange)
            }
            if (duplicatePins || conflict) {
                issues += CompatibilityIssue.PinConflict(
                    component.id,
                    component.pins.filter { it in usedPins ||
                        component.pins.count { candidate -> candidate == it } > 1 }
                )
            }

            val replacement = remap(component, target, usedPins)
            if (replacement != null) {
                remapped += RemappedComponent(component.id, component.pins, replacement)
                usedPins += replacement
                compatibleCount++
            } else {
                incompatibleCount++
                unmapped += component.id
            }
        }

        val level = when {
            incompatibleCount > 0 && compatibleCount == 0 -> CompatibilityLevel.INCOMPATIBLE
            incompatibleCount > 0 -> CompatibilityLevel.PARTIALLY_COMPATIBLE
            remapped.isNotEmpty() -> CompatibilityLevel.REQUIRES_REMAP
            else -> CompatibilityLevel.COMPATIBLE
        }
        return CompatibilityReport(target.id, level, issues, remapped, unmapped.distinct())
    }

    /**
     * Pick fresh pins for [component] from the target's preferred list, skipping
     * reserved and already-used pins. Returns null when there is no safe option,
     * so the caller reports incompatibility instead of guessing.
     */
    private fun remap(
        component: LocalDesiredComponent,
        target: BoardProfile,
        usedPins: Set<Int>
    ): List<Int>? {
        // For the classic ESP32 use per-signal capabilities. In particular
        // never remap a DHT onto an ADC-only pin, or a relay onto RGB pins.
        val profile = if (target.id == "esp32-legacy-v1") {
            PinAssignmentPolicy.profile(component.id)
        } else null
        if (profile != null) {
            val allocated = usedPins.toMutableSet()
            val result = mutableListOf<Int>()
            for ((index, signal) in profile.signals.withIndex()) {
                val original = component.pins.getOrNull(index)
                val choices = listOfNotNull(original, signal.suggestedPin) + signal.allowedPins.sorted()
                val pin = choices.firstOrNull { it in signal.allowedPins &&
                    it in target.assignablePins && it !in allocated &&
                    (component.id == "led_rgb" || it !in PinAssignmentPolicy.rgbReserved)
                } ?: return null
                allocated += pin
                result += pin
            }
            return result
        }
        if (component.pins.isEmpty()) return null
        val preferred = target.preferredPins[component.type].orEmpty()
        val candidates = (preferred + target.assignablePins.sorted())
            .distinct()
            .filter { it !in usedPins && it in target.assignablePins &&
                (target.id != "esp32-legacy-v1" ||
                    it !in PinAssignmentPolicy.rgbReserved) }
        if (candidates.size < component.pins.size) return null
        return candidates.take(component.pins.size)
    }
}

/** Built-in board profiles and lookup. Unknown ids resolve to a marker profile. */
object BoardProfiles {
    val ESP32_LEGACY_V1 = BoardProfile(
        id = "esp32-legacy-v1",
        displayName = "ESP32 (legacy)",
        supportedTypes = setOf("sensor", "actuator", "display", "lighting",
            "input", "output", "controller", "unknown"),
        // GPIO outside audited classic ESP32 lab policy and boot/flash pins.
        // RGB 25/26/27 are reserved by their owning module, not globally.
        reservedPins = setOf(0, 2, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15),
        preferredPins = mapOf(
            "sensor" to listOf(32, 34, 35),
            "actuator" to listOf(18, 19),
            "lighting" to listOf(27, 25, 26),
            "display" to listOf(21, 22)
        )
    )

    val ESP32_S3_V1 = BoardProfile(
        id = "esp32-s3-v1",
        displayName = "ESP32-S3",
        supportedTypes = setOf("sensor", "actuator", "display", "unknown"),
        reservedPins = setOf(26, 27, 28, 29, 30, 31, 32),
        preferredPins = mapOf(
            "sensor" to listOf(4, 5, 6, 7, 15, 16),
            "actuator" to listOf(17, 18, 8, 9),
            "display" to listOf(1, 2)
        )
    )

    val all: List<BoardProfile> = listOf(ESP32_LEGACY_V1, ESP32_S3_V1)

    /** Look up a profile by id; unknown ids never throw, they return UNKNOWN. */
    fun find(id: String?): BoardProfile =
        all.firstOrNull { it.id == id } ?: BoardProfile.unknown(id ?: "desconocida")
}
