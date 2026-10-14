package com.rgbcontroller.feature.hardwarelab

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rgbcontroller.core.components.model.ConfiguredComponent
import com.rgbcontroller.core.components.model.ComponentState
import com.rgbcontroller.core.components.registry.ComponentCatalog
import com.rgbcontroller.core.components.ui.ComponentRenderer

@Composable
fun HardwareLabScreen() {
    var components by remember { mutableStateOf(listOf<ConfiguredComponent>()) }
    var showCatalog by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showCatalog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Agregar componente")
            }
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Text("Hardware Component Studio", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text("Catálogo de componentes modulares reutilizables", style = MaterialTheme.typography.bodyMedium)

            Spacer(Modifier.height(16.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (components.isEmpty()) {
                    item {
                        Card(Modifier.fillMaxWidth()) {
                            Column(
                                Modifier.fillMaxWidth().padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("No hay componentes configurados")
                                Text("Agrega componentes desde el catálogo", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                items(components.sortedBy { it.order }) { component ->
                    ComponentRenderer(component = component)
                }
            }
        }
    }

    if (showCatalog) {
        AlertDialog(
            onDismissRequest = { showCatalog = false },
            title = { Text("Catálogo de componentes") },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(ComponentCatalog.components) { desc ->
                        Card(
                            onClick = {
                                val newComp = ConfiguredComponent(
                                    id = desc.id,
                                    descriptor = desc,
                                    order = components.size,
                                    state = if (desc.supported) ComponentState.NOT_CONFIGURED else ComponentState.INCOMPATIBLE
                                )
                                components = components + newComp
                                showCatalog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(desc.name, style = MaterialTheme.typography.titleSmall)
                                Text(desc.description, style = MaterialTheme.typography.bodySmall)
                                Text("Categoría: ", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showCatalog = false }) { Text("Cerrar") } }
        )
    }
}
