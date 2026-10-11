package com.rgbcontroller.feature.hub

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.provider.Settings
import android.os.SystemClock
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.rgbcontroller.core.data.PreferencesRepository
import com.rgbcontroller.core.lab.LabExperimentLibrary
import com.rgbcontroller.core.model.RgbDeviceState
import com.rgbcontroller.core.sensors.AdcSample
import com.rgbcontroller.core.sensors.AdcTelemetry
import com.rgbcontroller.core.hardware.Esp32BoardPinout
import com.rgbcontroller.core.hardware.PinKind
import com.rgbcontroller.core.lab.LabStep
import com.rgbcontroller.core.ui.SectionHeader
import com.rgbcontroller.core.wifi.LocalLabClient
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun LabHubScreen() {
    val context = LocalContext.current
    val debug = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    val client = remember { LocalLabClient() }
    val preferences = remember(context) { PreferencesRepository(context.applicationContext) }
    val savedProjects by preferences.experimentProjects.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var secret by remember { mutableStateOf("") } // Never persist credentials
    var status by remember { mutableStateOf<JSONObject?>(null) }
    var firmware by remember { mutableStateOf<JSONObject?>(null) }
    var diagnostics by remember { mutableStateOf<JSONObject?>(null) }
    var networks by remember { mutableStateOf<JSONArray?>(null) }
    var ssid by remember { mutableStateOf("") }
    var wifiPassword by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("Conéctate a ESP32-Control-Lab para comenzar.") }
    var busy by remember { mutableStateOf(false) }
    var selectedPin by remember { mutableIntStateOf(18) }
    var adcValue by remember { mutableStateOf<Int?>(null) }
    var adcHistory by remember { mutableStateOf(emptyList<AdcSample>()) }
    var samplingAdc by remember { mutableStateOf(false) }
    var steps by remember { mutableStateOf("1:500,0:500,1:500,0:500") }
    var wifiRed by remember { mutableFloatStateOf(255f) }
    var wifiGreen by remember { mutableFloatStateOf(120f) }
    var wifiBlue by remember { mutableFloatStateOf(0f) }
    var wifiBrightness by remember { mutableFloatStateOf(255f) }
    var nextHigh by remember { mutableStateOf(true) }
    var nextDuration by remember { mutableStateOf("500") }
    var projectName by remember { mutableStateOf("") }

    // Sampling belongs to this Composable lifecycle; navigation away cancels the job.
    // No GPS, no background process, no unbounded retention and no device secrets in CSV.
    LaunchedEffect(samplingAdc) {
        if (!samplingAdc) return@LaunchedEffect
        val started = SystemClock.elapsedRealtime()
        try {
            for (i in 0 until 40) {
                val elapsed = SystemClock.elapsedRealtime() - started
                if (elapsed >= 20_000L) break
                val reading = client.readAnalog(secret, 34).optInt("raw", -1)
                adcHistory = AdcTelemetry.append(adcHistory, AdcSample(elapsed, reading))
                adcValue = reading
                delay(500L)
            }
            message = "Muestreo ADC1 finalizado. Valores brutos sin calibrar."
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            message = error.message ?: "Muestreo ADC1 interrumpido."
        } finally {
            samplingAdc = false
        }
    }

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
                        val fresh = client.status(secret)
                        status = fresh
                        RgbDeviceState.parse(fresh.optString("rgb"))?.let { actual ->
                            wifiRed = actual.red.toFloat()
                            wifiGreen = actual.green.toFloat()
                            wifiBlue = actual.blue.toFloat()
                            wifiBrightness = actual.brightness.toFloat()
                        }
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
        Text("Diagnóstico de hardware y recursos", style = MaterialTheme.typography.titleMedium)
        Text("Datos del dispositivo; sin direcciones MAC ni contraseñas.",
            style = MaterialTheme.typography.bodySmall)
        OutlinedButton(enabled = debug && !busy && LocalLabClient.validKey(secret),
            onClick = { action {
                diagnostics = client.diagnostics(secret)
                message = "Diagnóstico actualizado desde el ESP32."
            } }) { Text("Consultar diagnóstico") }
        diagnostics?.let { info ->
            Text("Chip: " + info.optString("chip") + " · rev " + info.optInt("chipRevision"))
            Text("Flash: " + info.optLong("flashBytes") + " bytes")
            Text("RAM libre: " + info.optLong("freeHeap") + " bytes")
            Text("RAM mínima observada: " + info.optLong("minFreeHeap") + " bytes")
            Text("Reinicio (código): " + info.optInt("resetReason"))
            Text("Wi-Fi RSSI: " + info.optInt("wifiRssi") + " dBm (0 = sin conexión)")
            Text("GPIO reservados: " + info.optInt("leasedPins"))
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
        Text("Solo GPIO 18, 19, 21, 22, 23 permiten salidas temporales de laboratorio. " +
            "RGB 27/25/26 está reservado.", style = MaterialTheme.typography.bodySmall)
        listOf(listOf(18, 19, 21), listOf(22, 23)).forEach { rowPins ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowPins.forEach { pin ->
                    FilterChip(selected = selectedPin == pin, onClick = { selectedPin = pin },
                        label = { Text("GPIO " + pin) })
                }
            }
        }
        Text("Mapa de pines · ESP32 clásico verificado", style = MaterialTheme.typography.titleSmall)
        Text("Inventario informativo; no demuestra cableado físico ni reserva en vivo.",
            style = MaterialTheme.typography.bodySmall)
        Esp32BoardPinout.pins.forEach { pin ->
            val label = when (pin.kind) {
                PinKind.LAB_OUTPUT -> "Lab"
                PinKind.RGB_RESERVED -> "RGB reservado"
                PinKind.ADC1_INPUT -> "ADC1"
                PinKind.SYSTEM_RESERVED -> "Protegido"
            }
            Text("GPIO " + pin.number + " · " + label + " · " + pin.note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        Spacer(Modifier.height(10.dp))
        Text("Telemetría ADC1 (máximo 40 muestras / 20 segundos)",
            style = MaterialTheme.typography.titleSmall)
        Text("Lecturas RAW 12 bits; no son voltios calibrados ni un osciloscopio.",
            style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                adcHistory = emptyList()
                samplingAdc = true
            }, enabled = debug && !busy && !samplingAdc && LocalLabClient.validKey(secret)) {
                Text("Iniciar")
            }
            OutlinedButton(onClick = { samplingAdc = false },
                enabled = samplingAdc) { Text("Detener") }
        }
        if (samplingAdc) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        if (adcHistory.isNotEmpty()) {
            val minValue = adcHistory.minOf { it.raw }
            val maxValue = adcHistory.maxOf { it.raw }
            Text("Muestras: " + adcHistory.size + " · min: " + minValue + " · max: " + maxValue,
                style = MaterialTheme.typography.bodySmall)
            val graphColor = MaterialTheme.colorScheme.primary
            Canvas(modifier = Modifier.fillMaxWidth().height(150.dp)) {
                val samples = adcHistory
                if (samples.size >= 2) {
                    for (i in 1 until samples.size) {
                        val x1 = size.width * (i - 1).toFloat() / (samples.size - 1)
                        val x2 = size.width * i.toFloat() / (samples.size - 1)
                        val y1 = size.height * (1f - samples[i - 1].raw / 4095f)
                        val y2 = size.height * (1f - samples[i].raw / 4095f)
                        drawLine(graphColor, Offset(x1, y1), Offset(x2, y2),
                            strokeWidth = 2.dp.toPx())
                    }
                }
            }
            OutlinedButton(onClick = {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/csv"
                    putExtra(Intent.EXTRA_TEXT, AdcTelemetry.csv(adcHistory))
                }
                context.startActivity(Intent.createChooser(send, "Compartir datos ADC1"))
            }, enabled = !samplingAdc) { Text("Exportar CSV") }
        }
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
        Spacer(Modifier.height(12.dp))
        Text("Proyectos locales · máximo 8", style = MaterialTheme.typography.titleSmall)
        OutlinedTextField(value = projectName,
            onValueChange = { projectName = it.take(24).filterNot(Char::isISOControl) },
            label = { Text("Nombre del proyecto") },
            singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = debug && !busy)
        Button(onClick = { action {
            preferences.saveExperimentProject(projectName, steps)
            message = "Proyecto guardado localmente; sin credenciales de dispositivo."
        } }, enabled = debug && !busy && projectName.isNotBlank() && parsedSteps != null) {
            Text("Guardar proyecto")
        }
        savedProjects.forEach { project ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(project.name, style = MaterialTheme.typography.titleSmall)
                    Text(project.sequence, style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = {
                            steps = project.sequence
                            projectName = project.name
                            message = "Proyecto cargado para editar o ejecutar."
                        }, enabled = debug && !busy) { Text("Abrir") }
                        TextButton(onClick = { action {
                            preferences.deleteExperimentProject(project.name)
                            message = "Proyecto eliminado del teléfono."
                        } }, enabled = debug && !busy) { Text("Eliminar") }
                    }
                }
            }
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
        Text("Mezcla de color por Wi-Fi", style = MaterialTheme.typography.titleSmall)
        Text("Valores confirmados en el dispositivo al consultar su estado.",
            style = MaterialTheme.typography.bodySmall)
        Text("Rojo " + wifiRed.toInt())
        Slider(value = wifiRed, onValueChange = { wifiRed = it },
            valueRange = 0f..255f, enabled = debug && !busy)
        Text("Verde " + wifiGreen.toInt())
        Slider(value = wifiGreen, onValueChange = { wifiGreen = it },
            valueRange = 0f..255f, enabled = debug && !busy)
        Text("Azul " + wifiBlue.toInt())
        Slider(value = wifiBlue, onValueChange = { wifiBlue = it },
            valueRange = 0f..255f, enabled = debug && !busy)
        Text("Brillo " + wifiBrightness.toInt())
        Slider(value = wifiBrightness, onValueChange = { wifiBrightness = it },
            valueRange = 0f..255f, enabled = debug && !busy)
        Button(enabled = debug && !busy && LocalLabClient.validKey(secret),
            onClick = { action {
                client.setRgb(secret, wifiRed.toInt(), wifiGreen.toInt(),
                    wifiBlue.toInt(), wifiBrightness.toInt())
                status = client.status(secret)
                message = "Color aplicado y estado confirmado por Wi-Fi."
            } }) { Text("Aplicar color RGB por Wi-Fi") }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(enabled = debug && !busy && LocalLabClient.validKey(secret),
            onClick = { action {
                client.startRgbEffect(secret, "BREATHE", 120)
                status = client.status(secret)
                message = "Efecto respiración confirmado por Wi-Fi."
            } }) { Text("Respiración por Wi-Fi") }
        Spacer(Modifier.height(16.dp))
        if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        Text(message, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(30.dp))
    }
}
