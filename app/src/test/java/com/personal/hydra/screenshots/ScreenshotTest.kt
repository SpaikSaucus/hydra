package com.personal.hydra.screenshots

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import com.github.takahirom.roborazzi.captureRoboImage
import com.personal.hydra.domain.model.Achievement
import com.personal.hydra.domain.model.DayStat
import com.personal.hydra.domain.model.ThemeMode
import com.personal.hydra.domain.model.UnitSystem
import com.personal.hydra.ui.components.BarValue
import com.personal.hydra.ui.history.HistoryScreen
import com.personal.hydra.ui.history.HistoryUiState
import com.personal.hydra.ui.home.EntryRow
import com.personal.hydra.ui.home.HomeScreen
import com.personal.hydra.ui.home.HomeUiState
import com.personal.hydra.ui.theme.HydraTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/** Stub Application so Robolectric doesn't run HydraApp.onCreate (WorkManager). */
class ScreenshotApplication : Application()

/**
 * Headless screenshot generation (no emulator) via Roborazzi + Robolectric
 * NATIVE graphics. Run: gradle :app:recordRoborazziDebug --tests "*ScreenshotTest".
 * PNGs are written to docs/screenshots for the README.
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

    private fun capture(name: String, dark: Boolean, content: @Composable () -> Unit) {
        captureRoboImage("../docs/screenshots/$name.png") {
            HydraTheme(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT) {
                Surface(color = MaterialTheme.colorScheme.background) { content() }
            }
        }
    }

    private fun sampleHome(unit: UnitSystem = UnitSystem.METRIC, pausedDays: Int = 0): HomeUiState {
        val metric = unit == UnitSystem.METRIC
        val presets = if (metric) listOf(250, 500, 750) else listOf(237, 473, 710)
        val a = if (metric) listOf(500, 500, 250, 500) else listOf(473, 473, 237, 473)
        return HomeUiState(
            loading = false,
            unit = unit,
            goalMl = 2541,
            consumedMl = 1750,
            remainingMl = 791,
            progress = 1750f / 2541f,
            presetsMl = presets,
            remindersEnabled = true,
            pauseRemainingDays = pausedDays,
            entries = listOf(
                EntryRow(4, "2026-06-14", "15:05", a[0]),
                EntryRow(3, "2026-06-14", "12:40", a[1]),
                EntryRow(2, "2026-06-14", "10:15", a[2]),
                EntryRow(1, "2026-06-14", "08:30", a[3]),
            ),
        )
    }

    private fun sampleHistory(unit: UnitSystem = UnitSystem.METRIC): HistoryUiState {
        val chart = listOf(
            0.6f to false, 1f to true, 0.9f to false, 1f to true, 1f to true, 0.7f to false,
            1f to true, 1f to true, 1f to true, 0.85f to false, 1f to true, 1f to true,
            0.95f to false, 0.69f to false,
        ).map { BarValue(it.first, it.second) }
        val unlocked = setOf(
            Achievement.FIRST_DAY, Achievement.DAYS_7, Achievement.DAYS_30,
            Achievement.STREAK_3, Achievement.PERFECT_WEEK, Achievement.STREAK_14,
        )
        return HistoryUiState(
            loading = false,
            unit = unit,
            currentStreak = 12,
            bestStreak = 14,
            daysCompleted = 42,
            averagePercent = 0.88,
            chart = chart,
            days = listOf(
                DayStat(LocalDate.of(2026, 6, 14), 2541, 1750),
                DayStat(LocalDate.of(2026, 6, 13), 2541, 2541),
                DayStat(LocalDate.of(2026, 6, 12), 2541, 2541),
                DayStat(LocalDate.of(2026, 6, 11), 2541, 2100),
            ),
            achievements = Achievement.entries.map { it to (it in unlocked) },
        )
    }
}
