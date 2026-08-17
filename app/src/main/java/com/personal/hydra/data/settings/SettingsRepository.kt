package com.personal.hydra.data.settings

import com.personal.hydra.domain.model.AppLanguage
import com.personal.hydra.domain.model.BackupMode
import com.personal.hydra.domain.model.ConfigMode
import com.personal.hydra.domain.model.HeatmapStyle
import com.personal.hydra.domain.model.HydraConfig
import com.personal.hydra.domain.model.PausePeriod
import com.personal.hydra.domain.model.ThemeMode
import com.personal.hydra.domain.model.UnitSystem
import kotlinx.coroutines.flow.Flow

/**
 * Typed wrapper over the Preferences DataStore. Each setter validates/clamps to
 * the canonical ranges before persisting. Exposes the whole config reactively.
 */
interface SettingsRepository {
    val config: Flow<HydraConfig>
    suspend fun snapshot(): HydraConfig

    suspend fun setWeightKg(kg: Double)
    suspend fun setFactor(mlPerKg: Int)
    suspend fun setManualAdjust(pct: Int)
    suspend fun setHeatMode(enabled: Boolean, userInitiated: Boolean)
    suspend fun setWakeTime(min: Int)
    suspend fun setSleepTime(min: Int)
    suspend fun setNightCutoff(min: Int)
    suspend fun setMorningShare(pct: Int)
    suspend fun setPauses(pauses: List<PausePeriod>)

    /** Mutes reminders for [dayKey] (ISO date); null clears the mute. */
    suspend fun setRemindersMutedDay(dayKey: String?)
    suspend fun setReminderInterval(min: Int)
    suspend fun setSnooze(min: Int)
    suspend fun setHourlyCap(ml: Int)
    suspend fun setRemindersEnabled(enabled: Boolean)
    suspend fun setConfigMode(mode: ConfigMode)
    suspend fun setUnitSystem(u: UnitSystem)
    suspend fun setCountry(code: String)
    suspend fun setTheme(m: ThemeMode)
    suspend fun setLanguage(l: AppLanguage)
    suspend fun setPresets(presetsMl: List<Int>)
    suspend fun setBackupMode(m: BackupMode)
    suspend fun setCaffeineWarning(enabled: Boolean)
    suspend fun setHeatmapStyle(style: HeatmapStyle)
    suspend fun markOnboardingDone()
    suspend fun replaceConfig(c: HydraConfig)
}
