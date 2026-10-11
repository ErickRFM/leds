package com.rgbcontroller.feature.hub

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.rgbcontroller.core.data.PreferencesRepository
import com.rgbcontroller.core.lab.LabExperimentLibrary
import com.rgbcontroller.core.lab.LabStep
import com.rgbcontroller.core.ui.SectionHeader
import com.rgbcontroller.core.wifi.LocalLabClient
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun LabHubScreen() {
    val context = LocalContext.current
    val debug = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    val client = remember { LocalLabClient() }
    val preferences = remember(context) { PreferencesRepository(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var secret by remember { mutableStateOf("") } // Never persist credentials
    var status by remember { mutableStateOf<JSONObject?>(null) }
    var firmware by remember { mutableStateOf<JSONObject?>(null) }
    var networks by remember { mutableStateOf<JSONArray?>(null) }
    var ssid by remember { mutableStateOf("") }
    var wifiPassword by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("Conéctate a ESP32-Control-Lab para comenzar.") }
    var busy by remember { mutableStateOf(false) }
    var selectedPin by remember { mutableIntStateOf(18) }
    var adcValue by remember { mutableStateOf<Int?>(null) }
    var steps by remember { mutableStateOf("1:500,0:500,1:500,0:500") }
    var nextHigh by remember { mutableStateOf(true) }
    var nextDuration by remember { mutableStateOf("500") }

    fun action(block: suspend () -> Unit) {
        if (!debug || busy) return
        busy = true
        scope.launch {
            try { block() }
            catch (e: Exception) { message = e.message ?: "No hay respuesta del ESP32." }
            finally { busy = false }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
        .padding(horizontal = 18.dp, vertical = 20.dp)) {
        SectionHeader("Centro ESP32", "Wi-Fi, GPIO, sensores y laboratorio")
        Spacer(Modifier.height(12.dp))
        Card(Modifier.fillMaxWidth(), colors =
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(16.dp)) {
                Text(if (debug) "Modo laboratorio: solo APK debug"
                    else "Control Wi-Fi deshabilitado en release",
                    style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(6.dp))
                Text("Conecta a ESP32-Control-Lab. Su contraseña y la clave de control " +
                    "están en el monitor serie USB (115200). Este transporte no usa TLS; solo pruebas.",
                    style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = {
                    context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
                }) { Text("Abrir ajustes Wi-Fi") }
                Text("SoftAP · 192.168.4.1", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(value = secret, onValueChange = { secret = it.trim().take(16) },
                    label = { Text("Clave de control del dispositivo") },
                    singleLine = true, visualTransformation = PasswordVisualTransformation(),
                    enabled = debug, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                Button(onClick = {
                    action {
                        status = client.status(secret)
                        message = "Estado recibido del ESP32."
                    }
                }, enabled = debug && !busy && LocalLabClient.validKey(secret)) {
                    Text("Consultar estado")
                }
                status?.let { data ->
                    Spacer(Modifier.height(10.dp))
                    Text("Wi-Fi del router: " + if (data.optBoolean("wifiConnected")) "Conectado" else "No conectado")
                    Text("IP estación: " + data.optString("stationIp"))
                    Text("RAM disponible: " + data.optInt("freeHeap") + " B")
                    Text("Tiempo activo: " + data.optLong("uptimeMs") + " ms")
                    Text("RGB: " + data.optString("rgb"), style = MaterialTheme.typography.bodySmall)
                    Text("Recursos ocupados: " + data.optInt("leasedPins"))
                    Text("Experimento: " + if (data.optBoolean("experimentActive")) "Activo" else "Inactivo")
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Text("Diagnóstico de firmware", style = MaterialTheme.typography.titleMedium)
        Text("Inspección de particiones OTA. La actualización no está activada.",
            style = MaterialTheme.typography.bodySmall)
        OutlinedButton(enabled = debug && !busy && LocalLabClient.validKey(secret),
            onClick = { action {
                firmware = client.firmwareInfo(secret)
                message = "Particiones leídas directamente desde el ESP32."
            } }) { Text("Comprobar espacio para OTA") }
        firmware?.let { data ->
            Text("Firmware: " + data.optString("firmwareVersion"))
            Text("Actual: " + data.optString("activePartition") +
                " · " + data.optLong("activeSize") + " bytes")
            Text("Siguiente: " + data.optString("nextPartition") +
                " · " + data.optLong("nextSize") + " bytes")
            Text("Partición candidata: " + if (data.optBoolean("otaSlotPresent")) "Sí" else "No")
            Text("Subir firmware OTA: DESHABILITADO", color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(20.dp))
        Text("Wi-Fi del ESP32", style = MaterialTheme.typography.titleMedium)
        Text("La búsqueda la realiza el ESP32, no el teléfono.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(enabled = debug && !busy && LocalLabClient.validKey(secret),
            onClick = { action {
                networks = client.scan(secret).optJSONArray("networks")
                message = "Redes disponibles actualizadas."
            } }) { Text("Buscar redes") }
        networks?.let { result ->
            for (i in 0 until minOf(result.length(), 24)) {
                val network = result.optJSONObject(i) ?: continue
                val name = network.optString("ssid")
                if (name.isNotEmpty()) {
                    TextButton(onClick = { ssid = name }) {
                        Text(name + " · " + network.optInt("rssi") + " dBm")
                    }
                }
            }
        }
        OutlinedTextField(value = ssid, onValueChange = { ssid = it.take(32) },
            label = { Text("SSID") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(value = wifiPassword, onValueChange = { wifiPassword = it.take(63) },
            label = { Text("Contraseña de la red") },
            visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
            singleLine = true)
        Spacer(Modifier.height(8.dp))
        Button(enabled = debug && !busy && LocalLabClient.validKey(secret) &&
            LocalLabClient.validSsid(ssid) && LocalLabClient.validPassword(wifiPassword),
            onClick = { action {
                client.connectWifi(secret, ssid, wifiPassword)
                wifiPassword = ""
                message = "ESP32 conectando al router. Consulta Estado para confirmar."
            } }) { Text("Conectar ESP32 al router") }

        Spacer(Modifier.height(20.dp))
        Text("Entradas y salidas GPIO", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(18, 19, 21, 22, 23).forEach { pin ->
                FilterChip(selected = selectedPin == pin, onClick = { selectedPin = pin },
                    label = { Text(pin.toString()) })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = debug && !busy && LocalLabClient.validKey(secret),
                onClick = { action {
                    client.writePin(secret, selectedPin, true)
                    message = "GPIO " + selectedPin + " en HIGH por máximo 30 s."
                } }) { Text("HIGH") }
            OutlinedButton(enabled = debug && !busy && LocalLabClient.validKey(secret),
                onClick = { action {
                    client.writePin(secret, selectedPin, false)
                    message = "GPIO " + selectedPin + " en LOW."
                } }) { Text("LOW") }
            OutlinedButton(enabled = debug && !busy && LocalLabClient.validKey(secret),
                onClick = { action {
                    val value = client.readPin(secret, selectedPin).optInt("value")
                    message = "GPIO " + selectedPin + ": " + value
                } }) { Text("Leer") }
        }
        OutlinedButton(enabled = debug && !busy && LocalLabClient.validKey(secret),
            onClick = { action {
                client.resetPins(secret)
                message = "Salidas manuales restauradas a LOW."
            } }) { Text("Liberar salidas") }
        Spacer(Modifier.height(18.dp))
        Text("Sensores / ADC1", style = MaterialTheme.typography.titleMedium)
        Text("GPIO34 (entrada analógica, máximo 3.3 V). Valor sin calibrar.",
            style = MaterialTheme.typography.bodySmall)
        Button(enabled = debug && !busy && LocalLabClient.validKey(secret),
            onClick = { action {
                adcValue = client.readAnalog(secret, 34).optInt("raw")
                message = "ADC1 leído desde el ESP32."
            } }) { Text("Leer ADC GPIO34") }
        adcValue?.let { Text("ADC: " + it) }
        Spacer(Modifier.height(20.dp))
        Text("Laboratorio protegido", style = MaterialTheme.typography.titleMedium)
        Text("Prueba declarativa: GPIO19 se alterna 5 veces, cada 500 ms.",
            style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = debug && !busy && LocalLabClient.validKey(secret),
                onClick = { action {
                    client.blink(secret, 19, 500, 5)
                    message = "Prueba de parpadeo iniciada."
                } }) { Text("Iniciar") }
            OutlinedButton(enabled = debug && !busy && LocalLabClient.validKey(secret),
                onClick = { action {
                    client.stopExperiment(secret)
                    message = "Experimento detenido y pin liberado."
                } }) { Text("Detener") }
        }
        Spacer(Modifier.height(12.dp))
        Text("Secuencia editable: 1=HIGH, 0=LOW; tiempo en ms por paso.",
            style = MaterialTheme.typography.bodySmall)
        Text("Plantillas de laboratorio", style = MaterialTheme.typography.titleSmall)
        LabExperimentLibrary.templates.forEach { template ->
            OutlinedButton(onClick = { steps = template.sequence }, enabled = debug && !busy) {
                Text(template.name)
            }
        }
        val parsedSteps = LabExperimentLibrary.parse(steps)
        parsedSteps?.forEachIndexed { index, step ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Paso " + (index + 1) + ": " +
                    (if (step.high) "HIGH" else "LOW") + " · " + step.durationMs + " ms",
                    modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = {
                    steps = LabExperimentLibrary.removeAt(steps, index) ?: steps
                }, enabled = debug && !busy) { Text("Quitar") }
            }
        }
        Text("Añadir paso (máximo 16; duración total 20 s)",
            style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = nextHigh, onClick = { nextHigh = true },
                label = { Text("HIGH") }, enabled = debug && !busy)
            FilterChip(selected = !nextHigh, onClick = { nextHigh = false },
                label = { Text("LOW") }, enabled = debug && !busy)
        }
        OutlinedTextField(value = nextDuration,
            onValueChange = { nextDuration = it.filter(Char::isDigit).take(4) },
            label = { Text("Duración en ms (100–2000)") },
            singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = debug && !busy)
        val nextStep = LabStep(nextHigh, nextDuration.toIntOrNull() ?: -1)
        val appended = LabExperimentLibrary.append(steps, nextStep)
        Button(onClick = { if (appended != null) steps = appended },
            enabled = debug && !busy && appended != null) { Text("Agregar paso") }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { action {
                preferences.saveExperimentDraft(steps)
                message = "Borrador guardado en este teléfono (sin credenciales)."
            } }, enabled = debug && !busy && parsedSteps != null) {
                Text("Guardar")
            }
            OutlinedButton(onClick = { action {
                val saved = preferences.experimentDraft.first()
                if (saved == null) message = "No hay borrador guardado."
                else {
                    steps = saved
                    message = "Borrador local cargado."
                }
            } }, enabled = debug && !busy) { Text("Cargar") }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = steps, onValueChange = { steps = it.take(120) },
            label = { Text("Instrucciones (máximo 16 pasos)") },
            modifier = Modifier.fillMaxWidth(), singleLine = false)
        Spacer(Modifier.height(8.dp))
        Button(enabled = debug && !busy && LocalLabClient.validKey(secret) &&
            LocalLabClient.validSequence(steps),
            onClick = { action {
                client.sequence(secret, 19, steps)
                message = "Secuencia declarativa iniciada en GPIO19."
            } }) { Text("Ejecutar secuencia") }
        Spacer(Modifier.height(20.dp))
        Text("LED RGB por Wi-Fi", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = debug && !busy && LocalLabClient.validKey(secret),
                onClick = { action { client.rgb(secret, "ON"); message = "RGB encendido." } }) {
                Text("ON")
            }
            OutlinedButton(enabled = debug && !busy && LocalLabClient.validKey(secret),
                onClick = { action { client.rgb(secret, "OFF"); message = "RGB apagado." } }) {
                Text("OFF")
            }
            OutlinedButton(enabled = debug && !busy && LocalLabClient.validKey(secret),
                onClick = { action {
                    client.rgb(secret, "FX,RAINBOW,120")
                    message = "Efecto arcoíris activado."
                } }) { Text("Arcoíris") }
        }
        Spacer(Modifier.height(16.dp))
        if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        Text(message, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(30.dp))
    }
}
