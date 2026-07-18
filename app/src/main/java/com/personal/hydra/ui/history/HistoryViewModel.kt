package com.personal.hydra.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personal.hydra.core.time.DayKeyResolver
import com.personal.hydra.di.AppContainer
import com.personal.hydra.domain.AchievementEvaluator
import com.personal.hydra.domain.StreakCalculator
import com.personal.hydra.domain.model.Achievement
import com.personal.hydra.domain.model.DayStat
import com.personal.hydra.domain.model.UnitSystem
import com.personal.hydra.ui.components.BarValue
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

data class HistoryUiState(
    val loading: Boolean = true,
    val unit: UnitSystem = UnitSystem.METRIC,
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
    val daysCompleted: Int = 0,
    val averagePercent: Double = 0.0,
    val chart: List<BarValue> = emptyList(),
    val days: List<DayStat> = emptyList(),
    val achievements: List<Pair<Achievement, Boolean>> = emptyList(),
)

class HistoryViewModel(container: AppContainer) : ViewModel() {

    private val settings = container.settingsRepository
    private val hydration = container.hydrationRepository
    private val resolver = DayKeyResolver()

    val uiState: StateFlow<HistoryUiState> =
        combine(settings.config, hydration.observeHistory()) { c, dayEntities ->
            val stats = dayEntities.map { DayStat(LocalDate.parse(it.dayKey), it.goalMl, it.totalMl) }
            val today = LocalDate.parse(resolver.todayKey(c.settings.wakeTime))
            val s = StreakCalculator.stats(stats, today, c.pauses)
            val unlocked = AchievementEvaluator.unlocked(s)
            HistoryUiState(
                loading = false,
                unit = c.settings.unitSystem,
                currentStreak = s.currentStreak,
                bestStreak = s.bestStreak,
                daysCompleted = s.daysCompleted,
                averagePercent = s.averagePercent,
                chart = stats.sortedBy { it.date }.takeLast(30)
                    .map { BarValue(minOf(it.percent, 1.0).toFloat(), it.completed) },
                days = stats.sortedByDescending { it.date },
                achievements = Achievement.entries.map { it to (it in unlocked) },
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())
}
