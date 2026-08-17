package com.personal.hydra.screenshots

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.personal.hydra.R
import com.personal.hydra.domain.AchievementEvaluator
import com.personal.hydra.domain.ChartPeriod
import com.personal.hydra.domain.ChartSelection
import com.personal.hydra.domain.ChartSelectionPolicy
import com.personal.hydra.domain.DateRange
import com.personal.hydra.domain.GoalReachAnalytics
import com.personal.hydra.domain.HistoryAnalytics
import com.personal.hydra.domain.IntakeDistribution
import com.personal.hydra.domain.PaceCurve
import com.personal.hydra.domain.StreakCalculator
import com.personal.hydra.domain.CaffeineCutoff
import com.personal.hydra.domain.model.Achievement
import com.personal.hydra.domain.model.DayStat
import com.personal.hydra.domain.model.HeatmapStyle
import com.personal.hydra.domain.model.ThemeMode
import com.personal.hydra.domain.model.UnitSystem
import com.personal.hydra.ui.components.BarValue
import com.personal.hydra.ui.components.CalendarHeatmap
import com.personal.hydra.ui.components.HeatmapLegend
import com.personal.hydra.ui.components.SectionCard
import com.personal.hydra.ui.components.WeeklyBarsChart
import com.personal.hydra.ui.history.HistoryScreen
import com.personal.hydra.ui.history.HistoryUiState
import com.personal.hydra.ui.home.CaffeineNotice
import com.personal.hydra.ui.home.EntryRow
import com.personal.hydra.ui.home.HomeScreen
import com.personal.hydra.ui.home.HomeUiState
import com.personal.hydra.ui.theme.HydraTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import java.time.LocalTime

/** Stub Application so Robolectric doesn't run HydraApp.onCreate (WorkManager). */
class ScreenshotApplication : Application()

/**
 * Headless screenshot generation (no emulator) via Roborazzi + Robolectric
 * NATIVE graphics. Run: gradle :app:recordRoborazziDebug --tests "*ScreenshotTest".
 * PNGs are written to docs/screenshots for the README.
 *
 * All the state comes from [Sample]: 12 weeks of deterministic history fed
 * through the REAL domain analytics, so the screenshots show exactly what the
 * app would compute from that data.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-420dpi", application = ScreenshotApplication::class)
class ScreenshotTest {

    @Test fun home_dark() = capture("home-dark", dark = true) { HomeScreen(sampleHome(), {}, { _, _ -> }, { _, _ -> }) }

    @Test fun home_light() = capture("home-light", dark = false) { HomeScreen(sampleHome(), {}, { _, _ -> }, { _, _ -> }) }

    @Test fun history_dark() = capture("history-dark", dark = true) { HistoryScreen(sampleHistory()) }

    @Test fun history_light() = capture("history-light", dark = false) { HistoryScreen(sampleHistory()) }

    @Test fun home_imperial_dark() = capture("home-imperial-dark", dark = true) {
        HomeScreen(sampleHome(UnitSystem.IMPERIAL), {}, { _, _ -> }, { _, _ -> })
    }

    @Test fun home_imperial_light() = capture("home-imperial-light", dark = false) {
        HomeScreen(sampleHome(UnitSystem.IMPERIAL), {}, { _, _ -> }, { _, _ -> })
    }

    @Test fun history_imperial_dark() = capture("history-imperial-dark", dark = true) {
        HistoryScreen(sampleHistory(UnitSystem.IMPERIAL))
    }

    @Test fun history_imperial_light() = capture("history-imperial-light", dark = false) {
        HistoryScreen(sampleHistory(UnitSystem.IMPERIAL))
    }

    @Test fun home_paused_dark() = capture("home-paused-dark", dark = true) {
        HomeScreen(sampleHome(pausedDays = 3), {}, { _, _ -> }, { _, _ -> })
    }

    @Test fun home_paused_light() = capture("home-paused-light", dark = false) {
        HomeScreen(sampleHome(pausedDays = 3), {}, { _, _ -> }, { _, _ -> })
    }

    @Test fun home_muted_dark() = capture("home-muted-dark", dark = true) {
        HomeScreen(sampleHome(muted = true), {}, { _, _ -> }, { _, _ -> })
    }

    @Test fun home_muted_light() = capture("home-muted-light", dark = false) {
        HomeScreen(sampleHome(muted = true), {}, { _, _ -> }, { _, _ -> })
    }

}

/**
 * The two picker states, on a viewport tall enough for the whole chart card — on
 * the 800 dp phone viewport the read-out under the bars falls below the fold, and
 * the read-out is exactly the part worth showing here.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h1200dp-420dpi", application = ScreenshotApplication::class)
class RangeScreenshotTest {

    // A closed 21-day pick: the chart zooms into the range, the card is titled
    // after it, and "Show 30 days" is the way back out.
    @Test fun history_range_dark() = capture("history-range-dark", dark = true) {
        HistoryScreen(sampleHistory(period = ChartPeriod.SELECTION))
    }

    @Test fun history_range_light() = capture("history-range-light", dark = false) {
        HistoryScreen(sampleHistory(period = ChartPeriod.SELECTION))
    }

    // A young install (11 days) mid-pick: the states the 12-week sample can never
    // show — empty calendar slots, a rolling average that only starts once 7
    // consecutive days exist, and the anchor left by the first tap next to the
    // "Cancel" that abandons it.
    @Test fun history_sparse_dark() = capture("history-sparse-dark", dark = true) {
        HistoryScreen(sampleSparseHistory())
    }

    @Test fun history_sparse_light() = capture("history-sparse-light", dark = false) {
        HistoryScreen(sampleSparseHistory())
    }
}

/**
 * The 12-week card in BOTH readings, side by side on one page, from a history
 * that starts 60 days ago. That gap is the point: the calendar's first weeks are
 * dashed outlines (no record), the bars simply skip them, and the recorded part
 * shows the ramp and the trend. No full page can demonstrate that — the 12-week
 * sample is 84 consecutive days.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h620dp-420dpi", application = ScreenshotApplication::class)
class HeatmapStylesScreenshotTest {

    @Test fun heatmap_styles_dark() = capture("heatmap-styles-dark", dark = true) { HeatmapStyles() }

    @Test fun heatmap_styles_light() = capture("heatmap-styles-light", dark = false) { HeatmapStyles() }
}

/**
 * The whole history page in one PNG, on a TALL viewport — and on the 90-day
 * period, so it also shows the page-wide control moving every card at once.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h2400dp-420dpi", application = ScreenshotApplication::class)
class ChartsScreenshotTest {

    @Test fun charts_dark() = capture("charts-dark", dark = true) {
        HistoryScreen(sampleHistory(period = ChartPeriod.QUARTER))
    }

    @Test fun charts_light() = capture("charts-light", dark = false) {
        HistoryScreen(sampleHistory(period = ChartPeriod.QUARTER))
    }
}

/** The whole Home page including the pace chart, on a viewport that just fits it. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h1150dp-420dpi", application = ScreenshotApplication::class)
class HomeFullScreenshotTest {

    @Test fun home_full_dark() = capture("home-full-dark", dark = true) {
        HomeScreen(sampleHome(), {}, { _, _ -> }, { _, _ -> })
    }

    @Test fun home_full_light() = capture("home-full-light", dark = false) {
        HomeScreen(sampleHome(), {}, { _, _ -> }, { _, _ -> })
    }
}

// ---------------------------------------------------------------------------
// Capture helper + sample state, built from Sample through the real analytics.
// ---------------------------------------------------------------------------

/**
 * Both 12-week readings of the SAME data, built from the same public chart
 * composables and the same real analytics the screen uses.
 */
@Composable
private fun HeatmapStyles() {
    val recorded = Sample.days(60)
    val weeks = HistoryAnalytics.heatmap(recorded, Sample.TODAY)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionCard(title = stringResource(R.string.heatmap_title) + " · " + stringResource(R.string.heatmap_style_grid)) {
            CalendarHeatmap(weeks = weeks)
            HeatmapLegend(
                lessLabel = stringResource(R.string.heatmap_less),
                moreLabel = stringResource(R.string.heatmap_more),
            )
        }
        SectionCard(title = stringResource(R.string.heatmap_title) + " · " + stringResource(R.string.heatmap_style_bars)) {
            WeeklyBarsChart(values = HistoryAnalytics.weeklyAverages(weeks, Sample.TODAY))
        }
    }
}

private fun capture(name: String, dark: Boolean, content: @Composable () -> Unit) {
    captureRoboImage("../docs/screenshots/$name.png") {
        HydraTheme(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT) {
            Surface(color = MaterialTheme.colorScheme.background) { content() }
        }
    }
}

// Wake 07:00, sleep 23:00, 3 h night cutoff -> a 780 minute drinking window.
private const val WAKE_MIN = 7 * 60
private const val SLEEP_MIN = 23 * 60
private const val WINDOW_MIN = 780
private const val MORNING_SHARE = 65

private fun sampleHome(
    unit: UnitSystem = UnitSystem.METRIC,
    pausedDays: Int = 0,
    muted: Boolean = false,
): HomeUiState {
    val metric = unit == UnitSystem.METRIC
    val intakes = Sample.todayIntakes()
    val consumed = Sample.TODAY_CONSUMED_ML
    return HomeUiState(
        loading = false,
        unit = unit,
        goalMl = Sample.GOAL_ML,
        consumedMl = consumed,
        remainingMl = Sample.GOAL_ML - consumed,
        progress = consumed.toFloat() / Sample.GOAL_ML,
        presetsMl = if (metric) listOf(250, 500, 750) else listOf(237, 473, 710),
        remindersEnabled = true,
        pauseRemainingDays = pausedDays,
        remindersMuted = muted,
        pace = PaceCurve.of(
            goalMl = Sample.GOAL_ML,
            wakeMinute = WAKE_MIN,
            windowMinutes = WINDOW_MIN,
            morningSharePct = MORNING_SHARE,
            intakes = intakes,
            zone = Sample.ZONE,
            now = Sample.NOW,
        ),
        // Sample.NOW is 15:30 and bedtime is 23:00, so the advisory applies — it is
        // on by default, and these shots should show the app as it ships.
        caffeineNotice = CaffeineNotice(
            fromMinute = CaffeineCutoff.warningStartMinute(SLEEP_MIN),
            sleepMinute = SLEEP_MIN,
        ).takeIf { CaffeineCutoff.shouldWarn(Sample.NOW.hour * 60 + Sample.NOW.minute, SLEEP_MIN) },
        entries = intakes.reversed().mapIndexed { i, it ->
            val t = Instant.ofEpochMilli(it.timestampMillis).atZone(Sample.ZONE).toLocalTime()
            EntryRow(
                id = (intakes.size - i).toLong(),
                dayKey = Sample.TODAY.toString(),
                time = "%02d:%02d".format(t.hour, t.minute),
                amountMl = it.amountMl,
            )
        },
    )
}

/**
 * Mirrors HistoryViewModel exactly: ONE window resolved from the selection drives
 * the chart slots, the aggregates, the day list and every period-scoped card. The
 * heat-map is the deliberate exception — it always spans 12 weeks.
 *
 * Sharing the derivation with the real screen is the point: a screenshot that
 * builds its state by hand slowly stops matching what the app computes.
 */
private fun historyState(
    recorded: List<DayStat>,
    selection: ChartSelection = ChartSelection(),
    unit: UnitSystem = UnitSystem.METRIC,
    intakeDays: Int = 30,
    heatmapStyle: HeatmapStyle = HeatmapStyle.GRID,
): HistoryUiState {
    val byDate = recorded.associateBy { it.date }
    val window = ChartSelectionPolicy.window(selection, Sample.TODAY)
    val slotDates = ChartSelectionPolicy.slots(window)
    val slots = slotDates.map { byDate[it] }
    val rolling = HistoryAnalytics.rollingAverageFor(slots)
    val inWindow = HistoryAnalytics.daysIn(recorded, window)
    val windowIntakes = Sample.intakes(recorded, dayCount = intakeDays).filter {
        Instant.ofEpochMilli(it.timestampMillis).atZone(Sample.ZONE).toLocalDate() in window
    }
    val streaks = StreakCalculator.stats(recorded, Sample.TODAY)
    val unlocked = AchievementEvaluator.unlocked(streaks)
    val range = selection.range

    return HistoryUiState(
        loading = false,
        unit = unit,
        hasHistory = recorded.isNotEmpty(),
        currentStreak = streaks.currentStreak,
        bestStreak = streaks.bestStreak,
        daysCompleted = streaks.daysCompleted,
        averagePercent = streaks.averagePercent,
        chart = slots.map { d ->
            if (d == null) BarValue(0f, false, hasRecord = false)
            else BarValue(minOf(d.percent, 1.0).toFloat(), d.completed)
        },
        chartDates = slotDates,
        rollingAverage = rolling.map { it?.toFloat() },
        rollingLatest = rolling.lastOrNull { it != null },
        selectedBars = range?.takeIf { it != window }?.let { r ->
            val first = slotDates.indexOfFirst { it in r }
            if (first < 0) null else first..slotDates.indexOfLast { it in r }
        },
        anchorBar = selection.anchor?.let { a -> slotDates.indexOf(a).takeIf { it >= 0 } },
        anchorDate = selection.anchor,
        rangeSummary = HistoryAnalytics.summarize(recorded, window),
        days = inWindow.sortedByDescending { it.date },
        achievements = Achievement.entries.map { it to (it in unlocked) },
        period = selection.period,
        hasRange = range != null,
        periodWindow = window,
        hourly = IntakeDistribution.of(windowIntakes, Sample.ZONE, LocalTime.of(7, 0), WINDOW_MIN),
        morningTargetPct = MORNING_SHARE,
        weekday = HistoryAnalytics.byWeekday(inWindow),
        heatmap = HistoryAnalytics.heatmap(recorded, Sample.TODAY),
        weeklyAverages = HistoryAnalytics.weeklyAverages(
            HistoryAnalytics.heatmap(recorded, Sample.TODAY),
            Sample.TODAY,
        ),
        heatmapStyle = heatmapStyle,
        goalReach = GoalReachAnalytics.of(inWindow, windowIntakes, Sample.ZONE),
        // Newest recorded day, exactly what the card opens on.
        dayPace = PaceCurve.of(
            goalMl = Sample.GOAL_ML,
            wakeMinute = WAKE_MIN,
            windowMinutes = WINDOW_MIN,
            morningSharePct = MORNING_SHARE,
            intakes = Sample.todayIntakes(),
            zone = Sample.ZONE,
            now = Sample.NOW,
        ),
        dayPaceDate = Sample.TODAY,
        dayPaceIsToday = true,
        dayPaceTotalMl = Sample.TODAY_CONSUMED_ML,
        dayPaceGoalMl = Sample.GOAL_ML,
        olderDay = recorded.getOrNull(recorded.lastIndex - 1)?.date,
        newerDay = null,
    )
}

/**
 * An install that is only 11 days old, with the first tap of a range already
 * placed. Exercises what a full 12-week sample structurally cannot: 19 empty
 * calendar slots, a 7-day average with only 5 plottable points, and the anchor
 * highlight that gives the first tap its feedback.
 */
private fun sampleSparseHistory(): HistoryUiState = historyState(
    recorded = Sample.days(11),
    // The 23rd of 30 slots: a range's start day, waiting for its end.
    selection = ChartSelection(anchor = Sample.TODAY.minusDays(7)),
    intakeDays = 11,
)

private fun sampleHistory(
    unit: UnitSystem = UnitSystem.METRIC,
    period: ChartPeriod = ChartPeriod.MONTH,
    heatmapStyle: HeatmapStyle = HeatmapStyle.GRID,
): HistoryUiState = historyState(
    recorded = Sample.days(),
    heatmapStyle = heatmapStyle,
    selection = when (period) {
        // A pick zooms the chart into itself, so this also shows the range title
        // and the way back out.
        ChartPeriod.SELECTION -> ChartSelection(
            range = DateRange(Sample.TODAY.minusDays(20), Sample.TODAY),
            period = ChartPeriod.SELECTION,
        )
        else -> ChartSelection(period = period)
    },
    unit = unit,
)
