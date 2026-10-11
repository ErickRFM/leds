package com.rgbcontroller.feature.programs

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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

/**
 * Reusable project panel for Programas: private app-only files, no device key.
 * Parent owns the active editor source and receives the project on "Abrir".
 */
@Composable
fun CodeProjectsPanel(
    source: String,
    language: ProgramLanguage,
    onOpen: (CodeProject) -> Unit
) {
    val context = LocalContext.current
    val store = remember(context) { CodeProjectStore(context.applicationContext) }
    val scope = rememberCoroutineScope()
    val projects = remember { mutableStateListOf<CodeProject>() }
    var projectName by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("Se guardan en el teléfono, no en el ESP32.") }
    var pendingDelete by remember { mutableStateOf<CodeProject?>(null) }

    LaunchedEffect(store) {
        try {
            val saved = store.list()
            projects.clear()
            projects.addAll(saved)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            message = "No se pudo leer la biblioteca; se conserva el archivo original. " +
                (error.message ?: "")
        }
    }

    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Mis proyectos", style = MaterialTheme.typography.titleMedium)
            Text(
                "Hasta " + CodeProjectLibrary.MAX_PROJECTS +
                    " proyectos locales. Guardar no envía instrucciones ni firmware a la placa.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = projectName,
                onValueChange = {
                    if (it.length <= CodeProjectLibrary.MAX_NAME_LENGTH) projectName = it
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Nombre del proyecto") },
                enabled = !busy
            )
            Button(
                enabled = !busy && projectName.isNotBlank() &&
                    ProgramSourcePolicy.inspect(source).acceptedForEditing,
                onClick = {
                    scope.launch {
                        busy = true
                        try {
                            val newProject = CodeProject(
                                name = projectName.trim(),
                                language = language,
                                source = source,
                                updatedAtMillis = System.currentTimeMillis()
                            )
                            val saved = store.save(newProject)
                            projects.clear()
                            projects.addAll(saved)
                            message = "Proyecto guardado localmente: " + newProject.name
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: Exception) {
                            message = error.message ?: "No se pudo guardar el proyecto."
                        } finally {
                            busy = false
                        }
                    }
                }
            ) { Text("Guardar proyecto") }

            Text(
                "Guardados: " + projects.size + "/" + CodeProjectLibrary.MAX_PROJECTS,
                style = MaterialTheme.typography.labelMedium
            )
            if (projects.isEmpty()) {
                Text(
                    "Aún no hay proyectos. Escribe o importa código y selecciona Guardar proyecto.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            projects.forEach { project ->
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(project.name, style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (project.language == ProgramLanguage.ARDUINO_CPP)
                            "Arduino C++" else "Laboratorio Script",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(enabled = !busy, onClick = {
                            projectName = project.name
                            onOpen(project)
                            message = "Proyecto abierto: " + project.name
                        }) { Text("Abrir") }
                        TextButton(enabled = !busy, onClick = { pendingDelete = project }) {
                            Text("Eliminar")
                        }
                    }
                    HorizontalDivider()
                }
            }
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    val target = pendingDelete
    if (target != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Eliminar proyecto") },
            text = { Text("¿Eliminar '" + target.name + "' solo de este teléfono?") },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    scope.launch {
                        busy = true
                        try {
                            val updated = store.delete(target.name)
                            projects.clear()
                            projects.addAll(updated)
                            message = "Proyecto eliminado de la biblioteca local."
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: Exception) {
                            message = error.message ?: "No se pudo eliminar."
                        } finally {
                            busy = false
                        }
                    }
                }) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancelar") }
            }
        )
    }
}
