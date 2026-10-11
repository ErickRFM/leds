package com.rgbcontroller.feature.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rgbcontroller.core.ui.SectionHeader

@Composable
fun DashboardScreen(
    onLed: () -> Unit,
    onCode: () -> Unit,
    onCenter: () -> Unit
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
        .padding(horizontal = 18.dp, vertical = 20.dp)) {
        SectionHeader("ESP32 Control Center", "Una plataforma modular para controlar, crear y probar")
        Spacer(Modifier.height(20.dp))
        FeatureCard("LED RGB", "Color, brillo, favoritos y efectos. Cambia entre Bluetooth y Wi-Fi desde el módulo.",
            "Abrir módulo LED", onLed)
        Spacer(Modifier.height(12.dp))
        FeatureCard("Código", "Importa, pega, copia y exporta TXT/INO; conserva el flujo de firmware seguro.",
            "Abrir editor", onCode)
        Spacer(Modifier.height(12.dp))
        FeatureCard("Centro ESP32", "Equipos, conectividad, diagnóstico, GPIO, sensores y experimentos.",
            "Ir al centro", onCenter)
        Spacer(Modifier.height(12.dp))
        Text("La conexión Wi-Fi es de laboratorio en APK debug; el flasheo desde Android aún no está habilitado.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun CenterLandingScreen(onDevices: () -> Unit, onLab: () -> Unit, onCode: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
        .padding(horizontal = 18.dp, vertical = 20.dp)) {
        SectionHeader("Centro", "El resto de herramientas vive aquí, no dentro del módulo LED")
        Spacer(Modifier.height(18.dp))
        FeatureCard("Dispositivos", "Busca y conecta por BLE. El modo Wi-Fi se selecciona en LED.",
            "Administrar dispositivos", onDevices)
        Spacer(Modifier.height(12.dp))
        FeatureCard("Hardware y laboratorio", "Estado, red, pines seguros, ADC1, diagnóstico y secuencias.",
            "Abrir laboratorio", onLab)
        Spacer(Modifier.height(12.dp))
        FeatureCard("Proyectos de código", "Importa, pega, copia y exporta tus fuentes; el flasheo seguro está pendiente.",
            "Abrir código", onCode)
    }
}

@Composable
private fun FeatureCard(
    title: String,
    summary: String,
    action: String,
    onClick: () -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(6.dp))
            Text(summary, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
            Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(action) }
        }
    }
}
