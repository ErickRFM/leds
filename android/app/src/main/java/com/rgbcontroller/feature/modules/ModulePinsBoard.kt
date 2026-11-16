package com.rgbcontroller.feature.modules

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rgbcontroller.core.components.registry.ComponentCatalog
import com.rgbcontroller.core.components.registry.PinBoardReport
import com.rgbcontroller.core.components.registry.PinBoardRow
import com.rgbcontroller.core.components.registry.PinAssignmentPolicy
import com.rgbcontroller.core.components.registry.PinClaimSource
import com.rgbcontroller.core.components.registry.PinConflict
import com.rgbcontroller.core.ui.CompiTokens

/**
 * The board-wide GPIO surface: every module that owns a wiring profile shows
 * its signals with the candidate GPIOs already filtered by what the other
 * modules hold. Assigning is one tap and writes the local draft immediately,
 * which is why the chosen pin can never silently collide with another module.
 */
@OptIn(ExperimentalLayoutApi::class)
internal fun LazyListScope.modulePinsItems(
    report: PinBoardReport,
    statusMessage: String?,
    onAssign: (componentId: String, signalIndex: Int, pin: Int) -> Unit
) {
    item(key = "pins_summary") { PinsSummaryCard(report) }
    if (report.conflicts.isNotEmpty()) {
        item(key = "pins_conflicts") { PinsConflictCard(report.conflicts) }
    }
    if (statusMessage != null) {
        item(key = "pins_result") {
            Text(
                statusMessage,
                style = CompiTokens.Typography.BodyClean,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CompiTokens.Spacing.xs)
                    .testTag("modules_pins_result")
                    .semantics { liveRegion = LiveRegionMode.Polite }
            )
        }
    }
    items(items = report.rows, key = { "pins_row_${it.componentId}" }) { row ->
        PinModuleCard(
            row = row,
            report = report,
            onAssign = { signalIndex, pin -> onAssign(row.componentId, signalIndex, pin) }
        )
    }
    item(key = "pins_footnote") {
        Text(
            "La asignación guarda un borrador local. El ESP32 solo cambia de GPIO cuando aplicas desde el editor del módulo con Bluetooth o Wi-Fi autorizado.",
            style = CompiTokens.Typography.BodyClean,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CompiTokens.Spacing.xs, vertical = CompiTokens.Spacing.xs)
                .testTag("modules_pins_footnote")
        )
    }
}

@Composable
private fun PinsSummaryCard(report: PinBoardReport) {
    val reserved = report.claims.map { it.pin }.distinct().size
    val offerable = PinAssignmentPolicy.offerablePins.size
    BoardCard(modifier = Modifier.testTag("modules_pins_summary")) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CompiTokens.Spacing.s)
        ) {
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(CompiTokens.Spacing.xxs)
            ) {
                Text(
                    "Mapa de GPIO del ESP32",
                    style = CompiTokens.Typography.SectionHeader,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "$reserved de $offerable GPIO reservados · ${offerable - reserved} disponibles",
                    style = CompiTokens.Typography.BadgeLabel,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            MetricCount(value = report.rows.size.toString(), caption = "MÓDULOS")
            MetricCount(
                value = report.conflicts.size.toString(),
                caption = "CONFLICTOS",
                alert = report.hasConflicts
            )
        }
    }
}

@Composable
private fun MetricCount(value: String, caption: String, alert: Boolean = false) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CompiTokens.Spacing.xxs)
    ) {
        Text(
            value,
            style = CompiTokens.Typography.MetricValue,
            color = if (alert) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Text(
            caption,
            style = CompiTokens.Typography.MetricCaption,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PinsConflictCard(conflicts: List<PinConflict>) {
    BoardCard(
        containerColor = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        modifier = Modifier.testTag("modules_pins_conflicts")
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(CompiTokens.Spacing.xxs)) {
            Text("GPIO en disputa entre módulos", style = CompiTokens.Typography.SectionHeader)
            conflicts.forEach { conflict ->
                val names = conflict.componentIds.joinToString(" y ") { moduleShortName(it) }
                Text(
                    "GPIO ${conflict.pin} · $names" +
                        if (conflict.firmwareInvolved) " · el firmware lo confirma" else "",
                    style = CompiTokens.Typography.BodyClean,
                    modifier = Modifier.testTag("modules_pins_conflict_${conflict.pin}")
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PinModuleCard(
    row: PinBoardRow,
    report: PinBoardReport,
    onAssign: (signalIndex: Int, pin: Int) -> Unit
) {
    val conflictingPins = report.conflicts
        .filter { row.componentId in it.componentIds }
        .map { it.pin }
        .toSet()
    BoardCard(modifier = Modifier.testTag("module_pin_row_${row.componentId}")) {
        Column(verticalArrangement = Arrangement.spacedBy(CompiTokens.Spacing.s)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CompiTokens.Spacing.xs)
            ) {
                Text(
                    moduleShortName(row.componentId),
                    style = CompiTokens.Typography.SectionHeader,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                SourceBadge(row)
            }
            if (row.draftDiffersFromFirmware) {
                Text(
                    "El firmware tiene otros GPIO; aplica desde el módulo para igualarlos.",
                    style = CompiTokens.Typography.BadgeLabel,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("module_pin_divergent_${row.componentId}")
                )
            }
            row.profile.signals.forEachIndexed { signalIndex, signal ->
                Column(verticalArrangement = Arrangement.spacedBy(CompiTokens.Spacing.xxs)) {
                    Text(
                        signal.label,
                        style = CompiTokens.Typography.MetricCaption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("module_pin_signal_${row.componentId}_${signalIndex}")
                    )
                    val options = report.optionsFor(row.componentId, signalIndex)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(CompiTokens.Spacing.xs),
                        verticalArrangement = Arrangement.spacedBy(CompiTokens.Spacing.xxs)
                    ) {
                        options.forEach { option ->
                            val selected = row.pins.getOrNull(signalIndex) == option.pin
                            FilterChip(
                                selected = selected,
                                // A disabled chip is invisible as a selection, so the GPIO
                                // the module already holds stays readable even in a dispute;
                                // the conflict line below says what is wrong with it.
                                enabled = option.available || selected,
                                onClick = { onAssign(signalIndex, option.pin) },
                                label = {
                                    Text(
                                        if (option.available || selected) option.pin.toString()
                                        else "${option.pin} ✕",
                                        style = CompiTokens.Typography.BadgeLabel
                                    )
                                },
                                shape = CompiTokens.Shapes.Small,
                                modifier = Modifier
                                    .heightIn(min = 40.dp)
                                    .testTag(
                                        "module_pin_option_${row.componentId}_${signalIndex}_${option.pin}"
                                    )
                            )
                        }
                    }
                    val occupiedByOthers = options
                        .filter { !it.available && it.takenBy != row.componentId }
                        .mapNotNull { option -> option.takenBy?.let { option.pin to it } }
                    if (occupiedByOthers.isNotEmpty()) {
                        Text(
                            occupiedByOthers.joinToString(" · ") { (pin, owner) ->
                                "GPIO $pin en ${moduleShortName(owner)}"
                            },
                            style = CompiTokens.Typography.BadgeLabel,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("module_pin_taken_${row.componentId}_${signalIndex}")
                        )
                    }
                    if (conflictingPins.isNotEmpty()) {
                        Text(
                            "Conflicto en GPIO ${conflictingPins.joinToString()} · resuelve antes de aplicar",
                            style = CompiTokens.Typography.BadgeLabel,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.testTag("module_pin_conflict_${row.componentId}")
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceBadge(row: PinBoardRow) {
    val label = when {
        !row.assigned -> "Sin asignar"
        row.source == PinClaimSource.FIRMWARE -> "Firmware"
        else -> "Borrador local"
    }
    val confirmed = row.assigned && row.source == PinClaimSource.FIRMWARE
    Surface(
        color = if (confirmed) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (confirmed) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant,
        shape = CompiTokens.Shapes.Small,
        modifier = Modifier.testTag("module_pin_source_${row.componentId}")
    ) {
        Text(
            label,
            style = CompiTokens.Typography.BadgeLabel,
            modifier = Modifier.padding(
                horizontal = CompiTokens.Spacing.xs,
                vertical = CompiTokens.Spacing.xxs
            )
        )
    }
}

@Composable
private fun BoardCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = CompiTokens.Shapes.Medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = containerColor, contentColor = contentColor),
        elevation = CardDefaults.cardElevation(defaultElevation = CompiTokens.Elevations.flat)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(CompiTokens.Spacing.m),
            verticalArrangement = Arrangement.spacedBy(CompiTokens.Spacing.s)
        ) { content() }
    }
}

/** Short hardware labels: the full catalog names do not fit a pin chip. */
internal fun moduleShortName(id: String): String = when (id) {
    "soil_moisture" -> "Suelo"
    "ldr_light" -> "LDR"
    "ssd1306_oled" -> "OLED"
    "led_rgb" -> "RGB"
    "robotics_crane" -> "Grúa"
    "analog_sensor" -> "Sensor analógico"
    "digital_sensor" -> "Sensor digital"
    "actuator_relay" -> "Relé"
    else -> ComponentCatalog.get(id)?.name ?: id
}
