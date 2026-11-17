package com.rgbcontroller.crane

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.rgbcontroller.core.ble.BleConnectionState
import com.rgbcontroller.core.ble.BleLinkHealth
import com.rgbcontroller.core.ble.BleManager
import com.rgbcontroller.core.ble.BleSecurityState
import com.rgbcontroller.core.ble.BleSnapshotAssembler
import com.rgbcontroller.core.ble.CraneControlReply
import com.rgbcontroller.core.components.model.ComponentStatusCodec
import com.rgbcontroller.core.components.registry.ComponentCatalog
import com.rgbcontroller.core.components.registry.CraneWiring
import com.rgbcontroller.core.components.registry.CraneWiringDraft
import com.rgbcontroller.core.components.status.ComponentStatusRepository
import com.rgbcontroller.core.components.status.LocalDesiredComponent
import com.rgbcontroller.core.components.status.LocalDesiredConfig
import com.rgbcontroller.core.data.DataStoreComponentStatusStorage
import com.rgbcontroller.core.ui.CompiTokens
import com.rgbcontroller.feature.devices.BleLinkHealthHeader
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

private const val MIN_ANGLE = 10f
private const val MAX_ANGLE = 170f
private const val MIN_SPEED = 10f
private const val MAX_SPEED = 120f
private const val CONFIRM_TIMEOUT_MS = 2500L
private const val STATUS_POLL_MS = 4000L
private const val HEARTBEAT_MS = 500L

/**
 * The whole crane application in one surface: session, radio health, ordered
 * angles, the virtual joystick, the firmware receipt and the seven GPIO. Every
 * number shown here is either a local draft or a firmware confirmation, never
 * an inference, and the mode badge only changes when the ESP32 says it did.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CraneConsoleScreen(
    bleManager: BleManager,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    val connection by bleManager.connectionState.collectAsState()
    val security by bleManager.securityState.collectAsState()
    val reply by bleManager.craneReply.collectAsState()
    val linkHealth by bleManager.linkHealth.collectAsState()
    val latestReply by rememberUpdatedState(reply)

    val authorized = connection == BleConnectionState.Ready &&
        security == BleSecurityState.Authorized

    var firmwareActive by remember { mutableStateOf(false) }
    var firmwarePins by remember { mutableStateOf<List<Int>?>(null) }
    var otherClaims by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
    var draftPins by remember { mutableStateOf<List<Int>?>(null) }

    val repository = remember(context) {
        ComponentStatusRepository(DataStoreComponentStatusStorage(context.applicationContext))
    }
    LaunchedEffect(repository) {
        repository.loadPersisted()
        draftPins = repository.state.value.desiredConfig.components
            .firstOrNull { it.id == CraneWiring.COMPONENT_ID }?.pins
    }

    // Firmware truth comes from an owner-authenticated GATT snapshot, never from
    // the local draft; without a session the board's pins stay unknown.
    LaunchedEffect(authorized) {
        if (!authorized) {
            firmwareActive = false
            firmwarePins = null
            otherClaims = emptyMap()
            return@LaunchedEffect
        }
        while (true) {
            val decoded = ComponentStatusCodec.decode(
                bleManager.readComponentSnapshot(BleSnapshotAssembler.Kind.STATUS)
            )
            if (decoded != null) {
                val crane = decoded.firstOrNull {
                    ComponentCatalog.catalogIdForFirmwareId(it.id) == CraneWiring.COMPONENT_ID
                }
                firmwareActive = crane?.active == true
                firmwarePins = crane?.pins?.takeIf { it.isNotEmpty() }
                otherClaims = decoded
                    .filter { it.active && it.id != CraneWiring.COMPONENT_ID }
                    .flatMap { status -> status.pins.map { it to status.id } }
                    .toMap()
            }
            delay(STATUS_POLL_MS)
        }
    }

    val draft = remember(draftPins, firmwarePins, otherClaims) {
        CraneWiringDraft.of(draft = draftPins, firmware = firmwarePins, reservedByOthers = otherClaims)
    }

    var applying by remember { mutableStateOf(false) }
    var wiringMessage by remember { mutableStateOf<String?>(null) }

    fun persist(pins: List<Int>) {
        draftPins = pins
        scope.launch {
            try {
                val others = repository.state.value.desiredConfig.components
                    .filterNot { it.id == CraneWiring.COMPONENT_ID }
                repository.setDesired(
                    LocalDesiredConfig(
                        others + LocalDesiredComponent(
                            CraneWiring.COMPONENT_ID,
                            ComponentCatalog.get(CraneWiring.COMPONENT_ID)?.type?.name
                                ?.lowercase(Locale.ROOT) ?: "controller",
                            pins
                        )
                    )
                )
            } catch (failure: Exception) {
                wiringMessage = "No se guardó el borrador: ${failure.message ?: "inténtalo de nuevo"}"
            }
        }
    }

    fun applyWiring() {
        if (!draft.canApply || applying) return
        applying = true
        wiringMessage = null
        scope.launch {
            val outcome = runCatching { bleManager.applyCranePinsBle(draft.pins) }
            applying = false
            wiringMessage = outcome.fold(
                onSuccess = {
                    firmwarePins = draft.pins
                    "El ESP32 confirmó los siete GPIO: ${draft.pins.joinToString()}."
                },
                onFailure = { failure ->
                    "No aplicado: ${failure.message ?: "el ESP32 no confirmó"}"
                }
            )
        }
    }

    var base by rememberSaveable { mutableFloatStateOf(90f) }
    var arm by rememberSaveable { mutableFloatStateOf(90f) }
    var speed by rememberSaveable { mutableFloatStateOf(45f) }
    var requestId by rememberSaveable { mutableIntStateOf(1) }
    var remoteControl by rememberSaveable { mutableStateOf(false) }
    var pendingId by remember { mutableStateOf<Int?>(null) }
    var sentAt by remember { mutableLongStateOf(0L) }
    var timedOut by remember { mutableStateOf(false) }
    var writeError by remember { mutableStateOf<String?>(null) }
    var replyAt by remember { mutableLongStateOf(0L) }

    val sessionReady = authorized && firmwareActive

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
            writeError = "No enviado: revisa sesión de propietario y cola BLE."
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
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    // The ESP32 expires the remote lease without heartbeats and hands control
    // back to the physical joystick; losing that lease must be visible.
    LaunchedEffect(remoteControl, sessionReady) {
        if (remoteControl && sessionReady) {
            while (true) {
                delay(HEARTBEAT_MS)
                if (!bleManager.writeCraneCommand("CRN,${nextRequestId()},GET")) {
                    remoteControl = false
                    writeError = "Se perdió el canal de control remoto; la grúa volvió al joystick físico."
                    break
                }
            }
        }
    }

    LaunchedEffect(reply?.requestId) {
        val current = reply ?: return@LaunchedEffect
        replyAt = System.currentTimeMillis()
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

    Scaffold(
        modifier = modifier.testTag("crane_console"),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text("Compi Robotics · Grúa de dos ejes", style = CompiTokens.Typography.HeadlineTitle)
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        bottomBar = {
            CraneCommandBar(
                enabled = sessionReady,
                remoteControl = remoteControl,
                onLocal = {
                    // The badge flips only when the firmware confirms MODE,LOCAL.
                    if (send("MODE,LOCAL")) {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                },
                onStop = {
                    if (sessionReady) {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        send("STOP")
                    }
                }
            )
        }
    ) { insets ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(insets)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = CompiTokens.Spacing.m, vertical = CompiTokens.Spacing.s),
            verticalArrangement = Arrangement.spacedBy(CompiTokens.Spacing.s)
        ) {
            CraneSessionCard(
                authorized = authorized,
                firmwareActive = firmwareActive,
                remoteControl = remoteControl,
                health = linkHealth
            )
            if (!sessionReady) {
                CraneAccessCard(authorized = authorized, firmwareActive = firmwareActive)
            }
            CraneAxesCard(
                base = base,
                arm = arm,
                speed = speed,
                enabled = sessionReady,
                onBaseChange = { base = it },
                onArmChange = { arm = it },
                onSpeedChange = { speed = it },
                onApply = { applyTargets(base, arm) },
                onHome = { if (sessionReady && send("HOME")) { base = 90f; arm = 90f } }
            )
            CraneJoystickCard(
                enabled = sessionReady,
                onNudge = { baseDelta, armDelta -> applyTargets(base + baseDelta, arm + armDelta) },
                onCenter = { applyTargets(90f, 90f) }
            )
            CraneFeedbackCard(
                reply = reply,
                pendingId = pendingId,
                timedOut = timedOut,
                writeError = writeError,
                replyAt = replyAt
            )
            CraneWiringSection(
                draft = draft,
                authorized = authorized,
                applying = applying,
                resultMessage = wiringMessage,
                onAssign = { index, pin -> persist(draft.assign(index, pin).pins) },
                onRelease = { index -> persist(draft.release(index).pins) },
                onApply = ::applyWiring
            )
            CraneCaption(
                text = "Los grados son objetivos ordenados: el SG90 no tiene encoder. " +
                    "Fuente externa de 5 V, tierra común y pull-ups en los cinco contactos del joystick.",
                tag = "crane_hardware_note"
            )
        }
    }
}

@Composable
private fun CraneSessionCard(
    authorized: Boolean,
    firmwareActive: Boolean,
    remoteControl: Boolean,
    health: BleLinkHealth
) {
    CraneCard(
        title = "Sesión y enlace",
        tag = "crane_session_card",
        subtitle = "Sesión de propietario · estado del enlace BLE"
    ) {
        CraneChipRow {
            CraneStateChip(
                text = if (authorized) "BLE autorizado" else "BLE sin sesión",
                tone = if (authorized) CraneTone.LIVE else CraneTone.NEUTRAL,
                tag = "crane_badge_session"
            )
            CraneStateChip(
                text = if (firmwareActive) "Firmware listo" else "Firmware sin confirmar",
                tone = if (firmwareActive) CraneTone.LIVE else CraneTone.WARNING,
                tag = "crane_badge_firmware"
            )
            CraneStateChip(
                text = if (remoteControl) "Remoto" else "Control físico",
                tone = if (remoteControl) CraneTone.LIVE else CraneTone.NEUTRAL,
                tag = "crane_badge_mode"
            )
        }
        // Same header the lab application uses, so RSSI, MTU and the age of the
        // reading are stated one way in both editions.
        BleLinkHealthHeader(health)
    }
}

@Composable
private fun CraneAccessCard(authorized: Boolean, firmwareActive: Boolean) {
    CraneCard(
        title = if (authorized) "Controles bloqueados" else "Falta la sesión de propietario",
        tag = "crane_access_card",
        subtitle = if (authorized) {
            "El firmware no confirma la grúa activa. Habilita COMPI_ENABLE_CRANE_ROBOTICS " +
                "solo después de verificar cableado, tierra común y la fuente de 5 V."
        } else {
            "Conecta el ESP32 y autoriza este teléfono como propietario para enviar comandos."
        }
    ) {
        CraneCaption(
            text = when {
                !authorized -> "Falta la sesión: los controles quedan inactivos hasta autorizar este teléfono."
                !firmwareActive -> "Falta la confirmación del firmware: los controles quedan inactivos."
                else -> "Ambas condiciones están cubiertas; el cableado sigue editable."
            },
            tag = "crane_access_note"
        )
    }
}

@Composable
private fun CraneAxesCard(
    base: Float,
    arm: Float,
    speed: Float,
    enabled: Boolean,
    onBaseChange: (Float) -> Unit,
    onArmChange: (Float) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onApply: () -> Unit,
    onHome: () -> Unit
) {
    CraneCard(
        title = "Ejes",
        tag = "crane_axes_card",
        subtitle = "Ángulo ordenado · 10° a 170°"
    ) {
        CraneAxis("Base", base, enabled, "crane_base_slider", "Ángulo objetivo de la base", onBaseChange)
        CraneAxis("Brazo", arm, enabled, "crane_arm_slider", "Ángulo objetivo del brazo", onArmChange)
        Text(
            text = "Velocidad · ${speed.toInt()} °/s",
            style = CompiTokens.Typography.MetricCaption,
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
                CraneChip(
                    label = "$preset °/s",
                    selected = speed.toInt() == preset,
                    enabled = enabled,
                    onClick = { onSpeedChange(preset.toFloat()) },
                    tag = "crane_speed_$preset"
                )
            }
        }
        Button(
            onClick = onApply,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = CraneTouchTarget)
                .testTag("crane_apply_angles")
        ) { Text("Aplicar ${base.toInt()}° / ${arm.toInt()}°") }
        OutlinedButton(
            onClick = onHome,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = CraneTouchTarget)
                .testTag("crane_home")
        ) { Text("HOME · 90° / 90°") }
    }
}

@Composable
private fun CraneAxis(
    label: String,
    value: Float,
    enabled: Boolean,
    tag: String,
    description: String,
    onChange: (Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(CompiTokens.Spacing.xxs)) {
        Text(
            text = "$label · ${value.toInt()}°",
            style = CompiTokens.Typography.MetricCaption,
            color = MaterialTheme.colorScheme.primary
        )
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = MIN_ANGLE..MAX_ANGLE,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(tag)
                .semantics {
                    contentDescription = description
                    stateDescription = "${value.toInt()} grados"
                }
        )
    }
}

@Composable
private fun CraneJoystickCard(
    enabled: Boolean,
    onNudge: (Float, Float) -> Unit,
    onCenter: () -> Unit
) {
    CraneCard(
        title = "Joystick virtual",
        tag = "crane_joystick_card",
        subtitle = "Pasos de 5° por pulsación"
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(CompiTokens.Spacing.xs)) {
            CraneStepButton("Brazo +5°", "Subir el brazo 5 grados", "crane_virtual_up", enabled) {
                onNudge(0f, 5f)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(CompiTokens.Spacing.xs)) {
                OutlinedButton(
                    onClick = { onNudge(-5f, 0f) },
                    enabled = enabled,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = CraneTouchTarget)
                        .testTag("crane_virtual_left")
                        .semantics { contentDescription = "Girar la base 5 grados a la izquierda" }
                ) { Text("Base −5°") }
                OutlinedButton(
                    onClick = { onNudge(5f, 0f) },
                    enabled = enabled,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = CraneTouchTarget)
                        .testTag("crane_virtual_right")
                        .semantics { contentDescription = "Girar la base 5 grados a la derecha" }
                ) { Text("Base +5°") }
            }
            CraneStepButton("Brazo −5°", "Bajar el brazo 5 grados", "crane_virtual_down", enabled) {
                onNudge(0f, -5f)
            }
            CraneStepButton("Centrar 90° / 90°", "Centrar ambos ejes", "crane_center_home", enabled) {
                onCenter()
            }
        }
    }
}

@Composable
private fun CraneStepButton(
    label: String,
    description: String,
    tag: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = CraneTouchTarget)
            .testTag(tag)
            .semantics { contentDescription = description }
    ) { Text(label, style = CompiTokens.Typography.BadgeLabel) }
}

@Composable
private fun CraneFeedbackCard(
    reply: CraneControlReply?,
    pendingId: Int?,
    timedOut: Boolean,
    writeError: String?,
    replyAt: Long
) {
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(reply?.requestId, pendingId, timedOut, writeError) {
        if (reply == null) return@LaunchedEffect
        while (true) {
            delay(1000L)
            nowMs = System.currentTimeMillis()
        }
    }
    val ageSeconds = if (replyAt > 0L) ((nowMs - replyAt) / 1000L).coerceAtLeast(0L) else 0L
    val ageSuffix = if (ageSeconds > 5L) {
        " · datos de hace ${if (ageSeconds < 60) "$ageSeconds s" else "${ageSeconds / 60} min"}"
    } else ""

    val (title, detail, tone) = when {
        writeError != null -> Triple("Comando no enviado", writeError, CraneTone.BLOCKED)
        pendingId != null && timedOut -> Triple(
            "Sin confirmación",
            "El ESP32 no respondió al comando #$pendingId en 2.5 s. " +
                "Revisa la conexión y usa DETENER si hay movimiento.",
            CraneTone.BLOCKED
        )
        pendingId != null -> Triple(
            "En cola",
            "Esperando confirmación del ESP32 para el comando #$pendingId…",
            CraneTone.NEUTRAL
        )
        reply == null -> Triple(
            "Sin comandos en esta sesión",
            "La confirmación del firmware aparece aquí con modo, ángulos y movimiento.",
            CraneTone.NEUTRAL
        )
        reply.accepted -> Triple(
            "Confirmado #${reply.requestId}",
            buildString {
                append("Base ").append(formatAngle(reply.baseDeg))
                append(" · Brazo ").append(formatAngle(reply.armDeg))
                append(" · ").append(modalityText(reply.mode))
                append(" · ").append(if (reply.moving) "en movimiento" else "detenido")
            },
            CraneTone.LIVE
        )
        else -> Triple(
            "Rechazado #${reply.requestId}",
            reasonText(reply.error) + stateSuffix(reply),
            CraneTone.BLOCKED
        )
    }

    CraneCard(
        title = title,
        tag = "crane_feedback_card",
        subtitle = "Confirmación del ESP32"
    ) {
        CraneLiveRegion {
            when (tone) {
                CraneTone.BLOCKED -> CraneBlockerLine(text = detail + ageSuffix, tag = "crane_feedback_detail")
                else -> CraneCaption(text = detail + ageSuffix, tag = "crane_feedback_detail")
            }
        }
    }
}

@Composable
private fun CraneCommandBar(
    enabled: Boolean,
    remoteControl: Boolean,
    onLocal: () -> Unit,
    onStop: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CompiTokens.Spacing.m, vertical = CompiTokens.Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(CompiTokens.Spacing.xs)
        ) {
            OutlinedButton(
                onClick = onLocal,
                enabled = enabled && remoteControl,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = CraneCommandTouchTarget)
                    .testTag("crane_local_mode")
            ) { Text("Control físico", style = CompiTokens.Typography.BadgeLabel) }
            Button(
                onClick = onStop,
                enabled = enabled,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                    // A translucent error over the bar made the label unreadable;
                    // the disabled state keeps the same red family the scheme
                    // already pairs with a legible foreground.
                    disabledContainerColor = MaterialTheme.colorScheme.errorContainer,
                    disabledContentColor = MaterialTheme.colorScheme.onErrorContainer
                ),
                modifier = Modifier
                    .weight(1.2f)
                    .heightIn(min = CraneCommandTouchTarget)
                    .testTag("crane_stop")
                    .semantics { contentDescription = "Detener la grúa ahora" }
            ) { Text("DETENER", style = CompiTokens.Typography.BadgeLabel) }
        }
    }
}

private fun formatAngle(value: Float?): String =
    value?.let { String.format(Locale.ROOT, "%.1f°", it) } ?: "—"

private fun modalityText(mode: String?): String = when (mode) {
    "REMOTE" -> "control remoto"
    "LOCAL" -> "control físico"
    else -> "modo sin confirmar"
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
