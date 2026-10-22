package com.rgbcontroller.feature.hardwarelab

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import com.rgbcontroller.core.components.alerts.AlertComparison
import com.rgbcontroller.core.components.alerts.AlertRule
import com.rgbcontroller.core.components.alerts.AlertSeverity
import com.rgbcontroller.core.components.automation.AutomationRule
import com.rgbcontroller.core.components.editors.AlertRuleDraft
import com.rgbcontroller.core.components.editors.AlertRuleEditor
import com.rgbcontroller.core.components.editors.AutomationActionType
import com.rgbcontroller.core.components.editors.AutomationRuleDraft

/**
 * Editors for the locally stored alert/automation rules. They only ever produce
 * a valid rule set (the pure layer rejects invalid drafts), and they never claim
 * the firmware accepted anything: this store is local, as the cards state.
 */
@Composable
fun AlertRulesEditorDialog(
    rules: List<AlertRule>,
    onSave: (List<AlertRule>) -> Unit,
    onDismiss: () -> Unit
) {
    var working by remember { mutableStateOf(rules.map { AlertRuleDraft.from(it) }) }
    var editing by remember { mutableStateOf<AlertRuleDraft?>(null) }
    var editingIndex by remember { mutableStateOf<Int?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("alert_rules_editor"),
        title = { Text(if (editing == null) "Umbrales de alerta" else "Editar alerta") },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val draft = editing
                if (draft == null) {
                    if (working.isEmpty()) {
                        Text("Sin umbrales. Añade uno para vigilar una serie real.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    working.forEachIndexed { index, item ->
                        Card(Modifier.fillMaxWidth().testTag("alert_rule_${item.id}")) {
                            Column(Modifier.fillMaxWidth().padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    "${item.componentId} · ${item.measurementType} " +
                                        "${if (item.comparison == AlertComparison.ABOVE) "≥" else "≤"} " +
                                        "${item.thresholdText.ifBlank { "—" }} (${item.severity.name.lowercase()})",
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(
                                        onClick = { editing = item; editingIndex = index },
                                        modifier = Modifier.testTag("alert_rule_edit")
                                    ) { Text("Editar") }
                                    TextButton(
                                        onClick = {
                                            working = working.toMutableList().also { it.removeAt(index) }
                                        },
                                        modifier = Modifier.testTag("alert_rule_delete")
                                    ) { Text("Eliminar") }
                                }
                            }
                        }
                    }
                    TextButton(
                        onClick = { editing = AlertRuleDraft.blank(); editingIndex = null },
                        modifier = Modifier.testTag("alert_rule_add")
                    ) { Text("Añadir umbral") }
                } else {
                    AlertRuleForm(
                        draft = draft,
                        onChange = { editing = it },
                        issues = draft.validate().issues
                    )
                }
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.testTag("alert_rules_error"))
                }
            }
        },
        confirmButton = {
            val draft = editing
            if (draft == null) {
                TextButton(
                    onClick = {
                        val results = working.map { it.validate() }
                        val invalid = results.firstOrNull { !it.isValid }
                        if (invalid != null) {
                            error = invalid.issues.first().message
                            return@TextButton
                        }
                        onSave(results.mapNotNull { it.value })
                    },
                    modifier = Modifier.testTag("alert_rules_save")
                ) { Text("Guardar") }
            } else {
                TextButton(
                    onClick = {
                        val result = draft.validate()
                        if (!result.isValid) {
                            error = result.issues.first().message
                            return@TextButton
                        }
                        val list = working.toMutableList()
                        val index = editingIndex
                        if (index == null) list.add(draft) else list[index] = draft
                        working = list
                        editing = null
                        editingIndex = null
                        error = null
                    },
                    modifier = Modifier.testTag("alert_rule_apply")
                ) { Text("Aplicar") }
            }
        },
        dismissButton = {
            if (editing == null) {
                TextButton(onClick = onDismiss) { Text("Cerrar") }
            } else {
                TextButton(
                    onClick = { editing = null; editingIndex = null; error = null },
                    modifier = Modifier.testTag("alert_rule_cancel")
                ) { Text("Volver") }
            }
        }
    )
}

@Composable
private fun AlertRuleForm(
    draft: AlertRuleDraft,
    onChange: (AlertRuleDraft) -> Unit,
    issues: List<com.rgbcontroller.core.components.editors.EditorIssue>
) {
    val issueFor = { field: String -> issues.firstOrNull { it.field == field }?.message }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Componente", style = MaterialTheme.typography.labelMedium)
        ChipRow(AlertRuleEditor.components, draft.componentId) { id ->
            onChange(draft.copy(componentId = id,
                measurementType = AlertRuleEditor.measurementTypesFor(id).first()))
        }
        Text("Serie", style = MaterialTheme.typography.labelMedium)
        ChipRow(AlertRuleEditor.measurementTypesFor(draft.componentId), draft.measurementType) {
            onChange(draft.copy(measurementType = it))
        }
        Text("Comparación", style = MaterialTheme.typography.labelMedium)
        ChipRow(AlertComparison.values().map { it.name }, draft.comparison.name) {
            onChange(draft.copy(comparison = AlertComparison.valueOf(it)))
        }
        NumberField("Umbral", draft.thresholdText, "alert_rule_threshold",
            issueFor("threshold")) { onChange(draft.copy(thresholdText = it)) }
        NumberField("Histéresis", draft.hysteresisText, "alert_rule_hysteresis",
            issueFor("hysteresis")) { onChange(draft.copy(hysteresisText = it)) }
        NumberField("Intervalo mínimo (s)", draft.minIntervalSecondsText, "alert_rule_interval",
            issueFor("interval")) { onChange(draft.copy(minIntervalSecondsText = it)) }
        Text("Severidad", style = MaterialTheme.typography.labelMedium)
        ChipRow(AlertSeverity.values().map { it.name }, draft.severity.name) {
            onChange(draft.copy(severity = AlertSeverity.valueOf(it)))
        }
    }
}

@Composable
fun AutomationRulesEditorDialog(
    rules: List<AutomationRule>,
    onSave: (List<AutomationRule>) -> Unit,
    onDismiss: () -> Unit
) {
    var working by remember { mutableStateOf(rules.map { AutomationRuleDraft.from(it) }) }
    var editing by remember { mutableStateOf<AutomationRuleDraft?>(null) }
    var editingIndex by remember { mutableStateOf<Int?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("automation_rules_editor"),
        title = { Text(if (editing == null) "Automatizaciones" else "Editar automatización") },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val draft = editing
                if (draft == null) {
                    if (working.isEmpty()) {
                        Text("Sin automatizaciones configuradas.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    working.forEachIndexed { index, item ->
                        Card(Modifier.fillMaxWidth().testTag("automation_rule_${item.id}")) {
                            Column(Modifier.fillMaxWidth().padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    "${item.actionType.name} · ${item.componentId} · ${item.measurementType} " +
                                        "${if (item.comparison == AlertComparison.ABOVE) "≥" else "≤"} " +
                                        item.thresholdText.ifBlank { "—" },
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(
                                        onClick = { editing = item; editingIndex = index },
                                        modifier = Modifier.testTag("automation_rule_edit")
                                    ) { Text("Editar") }
                                    TextButton(
                                        onClick = {
                                            working = working.toMutableList().also { it.removeAt(index) }
                                        },
                                        modifier = Modifier.testTag("automation_rule_delete")
                                    ) { Text("Eliminar") }
                                }
                            }
                        }
                    }
                    TextButton(
                        onClick = { editing = AutomationRuleDraft.blank(); editingIndex = null },
                        modifier = Modifier.testTag("automation_rule_add")
                    ) { Text("Añadir automatización") }
                } else {
                    AutomationRuleForm(draft = draft, onChange = { editing = it },
                        issues = draft.validate().issues)
                }
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.testTag("automation_rules_error"))
                }
            }
        },
        confirmButton = {
            val draft = editing
            if (draft == null) {
                TextButton(
                    onClick = {
                        val results = working.map { it.validate() }
                        val invalid = results.firstOrNull { !it.isValid }
                        if (invalid != null) {
                            error = invalid.issues.first().message
                            return@TextButton
                        }
                        onSave(results.mapNotNull { it.value })
                    },
                    modifier = Modifier.testTag("automation_rules_save")
                ) { Text("Guardar") }
            } else {
                TextButton(
                    onClick = {
                        val result = draft.validate()
                        if (!result.isValid) {
                            error = result.issues.first().message
                            return@TextButton
                        }
                        val list = working.toMutableList()
                        val index = editingIndex
                        if (index == null) list.add(draft) else list[index] = draft
                        working = list
                        editing = null
                        editingIndex = null
                        error = null
                    },
                    modifier = Modifier.testTag("automation_rule_apply")
                ) { Text("Aplicar") }
            }
        },
        dismissButton = {
            if (editing == null) {
                TextButton(onClick = onDismiss) { Text("Cerrar") }
            } else {
                TextButton(
                    onClick = { editing = null; editingIndex = null; error = null },
                    modifier = Modifier.testTag("automation_rule_cancel")
                ) { Text("Volver") }
            }
        }
    )
}

@Composable
private fun AutomationRuleForm(
    draft: AutomationRuleDraft,
    onChange: (AutomationRuleDraft) -> Unit,
    issues: List<com.rgbcontroller.core.components.editors.EditorIssue>
) {
    val issueFor = { field: String -> issues.firstOrNull { it.field == field }?.message }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Componente", style = MaterialTheme.typography.labelMedium)
        ChipRow(AlertRuleEditor.components, draft.componentId) { id ->
            onChange(draft.copy(componentId = id,
                measurementType = AlertRuleEditor.measurementTypesFor(id).first()))
        }
        Text("Serie", style = MaterialTheme.typography.labelMedium)
        ChipRow(AlertRuleEditor.measurementTypesFor(draft.componentId), draft.measurementType) {
            onChange(draft.copy(measurementType = it))
        }
        Text("Comparación", style = MaterialTheme.typography.labelMedium)
        ChipRow(AlertComparison.values().map { it.name }, draft.comparison.name) {
            onChange(draft.copy(comparison = AlertComparison.valueOf(it)))
        }
        NumberField("Umbral", draft.thresholdText, "automation_rule_threshold",
            issueFor("threshold")) { onChange(draft.copy(thresholdText = it)) }
        NumberField("Histéresis", draft.hysteresisText, "automation_rule_hysteresis",
            issueFor("hysteresis")) { onChange(draft.copy(hysteresisText = it)) }
        NumberField("Intervalo mínimo (s)", draft.minIntervalSecondsText, "automation_rule_interval",
            issueFor("interval")) { onChange(draft.copy(minIntervalSecondsText = it)) }
        Text("Acción", style = MaterialTheme.typography.labelMedium)
        ChipRow(AutomationActionType.values().map { it.name }, draft.actionType.name) {
            onChange(draft.copy(actionType = AutomationActionType.valueOf(it)))
        }
        when (draft.actionType) {
            AutomationActionType.NOTIFY -> {
        OutlinedTextField(
            value = draft.messageText,
            onValueChange = { onChange(draft.copy(messageText = it)) },
            label = { Text("Aviso") },
            isError = issueFor("message") != null,
            supportingText = issueFor("message")?.let { { Text(it) } },
            singleLine = false,
            modifier = Modifier.fillMaxWidth().testTag("automation_rule_message")
        )
                Text("Severidad", style = MaterialTheme.typography.labelMedium)
                ChipRow(AlertSeverity.values().map { it.name }, draft.severity.name) {
                    onChange(draft.copy(severity = AlertSeverity.valueOf(it)))
                }
            }
            AutomationActionType.SET_RGB -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()) {
                    NumberField("Rojo", draft.redText, "automation_rule_red",
                        issueFor("red"), Modifier.weight(1f)) { onChange(draft.copy(redText = it)) }
                    NumberField("Verde", draft.greenText, "automation_rule_green",
                        issueFor("green"), Modifier.weight(1f)) { onChange(draft.copy(greenText = it)) }
                    NumberField("Azul", draft.blueText, "automation_rule_blue",
                        issueFor("blue"), Modifier.weight(1f)) { onChange(draft.copy(blueText = it)) }
                }
                NumberField("Brillo (0–100)", draft.brightnessText, "automation_rule_brightness",
                    issueFor("brightness")) { onChange(draft.copy(brightnessText = it)) }
                Text(
                    "Las acciones RGB/OLED solo se ejecutan por el canal ya autorizado; " +
                        "si no hay autorización quedan bloqueadas, nunca simuladas.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            AutomationActionType.UPDATE_OLED ->
                OutlinedTextField(
                    value = draft.oledText,
                    onValueChange = { onChange(draft.copy(oledText = it)) },
                    label = { Text("Texto OLED") },
                    isError = issueFor("oled") != null,
                    supportingText = issueFor("oled")?.let { { Text(it) } },
                    singleLine = false,
                    modifier = Modifier.fillMaxWidth().testTag("automation_rule_oled")
                )
        }
        HorizontalDivider()
        Text(
            "La automatización es local. El firmware no acepta esta configuración por sí sola.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ChipRow(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(option) }
            )
        }
    }
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    tag: String,
    error: String?,
    modifier: Modifier = Modifier,
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier.fillMaxWidth().testTag(tag)
    )
}

