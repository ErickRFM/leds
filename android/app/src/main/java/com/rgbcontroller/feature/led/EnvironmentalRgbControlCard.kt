package com.rgbcontroller.feature.led

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.rgbcontroller.core.wifi.CompiLabSession
import com.rgbcontroller.core.wifi.LabConnectionState
import com.rgbcontroller.core.wifi.LabTransport
import com.rgbcontroller.core.wifi.LocalLabClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Acknowledged firmware mode, not a local animation or a fake RGB timer. */
@Composable
fun EnvironmentalRgbControlCard(session: CompiLabSession, client: LocalLabClient) {
    val scope = rememberCoroutineScope()
    val authorized = session.transport == LabTransport.WIFI_LAB &&
        session.connectionState == LabConnectionState.AUTHORIZED && session.wifiReady
    var enabled by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("Consulta el estado ambiental por Wi-Fi.") }

    LaunchedEffect(authorized, session.revision) {
        if (!authorized) {
            enabled = false
            message = "Para automatizar el ESP32, conéctate al Wi-Fi local autorizado."
            return@LaunchedEffect
        }
        // Read back mode during this visible screen's lifetime: a manual BLE or
        // Wi-Fi command can legitimately cancel automation behind this UI.
        while (true) {
            try {
                if (!busy) {
                    val version = session.revision
                    val key = session.key
                    val result = session.serialized { client.environmentalRgbStatus(key) }
                    if (authorized && session.revision == version && !busy) {
                        enabled = result.optBoolean("enabled")
                        message = if (enabled)
                            "Modo habilitado. Solo cambia colores con temperatura válida."
                        else "Modo manual; puedes activar iluminación ambiental."
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                enabled = false
                message = "No se confirmó el modo ambiental: ${failure.message}"
            }
            delay(3_500L)
        }
    }

    Card(Modifier.fillMaxWidth().testTag("environmental_rgb_control")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("RGB ambiental", style = MaterialTheme.typography.titleMedium)
            Text("El firmware cambia el color según temperatura y el brillo según " +
                "la luz LDR calibrada. Si el DHT11 falla, suspende cambios; " +
                "si falta luz válida, usa un brillo limitado.",
                style = MaterialTheme.typography.bodySmall)
            Text(message, style = MaterialTheme.typography.bodySmall)
            Button(
                enabled = authorized && !busy,
                modifier = Modifier.fillMaxWidth().testTag("environmental_rgb_toggle"),
                onClick = {
                    scope.launch {
                        busy = true
                        try {
                            check(authorized) { "Se perdió la conexión Wi-Fi." }
                            val version = session.revision
                            val key = session.key
                            session.serialized {
                                if (enabled) client.stopEnvironmentalRgb(key)
                                else client.startEnvironmentalRgb(key)
                            }
                            check(authorized && session.revision == version) {
                                "La sesión cambió; vuelve a consultar el estado."
                            }
                            enabled = !enabled
                            message = if (enabled) "Automatización habilitada en ESP32 (solo RAM)."
                                else "Automatización detenida. El LED conserva su último color."
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (failure: Exception) {
                            message = "No se confirmó el cambio: ${failure.message}"
                        } finally {
                            busy = false
                        }
                    }
                }
            ) { Text(if (enabled) "Detener modo ambiental" else "Activar modo ambiental") }
        }
    }
}
