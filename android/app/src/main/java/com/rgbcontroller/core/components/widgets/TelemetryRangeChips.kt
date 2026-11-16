package com.rgbcontroller.core.components.widgets

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.rgbcontroller.core.components.telemetry.TelemetryChart

/**
 * The one range selector every chart surface uses, so the hardware lab and the
 * module workspace can never drift into promising different histories. Scrolls
 * horizontally because five ranges do not fit a narrow phone screen.
 */
@Composable
fun TelemetryRangeChips(
    selectedMs: Long,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier,
    tagPrefix: String = "telemetry_window"
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TelemetryChart.SUPPORTED_WINDOWS_MS.forEach { windowMs ->
            FilterChip(
                selected = selectedMs == windowMs,
                onClick = { onSelect(windowMs) },
                label = { Text(rangeChipLabel(windowMs)) },
                modifier = Modifier.testTag("${tagPrefix}_$windowMs")
            )
        }
    }
}

internal fun rangeChipLabel(windowMs: Long): String =
    if (windowMs >= 60 * 60_000L) "${windowMs / (60 * 60_000L)} h"
    else "${windowMs / 60_000L} min"
