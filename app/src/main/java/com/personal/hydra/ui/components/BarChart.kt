package com.personal.hydra.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.personal.hydra.domain.HourBucket

/** One slot of the day chart. [hasRecord] false = that calendar day has no data at all. */
data class BarValue(val fraction: Float, val completed: Boolean, val hasRecord: Boolean = true)

/**
 * Lightweight bar chart drawn with Canvas — no external dependency.
 *
 * Interaction: tap a bar to anchor the start of a range and tap another to
 * close it, or drag across the chart to pick the whole span in one gesture.
 * [anchor] is the pending start — drawn immediately, so the very first tap
 * gives feedback instead of appearing to do nothing.
 */
@Composable
fun BarChart(
    bars: List<BarValue>,
    modifier: Modifier = Modifier,
    selection: IntRange? = null,
    anchor: Int? = null,
    onBarClick: ((Int) -> Unit)? = null,
    onDragRange: ((Int, Int) -> Unit)? = null,
    /** Optional 0..1 overlay aligned with [bars]; nulls leave a gap in the line. */
    line: List<Float?>? = null,
    completedColor: Color = MaterialTheme.colorScheme.primary,
    incompleteColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    // Distinct hues on purpose: the band marks a selection, the line is data.
    selectionColor: Color = MaterialTheme.colorScheme.secondary,
    lineColor: Color = MaterialTheme.colorScheme.tertiary,
    gridColor: Color = MaterialTheme.colorScheme.outlineVariant,
) {
    val n = bars.size
    // Index under a horizontal position; mirrors the drawing maths so what you
    // press is what lights up.
    fun IntSize.indexAt(px: Float): Int =
        if (n == 0) 0 else (px / (width.toFloat() / n)).toInt().coerceIn(0, n - 1)

    val tappable = if (onBarClick == null || n == 0) {
        Modifier
    } else {
        Modifier.pointerInput(n) { detectTapGestures { p -> onBarClick(size.indexAt(p.x)) } }
    }
    // The span under the finger, kept HERE rather than reported on every delta:
    // the chart now zooms into whatever range is committed, so reporting mid-drag
    // would collapse the canvas under the finger and remap the indices being read.
    var dragFrom by remember(n) { mutableStateOf<Int?>(null) }
    var dragTo by remember(n) { mutableStateOf<Int?>(null) }

    // Horizontal-only, so the surrounding vertical scroll keeps working.
    val draggable = if (onDragRange == null || n == 0) {
        Modifier
    } else {
        Modifier.pointerInput(n) {
            detectHorizontalDragGestures(
                onDragStart = { p ->
                    dragFrom = size.indexAt(p.x)
                    dragTo = dragFrom
                },
                onHorizontalDrag = { change, _ -> dragTo = size.indexAt(change.position.x) },
                onDragEnd = {
                    val from = dragFrom
                    val to = dragTo
                    dragFrom = null
                    dragTo = null
                    if (from != null && to != null) onDragRange(from, to)
                },
                onDragCancel = {
                    dragFrom = null
                    dragTo = null
                },
            )
        }
    }

    // Live band while dragging, committed band otherwise.
    val band = dragFrom?.let { f -> dragTo?.let { t -> minOf(f, t)..maxOf(f, t) } } ?: selection

    Canvas(
        modifier
            .fillMaxWidth()
            .height(120.dp)
            .then(tappable)
            .then(draggable),
    ) {
        if (n == 0) return@Canvas
        // Thinner gaps as the chart gets denser, so 30 slots still read as bars.
        val gap = (size.width / n * 0.22f).coerceAtMost(3.dp.toPx())
        val barWidth = ((size.width - gap * (n - 1)) / n).coerceAtLeast(1f)
        val step = barWidth + gap

        // Selection band behind the bars, so the picked span reads as one block.
        // Fill + outline: the fill alone is invisible on the near-black dark theme.
        band?.let { sel ->
            val from = sel.first.coerceIn(0, n - 1)
            val to = sel.last.coerceIn(from, n - 1)
            val topLeft = Offset(from * step - gap / 2f, 0f)
            val bandSize = Size((to - from + 1) * step, size.height)
            val corner = CornerRadius(6.dp.toPx(), 6.dp.toPx())
            drawRoundRect(color = selectionColor.copy(alpha = 0.22f), topLeft = topLeft, size = bandSize, cornerRadius = corner)
            drawRoundRect(
                color = selectionColor,
                topLeft = topLeft,
                size = bandSize,
                cornerRadius = corner,
                style = Stroke(width = 1.5.dp.toPx()),
            )
            // Grab handles on the edges — makes the span read as adjustable.
            listOf(topLeft.x, topLeft.x + bandSize.width).forEach { hx ->
                drawRoundRect(
                    color = selectionColor,
                    topLeft = Offset(hx - 1.5.dp.toPx(), size.height / 2f - 10.dp.toPx()),
                    size = Size(3.dp.toPx(), 20.dp.toPx()),
                    cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
                )
            }
        }

        // Pending start of a range: immediate feedback for the very first tap.
        anchor?.takeIf { it in 0 until n }?.let { a ->
            drawRoundRect(
                color = selectionColor.copy(alpha = 0.28f),
                topLeft = Offset(a * step - gap / 2f, 0f),
                size = Size(step, size.height),
                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
            )
            drawCircle(
                color = selectionColor,
                radius = 3.dp.toPx(),
                center = Offset(a * step + barWidth / 2f, 3.dp.toPx()),
            )
        }

        bars.forEachIndexed { i, b ->
            val x = i * step
            val radius = CornerRadius(barWidth / 3f, barWidth / 3f)
            // track — dimmer when that calendar day has no record at all
            drawRoundRect(
                color = incompleteColor.copy(alpha = if (b.hasRecord) 0.4f else 0.18f),
                topLeft = Offset(x, 0f),
                size = Size(barWidth, size.height),
                cornerRadius = radius,
            )
            // value — a day with no record stays an empty track, never a fake 0%
            if (b.hasRecord) {
                val h = b.fraction.coerceIn(0f, 1f) * size.height
                drawRoundRect(
                    // A missed day is a translucent version of the accent, not the
                    // track colour: in the light theme the two were the same grey
                    // and short bars simply vanished.
                    color = if (b.completed) completedColor else completedColor.copy(alpha = 0.42f),
                    topLeft = Offset(x, size.height - h),
                    size = Size(barWidth, h),
                    cornerRadius = radius,
                )
            }
        }

        // Half-way reference, drawn ON TOP: behind the bars it was invisible on a
        // chart this dense, and the trend line needs something to be read against.
        drawLine(
            color = gridColor.copy(alpha = 0.75f),
            start = Offset(0f, size.height / 2f),
            end = Offset(size.width, size.height / 2f),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 8f)),
        )

        // Trend line over the bar centres, broken wherever the average is undefined.
        if (line != null) {
            var path: Path? = null
            fun flush() {
                path?.let { drawPath(it, color = lineColor, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round)) }
                path = null
            }
            line.take(n).forEachIndexed { i, v ->
                if (v == null) {
                    flush()
                    return@forEachIndexed
                }
                val px = i * step + barWidth / 2f
                val py = size.height - v.coerceIn(0f, 1f) * size.height
                val current = path
                if (current == null) path = Path().apply { moveTo(px, py) } else current.lineTo(px, py)
            }
            flush()
        }
    }
}

/**
 * 24-bar hour-of-day chart: how the intake of the chosen period spreads across
 * the clock. Bars are scaled to the busiest hour (relative shape matters more
 * than absolute millilitres here); [highlight] tints the peak block.
 */
@Composable
fun HourlyChart(
    buckets: List<HourBucket>,
    modifier: Modifier = Modifier,
    highlight: IntRange? = null,
    barColor: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
) {
    val max = buckets.maxOfOrNull { it.totalMl } ?: 0
    Column(modifier.fillMaxWidth()) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(110.dp),
        ) {
            if (buckets.isEmpty()) return@Canvas
            val n = buckets.size
            val gap = 2.dp.toPx()
            val barWidth = ((size.width - gap * (n - 1)) / n).coerceAtLeast(1f)
            buckets.forEachIndexed { i, b ->
                val frac = if (max > 0) b.totalMl.toFloat() / max else 0f
                val h = frac * size.height
                val x = i * (barWidth + gap)
                val radius = CornerRadius(barWidth / 2.5f, barWidth / 2.5f)
                drawRoundRect(
                    color = trackColor.copy(alpha = 0.35f),
                    topLeft = Offset(x, 0f),
                    size = Size(barWidth, size.height),
                    cornerRadius = radius,
                )
                if (h > 0f) {
                    val inPeak = highlight != null && b.hour in highlight
                    drawRoundRect(
                        color = if (inPeak) barColor else barColor.copy(alpha = 0.45f),
                        topLeft = Offset(x, size.height - h),
                        size = Size(barWidth, h),
                        cornerRadius = radius,
                    )
                }
            }
        }
        // One label every 6 hours, each owning a quarter of the width so it lines
        // up with the first bar of its block instead of drifting.
        Row(Modifier.fillMaxWidth()) {
            listOf("00", "06", "12", "18").forEach {
                Text(
                    it,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
