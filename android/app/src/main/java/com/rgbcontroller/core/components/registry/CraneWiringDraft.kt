package com.rgbcontroller.core.components.registry

/**
 * The crane's seven signals can be emptied in the app even though the flashed
 * firmware cannot empty them on the board. [UNASSIGNED] is GPIO 0: no wiring
 * profile ever offers it, so a zero in a draft can only mean "released here",
 * never "the ESP32 reported GPIO 0".
 */
object CraneWiring {
    const val COMPONENT_ID = "robotics_crane"
    const val UNASSIGNED = PinAssignmentPolicy.UNASSIGNED

    fun profile(): ModuleWiringProfile? = PinAssignmentPolicy.profile(COMPONENT_ID)

    fun signalCount(): Int = profile()?.signals?.size ?: 0
}

/**
 * Local-only view of the crane wiring: which GPIO each of the seven signals
 * holds, what the ESP32 currently reports, and therefore whether an apply can
 * be attempted at all. It never claims the firmware accepted anything — the
 * board is a seven-signal CAS transaction, so an incomplete draft is a blocked
 * apply, not a released pin.
 */
data class CraneWiringDraft(
    val pins: List<Int>,
    val firmwarePins: List<Int>? = null,
    val reservedByOthers: Map<Int, String> = emptyMap()
) {
    private val profile: ModuleWiringProfile? = CraneWiring.profile()
    private val labels: List<String> get() = profile?.signals?.map { it.label }.orEmpty()

    val released: List<Int> = pins.indices.filter { pins[it] == CraneWiring.UNASSIGNED }

    val assignedCount: Int = pins.count { it != CraneWiring.UNASSIGNED }

    /** Same GPIO held by two crane signals. */
    val duplicates: List<Int> = pins.filter { it != CraneWiring.UNASSIGNED }
        .groupingBy { it }.eachCount().filterValues { it > 1 }.keys.sorted()

    /** Signals whose pin the profile does not allow for that function. */
    val illegal: List<String> = buildList {
        val wiring = profile ?: return@buildList
        for ((index, pin) in pins.withIndex()) {
            val signal = wiring.signals.getOrNull(index) ?: continue
            if (pin != CraneWiring.UNASSIGNED && pin !in signal.allowedPins) {
                add("${signal.label} · GPIO $pin no es un contacto válido para esa señal")
            }
        }
    }

    /** Pins the crane draft wants that another module already holds locally. */
    val collisions: List<String> = buildList {
        for ((index, pin) in pins.withIndex()) {
            val owner = reservedByOthers[pin] ?: continue
            val label = labels.getOrNull(index) ?: "Señal $index"
            add("$label · GPIO $pin ya lo tiene $owner")
        }
    }

    /** Why "Aplicar en ESP32" cannot run, in the order a user can act on it. */
    val blockers: List<String> = buildList {
        if (profile == null) {
            add("La grúa no tiene perfil de pines en esta versión.")
            return@buildList
        }
        if (released.isNotEmpty()) {
            add("Faltan ${released.size} de ${profile.signals.size} señales: " +
                released.mapNotNull { labels.getOrNull(it) }.joinToString(", ") +
                ". El ESP32 recibe las siete en una sola transacción, así que liberar " +
                "una señal solo cambia el borrador local; no suelta el GPIO en la placa.")
        }
        if (duplicates.isNotEmpty()) {
            add("GPIO ${duplicates.joinToString(", ")} asignado a dos señales del brazo.")
        }
        illegal.forEach { add(it) }
        collisions.forEach { add(it) }
    }

    val canApply: Boolean get() = blockers.isEmpty()

    /** The board reports different GPIO than the draft, so applying is a real change. */
    val divergesFromFirmware: Boolean
        get() = firmwarePins != null && firmwarePins != pins

    val stateLabel: String
        get() = when {
            firmwarePins == null && assignedCount == 0 -> "Sin señales asignadas"
            firmwarePins == null -> "Sugerencia sin confirmar"
            firmwarePins == pins -> "Igual que el ESP32"
            else -> "Borrador distinto al ESP32"
        }

    fun assign(signalIndex: Int, pin: Int): CraneWiringDraft =
        if (signalIndex !in pins.indices) this
        else copy(pins = pins.toMutableList().also { it[signalIndex] = pin })

    fun release(signalIndex: Int): CraneWiringDraft =
        assign(signalIndex, CraneWiring.UNASSIGNED)

    companion object {
        /**
         * The saved local draft is what the user is editing, so it wins over the
         * firmware report; otherwise the periodic status poll would overwrite an
         * in-progress edit and no change could survive on screen. The firmware
         * list is never discarded: it is what [divergesFromFirmware] and
         * [stateLabel] compare against, and [reservedByOthers] still blocks an
         * apply that would take a GPIO another active lease owns.
         */
        fun of(
            draft: List<Int>?,
            firmware: List<Int>?,
            reservedByOthers: Map<Int, String> = emptyMap()
        ): CraneWiringDraft {
            val size = CraneWiring.signalCount()
            val trusted = if (draft?.size == size) draft
            else if (firmware?.size == size) firmware
            // First run: neither a saved draft nor a board report exists, so the
            // editor opens on the profile's suggested wiring rather than on seven
            // empty rows. Nothing is written until the user edits, and
            // stateLabel keeps saying the board was never asked.
            else CraneWiring.profile()?.defaults ?: List(size) { CraneWiring.UNASSIGNED }
            return CraneWiringDraft(trusted, firmware?.takeIf { it.size == size }, reservedByOthers)
        }
    }
}
