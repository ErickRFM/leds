package com.rgbcontroller.core.components.telemetry

/** How a single history sample must be read on a chart. */
enum class SampleQuality { VALID, INVALID, STALE }

/** A sample plus how to read it ([quality]) and its position in the window. */
data class ClassifiedSample(
    val sample: TelemetrySample,
    val quality: SampleQuality,
    val fractionX: Float
)

/**
 * A plotted point: [fractionX] is the position inside the time window (0..1,
 * 0 = window start, 1 = now) and [fractionY] the position inside the value range
 * (0..1). Only firmware-validated, non-stale samples ever become points.
 */
data class ChartPoint(
    val recordedAtMs: Long,
    val value: Float,
    val fractionX: Float,
    val fractionY: Float
)

/**
 * Everything a chart needs, derived purely from history. Invalid, stale and
 * disconnected readings are surfaced explicitly instead of being drawn as if
 * they were live numbers.
 */
data class TelemetryChartGeometry(
    val componentId: String,
    val measurementType: String,
    val unit: String,
    val segments: List<List<ChartPoint>>,
    val classified: List<ClassifiedSample>,
    val minValue: Float,
    val maxValue: Float,
    val validCount: Int,
    val invalidCount: Int,
    val staleCount: Int,
    val disconnected: Boolean,
    val visibleDurationMs: Long = 0L,
    val requestedWindowMs: Long = 0L
) {
    val points: List<ChartPoint> get() = segments.flatten()
    val hasData: Boolean get() = segments.any { it.isNotEmpty() }
    val hasGap: Boolean get() = segments.size > 1
    val latestValid: ChartPoint? get() = points.lastOrNull()
}

/**
 * Pure chart geometry. Kept free of Android so the "valid / invalid / old /
 * disconnected" rules are unit-tested rather than trusted to a Canvas.
 */
object TelemetryChart {
    const val DEFAULT_WINDOW_MS = 30 * 60 * 1000L
    const val DEFAULT_STALE_AFTER_MS = 5 * 60 * 1000L

    fun classify(sample: TelemetrySample, nowMs: Long, staleAfterMs: Long): SampleQuality = when {
        sample.status == "stale" -> SampleQuality.STALE
        nowMs - sample.recordedAtMs > staleAfterMs -> SampleQuality.STALE
        sample.isPlottable -> SampleQuality.VALID
        else -> SampleQuality.INVALID
    }

    fun build(
        componentId: String,
        measurementType: String,
        samples: List<TelemetrySample>,
        nowMs: Long,
        windowMs: Long = DEFAULT_WINDOW_MS,
        staleAfterMs: Long = DEFAULT_STALE_AFTER_MS,
        disconnected: Boolean = false
    ): TelemetryChartGeometry {
        val from = nowMs - windowMs
        val inWindow = samples
            .filter { it.recordedAtMs in from..nowMs }
            .sortedBy { it.recordedAtMs }
        // Zoom to actual available history until the selected window fills up.
        // The original selected duration remains visible in the chart caption.
        val earliest = inWindow.minOfOrNull { it.recordedAtMs } ?: from
        val observedMs = (nowMs - earliest).coerceAtLeast(0L)
        val paddingMs = (observedMs / 15L).coerceAtLeast(10_000L)
        val viewportFrom = if (inWindow.isEmpty()) from else maxOf(from, earliest - paddingMs)
        val spanX = (nowMs - viewportFrom).toFloat()
        fun fractionXFor(recordedAtMs: Long): Float =
            if (spanX > 0f) ((recordedAtMs - viewportFrom).toFloat() / spanX).coerceIn(0f, 1f) else 0f
        val classified = inWindow.map {
            ClassifiedSample(it, classify(it, nowMs, staleAfterMs), fractionXFor(it.recordedAtMs))
        }
        val values = classified
            .filter { it.quality == SampleQuality.VALID }
            .mapNotNull { it.sample.value }
        val minV = values.minOrNull() ?: 0f
        val maxV = values.maxOrNull() ?: 1f
        // Keep small DHT fluctuations from becoming a misleading full-height spike.
        val minimumSpan = when (measurementType) {
            "temperature" -> 2f
            "humidity" -> 5f
            else -> 1f
        }
        val displaySpan = maxOf(maxV - minV, minimumSpan)
        val displayMin = (minV + maxV) / 2f - displaySpan / 2f

        val segments = ArrayList<List<ChartPoint>>()
        var current = ArrayList<ChartPoint>()
        var previousIndex = -2
        classified.forEachIndexed { index, cs ->
            if (cs.quality != SampleQuality.VALID) return@forEachIndexed
            val value = cs.sample.value ?: return@forEachIndexed
            if (index - previousIndex > 1 && current.isNotEmpty()) {
                segments += current
                current = ArrayList()
            }
            current += ChartPoint(
                recordedAtMs = cs.sample.recordedAtMs,
                value = value,
                fractionX = cs.fractionX,
                fractionY = if (displaySpan > 0f) ((value - displayMin) / displaySpan).coerceIn(0f, 1f) else 0.5f
            )
            previousIndex = index
        }
        if (current.isNotEmpty()) segments += current

        return TelemetryChartGeometry(
            componentId = componentId,
            measurementType = measurementType,
            unit = inWindow.lastOrNull()?.unit ?: "",
            segments = segments,
            classified = classified,
            minValue = minV,
            maxValue = maxV,
            validCount = classified.count { it.quality == SampleQuality.VALID },
            invalidCount = classified.count { it.quality == SampleQuality.INVALID },
            staleCount = classified.count { it.quality == SampleQuality.STALE },
            disconnected = disconnected,
            visibleDurationMs = (nowMs - viewportFrom).coerceAtLeast(0L),
            requestedWindowMs = windowMs
        )
    }
}
