package com.rgbcontroller.core.components.widgets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rgbcontroller.core.ui.CompiTokens
import java.util.Locale

/**
 * The one reading block every sensor card shares: a wide-tracked caption, the
 * number as the only loud element and the unit kept quiet beside it. The unit is
 * a separate text so TalkBack reads "Temperatura, 21, grados" instead of one
 * run-on label. [value] is pre-formatted because each caller decides its own
 * placeholder ("—") and precision.
 */
@Composable
internal fun MetricReading(
    label: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(CompiTokens.Spacing.xxs)) {
        Text(
            label.uppercase(Locale.getDefault()),
            style = CompiTokens.Typography.MetricCaption,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            // The em-dash is the app-wide "no valid reading" token. At metric
            // weight it otherwise reads as a minus sign, so it stays quiet.
            Text(
                value,
                style = CompiTokens.Typography.MetricValue,
                color = if (value == PLACEHOLDER) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface
            )
            Text(
                unit,
                style = CompiTokens.Typography.MetricUnit,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private const val PLACEHOLDER = "—"
