package com.personal.hydra.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personal.hydra.di.AppContainer
import com.personal.hydra.domain.GoalCalculator
import com.personal.hydra.domain.SeasonInference
import com.personal.hydra.domain.model.GoalInput
import com.personal.hydra.domain.model.Ranges
import com.personal.hydra.domain.model.Season
import com.personal.hydra.domain.model.UnitSystem
import com.personal.hydra.reminder.ReminderScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Locale

data class OnboardingUiState(
    val countryCode: String = "",
    val unitSystem: UnitSystem = UnitSystem.METRIC,
    val weightKg: Double = 70.0,
    val wakeMin: Int = 7 * 60,
    val sleepMin: Int = 23 * 60,
    val cutoffMin: Int = 180,
    val season: Season = Season.SUMMER,
    val goalMl: Int = 0,
)

class OnboardingViewModel(private val container: AppContainer) : ViewModel() {

    private val settings = container.settingsRepository

    private val _state = MutableStateFlow(initial())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    private fun initial(): OnboardingUiState {
        val country = Locale.getDefault().country
        val unit = if (country.uppercase() in setOf("US", "LR", "MM")) UnitSystem.IMPERIAL else UnitSystem.METRIC
        return withDerived(OnboardingUiState(countryCode = country, unitSystem = unit))
    }

    /** In simple mode heat is automatic, so the goal here always uses season-derived heat. */
    private fun withDerived(st: OnboardingUiState): OnboardingUiState {
        val season = SeasonInference.infer(st.countryCode, LocalDate.now()).season
        val heat = season == Season.SUMMER
        val goal = GoalCalculator.calculate(GoalInput(st.weightKg, Ranges.FACTOR_NORMAL, heat, 0)).goalMl
        return st.copy(season = season, goalMl = goal)
    }

    fun setCountry(code: String) = _state.update { withDerived(it.copy(countryCode = code)) }
    fun setUnit(u: UnitSystem) = _state.update { it.copy(unitSystem = u) }
    fun setWeight(kg: Double) = _state.update { withDerived(it.copy(weightKg = kg.coerceIn(Ranges.WEIGHT_MIN, Ranges.WEIGHT_MAX))) }
    fun setWake(min: Int) = _state.update { it.copy(wakeMin = min) }
    fun setSleep(min: Int) = _state.update { it.copy(sleepMin = min) }

    fun finish(remindersGranted: Boolean, onDone: () -> Unit) {
        viewModelScope.launch {
            val st = _state.value
            settings.setUnitSystem(st.unitSystem)
            settings.setCountry(st.countryCode)
            settings.setWeightKg(st.weightKg)
            settings.setWakeTime(st.wakeMin)
            settings.setSleepTime(st.sleepMin)
            settings.setNightCutoff(st.cutoffMin)
            settings.setHeatMode(st.season == Season.SUMMER, userInitiated = false)
            settings.setRemindersEnabled(remindersGranted)
            settings.markOnboardingDone()
            if (remindersGranted) ReminderScheduler.ensureScheduled(container.appContext)
            onDone()
        }
    }
}
