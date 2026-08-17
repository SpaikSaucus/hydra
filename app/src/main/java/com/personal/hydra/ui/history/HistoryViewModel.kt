@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.personal.hydra.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personal.hydra.core.time.DayKeyResolver
import com.personal.hydra.data.db.entity.DayLogEntity
import com.personal.hydra.di.AppContainer
import com.personal.hydra.domain.AchievementEvaluator
import com.personal.hydra.domain.ChartPeriod
import com.personal.hydra.domain.ChartSelection
import com.personal.hydra.domain.ChartSelectionPolicy
import com.personal.hydra.domain.DateRange
import com.personal.hydra.domain.DistributionSummary
import com.personal.hydra.domain.GoalReachAnalytics
import com.personal.hydra.domain.GoalReachSummary
import com.personal.hydra.domain.DayPace
import com.personal.hydra.domain.HeatCell
import com.personal.hydra.domain.HistoryAnalytics
import com.personal.hydra.domain.IntakeDistribution
import com.personal.hydra.domain.PaceCurve
import com.personal.hydra.domain.RangeSummary
import com.personal.hydra.domain.ScheduleGenerator
import com.personal.hydra.domain.StreakCalculator
import com.personal.hydra.domain.TimedIntake
import com.personal.hydra.domain.WeekdayStat
import com.personal.hydra.domain.model.Achievement
import com.personal.hydra.domain.model.DayStat
import com.personal.hydra.domain.model.HeatmapStyle
import com.personal.hydra.domain.model.HydraConfig
import com.personal.hydra.domain.model.UnitSystem
import com.personal.hydra.ui.components.BarValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

data class HistoryUiState(
    val loading: Boolean = true,
    val unit: UnitSystem = UnitSystem.METRIC,
    /** Whether ANY day was ever recorded — not whether the period holds one. */
    val hasHistory: Boolean = false,
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
    val daysCompleted: Int = 0,
    val averagePercent: Double = 0.0,
    /** One slot per calendar day of the period; no record renders as an empty track. */
    val chart: List<BarValue> = emptyList(),
    /** Dates behind [chart], same order — lets a tap map back to a day. */
    val chartDates: List<LocalDate> = emptyList(),
    /**
     * 7-day trailing mean; null where the window isn't complete, and EMPTY when
     * the period is too short for the line to be more than a single dot.
     */
    val rollingAverage: List<Float?> = emptyList(),
    /** Most recent value of [rollingAverage], for the caption. */
    val rollingLatest: Double? = null,
    /**
     * Bars covered by the picked range, highlighted in the chart. Null once the
     * chart window IS the range — banding the whole canvas says nothing.
     */
    val selectedBars: IntRange? = null,
    /** Bar holding the pending start of a range, after the first tap. */
    val anchorBar: Int? = null,
    val anchorDate: LocalDate? = null,
    /** Aggregates of the current period — always present, not only for a pick. */
    val rangeSummary: RangeSummary? = null,
    /** Day list, scoped to the current period. */
    val days: List<DayStat> = emptyList(),
    val achievements: List<Pair<Achievement, Boolean>> = emptyList(),
    val period: ChartPeriod = ChartPeriod.MONTH,
    /** Whether a range exists, i.e. whether [ChartPeriod.SELECTION] is available. */
    val hasRange: Boolean = false,
    /** Concrete days every period-scoped card covers right now. */
    val periodWindow: DateRange? = null,
    val hourly: DistributionSummary? = null,
    /** Configured morning share, to compare against the observed one. */
    val morningTargetPct: Int = 0,
    /** Average completion per weekday over the period, Monday..Sunday. */
    val weekday: List<WeekdayStat> = emptyList(),
    /** 12-week calendar heat-map, one column per week. Deliberately NOT scoped. */
    val heatmap: List<List<HeatCell>> = emptyList(),
    /** The same 12 weeks averaged per week, for the bar rendering. */
    val weeklyAverages: List<Double?> = emptyList(),
    val heatmapStyle: HeatmapStyle = HeatmapStyle.GRID,
    /** Pace curve of ONE chosen day — independent of [period]. */
    val dayPace: DayPace? = null,
    val dayPaceDate: LocalDate? = null,
    val dayPaceIsToday: Boolean = false,
    val dayPaceTotalMl: Int = 0,
    val dayPaceGoalMl: Int = 0,
    /**
     * The recorded days either side of [dayPaceDate], or null at the ends. Kept
     * as dates rather than booleans so stepping never has to re-scan a list that
     * the period might have filtered.
     */
    val olderDay: LocalDate? = null,
    val newerDay: LocalDate? = null,
    /** When the goal was actually met, over the selected period. */
    val goalReach: GoalReachSummary? = null,
)

class HistoryViewModel(container: AppContainer) : ViewModel() {

    private val settings = container.settingsRepository
    private val hydration = container.hydrationRepository
    private val resolver = DayKeyResolver()
    private val zone: ZoneId = ZoneId.systemDefault()

    /** Whole picker state in one place; the rules live in the domain policy. */
    private val selection = MutableStateFlow(ChartSelection())

    /**
     * Day shown by the pace card. Its own state, NOT part of [ChartSelection]:
     * the card is explicitly independent of the page-wide period. Null means
     * "the most recent recorded day".
     */
    private val paceDay = MutableStateFlow<LocalDate?>(null)

    private data class Inputs(
        val config: HydraConfig,
        val days: List<DayLogEntity>,
        val selection: ChartSelection,
        val paceDay: LocalDate?,
    )

    val uiState: StateFlow<HistoryUiState> =
        combine(settings.config, hydration.observeHistory(), selection, paceDay, ::Inputs)
            .flatMapLatest { i ->
                val today = LocalDate.parse(resolver.todayKey())
                val stats = i.days
                    .map { DayStat(LocalDate.parse(it.dayKey), it.goalMl, it.totalMl) }
                    .sortedBy { it.date }
                val byDate = stats.associateBy { it.date }

                // One window drives the whole page: the bar chart, its aggregates,
                // the day list and every period-scoped card below.
                val window = ChartSelectionPolicy.window(i.selection, today)
                // One slot per calendar day of the window, so a day with no record
                // is a gap rather than a bar squeezed in beside its neighbours.
                val slotDates = ChartSelectionPolicy.slots(window)
                val slots = slotDates.map { byDate[it] }
                val rolling = HistoryAnalytics.rollingAverageFor(slots)
                val inWindow = HistoryAnalytics.daysIn(stats, window)

                val s = i.config.settings
                // Same wake -> night-cutoff window the reminder pacing uses, so the
                // observed balance is comparable with the configured one.
                val sleepFromWake = ScheduleGenerator.minutesFromWake(s.wakeTime, s.sleepTime)
                    .let { if (it == 0) 1440 else it }
                val windowMinutes = (sleepFromWake - s.nightCutoffBeforeSleepMin).coerceIn(1, 1440)

                // The pace card's day, resolved against what is actually recorded.
                val recorded = stats.map { it.date }
                val paceDate = i.paceDay?.takeIf { it in recorded } ?: recorded.lastOrNull()
                val paceIndex = paceDate?.let { recorded.indexOf(it) } ?: -1
                val paceDay = paceDate?.let { d -> i.days.firstOrNull { it.dayKey == d.toString() } }

                combine(
                    hydration.observeEntriesBetween(window.from.toString(), window.to.toString()),
                    hydration.observeEntriesBetween(
                        paceDate?.toString().orEmpty(),
                        paceDate?.toString().orEmpty(),
                    ),
                ) { entries, dayEntries ->
                    val streaks = StreakCalculator.stats(stats, today, i.config.pauses)
                    val unlocked = AchievementEvaluator.unlocked(streaks)
                    val timed = entries.map { e -> TimedIntake(e.timestamp, e.amountMl) }
                    val range = i.selection.range
                    HistoryUiState(
                        loading = false,
                        unit = s.unitSystem,
                        // The empty screen asks "did you ever log anything?", never
                        // "is this period empty?" — a 7-day period with no water is
                        // not an empty history.
                        hasHistory = stats.isNotEmpty(),
                        currentStreak = streaks.currentStreak,
                        bestStreak = streaks.bestStreak,
                        daysCompleted = streaks.daysCompleted,
                        averagePercent = streaks.averagePercent,
                        chart = slots.map { d ->
                            if (d == null) {
                                BarValue(0f, completed = false, hasRecord = false)
                            } else {
                                BarValue(minOf(d.percent, 1.0).toFloat(), d.completed)
                            }
                        },
                        chartDates = slotDates,
                        rollingAverage = rolling.map { it?.toFloat() },
                        rollingLatest = rolling.lastOrNull { it != null },
                        // Only worth drawing while the range is narrower than what
                        // the chart shows, e.g. a saved pick seen from the 7-day view.
                        selectedBars = range?.takeIf { it != window }?.let { r -> barsCovering(slotDates, r) },
                        anchorBar = i.selection.anchor?.let { a -> slotDates.indexOf(a).takeIf { it >= 0 } },
                        anchorDate = i.selection.anchor,
                        rangeSummary = HistoryAnalytics.summarize(stats, window),
                        days = inWindow.sortedByDescending { it.date },
                        achievements = Achievement.entries.map { it to (it in unlocked) },
                        period = i.selection.period,
                        hasRange = range != null,
                        periodWindow = window,
                        hourly = IntakeDistribution.of(
                            intakes = timed,
                            zone = zone,
                            wake = s.wakeTime,
                            windowMinutes = windowMinutes,
                        ),
                        morningTargetPct = s.morningSharePct,
                        weekday = HistoryAnalytics.byWeekday(inWindow),
                        // The long view on purpose: 12 aligned weeks is what the
                        // heat-map is FOR, so it ignores the period and says so.
                        heatmap = HistoryAnalytics.heatmap(stats, today),
                        weeklyAverages = HistoryAnalytics.weeklyAverages(
                            HistoryAnalytics.heatmap(stats, today),
                            today,
                        ),
                        heatmapStyle = s.heatmapStyle,
                        goalReach = GoalReachAnalytics.of(inWindow, timed, zone),
                        // Rebuilt from the day's OWN frozen snapshot (goal, wake,
                        // cutoff), so changing the profile can never rewrite how a
                        // past day is drawn.
                        dayPace = paceDay?.let { d ->
                            PaceCurve.of(
                                goalMl = d.goalMl,
                                wakeMinute = d.wakeMinuteOfDay,
                                windowMinutes = (d.cutoffMinuteOfDay - d.wakeMinuteOfDay).coerceIn(1, 1440),
                                morningSharePct = d.morningSharePct,
                                intakes = dayEntries.map { e -> TimedIntake(e.timestamp, e.amountMl) },
                                zone = zone,
                                now = if (paceDate == today) LocalTime.now() else LocalTime.MAX,
                            )
                        },
                        dayPaceDate = paceDate,
                        dayPaceIsToday = paceDate == today,
                        dayPaceTotalMl = paceDay?.totalMl ?: 0,
                        dayPaceGoalMl = paceDay?.goalMl ?: 0,
                        olderDay = recorded.getOrNull(paceIndex - 1),
                        newerDay = if (paceIndex < 0) null else recorded.getOrNull(paceIndex + 1),
                    )
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    fun onBarTap(index: Int) {
        val date = uiState.value.chartDates.getOrNull(index) ?: return
        selection.value = ChartSelectionPolicy.tap(selection.value, date)
    }

    fun onDragRange(fromIndex: Int, toIndex: Int) {
        val dates = uiState.value.chartDates
        val from = dates.getOrNull(fromIndex) ?: return
        val to = dates.getOrNull(toIndex) ?: return
        selection.value = ChartSelectionPolicy.drag(from, to)
    }

    fun clearRange() {
        selection.value = ChartSelectionPolicy.clear(selection.value)
    }

    /** Abandons a half-finished pick (tap off the bars, or the Cancel button). */
    fun cancelPick() {
        if (selection.value.anchor != null) {
            selection.value = ChartSelectionPolicy.cancel(selection.value)
        }
    }

    fun setPeriod(p: ChartPeriod) {
        selection.value = ChartSelectionPolicy.setPeriod(selection.value, p)
    }

    fun setHeatmapStyle(style: HeatmapStyle) {
        viewModelScope.launch { settings.setHeatmapStyle(style) }
    }

    /** Steps the pace card over RECORDED days only, so it never lands on a blank. */
    fun showOlderDay() {
        uiState.value.olderDay?.let { paceDay.value = it }
    }

    fun showNewerDay() {
        uiState.value.newerDay?.let { paceDay.value = it }
    }

    /** Back to the newest recorded day, i.e. today once anything is logged. */
    fun showTodayPace() {
        paceDay.value = null
    }

    private fun barsCovering(dates: List<LocalDate>, r: DateRange): IntRange? {
        val first = dates.indexOfFirst { it in r }
        if (first < 0) return null
        return first..dates.indexOfLast { it in r }
    }
}
