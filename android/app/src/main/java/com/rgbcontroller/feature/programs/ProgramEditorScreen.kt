package com.rgbcontroller.feature.programs

import android.content.pm.ApplicationInfo
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.rgbcontroller.core.programs.LabGpioScriptCompiler
import com.rgbcontroller.core.programs.LabRgbScriptPolicy
import com.rgbcontroller.core.programs.ProgramLanguage
import com.rgbcontroller.core.programs.ProgramSourcePolicy
import com.rgbcontroller.core.wifi.LocalLabClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Offline V4 editor foundation: import, paste and inspect.
 * NO compilation, arbitrary execution, firmware upload or OTA endpoint is exposed.
 */
@Composable
fun ProgramEditorScreen() {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val debug = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    val labClient = remember(context) { LocalLabClient(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var source by rememberSaveable { mutableStateOf("") }
    var fileName by rememberSaveable { mutableStateOf("Sin archivo") }
    var selected by rememberSaveable { mutableStateOf(ProgramLanguage.LAB_SCRIPT.name) }
    var notice by rememberSaveable { mutableStateOf("Selecciona un archivo o pega tus instrucciones.") }
    var controlKey by remember { mutableStateOf("") } // RAM only. Never persisted or exported.
    var experimentBusy by remember { mutableStateOf(false) }
    var laboratoryMode by rememberSaveable { mutableStateOf("GPIO") }
    val language = ProgramLanguage.valueOf(selected)
    val compiled = remember(source, selected) {
        if (selected == ProgramLanguage.LAB_SCRIPT.name) {
            runCatching { LabGpioScriptCompiler.compile(source) }
        } else null
    }
    val gpioPlan = compiled?.getOrNull()
    val inspection = remember(source) { ProgramSourcePolicy.inspect(source) }
    val rgbCheck = remember(source) { LabRgbScriptPolicy.validate(source) }

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
                    source = text
                    fileName = name
                    selected = ProgramSourcePolicy.inferLanguage(name, text).name
                    notice = "Archivo importado. Falta validar sintaxis antes de ejecutar."
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

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Programas", style = MaterialTheme.typography.headlineMedium)
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
                            source = copied
                            fileName = "Portapapeles"
                            selected = ProgramSourcePolicy.inferLanguage(null, copied).name
                            notice = "Texto pegado. Revisa su modo antes de continuar."
                        }
                    }) { Text("Pegar") }
                }
                Text("Origen: $fileName", style = MaterialTheme.typography.labelMedium)
            }
        }

        Text("Tipo de proyecto", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = language == ProgramLanguage.LAB_SCRIPT,
                onClick = { selected = ProgramLanguage.LAB_SCRIPT.name },
                label = { Text("Laboratorio") })
            FilterChip(selected = language == ProgramLanguage.ARDUINO_CPP,
                onClick = { selected = ProgramLanguage.ARDUINO_CPP.name },
                label = { Text("Arduino C++") })
        }

        OutlinedTextField(
            value = source,
            onValueChange = { value ->
                if (value.toByteArray(Charsets.UTF_8).size <= ProgramSourcePolicy.MAX_BYTES) {
                    source = value
                } else notice = "Máximo 32 KiB por archivo en esta versión."
            },
            label = { Text("Código fuente") },
            placeholder = { Text("Escribe código o importa un archivo .txt / .ino") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 9,
            maxLines = 16,
            textStyle = MaterialTheme.typography.bodyMedium
        )

        Text("${inspection.byteCount} / ${ProgramSourcePolicy.MAX_BYTES} bytes · " +
            if (language == ProgramLanguage.LAB_SCRIPT) "Modo script" else "Modo Arduino C++",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { notice = ProgramSourcePolicy.inspect(source).explanation }) {
                Text("Revisar texto")
            }
            OutlinedButton(
                enabled = inspection.acceptedForEditing,
                onClick = { exporter.launch(if (language == ProgramLanguage.ARDUINO_CPP) "programa.ino" else "programa.txt") }
            ) { Text("Exportar") }
            OutlinedButton(
                enabled = inspection.acceptedForEditing,
                onClick = {
                    clipboard.setText(androidx.compose.ui.text.AnnotatedString(source))
                    notice = "Código copiado al portapapeles."
                }
            ) { Text("Copiar") }
        }
        Text(notice, color = if (inspection.acceptedForEditing)
            MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall)

        CodeProjectsPanel(
            source = source,
            language = language,
            onOpen = { project ->
                source = project.source
                selected = project.language.name
                fileName = project.name
                if (project.language == ProgramLanguage.LAB_SCRIPT) {
                    laboratoryMode = if (Regex("""(?m)^\s*RGB\s+""").containsMatchIn(project.source)) "RGB" else "GPIO"
                }
                notice = "Proyecto abierto: " + project.name + ". No se envió código a la placa."
            }
        )

        if (language == ProgramLanguage.LAB_SCRIPT) {
            Text("Motor de ejecución", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = laboratoryMode == "GPIO", onClick = { laboratoryMode = "GPIO" },
                    label = { Text("GPIO") })
                FilterChip(selected = laboratoryMode == "RGB", onClick = { laboratoryMode = "RGB" },
                    label = { Text("LED RGB") })
            }
        }

        if (language == ProgramLanguage.ARDUINO_CPP || laboratoryMode == "GPIO") ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Laboratorio · ejecución real", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Compatible hoy: GPIO 18, 19, 21, 22 o 23; cada HIGH/LOW debe ir seguido de WAIT 100–2000. " +
                        "Máximo 16 pasos y 20 segundos. No sobrescribe el firmware.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(onClick = {
                    source = "GPIO 19 HIGH\nWAIT 500\nGPIO 19 LOW\nWAIT 500\nSTOP"
                    fileName = "Ejemplo GPIO19"
                    selected = ProgramLanguage.LAB_SCRIPT.name
                    notice = "Ejemplo preparado. Usa una salida GPIO aprobada y verifica el cableado."
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
                        onValueChange = { if (it.length <= 16) controlKey = it },
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
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            enabled = debug && !experimentBusy && gpioPlan != null &&
                                LocalLabClient.validKey(controlKey),
                            onClick = {
                                val plan = gpioPlan
                                if (plan != null) scope.launch {
                                    experimentBusy = true
                                    try {
                                        labClient.sequence(controlKey, plan.pin, plan.sequence)
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
                                        labClient.stopExperiment(controlKey)
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
                HorizontalDivider()
                Text("Arduino C++", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Puedes importar/editar/exportar Arduino C++, compilar con Arduino CLI local y USB " +
                        "desde PC. Instalar C++ arbitrario reemplaza el firmware; OTA firmado aún pendiente.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedButton(onClick = {}, enabled = false) { Text("Instalar firmware (pendiente)") }
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
                        source = "RGB 255 0 0\\nWAIT 500\\nRGB 0 0 255\\nWAIT 500\\nOFF\\nSTOP".replace("\\n", "\n")
                        fileName = "Ejemplo RGB V4"
                        notice = "Ejemplo RGB cargado. Comprueba la conexión SoftAP."
                    }) { Text("Cargar ejemplo RGB") }
                    Text(rgbCheck.message, style = MaterialTheme.typography.bodySmall,
                        color = if (rgbCheck.accepted) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.error)
                    OutlinedTextField(
                        value = controlKey,
                        onValueChange = { if (it.length <= 16) controlKey = it },
                        label = { Text("Clave de control del ESP32 (16 caracteres)") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        enabled = debug && !experimentBusy
                    )
                    Text("APK debug únicamente · Wi-Fi ESP32-Control-Lab sin Internet. " +
                        "La clave queda en memoria, no se guarda. API HTTP sin TLS.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            enabled = debug && !experimentBusy && rgbCheck.accepted &&
                                LocalLabClient.validKey(controlKey),
                            onClick = {
                                scope.launch {
                                    experimentBusy = true
                                    try {
                                        labClient.runRgbScript(controlKey, source)
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
                                        labClient.stopRgbScript(controlKey)
                                        notice = "Detención confirmada; el último color se conserva."
                                    } catch (cancelled: CancellationException) { throw cancelled
                                    } catch (error: Exception) {
                                        notice = error.message ?: "No se pudo detener el RGB script."
                                    } finally { experimentBusy = false }
                                }
                            }
                        ) { Text("Detener") }
                    }
                    OutlinedButton(
                        enabled = debug && !experimentBusy && LocalLabClient.validKey(controlKey),
                        onClick = {
                            scope.launch {
                                experimentBusy = true
                                try {
                                    val state = labClient.rgbScriptStatus(controlKey)
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
