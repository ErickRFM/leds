package com.rgbcontroller.crane

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rgbcontroller.core.ui.CompiTokens

/** Nothing tappable in this console is smaller than the platform touch target. */
internal val CraneTouchTarget = 48.dp

/** The two thumb-first command bar actions get extra height over the rest. */
internal val CraneCommandTouchTarget = 52.dp

/** Semantic states of the console, resolved through the active color scheme. */
enum class CraneTone { NEUTRAL, LIVE, WARNING, BLOCKED }

@Composable
private fun CraneTone.surfaceColors(): Pair<Color, Color> = when (this) {
    CraneTone.NEUTRAL -> MaterialTheme.colorScheme.surfaceVariant to
        MaterialTheme.colorScheme.onSurfaceVariant
    CraneTone.LIVE -> MaterialTheme.colorScheme.primaryContainer to
        MaterialTheme.colorScheme.onPrimaryContainer
    // Not tertiaryContainer: in this scheme tertiary is the green accent, so a
    // warning chip would read as "everything is fine".
    CraneTone.WARNING -> CompiTokens.Colors.WarningAmberContainer to
        CompiTokens.Colors.WarningAmber
    CraneTone.BLOCKED -> MaterialTheme.colorScheme.errorContainer to
        MaterialTheme.colorScheme.onErrorContainer
}

/**
 * The only card the crane console uses: one shape, one border, one inner
 * rhythm. Every section is built from it so the screen reads as one system
 * instead of a stack of differently styled boxes.
 */
@Composable
fun CraneCard(
    title: String,
    tag: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth().testTag(tag),
        shape = CompiTokens.Shapes.Medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = CompiTokens.Elevations.flat)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(CompiTokens.Spacing.m),
            verticalArrangement = Arrangement.spacedBy(CompiTokens.Spacing.s)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CompiTokens.Spacing.xs)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(CompiTokens.Spacing.xxs)
                ) {
                    Text(
                        text = title,
                        style = CompiTokens.Typography.SectionHeader,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = CompiTokens.Typography.MetricCaption,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                trailing?.invoke()
            }
            content()
        }
    }
}

@Composable
fun CraneStateChip(
    text: String,
    tone: CraneTone,
    tag: String,
    modifier: Modifier = Modifier
) {
    val (container, onContainer) = tone.surfaceColors()
    Surface(
        // Merging keeps tag and label on one node: a state chip is read, and
        // asserted, as "crane_badge_session = BLE sin sesión", never as a tag
        // whose text lives in an untagged child.
        modifier = modifier.testTag(tag).semantics(mergeDescendants = true) {},
        shape = CompiTokens.Shapes.Small,
        color = container,
        contentColor = onContainer
    ) {
        Text(
            text = text,
            style = CompiTokens.Typography.BadgeLabel,
            modifier = Modifier.padding(
                horizontal = CompiTokens.Spacing.xs,
                vertical = CompiTokens.Spacing.xxs
            )
        )
    }
}

/** Chip row that keeps the console's single touch-target rule: never under 48 dp. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CraneChipRow(content: @Composable () -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(CompiTokens.Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(CompiTokens.Spacing.xs)
    ) { content() }
}

@Composable
fun CraneChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    tag: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        shape = CompiTokens.Shapes.Small,
        colors = FilterChipDefaults.filterChipColors(
            disabledLabelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        ),
        modifier = modifier.heightIn(min = CraneTouchTarget).testTag(tag),
        label = { Text(label, style = CompiTokens.Typography.BadgeLabel) }
    )
}

/** A status area a screen reader must announce when the crane answers. */
@Composable
fun CraneLiveRegion(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(CompiTokens.Spacing.xxs)
    ) { content() }
}

@Composable
fun CraneCaption(
    text: String,
    tag: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        style = CompiTokens.Typography.BodyClean,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.testTag(tag)
    )
}

@Composable
fun CraneBlockerLine(
    text: String,
    tag: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        style = CompiTokens.Typography.BodyClean,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.error,
        modifier = modifier.testTag(tag)
    )
}
