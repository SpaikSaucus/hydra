package com.personal.hydra.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.personal.hydra.domain.DayPace
import com.personal.hydra.domain.GoalReach
import com.personal.hydra.domain.HeatCell
import com.personal.hydra.domain.WeekdayStat

/**
 * Average completion per weekday. Labels come from the caller so the domain
 * never touches locales; [labels] must be 7 entries aligned with [stats].
 */
@Composable
fun WeekdayChart(
    stats: List<WeekdayStat>,
    labels: List<String>,
    modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
) {
    Column(modifier.fillMaxWidth()) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(100.dp),
        ) {
            if (stats.isEmpty()) return@Canvas
            val n = stats.size
            val gap = 8.dp.toPx()
            val barWidth = ((size.width - gap * (n - 1)) / n).coerceAtLeast(1f)
            stats.forEachIndexed { i, s ->
                val x = i * (barWidth + gap)
                val radius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                drawRoundRect(
                    color = trackColor.copy(alpha = 0.35f),
                    topLeft = Offset(x, 0f),
                    size = Size(barWidth, size.height),
                    cornerRadius = radius,
                )
                val h = s.averagePercent.toFloat().coerceIn(0f, 1f) * size.height
                if (h > 0f) {
                    // Weakest days read dimmer, so the problem day pops out.
                    val strong = s.averagePercent >= 0.9
                    drawRoundRect(
                        color = if (strong) barColor else barColor.copy(alpha = 0.5f),
                        topLeft = Offset(x, size.height - h),
                        size = Size(barWidth, h),
                        cornerRadius = radius,
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth()) {
            labels.forEach {
                Text(
                    it,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * GitHub-style calendar heat-map: one column per week, Monday at the top.
 *
 * Colours come from [HeatmapPalette], which blends outwards from [cardColor]
 * instead of stacking alpha — on the near-black dark card an alpha ramp collapsed
 * the empty square and the lowest step into the same near-black.
 */
@Composable
fun CalendarHeatmap(
    weeks: List<List<HeatCell>>,
    modifier: Modifier = Modifier,
    cellColor: Color = MaterialTheme.colorScheme.primary,
    cardColor: Color = MaterialTheme.colorScheme.surfaceContainer,
) {
    if (weeks.isEmpty()) return
    val ramp = HeatmapPalette.ramp(cardColor, cellColor)
    // Square side comes from the WIDTH and the canvas height follows; pinning the
    // height first used to leave the squares at ~65% of the card.
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val side = HeatmapLayout.cellSide(maxWidth.value, weeks.size).dp
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(HeatmapLayout.gridHeight(side.value).dp),
        ) {
            val gap = HeatmapLayout.GAP.dp.toPx()
            val cell = side.toPx()
            val radius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
            weeks.forEachIndexed { w, column ->
                column.forEachIndexed { d, c ->
                    val topLeft = Offset(w * (cell + gap), d * (cell + gap))
                    val step = HeatmapPalette.stepOf(c.percent)
                    if (step == 0) {
                        // A day with no record is an OUTLINE, not a fill: shape
                        // separates it from a logged-but-weak day at a glance,
                        // which colour alone can't do on a small square.
                        drawRoundRect(
                            color = ramp[0],
                            topLeft = topLeft,
                            size = Size(cell, cell),
                            cornerRadius = radius,
                            style = Stroke(
                                width = 1.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f)),
                            ),
                        )
                    } else {
                        drawRoundRect(
                            color = ramp[step],
                            topLeft = topLeft,
                            size = Size(cell, cell),
                            cornerRadius = radius,
                        )
                    }
                }
            }
        }
    }
}

/**
 * The same 12 weeks as [CalendarHeatmap], read as a trend instead of a texture:
 * one bar per week at that week's average completion. A week with nothing logged
 * draws the same dashed outline the grid uses, so "no data" never masquerades as
 * a 0% week.
 */
@Composable
fun WeeklyBarsChart(
    values: List<Double?>,
    modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.colorScheme.primary,
    cardColor: Color = MaterialTheme.colorScheme.surfaceContainer,
) {
    if (values.isEmpty()) return
    val trackColor = ChartTint.awayFromCard(cardColor, 0.10f)
    val emptyColor = HeatmapPalette.emptyCell(cardColor)
    Column(modifier.fillMaxWidth()) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(120.dp),
        ) {
            val n = values.size
            val gap = 6.dp.toPx()
            val barWidth = ((size.width - gap * (n - 1)) / n).coerceAtLeast(1f)
            val radius = CornerRadius(barWidth / 4f, barWidth / 4f)
            values.forEachIndexed { i, v ->
                val x = i * (barWidth + gap)
                if (v == null) {
                    drawRoundRect(
                        color = emptyColor,
                        topLeft = Offset(x, 0f),
                        size = Size(barWidth, size.height),
                        cornerRadius = radius,
                        style = Stroke(
                            width = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f)),
                        ),
                    )
                    return@forEachIndexed
                }
                drawRoundRect(
                    color = trackColor,
                    topLeft = Offset(x, 0f),
                    size = Size(barWidth, size.height),
                    cornerRadius = radius,
                )
                val h = v.toFloat().coerceIn(0f, 1f) * size.height
                if (h > 0f) {
                    // Full weeks read solid, short ones translucent — same language
                    // as the day chart, where a missed day is the dimmed accent.
                    drawRoundRect(
                        color = if (v >= 1.0) barColor else barColor.copy(alpha = 0.55f),
                        topLeft = Offset(x, size.height - h),
                        size = Size(barWidth, h),
                        cornerRadius = radius,
                    )
                }
            }
        }
    }
}

/**
 * Colour key for [CalendarHeatmap]: empty, then the four intensity steps. Built
 * from the SAME ramp as the grid, so the two can never drift apart.
 */
@Composable
fun HeatmapLegend(
    lessLabel: String,
    moreLabel: String,
    modifier: Modifier = Modifier,
    cellColor: Color = MaterialTheme.colorScheme.primary,
    cardColor: Color = MaterialTheme.colorScheme.surfaceContainer,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            lessLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HeatmapPalette.ramp(cardColor, cellColor).forEach { c ->
            Canvas(
                Modifier
                    .padding(horizontal = 2.dp)
                    .size(10.dp),
            ) {
                drawRoundRect(color = c, cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()))
            }
        }
        Text(
            moreLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Today's cumulative intake (solid) against the pacing target (dashed), on a
 * 0..24 h axis with the wake->cutoff window shaded and a marker at "now".
 */
@Composable
fun PaceChart(
    pace: DayPace,
    modifier: Modifier = Modifier,
    /** False for a finished day: a "now" marker on last Tuesday means nothing. */
    showNow: Boolean = true,
    actualColor: Color = MaterialTheme.colorScheme.primary,
    idealColor: Color = MaterialTheme.colorScheme.tertiary,
    cardColor: Color = MaterialTheme.colorScheme.surfaceContainer,
) {
    // Blended away from the card rather than alpha-stacked: `surfaceVariant` at
    // 30% over the LIGHT card is almost the card itself, so the drinking window
    // was invisible in the light theme.
    val windowColor = ChartTint.awayFromCard(cardColor, 0.10f)
    val goalLineColor = ChartTint.awayFromCard(cardColor, 0.22f)
    Column(modifier.fillMaxWidth()) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(140.dp),
        ) {
            // The axis tops out at the goal, or at the actual total when it went over.
            val maxMl = maxOf(pace.goalMl, pace.actual.lastOrNull()?.ml ?: 0, 1)
            fun x(minute: Int) = size.width * minute / 1440f
            fun y(ml: Int) = size.height - size.height * ml.coerceIn(0, maxMl) / maxMl.toFloat()

            drawRect(
                color = windowColor,
                topLeft = Offset(x(pace.wakeMinute), 0f),
                size = Size(x(pace.cutoffMinute) - x(pace.wakeMinute), size.height),
            )
            // Goal line — only visibly separate from the top edge on an over-goal day.
            drawLine(
                color = goalLineColor,
                start = Offset(0f, y(pace.goalMl)),
                end = Offset(size.width, y(pace.goalMl)),
                strokeWidth = 1.dp.toPx(),
            )

            fun pathOf(points: List<com.personal.hydra.domain.PacePoint>): Path = Path().apply {
                points.forEachIndexed { i, p ->
                    if (i == 0) moveTo(x(p.minuteOfDay), y(p.ml)) else lineTo(x(p.minuteOfDay), y(p.ml))
                }
            }

            drawPath(
                pathOf(pace.ideal),
                color = idealColor,
                style = Stroke(
                    width = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)),
                ),
            )
            drawPath(
                pathOf(pace.actual),
                color = actualColor,
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
            )
            // One small marker per drink, so the curve says WHEN each glass landed
            // and not just that it climbed. Ringed with the card colour so two
            // drinks close together still read as two.
            pace.drinks.forEach { d ->
                val center = Offset(x(d.minuteOfDay), y(d.ml))
                drawCircle(color = cardColor, radius = 3.5.dp.toPx(), center = center)
                drawCircle(color = actualColor, radius = 2.5.dp.toPx(), center = center)
            }
            if (showNow) {
                drawCircle(
                    color = actualColor,
                    radius = 4.dp.toPx(),
                    center = Offset(x(pace.nowMinute), y(pace.nowActualMl)),
                )
            }
        }
        HourAxisLabels()
    }
}

/**
 * One dot per day, placed horizontally at the clock time the goal was met —
 * same 0..24 h axis as every other chart, so "I finish around 19:00" reads
 * left-to-right. Newest day at the bottom; a vertical dashed line marks the
 * typical (median) time, and days that never reached the goal get a faint
 * marker pinned to the right edge.
 */
@Composable
fun GoalReachChart(
    points: List<GoalReach>,
    medianMinute: Int?,
    modifier: Modifier = Modifier,
    dotColor: Color = MaterialTheme.colorScheme.primary,
    medianColor: Color = MaterialTheme.colorScheme.tertiary,
    gridColor: Color = MaterialTheme.colorScheme.surfaceVariant,
) {
    Column(modifier.fillMaxWidth()) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(130.dp),
        ) {
            if (points.isEmpty()) return@Canvas
            fun x(minute: Int) = size.width * minute / 1440f
            listOf(360, 720, 1080).forEach { m ->
                drawLine(
                    color = gridColor.copy(alpha = 0.45f),
                    start = Offset(x(m), 0f),
                    end = Offset(x(m), size.height),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            medianMinute?.let {
                drawLine(
                    color = medianColor,
                    start = Offset(x(it), 0f),
                    end = Offset(x(it), size.height),
                    strokeWidth = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
                )
            }
            val inset = 5.dp.toPx()
            val usable = (size.height - inset * 2).coerceAtLeast(1f)
            points.forEachIndexed { i, p ->
                val cy = inset + if (points.size > 1) usable * i / (points.size - 1) else usable / 2f
                val minute = p.minuteOfDay
                if (minute == null) {
                    drawCircle(color = gridColor, radius = 2.dp.toPx(), center = Offset(size.width - inset, cy))
                } else {
                    drawCircle(color = dotColor, radius = 3.5.dp.toPx(), center = Offset(x(minute), cy))
                }
            }
        }
        HourAxisLabels()
    }
}

/** Shared 6-hourly axis: each label owns a quarter of the width. */
@Composable
private fun HourAxisLabels() {
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
