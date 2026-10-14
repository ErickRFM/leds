package com.rgbcontroller.feature.hardwarelab

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.rgbcontroller.core.components.model.ComponentMeasurement
import com.rgbcontroller.core.components.model.ComponentState
import com.rgbcontroller.core.components.model.ComponentTelemetryCodec
import com.rgbcontroller.core.components.model.ConfiguredComponent
import com.rgbcontroller.core.components.registry.ComponentCatalog
import com.rgbcontroller.core.components.ui.ComponentRenderer
import com.rgbcontroller.core.wifi.CompiLabSession
import com.rgbcontroller.core.wifi.LocalLabClient
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HardwareLabScreen(
    session: CompiLabSession? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var components by remember { mutableStateOf(emptyList<ConfiguredComponent>()) }
    var measurements by remember { mutableStateOf<List<ComponentMeasurement>?>(null) }
    var syncMessage by remember { mutableStateOf<String?>(null) }
    var showCatalog by remember { mutableStateOf(false) }

    val canSync = session != null && LocalLabClient.validKey(session.key)

    fun syncFromDevice() {
        val active = session
        if (active == null || !LocalLabClient.validKey(active.key)) {
            syncMessage = "Conecta y autoriza el ESP32 (clave de 16 hex) para leer componentes."
            return
        }
        scope.launch {
            try {
                val client = LocalLabClient(context)
                val payload = active.userOperation("Leer componentes") {
                    client.componentTelemetry(active.key)
                }
                val decoded = ComponentTelemetryCodec.decode(payload.toString())
                measurements = decoded
                components = components.map {
                    it.copy(state = ComponentTelemetryCodec.stateFor(it.id, decoded))
                }
                syncMessage = when {
                    decoded == null -> "El ESP32 devolvió telemetría no válida."
                    decoded.isEmpty() -> "Sin componentes activos en el ESP32."
                    else -> "Telemetría sincronizada (${decoded.size} lecturas)."
                }
            } catch (failure: Exception) {
                syncMessage = failure.message ?: "No se pudo sincronizar con el ESP32."
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Hardware Component Studio") },
                actions = {
                    IconButton(onClick = { syncFromDevice() }, enabled = canSync) {
                        Icon(Icons.Default.Refresh, contentDescription = "Sincronizar con ESP32")
                    }
                }
            )
        },
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
            Text("Catálogo de componentes modulares reutilizables", style = MaterialTheme.typography.bodyMedium)
            syncMessage?.let { message ->
                Spacer(Modifier.height(8.dp))
                Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }

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
                items(components.sortedBy { it.order }, key = { it.instanceId }) { component ->
                    ComponentRenderer(component = component, measurements = measurements)
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
                    items(ComponentCatalog.components, key = { it.id }) { desc ->
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
                                Text("Categoría: ${desc.category}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showCatalog = false }) { Text("Cerrar") } }
        )
    }
}
