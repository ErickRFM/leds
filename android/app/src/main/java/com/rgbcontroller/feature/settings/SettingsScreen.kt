package com.rgbcontroller.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.rgbcontroller.core.ble.BleConnectionState
import com.rgbcontroller.core.ble.BleSecurityState
import com.rgbcontroller.core.ble.BleManager
import com.rgbcontroller.core.data.PreferencesRepository
import com.rgbcontroller.core.ui.CompiInlineMessage
import com.rgbcontroller.core.ui.CompiModuleCard
import com.rgbcontroller.core.ui.SectionHeader
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    bleManager: BleManager,
    preferencesRepository: PreferencesRepository,
    onVoiceTest: () -> Boolean = { false },
    onOpenHub: () -> Unit,
    onOpenPrograms: () -> Unit
) {
    val connection by bleManager.connectionState.collectAsState()
    val security by bleManager.securityState.collectAsState()
    val frame by bleManager.deviceState.collectAsState()
    val theme by preferencesRepository.themeMode.collectAsState(initial = "SYSTEM")
    val context = LocalContext.current
    val voicePrefs = remember(context) {
        context.applicationContext.getSharedPreferences("compi_voice", android.content.Context.MODE_PRIVATE)
    }
    var phoneVoiceEnabled by remember { mutableStateOf(voicePrefs.getBoolean("phone_enabled", false)) }
    var voiceTestMessage by remember { mutableStateOf<String?>(null) }
    var phoneVolume by remember { mutableFloatStateOf(voicePrefs.getInt("phone_volume", 100).toFloat()) }
    var phoneRate by remember { mutableFloatStateOf(voicePrefs.getInt("phone_rate", 100).toFloat()) }
    val scope = rememberCoroutineScope()
    var showTechnical by rememberSaveable { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 20.dp)
            .testTag("settings_screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionHeader("Ajustes", "Apariencia, conexión y ayuda")
        Text("Apariencia", style = MaterialTheme.typography.titleMedium)
        Text("Compi respeta la apariencia del teléfono si seleccionas Sistema.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            listOf("SYSTEM" to "Sistema", "DARK" to "Oscuro", "LIGHT" to "Claro").forEachIndexed { i, pair ->
                SegmentedButton(
                    shape = SegmentedButtonDefaults.itemShape(i, 3),
                    selected = theme == pair.first,
                    onClick = { scope.launch { preferencesRepository.saveThemeMode(pair.first) } },
                    label = { Text(pair.second) }
                )
            }
        }

        Text("Conexión del controlador", style = MaterialTheme.typography.titleMedium)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(when (val state = connection) {
                    BleConnectionState.Ready -> if (security == BleSecurityState.Authorized)
                        "●  ESP32 autorizado por Bluetooth" else "ESP32 conectado · falta autorización"
                    BleConnectionState.Scanning -> "Buscando controladores"
                    BleConnectionState.Connecting -> "Estableciendo conexión…"
                    BleConnectionState.Discovering -> "Sincronizando estado…"
                    BleConnectionState.Disconnected -> "Sin conexión Bluetooth"
                    is BleConnectionState.Error -> "Conexión necesita atención"
                }, style = MaterialTheme.typography.titleMedium)
                if (connection is BleConnectionState.Error) {
                    CompiInlineMessage((connection as BleConnectionState.Error).message, isError = true)
                }
                OutlinedButton(
                    enabled = connection == BleConnectionState.Ready && security == BleSecurityState.Authorized,
                    onClick = { bleManager.writeCommand("GET") }
                ) { Text("Consultar estado actual") }
                TextButton(onClick = { showTechnical = !showTechnical }) {
                    Text(if (showTechnical) "Ocultar detalles técnicos" else "Ver detalles técnicos")
                }
                if (showTechnical) {
                    Text("Protocolo BLE: STATE / SET / FX", style = MaterialTheme.typography.bodySmall)
                    Text("Última trama: $frame", style = MaterialTheme.typography.bodySmall)
                    Text("El estado BLE no indica propiedad segura del ESP32.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Text("Compi Voice", style = MaterialTheme.typography.titleMedium)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text("Voz automática del celular", style = MaterialTheme.typography.titleSmall)
                        Text("El celular anuncia cambios de luz y temperatura sin microSD.",
                            style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(
                        checked = phoneVoiceEnabled,
                        onCheckedChange = {
                            phoneVoiceEnabled = it
                            voicePrefs.edit().putBoolean("phone_enabled", it).apply()
                        },
                        modifier = Modifier.testTag("voice_phone_enabled")
                    )
                }
                Text("Volumen del celular: ${phoneVolume.toInt()} %")
                Slider(value = phoneVolume, onValueChange = {
                    phoneVolume = it
                    voicePrefs.edit().putInt("phone_volume", it.toInt()).apply()
                }, valueRange = 0f..100f, modifier = Modifier.testTag("voice_phone_volume"))
                Text("Velocidad de voz: ${phoneRate.toInt()} %")
                Slider(value = phoneRate, onValueChange = {
                    phoneRate = it
                    voicePrefs.edit().putInt("phone_rate", it.toInt()).apply()
                }, valueRange = 70f..130f, modifier = Modifier.testTag("voice_phone_rate"))
                OutlinedButton(onClick = {
                    voiceTestMessage = if (onVoiceTest()) "Reproduciendo prueba de voz."
                        else "La voz española todavía no está disponible en Android."
                }, modifier = Modifier.testTag("voice_test_button")) {
                    Text("Probar voz del celular")
                }
                voiceTestMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                Text("Funciona mientras Compi recibe lecturas actuales y la app permanece activa. " +
                    "El audio físico del DFPlayer se controla por separado.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Text("Herramientas", style = MaterialTheme.typography.titleMedium)
        CompiModuleCard("Hardware Lab", "Wi-Fi local, GPIO, sensores y diagnóstico",
            Icons.Default.Memory, onClick = onOpenHub)
        CompiModuleCard("Compi Studio", "Archivos, proyectos y scripts compatibles",
            Icons.Default.Code, onClick = onOpenPrograms)

        Text("¿No encuentras el dispositivo?", style = MaterialTheme.typography.titleMedium)
        CompiInlineMessage("Comprueba alimentación y Bluetooth. Concede los permisos de la app, " +
            "abre Conectar → Bluetooth y busca RGB-ESP32. Si no aparece, revisa firmware y UUID.")
        Text("Wi-Fi de laboratorio está limitado a compilaciones debug y no implica una conexión " +
            "segura de producción.", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
