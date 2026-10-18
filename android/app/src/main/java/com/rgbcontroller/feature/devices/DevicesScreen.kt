package com.rgbcontroller.feature.devices

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rgbcontroller.core.ble.BleConnectionState
import com.rgbcontroller.core.ble.BleManager
import com.rgbcontroller.core.ble.BleSecurityState
import com.rgbcontroller.core.data.PreferencesRepository
import com.rgbcontroller.core.ui.SectionHeader

/**
 * A single scrolling surface: nearby devices never disappear below a fixed
 * card stack on short displays. GATT readiness is not owner authorization.
 */
@SuppressLint("MissingPermission")
@Composable
fun DevicesScreen(
    bleManager: BleManager,
    preferencesRepository: PreferencesRepository,
    onInspectSecurity: () -> Unit
) {
    val connection by bleManager.connectionState.collectAsState()
    val security by bleManager.securityState.collectAsState()
    val authorizationMessage by bleManager.authorizationMessage.collectAsState()
    var enrollmentCode by remember { mutableStateOf("") }
    val devices by bleManager.discoveredDevices.collectAsState()
    val scanStats by bleManager.scanStatistics.collectAsState()
    val publicId by bleManager.publicDeviceId.collectAsState()
    val lastMac by preferencesRepository.lastDeviceMac.collectAsState(initial = null)
    var searched by rememberSaveable { mutableStateOf(false) }
    var showIdentityNote by rememberSaveable { mutableStateOf(false) }
    val busy = connection is BleConnectionState.Connecting ||
        connection is BleConnectionState.Discovering
    val searching = connection == BleConnectionState.Scanning
    val ready = connection == BleConnectionState.Ready
    val authorized = ready && security == BleSecurityState.Authorized
    val status = when (connection) {
        BleConnectionState.Disconnected -> "Sin conexión"
        BleConnectionState.Scanning -> "Buscando ESP32"
        BleConnectionState.Connecting -> "Conectando…"
        BleConnectionState.Discovering -> "Leyendo firmware…"
        BleConnectionState.Ready -> if (authorized) "ESP32 autorizado" else "ESP32 conectado"
        is BleConnectionState.Error -> "Conexión necesita atención"
    }
    val detail = when (val current = connection) {
        BleConnectionState.Disconnected -> "Listo para buscar."
        BleConnectionState.Scanning -> "Escaneando BLE…"
        BleConnectionState.Connecting -> "Conectando…"
        BleConnectionState.Discovering -> "Verificando GATT…"
        BleConnectionState.Ready -> if (authorized)
            "Propietario autorizado por firmware."
        else "BLE conectado · autorización pendiente."
        is BleConnectionState.Error -> current.message
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Dispositivos cercanos", style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold)
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(
                                if (authorized) Icons.Default.CheckCircle else Icons.Default.Bluetooth,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(12.dp).size(24.dp)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(status, style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold)
                            Text(detail, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (busy || searching) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                    if (ready) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Text(
                            publicId?.let { "Identidad pública: $it" }
                                ?: "Identidad pública no disponible en este firmware.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(onClick = { showIdentityNote = !showIdentityNote }) {
                            Text(if (showIdentityNote) "Ocultar aviso" else "Acerca de la identidad")
                        }
                        if (showIdentityNote) {
                            Text("Este ID no concede acceso ni confirma propiedad.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else if (lastMac != null) {
                        Text("Último ESP32: …${lastMac!!.takeLast(5)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        if (ready && !authorized) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("ble_owner_enrollment"),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Autorización del propietario", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Conectado no significa autorizado. Para el primer registro, mantén BOOT " +
                                "del ESP32 durante 3 segundos y lee el código temporal en el " +
                                "Monitor Serie USB (115200). No desconectes la placa.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (security == BleSecurityState.Authorizing ||
                            security == BleSecurityState.Pairing) {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                        }
                        authorizationMessage?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.testTag("ble_owner_status"))
                        }
                        OutlinedTextField(
                            value = enrollmentCode,
                            onValueChange = { value ->
                                enrollmentCode = value.filter { it.isDigit() ||
                                    it.lowercaseChar() in 'a'..'f' }.take(8).uppercase()
                            },
                            label = { Text("Código BOOT de 8 caracteres") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth().testTag("ble_owner_code")
                        )
                        Button(
                            onClick = {
                                bleManager.beginOwnerEnrollment(enrollmentCode)
                                enrollmentCode = ""
                            },
                            enabled = enrollmentCode.length == 8 &&
                                security != BleSecurityState.Authorizing &&
                                security != BleSecurityState.Pairing,
                            modifier = Modifier.fillMaxWidth().testTag("ble_owner_authorize")
                        ) { Text("Registrar y autorizar ESP32") }
                        Text(
                            "Si este ESP32 ya tiene otro propietario, usa la recuperación " +
                                "física documentada. La app nunca omite esta autorización.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        if (searching) bleManager.stopScan() else {
                            searched = true
                            bleManager.startScan(lastMac)
                        }
                    },
                    enabled = !busy && !ready,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                ) {
                    Text(if (searching) "Detener" else if (searched) "Buscar de nuevo" else "Buscar ESP32",
                        maxLines = 1)
                }
                OutlinedButton(
                    onClick = { bleManager.disconnect() },
                    enabled = busy || ready || connection is BleConnectionState.Error,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                ) { Text("Desconectar", maxLines = 1) }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Resultados", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (scanStats.advertisements > 0) {
                    Text("${scanStats.compatibleDevices} compatibles",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (devices.isEmpty()) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.BluetoothSearching, contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            when {
                                searching -> "Buscando dispositivos cercanos…"
                                connection is BleConnectionState.Error ->
                                    "Revisa Bluetooth, permisos y arranque del ESP32."
                                searched && scanStats.advertisements == 0 ->
                                "No llegaron anuncios BLE. Revisa alimentación y arranque del ESP32."
                            searched -> "Se recibieron anuncios BLE, pero ninguno coincidió con Compi."
                                else -> "Pulsa «Buscar ESP32» para comenzar."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        item {
            TextButton(onClick = onInspectSecurity, modifier = Modifier.testTag("ble_security_details")) {
                Icon(Icons.Default.Shield, contentDescription = null,
                    modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (authorized) "Propietario autorizado" else "Diagnóstico del ESP32")
                Icon(Icons.Default.ChevronRight, contentDescription = null,
                    modifier = Modifier.size(17.dp))
            }
        }
        items(devices, key = { it.address }) { device ->
            Card(onClick = { if (!busy && !ready) bleManager.connect(device) },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(12.dp)) {
                        Icon(Icons.Default.Bluetooth, contentDescription = null,
                            modifier = Modifier.padding(10.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(device.name ?: "ESP32 sin nombre",
                            style = MaterialTheme.typography.titleSmall)
                        Text("Dispositivo BLE encontrado · sin autorización",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("Conectar", style = MaterialTheme.typography.labelMedium,
                        color = if (!busy && !ready) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
