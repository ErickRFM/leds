package com.rgbcontroller.feature.hardwarelab

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.rgbcontroller.core.components.editors.ComponentConfigDraft
import com.rgbcontroller.core.components.editors.LabConfigValidation
import com.rgbcontroller.core.components.editors.SoilCalibrationDraft
import com.rgbcontroller.core.components.registry.ComponentCatalog
import com.rgbcontroller.core.components.status.LocalDesiredComponent

/**
 * Wizard to capture the two soil references from real readings. It never invents
 * a value: without a fresh, valid `adc_raw` reading there is nothing to capture,
 * and the pair is validated (range + minimum separation) before it can be saved.
 */
@Composable
fun SoilCalibrationCaptureDialog(
    deviceId: String?,
    storedDry: Int?,
    storedWet: Int?,
    liveRaw: Int?,
    onSave: (Int, Int) -> Unit,
    onDismiss: () -> Unit
) {
    var dryText by remember { mutableStateOf(storedDry?.toString() ?: "") }
    var wetText by remember { mutableStateOf(storedWet?.toString() ?: "") }
    val result = remember(dryText, wetText) { SoilCalibrationDraft(dryText, wetText).validate() }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("soil_calibration_wizard"),
        title = { Text("Calibrar humedad del suelo") },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    if (liveRaw != null) "Lectura actual (adc_raw): $liveRaw"
                    else "Sin lectura real de ADC: captura deshabilitada, nunca se simula.",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (liveRaw != null) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("soil_calibration_live")
                )
                RawCalibrationField(
                    label = "Referencia seco (ADC)", value = dryText,
                    fieldTag = "soil_calibration_dry", captureTag = "soil_calibration_dry_capture",
                    error = result.issues.firstOrNull { it.field == "dry" }?.message,
                    liveRaw = liveRaw, onChange = { dryText = it }
                )
                RawCalibrationField(
                    label = "Referencia húmedo (ADC)", value = wetText,
                    fieldTag = "soil_calibration_wet", captureTag = "soil_calibration_wet_capture",
                    error = result.issues.firstOrNull { it.field == "wet" }?.message,
                    liveRaw = liveRaw, onChange = { wetText = it }
                )
                if (deviceId == null) {
                    Text("Sin dispositivo identificado: no se puede guardar una calibración.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("soil_calibration_no_device"))
                }
                Text(
                    "La calibración se guarda ligada a este dispositivo y solo afecta al " +
                        "porcentaje mostrado en local; no cambia la configuración del firmware.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { result.value?.let { onSave(it.dryRaw, it.wetRaw) } },
                enabled = result.isValid && deviceId != null,
                modifier = Modifier.testTag("soil_calibration_save")
            ) { Text("Guardar calibración") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun RawCalibrationField(
    label: String,
    value: String,
    fieldTag: String,
    captureTag: String,
    error: String?,
    liveRaw: Int?,
    onChange: (String) -> Unit
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            label = { Text(label) },
            isError = error != null,
            supportingText = error?.let { { Text(it) } },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth().testTag(fieldTag)
        )
        TextButton(
            onClick = { liveRaw?.let { onChange(it.toString()) } },
            enabled = liveRaw != null,
            modifier = Modifier.testTag(captureTag)
        ) { Text("Usar lectura actual") }
    }
}

/** Editor for the reusable local component list (catalog id + GPIO). */
@Composable
fun ComponentConfigEditorDialog(
    components: List<LocalDesiredComponent>,
    onSave: (List<LocalDesiredComponent>) -> Unit,
    onDismiss: () -> Unit
) {
    val catalog = ComponentCatalog.components
    var working by remember {
        mutableStateOf(components.map { ComponentConfigDraft(it.id, it.pins.joinToString(", ")) })
    }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("component_config_editor"),
        title = { Text("Componentes locales reutilizables") },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (working.isEmpty()) {
                    Text("Sin componentes locales. Añade uno del catálogo.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                working.forEachIndexed { index, draft ->
                    Card(Modifier.fillMaxWidth().testTag("component_config_${draft.id}")) {
                        Column(Modifier.fillMaxWidth().padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(draft.id.ifBlank { "sin id" }, fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodySmall)
                            OutlinedTextField(
                                value = draft.pinsText,
                                onValueChange = {
                                    val next = working.toMutableList()
                                    next[index] = draft.copy(pinsText = it)
                                    working = next
                                },
                                label = { Text("GPIO (separados por comas)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                                    .testTag("component_config_pins_${draft.id}")
                            )
                            TextButton(
                                onClick = {
                                    working = working.toMutableList().also { it.removeAt(index) }
                                },
                                modifier = Modifier.testTag("component_config_remove")
                            ) { Text("Eliminar") }
                        }
                    }
                }
                Text("Añadir del catálogo", style = MaterialTheme.typography.labelMedium)
                Text("Selecciona el componente y ajusta los GPIO propuestos. " +
                    "Los cambios son locales hasta que el ESP32 los confirme.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                    catalog.forEach { descriptor ->
                        FilterChip(
                            selected = working.any { it.id == descriptor.id },
                            onClick = {
                                if (working.none { it.id == descriptor.id }) {
                                    working = working + ComponentConfigDraft(
                                        descriptor.id,
                                        (if (descriptor.id == "soil_moisture") listOf(34)
                                        else descriptor.requiredPins.sorted()).joinToString(", ")
                                    )
                                }
                            },
                            label = { Text(descriptor.id) }
                        )
                    }
                }
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.testTag("component_config_error"))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val results = working.map { it.validate(catalog) }
                    val invalid = results.firstOrNull { !it.isValid }
                    if (invalid != null) {
                        error = invalid.issues.first().message
                        return@TextButton
                    }
                    onSave(results.mapNotNull { it.value })
                },
                modifier = Modifier.testTag("component_config_save")
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

/** Banner listing problems in the currently stored local configuration. */
@Composable
fun ConfigValidationIssues(components: List<LocalDesiredComponent>) {
    val issues = LabConfigValidation.validateDesired(components)
    if (issues.isEmpty()) return
    Card(Modifier.fillMaxWidth().testTag("component_config_issues")) {
        Column(Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Configuración con problemas",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.SemiBold)
            issues.forEach {
                Text("· ${it.message}", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
