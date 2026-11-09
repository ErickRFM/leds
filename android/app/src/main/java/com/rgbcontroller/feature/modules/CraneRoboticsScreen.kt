package com.rgbcontroller.feature.modules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rgbcontroller.core.ble.BleManager
import kotlinx.coroutines.delay

@Composable
fun CraneRoboticsScreen(
    bleManager: BleManager,
    authorized: Boolean,
    moduleActive: Boolean,
    modifier: Modifier = Modifier
) {
    var base by rememberSaveable { mutableFloatStateOf(90f) }
    var arm by rememberSaveable { mutableFloatStateOf(90f) }
    var speed by rememberSaveable { mutableFloatStateOf(60f) }
    var requestId by rememberSaveable { mutableIntStateOf(1) }
    var feedback by remember { mutableStateOf("Sin comandos enviados en esta sesión.") }
    var remoteControl by rememberSaveable { mutableStateOf(false) }
    val deviceState by bleManager.deviceState.collectAsState()

    fun send(action: String): Boolean {
        val id = requestId
        requestId = if (requestId >= 65535) 1 else requestId + 1
        val accepted = bleManager.writeCraneCommand("CRN,$id,$action")
        feedback = if (accepted) "Comando en cola · esperando confirmación del ESP32"
            else "No enviado: verifica BLE listo, propietario autorizado y cola disponible."
        return accepted
    }

    fun applyTargets(nextBase: Float, nextArm: Float) {
        if (!authorized || !moduleActive) return
        val safeBase = nextBase.coerceIn(10f, 170f)
        val safeArm = nextArm.coerceIn(10f, 170f)
        val command = "TARGET,${safeBase.toInt()},${safeArm.toInt()},${speed.toInt()}"
        if (send(command)) {
            base = safeBase
            arm = safeArm
            remoteControl = true
        }
    }

    // Keep the remote lease alive while this screen owns remote control.
    // If BLE drops or the screen leaves composition, heartbeats stop and the
    // ESP32 cancels motion and safely returns control to the physical joystick.
    LaunchedEffect(remoteControl, authorized, moduleActive) {
        if (remoteControl && authorized && moduleActive) {
            while (true) {
                delay(500L)
                val id = requestId
                requestId = if (requestId >= 65535) 1 else requestId + 1
                if (!bleManager.writeCraneCommand("CRN,$id,GET")) {
                    remoteControl = false
                    feedback = "Se perdió el canal de control remoto."
                    break
                }
            }
        }
    }

    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Compi Robotics", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Grúa de dos ejes · 2 × SG90 · joystick digital",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (authorized) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(if (authorized) "BLE autorizado" else "BLE no autorizado",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelMedium)
                    }
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (moduleActive) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(if (moduleActive) "Firmware listo" else "Firmware no confirmado",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        if (!moduleActive) {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )) {
                Text(
                    "El firmware todavía no confirma la grúa activa. Habilita COMPI_ENABLE_CRANE_ROBOTICS después de verificar el cableado y la fuente externa de 5 V. Los controles permanecerán bloqueados.",
                    Modifier.padding(14.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Ejes · ángulo ordenado", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Base · ${base.toInt()}°", style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = base,
                    onValueChange = { base = it },
                    valueRange = 10f..170f,
                    enabled = authorized && moduleActive,
                    modifier = Modifier.testTag("crane_base_slider")
                )
                Text("Brazo · ${arm.toInt()}°", style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = arm,
                    onValueChange = { arm = it },
                    valueRange = 10f..170f,
                    enabled = authorized && moduleActive,
                    modifier = Modifier.testTag("crane_arm_slider")
                )
                Text("Velocidad · ${speed.toInt()}°/s", style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = speed,
                    onValueChange = { speed = it },
                    valueRange = 10f..120f,
                    enabled = authorized && moduleActive,
                    modifier = Modifier.testTag("crane_speed_slider")
                )
                Button(
                    onClick = { applyTargets(base, arm) },
                    enabled = authorized && moduleActive,
                    modifier = Modifier.fillMaxWidth().testTag("crane_apply_angles")
                ) { Text("Aplicar ángulos") }
            }
        }

        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Posiciones seguras", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(10, 45, 90).forEach { angle ->
                        OutlinedButton(
                            onClick = { applyTargets(angle.toFloat(), angle.toFloat()) },
                            enabled = authorized && moduleActive,
                            modifier = Modifier.weight(1f)
                        ) { Text("$angle°") }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(135, 170).forEach { angle ->
                        OutlinedButton(
                            onClick = { applyTargets(angle.toFloat(), angle.toFloat()) },
                            enabled = authorized && moduleActive,
                            modifier = Modifier.weight(1f)
                        ) { Text("$angle°") }
                    }
                    OutlinedButton(
                        onClick = { if (send("HOME")) { base = 90f; arm = 90f; remoteControl = true } },
                        enabled = authorized && moduleActive,
                        modifier = Modifier.weight(1f).testTag("crane_home")
                    ) { Text("HOME") }
                }
            }
        }

        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Joystick virtual", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    OutlinedButton(
                        onClick = { applyTargets(base, arm + 5f) },
                        enabled = authorized && moduleActive,
                        modifier = Modifier.testTag("crane_virtual_up")
                    ) { Text("↑ Brazo") }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { applyTargets(base - 5f, arm) },
                        enabled = authorized && moduleActive,
                        modifier = Modifier.weight(1f).testTag("crane_virtual_left")
                    ) { Text("← Base") }
                    OutlinedButton(
                        onClick = { applyTargets(base + 5f, arm) },
                        enabled = authorized && moduleActive,
                        modifier = Modifier.weight(1f).testTag("crane_virtual_right")
                    ) { Text("Base →") }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    OutlinedButton(
                        onClick = { applyTargets(base, arm - 5f) },
                        enabled = authorized && moduleActive,
                        modifier = Modifier.testTag("crane_virtual_down")
                    ) { Text("↓ Brazo") }
                }
                Text("Las flechas ordenan pasos de 5°. El joystick físico conserva prioridad cuando el ESP32 está en modo LOCAL.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { if (send("MODE,LOCAL")) remoteControl = false },
                enabled = authorized && moduleActive,
                modifier = Modifier.weight(1f).testTag("crane_local_mode")
            ) { Text("Control físico") }
            Button(
                onClick = { if (send("STOP")) remoteControl = false },
                enabled = authorized && moduleActive,
                modifier = Modifier.weight(1f).testTag("crane_stop")
            ) { Text("STOP") }
        }

        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Comunicación", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(feedback, style = MaterialTheme.typography.bodySmall)
                Text("ESP32: $deviceState", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("El SG90 no incorpora encoder. Los grados son objetivos ordenados, no una medición física.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
