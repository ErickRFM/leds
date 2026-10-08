package com.rgbcontroller.feature.effects

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rgbcontroller.core.ble.BleManager

@Composable
fun EffectsScreen(bleManager: BleManager) {
    var speed by remember { mutableStateOf(100f) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Efectos de Iluminación", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))

        Text("Velocidad del Efecto: ${speed.toInt()} ms")
        Slider(
            value = speed,
            onValueChange = { speed = it },
            valueRange = 10f..500f
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = { bleManager.writeCommand("FX,RAINBOW,${speed.toInt()}") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Efecto Arcoíris")
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = { bleManager.writeCommand("FX,BREATHE,${speed.toInt()}") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Efecto Respiración")
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = { bleManager.writeCommand("FX,FADE,${speed.toInt()}") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Efecto Fade RGB")
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = { bleManager.writeCommand("FX,STOP") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Detener Efecto")
        }
    }
}
