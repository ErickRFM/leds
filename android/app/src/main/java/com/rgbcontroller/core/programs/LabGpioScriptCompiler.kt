package com.rgbcontroller.core.programs

import com.rgbcontroller.core.hardware.Esp32BoardPinout
import com.rgbcontroller.core.lab.LabExperimentLibrary
import com.rgbcontroller.core.lab.LabStep

/** Compile a tiny safe GPIO DSL into the existing bounded V3 sequence contract.
 * The ESP32 still validates every instruction and arbitrates the output pin.
 * No C++ execution, direct flashing or privileged access is supported here.
 */
data class CompiledGpioScript(
    val pin: Int,
    val sequence: String,
    val stepCount: Int,
    val durationMs: Int
)

object LabGpioScriptCompiler {
    private const val MAX_SOURCE_LINES = 48

    /**
     * GPIO 19 HIGH
     * WAIT 500
     * GPIO 19 LOW
     * WAIT 500
     * STOP
     */
    fun compile(source: String): CompiledGpioScript {
        val inspection = ProgramSourcePolicy.inspect(source)
        require(inspection.acceptedForEditing) { inspection.explanation }
        val lines = source.lines()
        require(lines.size <= MAX_SOURCE_LINES) { "Máximo $MAX_SOURCE_LINES líneas por script GPIO." }
        val steps = ArrayList<LabStep>()
        var selectedPin: Int? = null
        var pendingLevel: Boolean? = null
        var durationTotal = 0
        var stopped = false

        for ((index, rawLine) in lines.withIndex()) {
            val line = rawLine.substringBefore('#').trim()
            if (line.isEmpty()) continue
            val parts = line.split(Regex("\\s+"))
            val command = parts[0].uppercase()
            fun fail(detail: String): Nothing =
                throw IllegalArgumentException("Línea " + (index + 1) + ": " + detail)

            if (stopped) fail("STOP debe ser la última instrucción.")
            when (command) {
                "GPIO" -> {
                    if (parts.size != 3) fail("Usa GPIO 19 HIGH o GPIO 19 LOW.")
                    if (pendingLevel != null) fail("Falta WAIT antes de cambiar la salida.")
                    val pin = parts[1].toIntOrNull() ?: fail("El GPIO debe ser numérico.")
                    if (!Esp32BoardPinout.canWrite(pin)) {
                        fail("GPIO $pin no está permitido. Usa 18, 19, 21, 22 o 23.")
                    }
                    if (selectedPin != null && selectedPin != pin) {
                        fail("Este motor admite un único GPIO por programa.")
                    }
                    selectedPin = pin
                    pendingLevel = when (parts[2].uppercase()) {
                        "HIGH" -> true
                        "LOW" -> false
                        else -> fail("El valor debe ser HIGH o LOW.")
                    }
                }
                "WAIT" -> {
                    if (parts.size != 2) fail("Usa WAIT 500 (milisegundos).")
                    val level = pendingLevel ?: fail("WAIT debe seguir a una instrucción GPIO.")
                    val duration = parts[1].toIntOrNull() ?: fail("Tiempo inválido.")
                    if (duration !in 100..2000) fail("WAIT debe estar entre 100 y 2000 ms.")
                    if (steps.size >= LabExperimentLibrary.MAX_STEPS) fail("Máximo 16 pasos.")
                    durationTotal += duration
                    if (durationTotal > LabExperimentLibrary.MAX_DURATION_MS) {
                        fail("Duración máxima: 20 segundos.")
                    }
                    steps.add(LabStep(level, duration))
                    pendingLevel = null
                }
                "STOP" -> {
                    if (parts.size != 1) fail("STOP no acepta parámetros.")
                    if (pendingLevel != null) fail("Falta WAIT antes de STOP.")
                    stopped = true
                }
                "RGB" -> fail("Estás en el motor GPIO. Cambia a Código → Motor LED RGB para ejecutar este script.")
                else -> fail("Instrucción no compatible: $command.")
            }
        }
        require(pendingLevel == null) { "Falta WAIT después del último GPIO." }
        require(steps.isNotEmpty()) { "Agrega al menos GPIO y WAIT." }
        val encoded = LabExperimentLibrary.encode(steps)
            ?: error("No se pudo codificar la secuencia dentro de los límites V3.")
        return CompiledGpioScript(selectedPin ?: error("GPIO ausente"), encoded, steps.size, durationTotal)
    }
}
