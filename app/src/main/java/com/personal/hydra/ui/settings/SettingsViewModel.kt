package com.personal.hydra.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personal.hydra.core.time.DayKeyResolver
import com.personal.hydra.data.backup.ImportReport
import com.personal.hydra.data.backup.ImportStrategy
import com.personal.hydra.di.AppContainer
import com.personal.hydra.domain.Hydration
import com.personal.hydra.domain.PauseManager
import com.personal.hydra.domain.UnitConverter
import com.personal.hydra.domain.model.AppLanguage
import com.personal.hydra.domain.model.BackupMode
import com.personal.hydra.domain.model.ConfigMode
import com.personal.hydra.domain.model.HydraConfig
import com.personal.hydra.domain.model.Ranges
import com.personal.hydra.domain.model.ThemeMode
import com.personal.hydra.domain.model.UnitSystem
import com.personal.hydra.reminder.ReminderScheduler
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class SettingsUiState(
    val config: HydraConfig = HydraConfig(),
    val goalMl: Int = 0,
    /** Days of pause left including today; 0 = tracking is active. */
    val pauseRemainingDays: Int = 0,
    /** Last paused day (ISO date) of the active pause, null when not paused. */
    val pauseEndDay: String? = null,
)

class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    private val settings = container.settingsRepository
    private val resolver = DayKeyResolver()

    val uiState: StateFlow<SettingsUiState> = settings.config
        .map { c ->
            val today = LocalDate.parse(resolver.todayKey(c.settings.wakeTime))
            SettingsUiState(
                config = c,
                goalMl = Hydration.goal(c, LocalDate.now()).goalMl,
                pauseRemainingDays = PauseManager.remainingDays(c.pauses, today),
                pauseEndDay = PauseManager.activePause(c.pauses, today)?.endDay,
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun setWeight(kg: Double) = run { settings.setWeightKg(kg) }
    fun setUnit(u: UnitSystem) = run { settings.setUnitSystem(u) }
    fun setCountry(code: String) = run { settings.setCountry(code) }
    fun setAdjust(pct: Int) = run { settings.setManualAdjust(pct) }
    fun setFactor(f: Int) = run { settings.setFactor(f) }
    fun setHeat(on: Boolean) = run { settings.setHeatMode(on, userInitiated = true) }
    fun setTheme(m: ThemeMode) = run { settings.setTheme(m) }
    fun setCutoff(min: Int) = run { settings.setNightCutoff(min) }
    fun setMorningShare(pct: Int) = run { settings.setMorningShare(pct) }
    fun setHourlyCap(ml: Int) = run { settings.setHourlyCap(ml) }
    fun setBackupMode(m: BackupMode) = run { settings.setBackupMode(m) }

    fun startPause(days: Int) = run {
        val c = settings.snapshot()
        val today = LocalDate.parse(resolver.todayKey(c.settings.wakeTime))
        settings.setPauses(PauseManager.startPause(c.pauses, today, days))
        container.notifier.cancel() // dismiss any reminder currently showing
    }

    fun resumePause() = run {
        val c = settings.snapshot()
        val today = LocalDate.parse(resolver.todayKey(c.settings.wakeTime))
        settings.setPauses(PauseManager.resumeEarly(c.pauses, today))
    }

    fun setWake(min: Int) = runReschedule { settings.setWakeTime(min) }
    fun setSleep(min: Int) = runReschedule { settings.setSleepTime(min) }
    fun setInterval(min: Int) = runReschedule { settings.setReminderInterval(min) }

    fun setAdvanced(enabled: Boolean) =
        run { settings.setConfigMode(if (enabled) ConfigMode.ADVANCED else ConfigMode.SIMPLE) }

    fun setRemindersEnabled(enabled: Boolean) = viewModelScope.launch {
        settings.setRemindersEnabled(enabled)
        if (enabled) ReminderScheduler.ensureScheduled(container.appContext)
        else ReminderScheduler.cancel(container.appContext)
    }

    fun setLanguage(l: AppLanguage) = run { settings.setLanguage(l) }

    fun addPreset() = run {
        val s = uiState.value.config.settings
        if (s.presetsMl.size < 8) {
            val metric = s.unitSystem == UnitSystem.METRIC
            val stepMl = if (metric) 50 else UnitConverter.flozToMl(1.0)
            var candidate = if (metric) 250 else UnitConverter.flozToMl(8.0)
            while (candidate in s.presetsMl && candidate < Ranges.PRESET_MAX) candidate += stepMl
            settings.setPresets((s.presetsMl + candidate).distinct().sorted())
        }
    }

    fun removePreset(index: Int) = run {
        val current = uiState.value.config.settings.presetsMl
        if (current.size > 1) settings.setPresets(current.filterIndexed { i, _ -> i != index })
    }

    fun setPresetSize(index: Int, deltaMl: Int) = run {
        val current = uiState.value.config.settings.presetsMl.toMutableList()
        if (index in current.indices) {
            current[index] = (current[index] + deltaMl).coerceIn(Ranges.PRESET_MIN, Ranges.PRESET_MAX)
            settings.setPresets(current)
        }
    }

    suspend fun export(uri: Uri): Result<Unit> = container.backupManager.export(uri)

    suspend fun import(uri: Uri): Result<ImportReport> =
        container.backupManager.import(uri, ImportStrategy.REPLACE)
            // The imported config may pause tracking or lower the goal; drop any
            // reminder posted under the old config — the next tick re-evaluates.
            .onSuccess { container.notifier.cancel() }

    private fun run(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    private fun runReschedule(block: suspend () -> Unit) {
        viewModelScope.launch {
            block()
            ReminderScheduler.reschedule(container.appContext)
        }
    }
}
