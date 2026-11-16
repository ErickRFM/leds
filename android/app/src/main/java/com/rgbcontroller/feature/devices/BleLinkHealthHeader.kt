package com.rgbcontroller.feature.devices

import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rgbcontroller.core.ble.BleLinkHealth
import com.rgbcontroller.core.ble.BleLinkQuality
import com.rgbcontroller.core.ble.BleManager

/**
 * Radio health for the live Bluetooth link, taken from the Android stack. Signal
 * strength is never a permission, so the caption repeats the authorization rule
 * at the one place a user could read a strong link as a granted command.
 */
@Composable
fun BleLinkHealthHeader(bleManager: BleManager, modifier: Modifier = Modifier) {
    val health by bleManager.linkHealth.collectAsState()
    BleLinkHealthHeader(health, modifier)
}

@Composable
fun BleLinkHealthHeader(health: BleLinkHealth, modifier: Modifier = Modifier) {
    val nowMs = SystemClock.elapsedRealtime()
    Surface(
        modifier = modifier.fillMaxWidth().testTag("ble_link_health"),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Default.SignalCellularAlt,
                contentDescription = null,
                tint = health.tint(),
                modifier = Modifier.size(18.dp)
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    if (health.connected) health.summary(nowMs)
                    else "Enlace Bluetooth sin medir · conecta el ESP32 para ver señal y payload",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    health.caption(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun BleLinkHealth.caption(): String {
    if (!connected) return "La fuerza de la señal no autoriza comandos."
    val parts = buildList {
        staleNotice?.let { add(it) }
        if (payloadBytes == null) add("Negociando MTU…")
        add("La señal no autoriza mandos: decide la sesión de propietario.")
    }
    return parts.joinToString(" · ")
}

@Composable
private fun BleLinkHealth.tint(): Color = when (quality) {
    BleLinkQuality.STRONG -> MaterialTheme.colorScheme.primary
    BleLinkQuality.FAIR -> MaterialTheme.colorScheme.secondary
    BleLinkQuality.WEAK -> MaterialTheme.colorScheme.tertiary
    BleLinkQuality.CRITICAL -> MaterialTheme.colorScheme.error
    BleLinkQuality.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
}
