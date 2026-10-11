package com.rgbcontroller.feature.programs

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
import androidx.compose.ui.unit.dp
import com.rgbcontroller.core.programs.ProgramLanguage
import com.rgbcontroller.core.programs.ProgramSourcePolicy
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
    val scope = rememberCoroutineScope()
    var source by rememberSaveable { mutableStateOf("") }
    var fileName by rememberSaveable { mutableStateOf("Sin archivo") }
    var selected by rememberSaveable { mutableStateOf(ProgramLanguage.LAB_SCRIPT.name) }
    var notice by rememberSaveable { mutableStateOf("Selecciona un archivo o pega tus instrucciones.") }
    val language = ProgramLanguage.valueOf(selected)
    val inspection = remember(source) { ProgramSourcePolicy.inspect(source) }

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
        }
        Text(notice, color = if (inspection.acceptedForEditing)
            MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall)

        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Ejecución y carga a la placa", style = MaterialTheme.typography.titleMedium)
                Text("En preparación: scripts con intérprete protegido y Arduino C++ mediante compilación externa + OTA firmado. " +
                    "Esta versión NO compila, ejecuta ni flashea firmware.",
                    style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {}, enabled = false) { Text("Ejecutar") }
                    OutlinedButton(onClick = {}, enabled = false) { Text("Instalar") }
                }
                Text("Primero deben pasar seguridad, particiones y recuperación USB.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
