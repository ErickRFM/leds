package com.rgbcontroller.core.lab

/** Bounded experiment steps executed by the existing ESP32 lab interpreter. */
data class LabStep(val high: Boolean, val durationMs: Int)
data class LabTemplate(val name: String, val sequence: String)

object LabExperimentLibrary {
    const val MAX_STEPS = 16
    const val MAX_DURATION_MS = 20_000
    val templates = listOf(
        LabTemplate("Pulso suave", "1:500,0:500"),
        LabTemplate("Doble destello", "1:150,0:150,1:150,0:500"),
        LabTemplate("Alerta lenta", "1:1000,0:1000,1:1000,0:1000")
    )

    /** null: invalid; empty sequence: edit-only draft without executable steps. */
    fun parse(sequence: String): List<LabStep>? {
        if (sequence.isBlank() || sequence.length > 120 || sequence.endsWith(",")) return null
        val parts = sequence.split(',')
        if (parts.size !in 1..MAX_STEPS) return null
        val parsed = ArrayList<LabStep>(parts.size)
        var total = 0
        for (part in parts) {
            val fields = part.split(':')
            if (fields.size != 2 || fields[0] !in setOf("0", "1") ||
                fields[1].length !in 3..4 || !fields[1].all { it in '0'..'9' }) return null
            val duration = fields[1].toIntOrNull() ?: return null
            if (duration !in 100..2000) return null
            total += duration
            if (total > MAX_DURATION_MS) return null
            parsed.add(LabStep(fields[0] == "1", duration))
        }
        return parsed
    }

    fun encode(steps: List<LabStep>): String? {
        if (steps.isEmpty() || steps.size > MAX_STEPS ||
            steps.any { it.durationMs !in 100..2000 } ||
            steps.sumOf { it.durationMs } > MAX_DURATION_MS) return null
        return steps.joinToString(",") { "${if (it.high) 1 else 0}:${it.durationMs}" }
            .takeIf { it.length <= 120 }
    }

    fun append(sequence: String, next: LabStep): String? {
        val existing = if (sequence.isBlank()) emptyList() else parse(sequence) ?: return null
        return encode(existing + next)
    }

    fun removeAt(sequence: String, index: Int): String? {
        val original = parse(sequence) ?: return null
        if (index !in original.indices) return null
        val remaining = original.filterIndexed { i, _ -> i != index }
        return if (remaining.isEmpty()) "" else encode(remaining)
    }
}
