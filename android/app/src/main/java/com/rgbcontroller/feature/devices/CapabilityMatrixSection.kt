package com.rgbcontroller.feature.devices

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rgbcontroller.core.model.CapabilitySupport
import com.rgbcontroller.core.model.CapabilityRow
import com.rgbcontroller.core.model.TransportCapabilityMatrix

/**
 * The one place where Bluetooth and the lab Wi-Fi are compared. Every claim comes
 * from TransportCapabilityMatrix, which cites the frame or endpoint that proves it,
 * so a screen can no longer describe a capability the firmware never exposed.
 */
@Composable
fun CapabilityMatrixSection(modifier: Modifier = Modifier, debugBuild: Boolean = true) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Card(modifier = modifier.fillMaxWidth().testTag("capability_matrix")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(
                onClick = { expanded = !expanded },
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth().testTag("capability_matrix_toggle")
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Qué permite cada conexión",
                            style = MaterialTheme.typography.titleMedium)
                        Text(if (expanded) "Ocultar detalle" else "Ver detalle por transporte",
                            style = MaterialTheme.typography.labelSmall)
                    }
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null
                    )
                }
            }
            if (expanded) {
                Column(
                    Modifier.fillMaxWidth().heightIn(max = 340.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TransportCapabilityMatrix.rowsByGroup().forEach { (group, rows) ->
                        Text(group, style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp))
                        MatrixHeader()
                        rows.forEach { row -> MatrixRow(row) }
                    }
                    if (!debugBuild) {
                        Text("En un APK release la columna Wi-Fi no está disponible: el control HTTP local " +
                            "solo se compila en builds de prueba.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("Los mandos dependen además de la autorización: vincular el dispositivo no " +
                        "sustituye la sesión de propietario.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun MatrixHeader() {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Capacidad", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        MatrixCell("Bluetooth", MatrixCellWidth, MaterialTheme.colorScheme.onSurfaceVariant,
            MaterialTheme.colorScheme.surface)
        Spacer(Modifier.width(6.dp))
        MatrixCell("Wi-Fi", MatrixCellWidth, MaterialTheme.colorScheme.onSurfaceVariant,
            MaterialTheme.colorScheme.surface)
    }
}

@Composable
private fun MatrixRow(row: CapabilityRow) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(row.label, style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            MatrixCell(row.ble.word(), MatrixCellWidth, row.ble.tone(),
                MaterialTheme.colorScheme.surfaceContainerHigh)
            Spacer(Modifier.width(6.dp))
            MatrixCell(row.wifi.word(), MatrixCellWidth, row.wifi.tone(),
                MaterialTheme.colorScheme.surfaceContainerHigh)
        }
        row.note?.let {
            Text(it, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MatrixCell(
    text: String,
    width: Dp,
    tone: Color,
    background: Color
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = background,
        modifier = Modifier.width(width)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = tone,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp)
        )
    }
}

private fun CapabilitySupport.word(): String = when (this) {
    CapabilitySupport.AVAILABLE -> "Sí"
    CapabilitySupport.LIMITED -> "Parcial"
    CapabilitySupport.UNAVAILABLE -> "No"
}

/** Colour alone never carries the verdict: every cell also spells the answer out. */
@Composable
private fun CapabilitySupport.tone(): Color = when (this) {
    CapabilitySupport.AVAILABLE -> MaterialTheme.colorScheme.primary
    CapabilitySupport.LIMITED -> MaterialTheme.colorScheme.tertiary
    CapabilitySupport.UNAVAILABLE -> MaterialTheme.colorScheme.onSurfaceVariant
}

private val MatrixCellWidth = 62.dp
