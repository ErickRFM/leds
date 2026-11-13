package com.rgbcontroller.feature.modules

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rgbcontroller.core.ble.BleManager
import com.rgbcontroller.core.ble.CraneControlReply
import com.rgbcontroller.core.ui.CompiBadge
import com.rgbcontroller.core.ui.CompiBadgeType
import com.rgbcontroller.core.ui.CompiTokens
import kotlinx.coroutines.delay
import java.util.Locale

private const val MIN_ANGLE = 10f
private const val MAX_ANGLE = 170f
private const val MIN_SPEED = 10f
private const val MAX_SPEED = 120f
private const val CONFIRM_TIMEOUT_MS = 2500L

// All colors resolve from MaterialTheme.colorScheme so the screen stays
// legible in every theme (DARK, DEEP_BLACK, LIGHT, SYSTEM). The dark
// palette maps these roles to the original audited values, so DARK is
// pixel-equivalent to the previous hardcoded palette.

@Composable
fun CraneRoboticsScreen(
    bleManager: BleManager,
    authorized: Boolean,
    moduleActive: Boolean,
    onOpenConnect: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var base by rememberSaveable { mutableFloatStateOf(90f) }
    var arm by rememberSaveable { mutableFloatStateOf(90f) }
    var speed by rememberSaveable { mutableFloatStateOf(45f) }
    var requestId by rememberSaveable { mutableIntStateOf(1) }
    var remoteControl by rememberSaveable { mutableStateOf(false) }
    var pendingId by remember { mutableStateOf<Int?>(null) }
    var sentAt by remember { mutableLongStateOf(0L) }
    var timedOut by remember { mutableStateOf(false) }
    var writeError by remember { mutableStateOf<String?>(null) }

    val reply by bleManager.craneReply.collectAsState()
    val latestReply by rememberUpdatedState(reply)
    val haptics = LocalHapticFeedback.current
    val sessionReady = authorized && moduleActive

    fun nextRequestId(): Int {
        val id = requestId
        requestId = if (requestId >= 65535) 1 else requestId + 1
        return id
    }

    fun send(action: String): Boolean {
        val id = nextRequestId()
        val accepted = bleManager.writeCraneCommand("CRN,$id,$action")
        if (accepted) {
            pendingId = id
            sentAt = System.currentTimeMillis()
            timedOut = false
            writeError = null
        } else {
            writeError = "No enviado: verifica BLE listo, propietario autorizado y cola disponible."
        }
        return accepted
    }

    fun applyTargets(nextBase: Float, nextArm: Float) {
        if (!sessionReady) return
        val safeBase = nextBase.coerceIn(MIN_ANGLE, MAX_ANGLE)
        val safeArm = nextArm.coerceIn(MIN_ANGLE, MAX_ANGLE)
        if (send("TARGET,${safeBase.toInt()},${safeArm.toInt()},${speed.toInt()}")) {
            base = safeBase
            arm = safeArm
            remoteControl = true
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    fun goHome() {
        if (!sessionReady) return
        if (send("HOME")) {
            base = 90f
            arm = 90f
            remoteControl = true
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    fun stopMotion() {
        if (!sessionReady) return
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        if (send("STOP")) {
            remoteControl = false
            // Keep the request pending so the firmware reply replaces any stale
            // "moving" confirmation instead of leaving it on screen.
        }
    }

    // Without these heartbeats the ESP32 expires the remote lease and returns
    // control to the physical joystick. Losing the link must be visible.
    LaunchedEffect(remoteControl, sessionReady) {
        if (remoteControl && sessionReady) {
            while (true) {
                delay(500L)
                if (!bleManager.writeCraneCommand("CRN,${nextRequestId()},GET")) {
                    remoteControl = false
                    writeError = "Se perdió el canal de control remoto. La grúa volvió al joystick físico."
                    break
                }
            }
        }
    }

    LaunchedEffect(reply?.requestId) {
        val current = reply ?: return@LaunchedEffect
        if (current.requestId != pendingId) return@LaunchedEffect
        pendingId = null
        timedOut = false
        if (current.accepted) {
            remoteControl = current.mode == "REMOTE"
            writeError = null
        }
    }

    LaunchedEffect(pendingId, sentAt) {
        val waitingFor = pendingId ?: return@LaunchedEffect
        delay(CONFIRM_TIMEOUT_MS)
        if (latestReply?.requestId != waitingFor) timedOut = true
    }

    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CraneStatusHeader(
                authorized = authorized,
                moduleActive = moduleActive,
                remoteControl = remoteControl
            )

            if (!sessionReady) {
                AccessBanner(
                    authorized = authorized,
                    onOpenConnect = onOpenConnect
                )
            }

            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val wide = maxWidth >= 700.dp
                if (wide) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            AxesCard(
                                base = base,
                                arm = arm,
                                speed = speed,
                                enabled = sessionReady,
                                onBaseChange = { base = it },
                                onArmChange = { arm = it },
                                onSpeedChange = { speed = it },
                                onApply = { applyTargets(base, arm) }
                            )
                            PresetsCard(
                                enabled = sessionReady,
                                onAngle = { angle -> applyTargets(angle, angle) },
                                onHome = ::goHome
                            )
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            JoystickCard(
                                enabled = sessionReady,
                                onNudge = { baseDelta, armDelta ->
                                    applyTargets(base + baseDelta, arm + armDelta)
                                },
                                onCenter = { applyTargets(90f, 90f) }
                            )
                            CommandFeedbackCard(
                                reply = reply,
                                pendingId = pendingId,
                                timedOut = timedOut,
                                writeError = writeError
                            )
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        AxesCard(
                            base = base,
                            arm = arm,
                            speed = speed,
                            enabled = sessionReady,
                            onBaseChange = { base = it },
                            onArmChange = { arm = it },
                            onSpeedChange = { speed = it },
                            onApply = { applyTargets(base, arm) }
                        )
                        PresetsCard(
                            enabled = sessionReady,
                            onAngle = { angle -> applyTargets(angle, angle) },
                            onHome = ::goHome
                        )
                        JoystickCard(
                            enabled = sessionReady,
                            onNudge = { baseDelta, armDelta ->
                                applyTargets(base + baseDelta, arm + armDelta)
                            },
                            onCenter = { applyTargets(90f, 90f) }
                        )
                        CommandFeedbackCard(
                            reply = reply,
                            pendingId = pendingId,
                            timedOut = timedOut,
                            writeError = writeError
                        )
                    }
                }
            }

            Text(
                "Los grados son objetivos ordenados; el SG90 no incorpora encoder. " +
                    "El joystick físico recupera el control si el ESP32 no recibe latidos.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        CommandBar(
            enabled = sessionReady,
            remoteControl = remoteControl,
            onLocal = {
                if (send("MODE,LOCAL")) {
                    remoteControl = false
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
            },
            onStop = ::stopMotion
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CraneStatusHeader(
    authorized: Boolean,
    moduleActive: Boolean,
    remoteControl: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "Compi Robotics",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                "Grúa de dos ejes · 2 × SG90 · joystick digital",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CompiBadge(
                    label = if (authorized) "BLE autorizado" else "BLE sin sesión",
                    type = if (authorized) CompiBadgeType.LIVE else CompiBadgeType.OFFLINE
                )
                CompiBadge(
                    label = if (moduleActive) "Firmware listo" else "Firmware sin confirmar",
                    type = if (moduleActive) CompiBadgeType.LIVE else CompiBadgeType.STALE
                )
                CompiBadge(
                    label = if (remoteControl) "Remoto" else "Local",
                    type = if (remoteControl) CompiBadgeType.CONFIRMED else CompiBadgeType.LIVE
                )
            }
        }
    }
}

@Composable
private fun AccessBanner(
    authorized: Boolean,
    onOpenConnect: (() -> Unit)?
) {
    val title = if (!authorized) "Sin sesión BLE" else "Controles bloqueados"
    val message = if (!authorized) {
        "Conecta el ESP32 y autoriza este teléfono como propietario para enviar comandos."
    } else {
        "El firmware todavía no confirma la grúa activa. " +
            "Habilita COMPI_ENABLE_CRANE_ROBOTICS solo después de verificar cableado, " +
            "tierra común y fuente externa de 5 V."
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, CompiTokens.Colors.WarningAmber.copy(alpha = 0.55f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = CompiTokens.Colors.WarningAmber
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!authorized && onOpenConnect != null) {
                OutlinedButton(onClick = onOpenConnect) {
                    Text("Ir a dispositivos")
                }
            }
        }
    }
}

@Composable
private fun AxesCard(
    base: Float,
    arm: Float,
    speed: Float,
    enabled: Boolean,
    onBaseChange: (Float) -> Unit,
    onArmChange: (Float) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onApply: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "Ejes · ángulo ordenado",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            AxisSlider(
                label = "Base",
                value = base,
                enabled = enabled,
                description = "Ángulo objetivo de la base",
                testTag = "crane_base_slider",
                onValueChange = onBaseChange
            )
            AxisSlider(
                label = "Brazo",
                value = arm,
                enabled = enabled,
                description = "Ángulo objetivo del brazo",
                testTag = "crane_arm_slider",
                onValueChange = onArmChange
            )
            Text(
                "Velocidad · ${speed.toInt()}°/s",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Slider(
                value = speed,
                onValueChange = onSpeedChange,
                valueRange = MIN_SPEED..MAX_SPEED,
                enabled = enabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("crane_speed_slider")
                    .semantics {
                        contentDescription = "Velocidad del movimiento"
                        stateDescription = "${speed.toInt()} grados por segundo"
                    }
            )
            CraneChipRow {
                listOf(20, 45, 90, 120).forEach { preset ->
                    FilterChip(
                        selected = speed.toInt() == preset,
                        onClick = { onSpeedChange(preset.toFloat()) },
                        enabled = enabled,
                        modifier = Modifier.heightIn(min = 48.dp),
                        label = { Text("$preset°/s", fontSize = 12.sp) },
                        colors = craneChipColors()
                    )
                }
            }
            Button(
                onClick = onApply,
                enabled = enabled,
                colors = ButtonDefaults.buttonColors(
                    disabledContainerColor = CompiTokens.Colors.SurfaceVariant,
                    disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("crane_apply_angles")
            ) { Text("Aplicar ${base.toInt()}° / ${arm.toInt()}°") }
        }
    }
}

@Composable
private fun AxisSlider(
    label: String,
    value: Float,
    enabled: Boolean,
    description: String,
    testTag: String,
    onValueChange: (Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            "$label · ${value.toInt()}°",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = MIN_ANGLE..MAX_ANGLE,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(testTag)
                .semantics {
                    contentDescription = description
                    stateDescription = "${value.toInt()} grados"
                }
        )
    }
}

@Composable
private fun PresetsCard(
    enabled: Boolean,
    onAngle: (Float) -> Unit,
    onHome: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "Posiciones seguras",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                "Aplica el mismo ángulo a base y brazo.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            CraneChipRow {
                listOf(10, 45, 90, 135, 170).forEach { angle ->
                    FilterChip(
                        selected = false,
                        onClick = { onAngle(angle.toFloat()) },
                        enabled = enabled,
                        modifier = Modifier.heightIn(min = 48.dp),
                        label = { Text("$angle°") },
                        colors = craneChipColors()
                    )
                }
            }
            OutlinedButton(
                onClick = onHome,
                enabled = enabled,
                colors = ButtonDefaults.outlinedButtonColors(
                    disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("crane_home")
            ) { Text("HOME · volver a 90° / 90°") }
        }
    }
}

@Composable
private fun JoystickCard(
    enabled: Boolean,
    onNudge: (Float, Float) -> Unit,
    onCenter: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Joystick virtual",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                "Pasos de 5° por pulsación.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            NudgeButton(
                label = "↑ Brazo +5°",
                description = "Subir el brazo 5 grados",
                enabled = enabled,
                testTag = "crane_virtual_up",
                onClick = { onNudge(0f, 5f) },
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NudgeButton(
                    label = "← Base −5°",
                    description = "Girar la base 5 grados a la izquierda",
                    enabled = enabled,
                    testTag = "crane_virtual_left",
                    onClick = { onNudge(-5f, 0f) },
                    modifier = Modifier.weight(1f)
                )
                NudgeButton(
                    label = "Base +5° →",
                    description = "Girar la base 5 grados a la derecha",
                    enabled = enabled,
                    testTag = "crane_virtual_right",
                    onClick = { onNudge(5f, 0f) },
                    modifier = Modifier.weight(1f)
                )
            }
            NudgeButton(
                label = "↓ Brazo −5°",
                description = "Bajar el brazo 5 grados",
                enabled = enabled,
                testTag = "crane_virtual_down",
                onClick = { onNudge(0f, -5f) },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedButton(
                onClick = onCenter,
                enabled = enabled,
                colors = ButtonDefaults.outlinedButtonColors(
                    disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("crane_center_home")
            ) { Text("Centrar 90° / 90°") }
        }
    }
}

@Composable
private fun NudgeButton(
    label: String,
    description: String,
    enabled: Boolean,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.outlinedButtonColors(
            disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        ),
        modifier = modifier
            .heightIn(min = 48.dp)
            .testTag(testTag)
            .semantics { contentDescription = description }
    ) { Text(label) }
}

@Composable
private fun CommandFeedbackCard(
    reply: CraneControlReply?,
    pendingId: Int?,
    timedOut: Boolean,
    writeError: String?
) {
    val (title, detail, isError) = when {
        writeError != null -> Triple("Comando no enviado", writeError, true)
        pendingId != null && timedOut -> Triple(
            "Sin confirmación",
            "El ESP32 no respondió al comando #$pendingId en 2.5 s. " +
                "Revisa la conexión y usa DETENER si hay movimiento.",
            true
        )
        pendingId != null -> Triple(
            "En cola",
            "Esperando confirmación del ESP32 para el comando #$pendingId…",
            false
        )
        reply == null -> Triple(
            "Sin comandos en esta sesión",
            "La confirmación del firmware aparecerá aquí con modo, ángulos y movimiento.",
            false
        )
        reply.accepted -> Triple(
            "Confirmado #${reply.requestId}",
            buildString {
                append("Base ").append(formatAngle(reply.baseDeg))
                append(" · Brazo ").append(formatAngle(reply.armDeg))
                append(" · ").append(modalityText(reply.mode))
                append(" · ").append(if (reply.moving) "en movimiento" else "detenido")
            },
            false
        )
        else -> Triple(
            "Rechazado #${reply.requestId}",
            reasonText(reply.error) + stateSuffix(reply),
            true
        )
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
        shape = RoundedCornerShape(18.dp),
        color = if (isError) MaterialTheme.colorScheme.errorContainer
        else MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (isError) MaterialTheme.colorScheme.onErrorContainer
                else MaterialTheme.colorScheme.onSurface
            )
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = if (isError) MaterialTheme.colorScheme.onErrorContainer
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CommandBar(
    enabled: Boolean,
    remoteControl: Boolean,
    onLocal: () -> Unit,
    onStop: () -> Unit
) {
    Surface(
        color = CompiTokens.Colors.MatteBackground,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = onLocal,
                enabled = enabled && remoteControl,
                colors = ButtonDefaults.outlinedButtonColors(
                    disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                ),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 52.dp)
                    .testTag("crane_local_mode")
            ) { Text("Control físico", fontSize = 13.sp) }
            Button(
                onClick = onStop,
                enabled = enabled,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                    disabledContainerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.25f),
                    disabledContentColor = MaterialTheme.colorScheme.onError.copy(alpha = 0.55f)
                ),
                modifier = Modifier
                    .weight(1.2f)
                    .heightIn(min = 52.dp)
                    .testTag("crane_stop")
                    .semantics { contentDescription = "Detener la grúa ahora" }
            ) {
                Text("DETENER", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CraneChipRow(content: @Composable () -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) { content() }
}

@Composable
private fun craneChipColors() = FilterChipDefaults.filterChipColors(
    disabledLabelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
)

private fun formatAngle(value: Float?): String =
    value?.let { String.format(Locale.ROOT, "%.1f°", it) } ?: "—"

private fun modalityText(mode: String?): String = when (mode) {
    "REMOTE" -> "control remoto"
    "LOCAL" -> "control físico"
    else -> "modo desconocido"
}

private fun reasonText(reason: String?): String = when (reason) {
    "UNAVAILABLE" -> "El firmware no tiene Compi Robotics habilitado (COMPI_ENABLE_CRANE_ROBOTICS)."
    "REJECTED" -> "El firmware rechazó el comando: revisa el modo activo y los límites de 10° a 170°."
    null -> "El firmware reportó un error sin detalle."
    else -> "El firmware rechazó el comando: ${reason.lowercase(Locale.ROOT)}."
}

private fun stateSuffix(reply: CraneControlReply): String {
    if (reply.baseDeg == null || reply.armDeg == null) return ""
    return " Estado actual: base ${formatAngle(reply.baseDeg)}, " +
        "brazo ${formatAngle(reply.armDeg)}, " + modalityText(reply.mode) +
        (if (reply.moving) ", en movimiento." else ", detenido.")
}
