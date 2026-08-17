package com.personal.hydra.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personal.hydra.R
import com.personal.hydra.domain.ChartPeriod
import com.personal.hydra.domain.model.Achievement
import com.personal.hydra.domain.model.DayStat
import com.personal.hydra.domain.model.HeatmapStyle
import com.personal.hydra.domain.model.UnitSystem
import com.personal.hydra.ui.components.BarChart
import com.personal.hydra.ui.components.CalendarHeatmap
import com.personal.hydra.ui.components.GoalReachChart
import com.personal.hydra.ui.components.HeatmapLegend
import com.personal.hydra.ui.components.HourlyChart
import com.personal.hydra.ui.components.PaceChart
import com.personal.hydra.ui.components.SectionCard
import com.personal.hydra.ui.components.WeekdayChart
import com.personal.hydra.ui.components.WeeklyBarsChart
import com.personal.hydra.ui.hydraViewModel
import com.personal.hydra.util.VolumeFormat
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun HistoryRoute() {
    val vm = hydraViewModel { HistoryViewModel(it) }
    val state by vm.uiState.collectAsStateWithLifecycle()
    HistoryScreen(
        state = state,
        onBarTap = vm::onBarTap,
        onDragRange = vm::onDragRange,
        onClearRange = vm::clearRange,
        onCancelPick = vm::cancelPick,
        onPeriod = vm::setPeriod,
        onHeatmapStyle = vm::setHeatmapStyle,
        onOlderDay = vm::showOlderDay,
        onNewerDay = vm::showNewerDay,
        onTodayPace = vm::showTodayPace,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HistoryScreen(
    state: HistoryUiState,
    onBarTap: (Int) -> Unit = {},
    onDragRange: (Int, Int) -> Unit = { _, _ -> },
    onClearRange: () -> Unit = {},
    onCancelPick: () -> Unit = {},
    onPeriod: (ChartPeriod) -> Unit = {},
    onHeatmapStyle: (HeatmapStyle) -> Unit = {},
    onOlderDay: () -> Unit = {},
    onNewerDay: () -> Unit = {},
    onTodayPace: () -> Unit = {},
) {
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.history_title)) }) }) { padding ->
        // Deliberately hasHistory, not days.isEmpty(): a 7-day period you drank
        // nothing in is an empty PERIOD, not an empty history.
        if (!state.loading && !state.hasHistory) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.no_history),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Scaffold
        }

        // While a pick is pending, a tap ANYWHERE on the page that isn't an
        // interactive child abandons it — including on another chart. Compose
        // gives children the event first, so bars, chips and buttons keep working;
        // the detector only exists during a pick, so idle taps do nothing.
        val picking = state.anchorDate != null
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
                .pointerInput(picking) {
                    if (picking) detectTapGestures { onCancelPick() }
                },
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(stringResource(R.string.stat_current_streak), stringResource(R.string.days_unit, state.currentStreak))
                StatCard(stringResource(R.string.stat_best_streak), stringResource(R.string.days_unit, state.bestStreak))
                StatCard(stringResource(R.string.stat_days_completed), state.daysCompleted.toString())
                StatCard(stringResource(R.string.stat_avg_completion), "${(state.averagePercent * 100).roundToInt()}%")
            }

            // One control for the whole page, at the top where it can't be mistaken
            // for a setting that belongs to a single card.
            PeriodSection(state, onPeriod)

            SectionCard(title = chartTitle(state)) {
                BarChart(
                    bars = state.chart,
                    selection = state.selectedBars,
                    anchor = state.anchorBar,
                    onBarClick = onBarTap,
                    onDragRange = onDragRange,
                    line = state.rollingAverage,
                )
                // Silent when the period is too short for a 7-day mean: a legend
                // about a line that isn't drawn is worse than no legend.
                if (state.rollingAverage.isNotEmpty()) {
                    Text(
                        state.rollingLatest?.let {
                            stringResource(R.string.rolling_avg_value, (it * 100).roundToInt())
                        } ?: stringResource(R.string.rolling_avg_pending),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                RangePicker(state, onClearRange, onCancelPick)
            }

            DayPaceSection(state, onOlderDay, onNewerDay, onTodayPace)
            HourlySection(state)
            GoalReachSection(state)
            WeekdaySection(state)
            HeatmapSection(state, onHeatmapStyle)

            SectionCard(title = stringResource(R.string.achievements)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    state.achievements.forEach { (a, unlocked) -> AchievementBadge(a, unlocked) }
                }
            }

            SectionCard(title = stringResource(R.string.history_title)) {
                PeriodBadge(state)
                state.days.forEach { DayRow(it, state.unit) }
            }
        }
    }
}

/** Title of the bar-chart card: it now follows the period instead of "30 days". */
@Composable
private fun chartTitle(state: HistoryUiState): String =
    if (state.period == ChartPeriod.SELECTION) {
        stringResource(R.string.chart_range_title)
    } else {
        stringResource(R.string.chart_days_title, state.periodWindow?.days ?: 30)
    }

/** Read-out under the chart: picking hint, the span drawn, and its aggregates. */
@Composable
private fun RangePicker(state: HistoryUiState, onClear: () -> Unit, onCancel: () -> Unit) {
    val summary = state.rangeSummary ?: return
    val anchor = state.anchorDate
    if (anchor != null) {
        // A visible way out, so abandoning a pick isn't a gesture you have to guess.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.range_pick_end, anchor.format(dateFmt)),
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
            )
            TextButton(onClick = onCancel) { Text(stringResource(R.string.range_cancel)) }
        }
    } else {
        Text(
            stringResource(R.string.range_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    // Only while zoomed into a pick: for a 7/30/90-day period the Period card
    // above already prints these exact dates, and "clear" would have no range to
    // clear.
    if (state.period == ChartPeriod.SELECTION) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(
                    R.string.range_label,
                    summary.range.from.format(dateFmt),
                    summary.range.to.format(dateFmt),
                ),
                Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
            )
            TextButton(onClick = onClear) { Text(stringResource(R.string.range_clear)) }
        }
    }
    Text(
        stringResource(
            R.string.range_summary,
            VolumeFormat.volume(summary.totalMl, state.unit),
            VolumeFormat.volume(summary.dailyAverageMl, state.unit),
            (summary.averagePercent * 100).roundToInt(),
        ),
        style = MaterialTheme.typography.bodyMedium,
    )
    Text(
        stringResource(
            R.string.range_days_logged,
            summary.loggedDays,
            summary.range.days,
            summary.completedDays,
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Average completion per weekday — surfaces the "I always fail on Saturdays" pattern. */
@Composable
private fun WeekdaySection(state: HistoryUiState) {
    if (state.weekday.isEmpty()) return
    SectionCard(title = stringResource(R.string.weekday_title)) {
        PeriodBadge(state)
        val locale = Locale.getDefault()
        WeekdayChart(
            stats = state.weekday,
            labels = state.weekday.map { it.dayOfWeek.getDisplayName(TextStyle.NARROW, locale) },
        )
        val worst = state.weekday.filter { it.days > 0 }.minByOrNull { it.averagePercent }
        if (worst != null) {
            Text(
                stringResource(
                    R.string.weekday_worst,
                    worst.dayOfWeek.getDisplayName(TextStyle.FULL, locale),
                    (worst.averagePercent * 100).roundToInt(),
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Text(
            stringResource(R.string.weekday_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 12-week calendar heat-map: the long view the day chart can't give. The one card
 * that does NOT follow the period — 12 aligned weeks is the whole point of it —
 * and it says so, so a chip that doesn't move it never reads as broken.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeatmapSection(state: HistoryUiState, onStyle: (HeatmapStyle) -> Unit) {
    if (state.heatmap.isEmpty()) return
    val bars = state.heatmapStyle == HeatmapStyle.BARS
    SectionCard(title = stringResource(R.string.heatmap_title)) {
        Text(
            stringResource(R.string.heatmap_fixed),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Two readings of the same 12 weeks: the grid shows texture (which DAYS
        // you missed), the bars show trend (whether the weeks are improving).
        // The choice is a setting, so it survives leaving the screen.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = !bars,
                onClick = { onStyle(HeatmapStyle.GRID) },
                label = { Text(stringResource(R.string.heatmap_style_grid)) },
            )
            FilterChip(
                selected = bars,
                onClick = { onStyle(HeatmapStyle.BARS) },
                label = { Text(stringResource(R.string.heatmap_style_bars)) },
            )
        }
        if (bars) {
            WeeklyBarsChart(values = state.weeklyAverages)
            Text(
                stringResource(R.string.heatmap_bars_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            CalendarHeatmap(weeks = state.heatmap)
            HeatmapLegend(
                lessLabel = stringResource(R.string.heatmap_less),
                moreLabel = stringResource(R.string.heatmap_more),
            )
            Text(
                stringResource(R.string.heatmap_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The Home pace curve, for any recorded day. Deliberately NOT wired to the page
 * period: you look at one day here, and the ‹ › walk over recorded days only so
 * it can never land on a blank chart.
 */
@Composable
private fun DayPaceSection(
    state: HistoryUiState,
    onOlder: () -> Unit,
    onNewer: () -> Unit,
    onToday: () -> Unit,
) {
    val pace = state.dayPace ?: return
    val date = state.dayPaceDate ?: return
    SectionCard(title = stringResource(R.string.day_pace_title)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onOlder, enabled = state.olderDay != null) {
                Icon(
                    Icons.Rounded.ChevronLeft,
                    contentDescription = stringResource(R.string.day_pace_prev),
                )
            }
            Text(
                date.format(dateFmt),
                Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.Center,
            )
            IconButton(onClick = onNewer, enabled = state.newerDay != null) {
                Icon(
                    Icons.Rounded.ChevronRight,
                    contentDescription = stringResource(R.string.day_pace_next),
                )
            }
            if (!state.dayPaceIsToday) {
                TextButton(onClick = onToday) { Text(stringResource(R.string.day_pace_today)) }
            }
        }
        PaceChart(pace = pace, showNow = state.dayPaceIsToday)
        Text(
            stringResource(
                R.string.day_pace_total,
                VolumeFormat.volume(state.dayPaceTotalMl, state.unit),
                VolumeFormat.volume(state.dayPaceGoalMl, state.unit),
                if (state.dayPaceGoalMl > 0) {
                    (minOf(state.dayPaceTotalMl.toDouble() / state.dayPaceGoalMl, 1.0) * 100).roundToInt()
                } else {
                    0
                },
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            stringResource(R.string.day_pace_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The page-wide period control. It used to live inside the "when you drink" card
 * while silently scoping a second one, which read as a setting that belonged to
 * neither: now it is its own card at the top, it states the days it resolves to,
 * and every card it governs carries a matching badge.
 */
@Composable
private fun PeriodSection(state: HistoryUiState, onPeriod: (ChartPeriod) -> Unit) {
    SectionCard(title = stringResource(R.string.section_period)) {
        PeriodChips(state, onPeriod)
        PeriodWindowLabel(state)
        Text(
            stringResource(R.string.period_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PeriodChips(state: HistoryUiState, onPeriod: (ChartPeriod) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(ChartPeriod.WEEK, ChartPeriod.MONTH, ChartPeriod.QUARTER).forEach { p ->
            FilterChip(
                selected = state.period == p,
                onClick = { onPeriod(p) },
                label = { Text(stringResource(periodLabel(p))) },
            )
        }
        // Always visible so its absence is never mistaken for a broken button;
        // disabled until a range has been drawn on the chart.
        FilterChip(
            selected = state.period == ChartPeriod.SELECTION,
            enabled = state.hasRange,
            onClick = { onPeriod(ChartPeriod.SELECTION) },
            label = { Text(stringResource(R.string.period_range)) },
        )
    }
}

/** The exact days the period resolves to — without them, chips look inert. */
@Composable
private fun PeriodWindowLabel(state: HistoryUiState) {
    val w = state.periodWindow ?: return
    Text(
        stringResource(R.string.period_window, w.from.format(dateFmt), w.to.format(dateFmt), w.days),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.secondary,
    )
}

/** Compact "· 30 days" marking a card as governed by the period control above. */
@Composable
private fun PeriodBadge(state: HistoryUiState) {
    Text(
        stringResource(R.string.period_badge, stringResource(periodLabel(state.period))),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun periodLabel(p: ChartPeriod): Int = when (p) {
    ChartPeriod.WEEK -> R.string.period_7d
    ChartPeriod.MONTH -> R.string.period_30d
    ChartPeriod.QUARTER -> R.string.period_90d
    ChartPeriod.SELECTION -> R.string.period_range
}

/** When the goal was actually met — the metric that matters for sleep. */
@Composable
private fun GoalReachSection(state: HistoryUiState) {
    val reach = state.goalReach ?: return
    SectionCard(title = stringResource(R.string.reach_title)) {
        PeriodBadge(state)
        if (reach.isEmpty) {
            Text(
                stringResource(R.string.reach_none),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@SectionCard
        }
        GoalReachChart(points = reach.points, medianMinute = reach.medianMinute)
        reach.medianMinute?.let {
            Text(
                stringResource(R.string.reach_median, it / 60, it % 60, reach.reachedDays, reach.totalDays),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Text(
            stringResource(R.string.reach_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** "When you drink": hour-of-day distribution over the period. */
@Composable
private fun HourlySection(state: HistoryUiState) {
    val hourly = state.hourly ?: return
    SectionCard(title = stringResource(R.string.hourly_title)) {
        PeriodBadge(state)
        if (hourly.isEmpty) {
            Text(
                stringResource(R.string.hourly_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@SectionCard
        }
        val peakStart = hourly.peakStartHour
        HourlyChart(
            buckets = hourly.buckets,
            highlight = peakStart?.let { it until it + hourly.peakWindowHours },
        )
        if (peakStart != null) {
            Text(
                stringResource(
                    R.string.hourly_peak,
                    peakStart,
                    (peakStart + hourly.peakWindowHours).coerceAtMost(24),
                    hourly.peakSharePct,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Text(
            stringResource(
                R.string.hourly_balance_actual,
                hourly.morningPct,
                hourly.afternoonPct,
                state.morningTargetPct,
                100 - state.morningTargetPct,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            stringResource(R.string.hourly_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatCard(label: String, value: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.width(150.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(value, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AchievementBadge(a: Achievement, unlocked: Boolean) {
    val icon = if (a.kind == Achievement.Kind.DAYS) Icons.Rounded.WaterDrop else Icons.Rounded.LocalFireDepartment
    val tint = if (unlocked) tierColor(a.tier) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(88.dp),
    ) {
        Icon(icon, contentDescription = null, tint = tint)
        Text(
            stringResource(achievementTitle(a)),
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            color = if (unlocked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        )
    }
}

private val dateFmt: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

@Composable
private fun DayRow(day: DayStat, unit: UnitSystem) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(day.date.format(dateFmt), style = MaterialTheme.typography.bodyMedium)
        Text(
            VolumeFormat.volume(day.totalMl, unit) + " / " + VolumeFormat.volume(day.goalMl, unit),
            Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text("${(minOf(day.percent, 1.0) * 100).roundToInt()}%", style = MaterialTheme.typography.bodyMedium)
        if (day.completed) {
            Icon(
                Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

private fun achievementTitle(a: Achievement): Int = when (a) {
    Achievement.FIRST_DAY -> R.string.ach_first_day
    Achievement.DAYS_7 -> R.string.ach_days_7
    Achievement.DAYS_30 -> R.string.ach_days_30
    Achievement.DAYS_100 -> R.string.ach_days_100
    Achievement.DAYS_365 -> R.string.ach_days_365
    Achievement.STREAK_3 -> R.string.ach_streak_3
    Achievement.PERFECT_WEEK -> R.string.ach_perfect_week
    Achievement.STREAK_14 -> R.string.ach_streak_14
    Achievement.STREAK_30 -> R.string.ach_streak_30
    Achievement.STREAK_100 -> R.string.ach_streak_100
    Achievement.STREAK_365 -> R.string.ach_streak_365
}

private fun tierColor(tier: Achievement.Tier): Color = when (tier) {
    Achievement.Tier.BRONZE -> Color(0xFFCD7F32)
    Achievement.Tier.SILVER -> Color(0xFFB0BEC5)
    Achievement.Tier.GOLD -> Color(0xFFFFC107)
}
