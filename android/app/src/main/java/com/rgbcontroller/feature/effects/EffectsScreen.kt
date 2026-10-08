package com.rgbcontroller.feature.effects

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rgbcontroller.core.ble.BleConnectionState
import com.rgbcontroller.core.ble.BleManager

@Composable
fun EffectsScreen(bleManager: BleManager) {
    val connection by bleManager.connectionState.collectAsState()
    val state by bleManager.rgbState.collectAsState()
    val ready = connection == BleConnectionState.Ready
    var speed by remember { mutableFloatStateOf(120f) }
    LaunchedEffect(state?.speed) {
        state?.let { speed = it.speed.toFloat() }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Efectos de iluminación", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            if (ready) "Efecto actual: ${state?.effect ?: "NONE"}"
            else "Conecta el ESP32 para controlar efectos"
        )
        Spacer(Modifier.height(16.dp))
        Text("Intervalo de velocidad: ${speed.toInt()} ms")
        Slider(
            value = speed, onValueChange = { speed = it },
            valueRange = 10f..500f, enabled = ready
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { bleManager.writeCommand("FX,RAINBOW,${speed.toInt()}") },
            enabled = ready, modifier = Modifier.fillMaxWidth()
        ) { Text("Arcoíris") }
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = { bleManager.writeCommand("FX,BREATHE,${speed.toInt()}") },
            enabled = ready, modifier = Modifier.fillMaxWidth()
        ) { Text("Respiración") }
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = { bleManager.writeCommand("FX,FADE,${speed.toInt()}") },
            enabled = ready, modifier = Modifier.fillMaxWidth()
        ) { Text("Fundido RGB") }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = { bleManager.writeCommand("FX,STOP") },
            enabled = ready, modifier = Modifier.fillMaxWidth()
        ) { Text("Detener efecto") }
        Spacer(Modifier.height(12.dp))
        Text(
            "Las animaciones se ejecutan dentro del ESP32 y continúan sin la app.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}
