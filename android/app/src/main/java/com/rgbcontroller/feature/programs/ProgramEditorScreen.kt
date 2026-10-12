package com.rgbcontroller.feature.programs

import android.content.pm.ApplicationInfo
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import com.rgbcontroller.core.ui.CompiInlineMessage
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontFamily
import com.rgbcontroller.core.programs.LabGpioScriptCompiler
import com.rgbcontroller.core.programs.LabRgbScriptPolicy
import com.rgbcontroller.core.programs.EditorReplacementPolicy
import com.rgbcontroller.core.programs.EditorDraftSnapshot
import com.rgbcontroller.core.data.EditorDraftStore
import com.rgbcontroller.core.programs.ProgramLanguage
import com.rgbcontroller.core.programs.ProgramSourcePolicy
import com.rgbcontroller.core.wifi.LocalLabClient
import com.rgbcontroller.core.wifi.CompiLabSession
import com.rgbcontroller.core.ble.BleManager
import com.rgbcontroller.core.ble.BleConnectionState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Offline V4 editor foundation: import, paste and inspect.
 * NO compilation, arbitrary execution, firmware upload or OTA endpoint is exposed.
 */
private data class PendingEditorReplacement(
    val text: String,
    val displayName: String,
    val language: ProgramLanguage,
    val notice: String
)

@Composable
fun ProgramEditorScreen(labSession: CompiLabSession, bleManager: BleManager? = null) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val debug = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    val labClient = remember(context) { LocalLabClient(context.applicationContext) }
    val draftStore = remember(context) { EditorDraftStore(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var source by rememberSaveable { mutableStateOf("") }
    var fileName by rememberSaveable { mutableStateOf("Sin archivo") }
    var selected by rememberSaveable { mutableStateOf(ProgramLanguage.LAB_SCRIPT.name) }
    var notice by rememberSaveable { mutableStateOf("Selecciona un archivo o pega tus instrucciones.") }
    val controlKey = labSession.key // Volatile, shared with LED and Hardware Lab.
    val bleConnection = bleManager?.connectionState?.collectAsState()?.value
    var experimentBusy by remember { mutableStateOf(false) }
    var laboratoryMode by rememberSaveable { mutableStateOf("GPIO") }
    var gpioDraft by rememberSaveable { mutableStateOf("") }
    var rgbDraft by rememberSaveable { mutableStateOf("") }
    var cppDraft by rememberSaveable { mutableStateOf("") }
    var gpioFileName by rememberSaveable { mutableStateOf("Sin archivo") }
    var rgbFileName by rememberSaveable { mutableStateOf("Sin archivo") }
    var cppFileName by rememberSaveable { mutableStateOf("Sin archivo") }
    var draftsLoaded by remember { mutableStateOf(false) }
    var draftStatus by remember { mutableStateOf("Preparando recuperación local…") }
    var draftRecoveryError by remember { mutableStateOf(false) }

    // Files are private to Android; credentials from CompiLabSession are never serialized.
    // Do not overwrite the disk snapshot if loading detected corruption or a newer version.
    LaunchedEffect(draftStore) {
        try {
            val saved = draftStore.load()
            if (saved != null && source.isBlank() && gpioDraft.isBlank() &&
                rgbDraft.isBlank() && cppDraft.isBlank()) {
                gpioDraft = saved.gpio
                rgbDraft = saved.rgb
                cppDraft = saved.arduino
                gpioFileName = saved.gpioName
                rgbFileName = saved.rgbName
                cppFileName = saved.arduinoName
                selected = saved.selected.name
                laboratoryMode = saved.engine
                source = when (saved.selected) {
                    ProgramLanguage.ARDUINO_CPP -> saved.arduino
                    ProgramLanguage.LAB_SCRIPT -> if (saved.engine == "RGB") saved.rgb else saved.gpio
                }
                fileName = when (saved.selected) {
                    ProgramLanguage.ARDUINO_CPP -> saved.arduinoName
                    ProgramLanguage.LAB_SCRIPT -> if (saved.engine == "RGB") saved.rgbName else saved.gpioName
                }
                notice = "Borradores locales recuperados. No se enviaron al ESP32."
            }
            draftStatus = if (saved == null) "Guardado automático local activo"
                else "Borradores locales restaurados"
            draftsLoaded = true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            draftRecoveryError = true
            draftStatus = "No se pudo recuperar el archivo local. " +
                "Se ha protegido contra sobrescritura; exporta tu código antes de repararlo."
        }
    }
    LaunchedEffect(
        draftsLoaded, source, fileName, selected, laboratoryMode,
        gpioDraft, rgbDraft, cppDraft, gpioFileName, rgbFileName, cppFileName
    ) {
        if (!draftsLoaded || draftRecoveryError) return@LaunchedEffect
        delay(500)
        val snapshot = EditorDraftSnapshot(
            gpio = if (selected == ProgramLanguage.LAB_SCRIPT.name && laboratoryMode == "GPIO")
                source else gpioDraft,
            rgb = if (selected == ProgramLanguage.LAB_SCRIPT.name && laboratoryMode == "RGB")
                source else rgbDraft,
            arduino = if (selected == ProgramLanguage.ARDUINO_CPP.name) source else cppDraft,
            gpioName = if (selected == ProgramLanguage.LAB_SCRIPT.name && laboratoryMode == "GPIO")
                fileName else gpioFileName,
            rgbName = if (selected == ProgramLanguage.LAB_SCRIPT.name && laboratoryMode == "RGB")
                fileName else rgbFileName,
            arduinoName = if (selected == ProgramLanguage.ARDUINO_CPP.name) fileName else cppFileName,
            selected = ProgramLanguage.valueOf(selected), engine = laboratoryMode
        )
        try {
            draftStore.save(snapshot)
            draftStatus = "Borradores guardados localmente · sin subir al ESP32"
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            draftStatus = "No se guardaron los borradores: " +
                (error.message ?: "error de almacenamiento")
            draftRecoveryError = true
        }
    }

    fun rememberCurrentDraft() {
        if (selected == ProgramLanguage.ARDUINO_CPP.name) {
            cppDraft = source
            cppFileName = fileName
        } else if (laboratoryMode == "RGB") {
            rgbDraft = source
            rgbFileName = fileName
        } else {
            gpioDraft = source
            gpioFileName = fileName
        }
    }

    fun chooseEngine(next: String) {
        if (next == laboratoryMode) return
        rememberCurrentDraft()
        laboratoryMode = next
        source = if (next == "RGB") rgbDraft else gpioDraft
        fileName = if (next == "RGB") rgbFileName else gpioFileName
        notice = "Borrador ${if (next == "RGB") "RGB" else "GPIO"} restaurado. " +
            "Cada motor conserva su propio código."
    }

    fun chooseLanguage(next: ProgramLanguage) {
        if (selected == next.name) return
        rememberCurrentDraft()
        selected = next.name
        source = when (next) {
            ProgramLanguage.ARDUINO_CPP -> cppDraft
            ProgramLanguage.LAB_SCRIPT -> if (laboratoryMode == "RGB") rgbDraft else gpioDraft
        }
        fileName = when (next) {
            ProgramLanguage.ARDUINO_CPP -> cppFileName
            ProgramLanguage.LAB_SCRIPT -> if (laboratoryMode == "RGB") rgbFileName else gpioFileName
        }
    }

    val language = ProgramLanguage.valueOf(selected)
    val compiled = remember(source, selected, laboratoryMode) {
        if (selected == ProgramLanguage.LAB_SCRIPT.name && laboratoryMode == "GPIO") {
            runCatching { LabGpioScriptCompiler.compile(source) }
        } else null
    }
    val gpioPlan = compiled?.getOrNull()
    val inspection = remember(source) { ProgramSourcePolicy.inspect(source) }
    val rgbCheck = remember(source) { LabRgbScriptPolicy.validate(source) }
    var pendingReplacement by remember { mutableStateOf<PendingEditorReplacement?>(null) }

    fun applyReplacement(item: PendingEditorReplacement) {
        rememberCurrentDraft()
        val nextMode = if (item.language == ProgramLanguage.LAB_SCRIPT &&
            LabRgbScriptPolicy.validate(item.text).accepted) "RGB" else "GPIO"
        source = item.text
        fileName = item.displayName
        selected = item.language.name
        laboratoryMode = nextMode
        rememberCurrentDraft()
        notice = item.notice
    }

    fun requestReplacement(item: PendingEditorReplacement) {
        val nextMode = if (item.language == ProgramLanguage.LAB_SCRIPT &&
            LabRgbScriptPolicy.validate(item.text).accepted) "RGB" else "GPIO"
        val target = when (item.language) {
            ProgramLanguage.ARDUINO_CPP ->
                if (selected == item.language.name) source else cppDraft
            ProgramLanguage.LAB_SCRIPT -> if (nextMode == "RGB") {
                if (selected == item.language.name && laboratoryMode == "RGB") source else rgbDraft
            } else {
                if (selected == item.language.name && laboratoryMode == "GPIO") source else gpioDraft
            }
        }
        if (EditorReplacementPolicy.requiresConfirmation(target, item.text)) {
            pendingReplacement = item
        } else applyReplacement(item)
    }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            try {
                val (name, text) = withContext(Dispatchers.IO) {
                    val displayName = context.contentResolver.query(
                        uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
                    )?.use { cursor ->
                        if (cursor.moveToFirst()) cursor.getString(0) else null
                    }
                    val contents = context.contentResolver.openInputStream(uri)?.use {
                        ProgramSourcePolicy.readUtf8(it)
                    } ?: error("No se pudo leer el archivo.")
                    (displayName ?: "Archivo importado") to contents
                }
                val result = ProgramSourcePolicy.inspect(text)
                if (result.acceptedForEditing) {
                    requestReplacement(PendingEditorReplacement(
                        text, name, ProgramSourcePolicy.inferLanguage(name, text),
                        "Archivo importado. Revisa su sintaxis antes de ejecutar."
                    ))
                } else notice = result.explanation
            } catch (error: Exception) {
                notice = error.message ?: "No fue posible abrir el archivo."
            }
        }
    }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use {
                        it.write(source.toByteArray(Charsets.UTF_8))
                    } ?: error("No se pudo guardar el documento.")
                }
                notice = "Texto exportado. No se ha enviado código al ESP32."
            } catch (error: Exception) {
                notice = error.message ?: "Error al exportar el documento."
            }
        }
    }

    pendingReplacement?.let { candidate ->
        AlertDialog(
            onDismissRequest = { pendingReplacement = null },
            title = { Text("¿Reemplazar el código actual?") },
            text = {
                Text("El editor contiene código diferente. Si continúas, será reemplazado " +
                    "por ${candidate.displayName}. Guarda o exporta el contenido actual si deseas conservarlo.")
            },
            confirmButton = {
                TextButton(onClick = {
                    applyReplacement(candidate)
                    pendingReplacement = null
                }) { Text("Reemplazar") }
            },
            dismissButton = {
                TextButton(onClick = { pendingReplacement = null }) { Text("Cancelar") }
            }
        )
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Programas", style = MaterialTheme.typography.headlineMedium)
        CompiInlineMessage(draftStatus, isError = draftRecoveryError)
        val operation = labSession.operationStatus
        if (operation.name.isNotBlank()) {
            CompiInlineMessage(
                "${operation.name}: ${operation.phase.name.lowercase()}" +
                    (if (operation.details.isBlank()) "" else " · ${operation.details}"),
                isError = operation.phase == com.rgbcontroller.core.wifi.LabOperationPhase.FAILED
            )
        }
        Text("Importa un TXT/INO, pega código o escribe un programa. El núcleo RGB y la conexión no se modifican.",
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Editor local", style = MaterialTheme.typography.titleMedium)
                Text("Los archivos se abren en el teléfono; nada se instala automáticamente en la placa.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        importer.launch(arrayOf("text/plain", "text/x-c++src", "text/x-c++hdr",
                            "application/octet-stream", "*/*"))
                    }) { Text("Importar") }
                    OutlinedButton(onClick = {
                        val copied = clipboard.getText()?.text.orEmpty()
                        if (copied.isBlank()) {
                            notice = "El portapapeles no contiene texto."
                        } else if (ProgramSourcePolicy.inspect(copied).byteCount > ProgramSourcePolicy.MAX_BYTES) {
                            notice = "El texto copiado supera 32 KiB."
                        } else {
                            requestReplacement(PendingEditorReplacement(
                                copied, "Portapapeles",
                                ProgramSourcePolicy.inferLanguage(null, copied),
                                "Texto pegado. Revisa su modo antes de continuar."
                            ))
                        }
                    }) { Text("Pegar") }
                }
                Text("Origen: $fileName", style = MaterialTheme.typography.labelMedium)
            }
        }

        Text("Tipo de proyecto", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = language == ProgramLanguage.LAB_SCRIPT,
                onClick = { chooseLanguage(ProgramLanguage.LAB_SCRIPT) },
                label = { Text("Laboratorio") })
            FilterChip(selected = language == ProgramLanguage.ARDUINO_CPP,
                onClick = { chooseLanguage(ProgramLanguage.ARDUINO_CPP) },
                label = { Text("Arduino C++") })
        }

        if (language == ProgramLanguage.LAB_SCRIPT) {
            Text("Motor de ejecución", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = laboratoryMode == "GPIO",
                    onClick = { chooseEngine("GPIO") },
                    modifier = Modifier.testTag("studio_engine_gpio"),
                    label = { Text("GPIO") }
                )
                FilterChip(
                    selected = laboratoryMode == "RGB",
                    onClick = { chooseEngine("RGB") },
                    modifier = Modifier.testTag("studio_engine_rgb"),
                    label = { Text("LED RGB") }
                )
            }
            Text(
                if (laboratoryMode == "GPIO")
                    "GPIO19 NO controla el LED RGB de 27/25/26. Conecta otro LED y resistencia al GPIO19 para probar este motor."
                else "Este motor usa tu LED RGB en 27/25/26 y el Wi-Fi local ESP32-Control-Lab.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        OutlinedTextField(
            value = source,
            onValueChange = { value ->
                if (value.toByteArray(Charsets.UTF_8).size <= ProgramSourcePolicy.MAX_BYTES) {
                    source = value
                    rememberCurrentDraft()
                } else notice = "Máximo 32 KiB por archivo en esta versión."
            },
            label = { Text("Código fuente") },
            placeholder = { Text("Escribe código o importa un archivo .txt / .ino") },
            modifier = Modifier.fillMaxWidth().testTag("studio_source"),
            minLines = 9,
            maxLines = 16,
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
        )

        Text("${inspection.byteCount} / ${ProgramSourcePolicy.MAX_BYTES} bytes · " +
            if (language == ProgramLanguage.LAB_SCRIPT) "Modo script" else "Modo Arduino C++",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                modifier = Modifier.weight(1f),
                onClick = { notice = ProgramSourcePolicy.inspect(source).explanation }
            ) { Text("Validar") }
            OutlinedButton(
                modifier = Modifier.weight(1f),
                enabled = inspection.acceptedForEditing,
                onClick = { exporter.launch(if (language == ProgramLanguage.ARDUINO_CPP) "programa.ino" else "programa.txt") }
            ) { Text("Exportar") }
        }
        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            enabled = inspection.acceptedForEditing,
            onClick = {
                clipboard.setText(androidx.compose.ui.text.AnnotatedString(source))
                notice = "Código copiado al portapapeles."
            }
        ) { Text("Copiar código") }
        CompiInlineMessage(notice, isError = notice.startsWith("Error", ignoreCase = true) ||
            notice.startsWith("No se pudo", ignoreCase = true) ||
            notice.startsWith("Máximo", ignoreCase = true))

        CodeProjectsPanel(
            source = source,
            language = language,
            onOpen = { project ->
                requestReplacement(PendingEditorReplacement(
                    project.source, project.name, project.language,
                    "Proyecto abierto: ${project.name}. No se envió código a la placa."
                ))
            }
        )


        if (language == ProgramLanguage.LAB_SCRIPT && laboratoryMode == "GPIO") ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Laboratorio · ejecución real", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Compatible hoy: GPIO 18, 19, 21, 22 o 23; cada HIGH/LOW debe ir seguido de WAIT 100–2000. " +
                        "Máximo 16 pasos y 20 segundos. No sobrescribe el firmware.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(onClick = {
                    requestReplacement(PendingEditorReplacement(
                        "GPIO 19 HIGH\nWAIT 500\nGPIO 19 LOW\nWAIT 500\nSTOP",
                        "Ejemplo GPIO19",
                        ProgramLanguage.LAB_SCRIPT,
                        "Ejemplo preparado. Usa una salida GPIO aprobada y verifica el cableado."
                    ))
                }) { Text("Cargar ejemplo GPIO") }
                if (language == ProgramLanguage.LAB_SCRIPT) {
                    Text(
                        if (gpioPlan != null) {
                            "Válido: GPIO " + gpioPlan.pin + " · " + gpioPlan.stepCount +
                                " pasos · " + gpioPlan.durationMs + " ms."
                        } else "Validación: " + (compiled?.exceptionOrNull()?.message ?: "Escribe un script GPIO."),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (gpioPlan != null) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.error
                    )
                    OutlinedTextField(
                        value = controlKey,
                        onValueChange = { labSession.updateKey(it) },
                        label = { Text("Clave de control del ESP32 (16 caracteres)") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = debug && !experimentBusy
                    )
                    Text(
                        if (debug) "Solo APK debug: conecta a ESP32-Control-Lab. " +
                            "La API usa HTTP local sin TLS; no la expongas a Internet."
                        else "Ejecución no habilitada en APK release sin seguridad de vinculación.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (experimentBusy) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text("Esperando respuesta del ESP32…", style = MaterialTheme.typography.labelSmall)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            enabled = debug && !experimentBusy && gpioPlan != null &&
                                LocalLabClient.validKey(controlKey),
                            onClick = {
                                val plan = gpioPlan
                                if (plan != null) scope.launch {
                                    experimentBusy = true
                                    try {
                                        labSession.userOperation("Secuencia GPIO") { labClient.sequence(controlKey, plan.pin, plan.sequence) }
                                        notice = "ESP32 aceptó el inicio de la secuencia en GPIO " + plan.pin +
                                            ". Confirmación lógica; revisa el circuito físico."
                                    } catch (cancelled: CancellationException) {
                                        throw cancelled
                                    } catch (error: Exception) {
                                        notice = error.message ?: "Error al ejecutar en el ESP32."
                                    } finally {
                                        experimentBusy = false
                                    }
                                }
                            }
                        ) { Text("Ejecutar GPIO") }
                        OutlinedButton(
                            enabled = debug && !experimentBusy && LocalLabClient.validKey(controlKey),
                            onClick = {
                                scope.launch {
                                    experimentBusy = true
                                    try {
                                        labSession.userOperation("Detener GPIO") { labClient.stopExperiment(controlKey) }
                                        notice = "El ESP32 confirmó la orden de detener el experimento."
                                    } catch (cancelled: CancellationException) {
                                        throw cancelled
                                    } catch (error: Exception) {
                                        notice = error.message ?: "No se pudo detener; revisa la placa."
                                    } finally {
                                        experimentBusy = false
                                    }
                                }
                            }
                        ) { Text("Detener") }
                    }
                }
            }
        }

        if (language == ProgramLanguage.ARDUINO_CPP) {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Arduino C++ · compilación y USB", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Puedes importar, guardar y exportar el código .ino desde Android. " +
                            "En Windows compílalo con Arduino CLI mediante " +
                            "scripts/build-user-sketch.ps1 o scripts/build-user-sketch.py.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "Un sketch Arduino C++ completo reemplaza el firmware RGB/BLE/Wi-Fi. " +
                            "No se ha habilitado flasheo desde Android ni OTA firmado; " +
                            "la sustitución USB requiere respaldo y confirmación explícita.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(onClick = {}, enabled = false) {
                        Text("Instalar desde Android (pendiente)")
                    }
                }
            }
        }

        if (language == ProgramLanguage.LAB_SCRIPT && laboratoryMode == "RGB") {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("RGB Script · ejecución real", style = MaterialTheme.typography.titleMedium)
                    Text("Ejecuta temporalmente RGB, WAIT, ON, OFF y STOP sin reflashear. " +
                        "Máximo 512 bytes, 32 pasos y 20 segundos. Requiere firmware V4 con /v2/script/run.",
                        style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = {
                        requestReplacement(PendingEditorReplacement(
                            "RGB 255 0 0\\nWAIT 500\\nRGB 0 0 255\\nWAIT 500\\nOFF\\nSTOP".replace("\\n", "\n"),
                            "Ejemplo RGB V4",
                            ProgramLanguage.LAB_SCRIPT,
                            "Ejemplo RGB preparado. Comprueba la conexión SoftAP."
                        ))
                    }) { Text("Cargar ejemplo RGB") }
                    Text(rgbCheck.message, style = MaterialTheme.typography.bodySmall,
                        color = if (rgbCheck.accepted) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.error)
                    OutlinedTextField(
                        value = controlKey,
                        onValueChange = { labSession.updateKey(it) },
                        label = { Text("Clave de control del ESP32 (16 caracteres)") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        enabled = debug && !experimentBusy
                    )
                    Text("APK debug únicamente · Wi-Fi ESP32-Control-Lab sin Internet. " +
                        "La clave queda en memoria, no se guarda. API HTTP sin TLS.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (experimentBusy) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text("Enviando script al ESP32…", style = MaterialTheme.typography.labelSmall)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            enabled = debug && !experimentBusy && rgbCheck.accepted &&
                                LocalLabClient.validKey(controlKey),
                            onClick = {
                                scope.launch {
                                    experimentBusy = true
                                    try {
                                        labSession.userOperation("Script RGB") { labClient.runRgbScript(controlKey, source) }
                                        notice = "ESP32 aceptó el script RGB. Consulta estado y verifica LED físico."
                                    } catch (cancelled: CancellationException) { throw cancelled
                                    } catch (error: Exception) {
                                        notice = error.message ?: "Falló la ejecución RGB."
                                    } finally { experimentBusy = false }
                                }
                            }
                        ) { Text("Ejecutar RGB") }
                        OutlinedButton(
                            enabled = debug && !experimentBusy && LocalLabClient.validKey(controlKey),
                            onClick = {
                                scope.launch {
                                    experimentBusy = true
                                    try {
                                        labSession.userOperation("Detener RGB") { labClient.stopRgbScript(controlKey) }
                                        notice = "Detención confirmada; el último color se conserva."
                                    } catch (cancelled: CancellationException) { throw cancelled
                                    } catch (error: Exception) {
                                        notice = error.message ?: "No se pudo detener el RGB script."
                                    } finally { experimentBusy = false }
                                }
                            }
                        ) { Text("Detener") }
                    }
                    Text(
                        "Bluetooth permite efectos RGB integrados, no ejecutar este archivo ni operar GPIO.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (bleConnection == BleConnectionState.Ready && bleManager != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = {
                                bleManager.writeCommand("FX,BREATHE,120")
                                notice = "Respiración BLE enviada. Comprueba el estado RGB recibido."
                            }) { Text("Respirar por BLE") }
                            TextButton(onClick = {
                                bleManager.writeCommand("FX,STOP")
                                notice = "Detención de efecto BLE enviada. Esperando estado."
                            }) { Text("Parar BLE") }
                        }
                    }
                    OutlinedButton(
                        enabled = debug && !experimentBusy && LocalLabClient.validKey(controlKey),
                        onClick = {
                            scope.launch {
                                experimentBusy = true
                                try {
                                    val state = labSession.userOperation("Consultar script RGB") { labClient.rgbScriptStatus(controlKey) }
                                    notice = if (state.optBoolean("active")) {
                                        "RGB ejecutando paso ${state.optInt("step") + 1} de ${state.optInt("total")}."
                                    } else "RGB sin script en ejecución."
                                } catch (cancelled: CancellationException) { throw cancelled
                                } catch (error: Exception) {
                                    notice = error.message ?: "No se pudo leer el estado RGB."
                                } finally { experimentBusy = false }
                            }
                        }
                    ) { Text("Consultar estado RGB") }
                }
            }
        }
    }
}
