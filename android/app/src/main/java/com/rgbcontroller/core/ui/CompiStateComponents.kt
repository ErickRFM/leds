package com.rgbcontroller.core.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SignalWifiBad
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Haptics helper for Android touch feedback.
 */
object CompiHaptics {
    @Composable
    fun performClick() {
        val view = LocalView.current
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    @Composable
    fun performSuccess() {
        val view = LocalView.current
        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
    }
}

/**
 * Standard Status Badge (En vivo, Confirmado, Obsoleto, Desconectado)
 */
@Composable
fun CompiBadge(
    label: String,
    type: CompiBadgeType,
    modifier: Modifier = Modifier
) {
    val (bg, fg, icon) = when (type) {
        CompiBadgeType.LIVE -> Triple(Color(0xFF133B29), Color(0xFF27D987), Icons.Default.CheckCircle)
        CompiBadgeType.CONFIRMED -> Triple(Color(0xFF232C42), Color(0xFFA88BFF), Icons.Default.CheckCircle)
        CompiBadgeType.STALE -> Triple(Color(0xFF3B2D12), Color(0xFFFFB84D), Icons.Default.Sync)
        CompiBadgeType.OFFLINE -> Triple(Color(0xFF2A1C1D), Color(0xFFFF5252), Icons.Default.CloudOff)
    }

    Surface(
        color = bg,
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(fg)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp
                ),
                color = fg
            )
        }
    }
}

enum class CompiBadgeType { LIVE, CONFIRMED, STALE, OFFLINE }

/**
 * Reusable Loading State Component
 */
@Composable
fun CompiLoadingState(
    message: String = "Cargando datos...",
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "pulse_loading")
    val alpha by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CompiTokens.Colors.SurfaceCard)
    ) {
        Row(
            modifier = Modifier
                .padding(20.dp)
                .graphicsLayer { this.alpha = alpha },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Sync,
                contentDescription = null,
                tint = CompiTokens.Colors.PrimaryViolet,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFC7D2FE)
            )
        }
    }
}

/**
 * Reusable Error Card Component
 */
@Composable
fun CompiErrorState(
    title: String = "Error de comunicación",
    description: String,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF261517))
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = CompiTokens.Colors.ErrorRed,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFFD1D1)
                )
            }
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFFFA8A8)
            )
            if (onRetry != null) {
                OutlinedButton(
                    onClick = onRetry,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Reintentar")
                }
            }
        }
    }
}

/**
 * Reusable Offline Banner
 */
@Composable
fun CompiOfflineBanner(
    message: String = "ESP32 sin conexión en vivo. Se muestra la última confirmación.",
    onConnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1F2536))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.SignalWifiBad,
                contentDescription = null,
                tint = CompiTokens.Colors.WarningAmber,
                modifier = Modifier.size(22.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Modo sin conexión",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFE2E8F0)
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF94A3B8)
                )
            }
            Button(onClick = onConnect) {
                Text("Conectar", fontSize = 12.sp)
            }
        }
    }
}
