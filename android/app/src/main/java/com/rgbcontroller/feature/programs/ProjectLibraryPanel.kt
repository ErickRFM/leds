package com.rgbcontroller.feature.programs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.rgbcontroller.core.data.CodeProjectStore
import com.rgbcontroller.core.programs.CodeProject
import com.rgbcontroller.core.programs.CodeProjectLibrary
import com.rgbcontroller.core.programs.ProgramLanguage
import com.rgbcontroller.core.programs.ProgramSourcePolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

/**
 * Projects are local editable sources, NOT installed programs.
 * The secret ESP32 control key is deliberately absent from this API/model.
 */
@Composable
fun ProjectLibraryPanel(
    source: String,
    language: ProgramLanguage,
    onOpen: (CodeProject) -> Unit
) {
    val context = LocalContext.current
    val store = remember(context) { CodeProjectStore(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var projects by remember { mutableStateOf<List<CodeProject>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(true) }
    var pendingOpen by remember { mutableStateOf<CodeProject?>(null) }
    var pendingDelete by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(store) {
        try {
            projects = store.list()
            error = null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = "No se pudo leer la biblioteca. No se sobrescribirá: " +
                (failure.message ?: "archivo no válido")
        } finally {
            busy = false
        }
    }

    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Mis proyectos", style = MaterialTheme.typography.titleMedium)
            Text(
                "Guarda el código en este teléfono. Los proyectos no se compilan ni instalan " +
                    "automáticamente. Las claves de conexión nunca se guardan aquí.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = name,
                onValueChange = { if (it.length <= CodeProjectLibrary.MAX_NAME_LENGTH) name = it },
                label = { Text("Nombre para guardar") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                enabled = !busy && error == null &&
                    ProgramSourcePolicy.inspect(source).acceptedForEditing && name.isNotBlank(),
                onClick = {
                    val selectedName = name.trim()
                    scope.launch {
                        busy = true
                        try {
                            projects = store.save(CodeProject(
                                selectedName, language, source, System.currentTimeMillis()
                            ))
                            error = null
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (failure: Exception) {
                            error = failure.message ?: "No se pudo guardar el proyecto."
                        } finally {
                            busy = false
                        }
                    }
                }
            ) { Text("Guardar / actualizar proyecto") }

            if (projects.isEmpty()) {
                Text("Todavía no hay proyectos guardados.",
                    style = MaterialTheme.typography.bodySmall)
            } else {
                Text("Guardados (" + projects.size + " / " +
                    CodeProjectLibrary.MAX_PROJECTS + ")",
                    style = MaterialTheme.typography.labelMedium)
                projects.forEach { project ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(project.name + " · " +
                            if (project.language == ProgramLanguage.ARDUINO_CPP) "Arduino C++" else "Script",
                            style = MaterialTheme.typography.bodyMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                enabled = !busy,
                                onClick = {
                                    if (source.isNotBlank() && source != project.source) {
                                        pendingOpen = project
                                    } else {
                                        onOpen(project)
                                        name = project.name
                                    }
                                }
                            ) { Text("Abrir") }
                            TextButton(enabled = !busy, onClick = {
                                pendingDelete = project.name
                            }) { Text("Eliminar") }
                        }
                    }
                }
            }

            error?.let {
                Text(it, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error)
            }
        }
    }

    pendingOpen?.let { project ->
        AlertDialog(
            onDismissRequest = { pendingOpen = null },
            title = { Text("¿Reemplazar el texto del editor?") },
            text = { Text("Se perderán los cambios no guardados en el editor actual. " +
                "Puedes cancelar y guardar tu borrador primero.") },
            confirmButton = {
                TextButton(onClick = {
                    onOpen(project)
                    name = project.name
                    pendingOpen = null
                }) { Text("Abrir proyecto") }
            },
            dismissButton = {
                TextButton(onClick = { pendingOpen = null }) { Text("Cancelar") }
            }
        )
    }

    pendingDelete?.let { selectedName ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Eliminar proyecto") },
            text = { Text("Se eliminará el proyecto guardado " + selectedName +
                " del teléfono. El firmware del ESP32 no cambia.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    scope.launch {
                        busy = true
                        try {
                            projects = store.delete(selectedName)
                            error = null
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (failure: Exception) {
                            error = failure.message ?: "No se pudo borrar el proyecto."
                        } finally { busy = false }
                    }
                }) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancelar") }
            }
        )
    }
}
