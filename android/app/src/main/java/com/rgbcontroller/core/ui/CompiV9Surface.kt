package com.rgbcontroller.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Compi V9 design tokens derived from the four approved interface references. */
object CompiV9 {
    val background = Color(0xFF0C0E15)
    val surface = Color(0xFF1A1E2B)
    val surfaceRaised = Color(0xFF222638)
    val primary = Color(0xFFAE94FF)
    val border = Color(0xFF383C54)
    val success = Color(0xFF27D987)
    val title = Color(0xFFF7F5FF)
    val muted = Color(0xFFB1B5CF)
}

@Composable
fun CompiV9Panel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, CompiV9.border),
        colors = CardDefaults.cardColors(containerColor = CompiV9.surface)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}

@Composable
fun CompiV9SectionTitle(title: String, modifier: Modifier = Modifier) {
    Text(
        title,
        modifier = modifier,
        color = CompiV9.title,
        style = MaterialTheme.typography.titleLarge
    )
}

@Composable
fun CompiV9Segment(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val border = if (selected) CompiV9.primary else CompiV9.border
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 52.dp),
        shape = RoundedCornerShape(16.dp),
        color = if (selected) CompiV9.primary.copy(alpha = 0.19f) else CompiV9.surface,
        border = BorderStroke(if (selected) 2.dp else 1.dp, border)
    ) {
        Box(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(text, color = if (selected) CompiV9.title else CompiV9.muted,
                style = MaterialTheme.typography.labelLarge)
        }
    }
}
