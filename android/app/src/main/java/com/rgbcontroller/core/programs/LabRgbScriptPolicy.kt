package com.rgbcontroller.core.programs

data class LabScriptCheck(val accepted: Boolean, val message: String, val steps: Int)

/**
 * Mirrors ESP32 RgbScriptRuntime grammar for fail-fast UX, NOT an authorization gate.
 * Firmware validates every script again and never runs Arduino C++ as text.
 */
object LabRgbScriptPolicy {
    const val MAX_SOURCE_BYTES = 512
    const val MAX_STEPS = 32
    const val MAX_DURATION_MS = 20_000
    private val rgb = Regex("""RGB ([0-9]{1,3}) ([0-9]{1,3}) ([0-9]{1,3})(?: ([0-9]{1,3}))?""")
    private val wait = Regex("""WAIT ([0-9]+)""")

    fun validate(source: String): LabScriptCheck {
        if (source.toByteArray(Charsets.UTF_8).size !in 1..MAX_SOURCE_BYTES)
            return invalid("El programa debe ocupar entre 1 y 512 bytes.")
        var steps = 0
        var totalWait = 0
        var action = false
        var stopped = false
        source.split('\n').forEachIndexed { index, original ->
            val line = original.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEachIndexed
            val lineNumber = index + 1
            if (stopped) return invalid("Línea $lineNumber: STOP debe ser la última instrucción.")
            steps++
            if (steps > MAX_STEPS) return invalid("Máximo 32 instrucciones por ejecución.")
            when {
                line == "ON" || line == "OFF" -> action = true
                line == "STOP" -> stopped = true
                rgb.matches(line) -> {
                    val values = rgb.matchEntire(line)!!.groupValues.drop(1)
                        .filter { it.isNotEmpty() }
                    if (values.any { it.toIntOrNull()?.let { number -> number in 0..255 } != true })
                        return invalid("Línea $lineNumber: RGB requiere valores 0–255.")
                    action = true
                }
                wait.matches(line) -> {
                    val time = wait.matchEntire(line)!!.groupValues[1].toIntOrNull()
                    if (time == null || time !in 100..2000)
                        return invalid("Línea $lineNumber: WAIT debe estar entre 100 y 2000 ms.")
                    totalWait += time
                    if (totalWait > MAX_DURATION_MS) return invalid("Máximo 20 segundos acumulados.")
                }
                else -> return invalid("Línea $lineNumber: comando no admitido. Usa RGB, WAIT, ON, OFF o STOP.")
            }
        }
        if (!action) return invalid("Incluye al menos una instrucción RGB, ON u OFF.")
        return LabScriptCheck(true, "Script compatible con el intérprete RGB V4. Falta verificar conexión y firmware.", steps)
    }

    private fun invalid(message: String) = LabScriptCheck(false, message, 0)
}
