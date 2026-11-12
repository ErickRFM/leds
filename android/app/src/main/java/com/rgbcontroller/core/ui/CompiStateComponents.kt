package com.rgbcontroller.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Standard Status Badge (En vivo, Confirmado, Obsoleto, Desconectado).
 * Colors derive from the active colorScheme so light and dark themes both keep
 * AA contrast, and state changes are announced politely to TalkBack.
 */
@Composable
fun CompiBadge(
    label: String,
    type: CompiBadgeType,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val (bg, fg, icon) = when (type) {
        CompiBadgeType.LIVE -> Triple(
            scheme.primaryContainer, scheme.onPrimaryContainer, Icons.Default.CheckCircle)
        CompiBadgeType.CONFIRMED -> Triple(
            scheme.secondaryContainer, scheme.onSecondaryContainer, Icons.Default.CheckCircle)
        CompiBadgeType.STALE -> Triple(
            scheme.tertiaryContainer, scheme.onTertiaryContainer, Icons.Default.Sync)
        CompiBadgeType.OFFLINE -> Triple(
            scheme.errorContainer, scheme.onErrorContainer, Icons.Default.CloudOff)
    }

    Surface(
        color = bg,
        shape = RoundedCornerShape(10.dp),
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite }
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

