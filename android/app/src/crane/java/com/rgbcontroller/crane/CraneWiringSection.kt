package com.rgbcontroller.crane

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.rgbcontroller.core.components.registry.CraneWiring
import com.rgbcontroller.core.components.registry.CraneWiringDraft
import com.rgbcontroller.core.ui.CompiTokens

/**
 * The crane's seven GPIO, one row per signal, with the two actions the board
 * actually supports: assign a contact and release it locally. Releasing is
 * shown as what it is — a draft change — because the flashed ESP32 takes all
 * seven pins in one CAS transaction and has no "free this GPIO" frame.
 */
@Composable
fun CraneWiringSection(
    draft: CraneWiringDraft,
    authorized: Boolean,
    applying: Boolean,
    resultMessage: String?,
    onAssign: (signalIndex: Int, pin: Int) -> Unit,
    onRelease: (signalIndex: Int) -> Unit,
    onApply: () -> Unit,
    modifier: Modifier = Modifier
) {
    val profile = CraneWiring.profile()
    CraneCard(
        title = "Cableado de la grúa",
        tag = "crane_wiring_card",
        modifier = modifier,
        subtitle = if (profile == null) "Sin perfil de pines en esta versión"
        else "${draft.assignedCount} de ${profile.signals.size} señales · ${draft.stateLabel}",
        trailing = {
            CraneStateChip(
                text = if (authorized) "Sesión de propietario" else "Sin sesión",
                tone = if (authorized) CraneTone.LIVE else CraneTone.NEUTRAL,
                tag = "crane_wiring_session_chip"
            )
        }
    ) {
        if (profile == null) {
            CraneCaption(
                text = "Esta compilación no define las siete señales de la grúa.",
                tag = "crane_wiring_no_profile"
            )
            return@CraneCard
        }
        profile.signals.forEachIndexed { signalIndex, signal ->
            val pin = draft.pins.getOrElse(signalIndex) { CraneWiring.UNASSIGNED }
            CraneWiringRow(
                label = signal.label,
                pins = draft.pins,
                signalIndex = signalIndex,
                pin = pin,
                options = signal.allowedPins.sorted(),
                heldElsewhere = draft.reservedByOthers,
                onAssign = { onAssign(signalIndex, it) },
                onRelease = { onRelease(signalIndex) },
                tagIndex = signalIndex
            )
        }

        CraneLiveRegion(modifier = Modifier.fillMaxWidth()) {
            draft.blockers.forEach { blocker ->
                CraneBlockerLine(text = blocker, tag = "crane_wiring_blocker")
            }
            if (resultMessage != null) {
                CraneCaption(text = resultMessage, tag = "crane_wiring_result")
            }
        }

        Button(
            onClick = onApply,
            enabled = authorized && draft.canApply && !applying,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = CraneTouchTarget)
                .testTag("crane_wiring_apply")
                .semantics {
                    contentDescription = if (draft.canApply)
                        "Aplicar los siete GPIO en el ESP32"
                    else "Aplicar bloqueado: ${draft.blockers.joinToString(" ")}"
                }
        ) {
            Text(
                if (applying) "Aplicando…" else "Aplicar en ESP32",
                style = CompiTokens.Typography.BadgeLabel
            )
        }
        if (draft.divergesFromFirmware) {
            CraneCaption(
                text = "El borrador local no coincide con los GPIO que el ESP32 reporta ahora.",
                tag = "crane_wiring_divergent"
            )
        }
        CraneCaption(
            text = "Liberar una señal solo cambia el borrador en el teléfono. El ESP32 " +
                "recibe las siete señales en una sola transacción y no tiene comando para " +
                "soltar un GPIO: aplica con las siete asignadas, o cambia el firmware.",
            tag = "crane_wiring_footnote"
        )
    }
}

@Composable
private fun CraneWiringRow(
    label: String,
    pins: List<Int>,
    signalIndex: Int,
    pin: Int,
    options: List<Int>,
    heldElsewhere: Map<Int, String>,
    onAssign: (Int) -> Unit,
    onRelease: () -> Unit,
    tagIndex: Int
) {
    // Another crane signal already sits on this GPIO; the current one keeps
    // its own pin readable even when it is the duplicate.
    val occupiedBySibling = { candidate: Int ->
        candidate != CraneWiring.UNASSIGNED && pins.withIndex().any { (index, other) ->
            index != signalIndex && other == candidate
        }
    }
    val selfDuplicated = pin != CraneWiring.UNASSIGNED && occupiedBySibling(pin)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("crane_wiring_row_$tagIndex"),
        verticalArrangement = Arrangement.spacedBy(CompiTokens.Spacing.xxs)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CompiTokens.Spacing.xs)
        ) {
            Text(
                text = label,
                style = CompiTokens.Typography.MetricCaption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = if (pin == CraneWiring.UNASSIGNED) "Liberada" else "GPIO $pin",
                style = CompiTokens.Typography.BadgeLabel,
                color = if (pin == CraneWiring.UNASSIGNED)
                    MaterialTheme.colorScheme.onSurfaceVariant
                else if (selfDuplicated || heldElsewhere.containsKey(pin))
                    MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag("crane_wiring_pin_$tagIndex")
            )
            OutlinedButton(
                onClick = onRelease,
                enabled = pin != CraneWiring.UNASSIGNED,
                modifier = Modifier
                    .heightIn(min = CraneTouchTarget)
                    .testTag("crane_wiring_release_$tagIndex")
                    .semantics { contentDescription = "Liberar $label" }
            ) { Text("Liberar", style = CompiTokens.Typography.BadgeLabel) }
        }
        CraneChipRow {
            options.forEach { option ->
                val selected = option == pin
                val blocked = !selected && (
                    heldElsewhere.containsKey(option) || occupiedBySibling(option)
                    )
                CraneChip(
                    label = if (blocked) "$option ✕" else option.toString(),
                    selected = selected,
                    enabled = !blocked,
                    onClick = { onAssign(option) },
                    tag = "crane_wiring_option_${tagIndex}_$option"
                )
            }
        }
        val owner = heldElsewhere[pin]
        if (owner != null) {
            CraneCaption(
                text = "GPIO $pin lo retiene $owner.",
                tag = "crane_wiring_owner_$tagIndex"
            )
        } else if (selfDuplicated) {
            CraneCaption(
                text = "GPIO $pin ya está en otra señal de la grúa.",
                tag = "crane_wiring_duplicate_$tagIndex"
            )
        }
    }
}
